package lk.nuvi.gemshop.notifications;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lk.nuvi.gemshop.audit.alert.Alerts;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.notifications.api.PushDtos.DeviceForm;
import lk.nuvi.gemshop.notifications.api.PushDtos.PushDeviceView;
import lk.nuvi.gemshop.notifications.api.PushDtos.PushKindSetting;
import lk.nuvi.gemshop.notifications.api.PushDtos.PushSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Push alerts to the owner's phone (P4-02, BRD K1, K2): phones sign up (Web Push subscription), each kind can be turned on
 * or off, and every live alert of a pushed kind goes to every active owner's phones once the alert is saved. A phone the
 * push service no longer knows (404 / 410) is removed.
 */
@Service
public class Push {

    private static final Logger LOG = LoggerFactory.getLogger(Push.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String DEFAULT_LINK = "/approvals/alerts";

    /** What goes to the phone; the app's service worker shows it and opens {@code url} on tap. */
    public record Message(String title, String body, String url, String tag) {}

    private record Device(long id, String endpoint, String p256dh, String auth) {}

    private final JdbcTemplate jdbc;
    private final PushKeys keys;
    private final CurrentUser current;
    private final ShopSettingsRepository shops;
    private final AppProperties.Push config;
    private final HttpClient http;
    private final ExecutorService sender = Executors.newVirtualThreadPerTaskExecutor();

    public Push(JdbcTemplate jdbc, PushKeys keys, CurrentUser current, ShopSettingsRepository shops, AppProperties app) {
        this.jdbc = jdbc;
        this.keys = keys;
        this.current = current;
        this.shops = shops;
        this.config = app.push();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).executor(sender).build();
    }

    // ------------------------------------------------------------------ the phone signs up

    public String publicKey() {
        return keys.get().publicKey();
    }

    @Transactional
    public PushDeviceView addDevice(DeviceForm f) {
        long me = current.require().id();
        checkEndpoint(f.endpoint());
        byte[] p256dh = WebPushCrypto.unb64(f.p256dh());
        if (p256dh.length != 65 || WebPushCrypto.unb64(f.auth()).length != 16) {
            throw ApiException.badRequest("PUSH_KEYS", "The phone sent keys this server cannot use.");
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into push_device (user_id, endpoint, p256dh, auth, device, created_at) values (?, ?, ?, ?, ?, ?)
                on conflict (endpoint) do update set user_id = excluded.user_id, p256dh = excluded.p256dh, auth = excluded.auth,
                    device = excluded.device, failures = 0""",
                me, f.endpoint(), f.p256dh(), f.auth(), blank(f.device()), now);
        return jdbc.queryForObject("select id, device, created_at, last_sent_at from push_device where endpoint = ?", Push::view, f.endpoint());
    }

    /** Stop pushes to this phone / browser (signed out, or turned off on the phone). */
    @Transactional
    public void removeDevice(String endpoint) {
        jdbc.update("delete from push_device where endpoint = ? and user_id = ?", endpoint, current.require().id());
    }

    @Transactional(readOnly = true)
    public PushSettings settings() {
        long me = current.require().id();
        Map<PushKind, Boolean> on = kinds(me);
        return new PushSettings(java.util.Arrays.stream(PushKind.values()).map(k -> new PushKindSetting(k, k.label(), on.get(k))).toList(),
                devices(me));
    }

    @Transactional
    public PushSettings saveSettings(Map<PushKind, Boolean> kinds) {
        long me = current.require().id();
        kinds.forEach((k, v) -> {
            if (k != null && v != null) {
                jdbc.update("insert into push_setting (user_id, kind, enabled) values (?, ?, ?) "
                        + "on conflict (user_id, kind) do update set enabled = excluded.enabled", me, k.name(), v);
            }
        });
        return settings();
    }

    /** "Send a test push to my phone". Returns how many phones / browsers it went to. */
    public int test() {
        long me = current.require().id();
        List<Device> mine = jdbc.query("select id, endpoint, p256dh, auth from push_device where user_id = ?", Push::device, me);
        Message m = new Message("Test push", "Push alerts work on this phone ✓", DEFAULT_LINK, "test");
        mine.forEach(d -> send(d, m));
        return mine.size();
    }

    // ------------------------------------------------------------------ alerts → phones

    /** After the alert is saved: push it to the owners who want this kind. Never slows or breaks the request. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAlert(Alerts.Raised a) {
        PushKind kind = PushKind.of(a.type());
        if (kind == null) {
            return;
        }
        try {
            List<Device> to = jdbc.query("""
                    select d.id, d.endpoint, d.p256dh, d.auth from push_device d join app_user u on u.id = d.user_id
                    where u.role = 'OWNER' and u.active
                      and not exists (select 1 from push_setting s where s.user_id = u.id and s.kind = ? and not s.enabled)""",
                    Push::device, kind.name());
            Message m = new Message(kind.title(), a.text(), a.link() == null || a.link().isBlank() ? DEFAULT_LINK : a.link(),
                    kind.name().toLowerCase(Locale.ROOT) + "-" + a.id());
            to.forEach(d -> send(d, m));
        } catch (RuntimeException e) {
            LOG.warn("Push for alert {} not sent: {}", a.id(), e.toString());
        }
    }

    /** Sends in the background; the answer updates or removes the device. */
    CompletableFuture<Integer> send(Device d, Message m) {
        try {
            checkEndpoint(d.endpoint());
            PushKeys.Vapid v = keys.get();
            byte[] body = WebPushCrypto.encrypt(JSON.writeValueAsString(m).getBytes(StandardCharsets.UTF_8), WebPushCrypto.unb64(d.p256dh()),
                    WebPushCrypto.unb64(d.auth()));
            HttpRequest req = HttpRequest.newBuilder(URI.create(d.endpoint())).timeout(Duration.ofSeconds(15))
                    .header("TTL", "86400").header("Urgency", "high").header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", WebPushCrypto.vapid(d.endpoint(), subject(), v.privateKey(), v.publicKey(), Instant.now()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            return http.sendAsync(req, HttpResponse.BodyHandlers.discarding()).handle((res, err) -> {
                int code = res == null ? 0 : res.statusCode();
                if (code == 404 || code == 410) {
                    jdbc.update("delete from push_device where id = ?", d.id());
                } else if (code >= 200 && code < 300) {
                    jdbc.update("update push_device set last_sent_at = ?, failures = 0 where id = ?", Timestamp.from(Instant.now()), d.id());
                } else {
                    LOG.warn("Push to device {} failed: {}", d.id(), err != null ? err.toString() : "HTTP " + code);
                    jdbc.update("update push_device set failures = failures + 1 where id = ?", d.id());
                }
                return code;
            });
        } catch (Exception e) {
            LOG.warn("Push to device {} not sent: {}", d.id(), e.toString());
            return CompletableFuture.completedFuture(0);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Only real push services (no requests to other servers from here). */
    void checkEndpoint(String endpoint) {
        URI u;
        try {
            u = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("PUSH_ENDPOINT", "Not a push address.");
        }
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
        boolean scheme = "https".equals(u.getScheme()) || (config.allowHttp() && "http".equals(u.getScheme()));
        boolean known = config.allowedHosts().stream().map(h -> h.toLowerCase(Locale.ROOT))
                .anyMatch(h -> h.startsWith(".") ? host.endsWith(h) : host.equals(h));
        if (!scheme || !known) {
            throw ApiException.badRequest("PUSH_ENDPOINT", "This push service is not allowed.");
        }
    }

    private String subject() {
        if (config.subject() != null && !config.subject().isBlank()) {
            return config.subject();
        }
        String mail = shops.current().map(ShopSettings::getEmail).filter(e -> e != null && !e.isBlank()).orElse(null);
        return mail != null ? "mailto:" + mail : "mailto:noreply@example.com";
    }

    private Map<PushKind, Boolean> kinds(long user) {
        Map<PushKind, Boolean> on = new EnumMap<>(PushKind.class);
        for (PushKind k : PushKind.values()) {
            on.put(k, true);
        }
        jdbc.query("select kind, enabled from push_setting where user_id = ?",
                rs -> {
                    on.put(PushKind.valueOf(rs.getString(1)), rs.getBoolean(2));
                }, user);
        return on;
    }

    private List<PushDeviceView> devices(long user) {
        return new ArrayList<>(jdbc.query("select id, device, created_at, last_sent_at from push_device where user_id = ? order by created_at",
                Push::view, user));
    }

    private static PushDeviceView view(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new PushDeviceView(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toInstant(),
                rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant());
    }

    private static Device device(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Device(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4));
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
