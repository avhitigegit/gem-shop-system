package lk.nuvi.gemshop.offers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.config.AuthProperties;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.mail.MailService;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.customers.Customer;
import lk.nuvi.gemshop.customers.CustomerBook;
import lk.nuvi.gemshop.customers.CustomerRepository;
import lk.nuvi.gemshop.messaging.MessageSettings;
import lk.nuvi.gemshop.messaging.Messages;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferAudience;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferAudienceForm;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferDetail;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferForm;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferList;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferOptions;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferRow;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferTemplate;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferTestResult;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferTotals;
import lk.nuvi.gemshop.settings.lists.ListName;
import lk.nuvi.gemshop.settings.lists.Lists;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * Offers (BRD J2, J3, J4, J6): a promotion by email (Amazon SES, picture, unsubscribe link) or WhatsApp (Meta-approved
 * template, "Reply STOP"), to customers chosen by country and gem bought before – only those who gave permission and did not
 * unsubscribe (rule 8). Results: sent, delivered, opened (email: picture pixel, WhatsApp: read receipt), clicked (email link),
 * unsubscribed (the link or STOP within 30 days of the offer). Owner only. Until P6-03 email goes to the test mail server and
 * WhatsApp is in test mode; then "delivered" = accepted.
 */
@Service
public class Offers {

    public static final String OFFER_EMAIL_OFF = "OFFER_EMAIL_OFF";
    public static final String NO_RECIPIENTS = "NO_RECIPIENTS";
    public static final String NOT_DRAFT = "NOT_DRAFT";
    public static final String OFFER_PHOTO = "OFFER_PHOTO";

    /** Meta-approved templates (the shop registers them in Meta Business Manager, P6-03). */
    public enum Template {
        new_arrivals("new_arrivals (Marketing)", "Hello {name}! 💎 New arrivals at {shop}: {text} Reply to reserve. Reply STOP to unsubscribe.",
                true),
        festive_offer("festive_offer (Marketing)", "Season's greetings {name}! 🎉 {text} – {shop}. Reply STOP to unsubscribe.", true),
        thank_you("thank_you (Utility)", "Thank you for your purchase at {shop}, {name}! {text}", false);

        final String label;
        final String text;
        final boolean marketing;

        Template(String label, String text, boolean marketing) {
            this.label = label;
            this.text = text;
            this.marketing = marketing;
        }
    }

    /** Meta marketing price per message in US$ by country (tech stack §6.3); others 0.08. Utility 0.005. Email 0.0001. */
    static final Map<String, BigDecimal> WHATSAPP_RATE = Map.of("Germany", new BigDecimal("0.13"), "United Kingdom",
            new BigDecimal("0.07"), "France", new BigDecimal("0.14"), "USA", new BigDecimal("0.025"), "Australia", new BigDecimal("0.07"),
            "Netherlands", new BigDecimal("0.16"), "India", new BigDecimal("0.011"));

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_PHOTO = 2 * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final CustomerRepository customers;
    private final CustomerBook book;
    private final Messages messages;
    private final Numbers numbers;
    private final Lists lists;
    private final ShopSettingsRepository shops;
    private final UserAccountRepository users;
    private final ActivityLog log;
    private final CurrentUser current;
    private final String appUrl;

    public Offers(JdbcTemplate jdbc, CustomerRepository customers, CustomerBook book, Messages messages, Numbers numbers, Lists lists,
            ShopSettingsRepository shops, UserAccountRepository users, ActivityLog log, CurrentUser current, AuthProperties auth) {
        this.jdbc = jdbc;
        this.customers = customers;
        this.book = book;
        this.messages = messages;
        this.numbers = numbers;
        this.lists = lists;
        this.shops = shops;
        this.users = users;
        this.log = log;
        this.current = current;
        this.appUrl = auth.appUrl().replaceAll("/+$", "");
    }

    // ------------------------------------------------------------------ screens

    @Transactional(readOnly = true)
    public OfferOptions options() {
        MessageSettings m = messages.settings();
        ShopSettings shop = shops.current().orElse(null);
        UserAccount me = users.findById(current.require().id()).orElseThrow();
        List<String> countries = new ArrayList<>(new TreeSet<>(jdbc.queryForList("select distinct country from customer", String.class)));
        List<String> gems = lists.values(ListName.GEM_TYPE).stream().filter(g -> !"Other".equalsIgnoreCase(g)).toList();
        List<OfferTemplate> templates = Arrays.stream(Template.values())
                .map(t -> new OfferTemplate(t.name(), t.label, t.text, t.marketing)).toList();
        return new OfferOptions(countries, gems, templates, m.emailOn() && m.ses().enabled(), m.emailOn(), m.whatsappReady(),
                messages.emailTestMode(), messages.whatsappTestMode(), shopName(), shop == null ? null : shop.getAddress(), website(),
                me.getEmail(), me.getPhone());
    }

    @Transactional(readOnly = true)
    public OfferAudience audience(OfferAudienceForm f) {
        boolean email = "EMAIL".equals(f.channel());
        Picked p = pick(email, f.countries(), f.gems());
        BigDecimal cost = BigDecimal.ZERO;
        Template t = template(f.template()).orElse(Template.new_arrivals);
        for (Customer c : p.ok()) {
            cost = cost.add(email ? new BigDecimal("0.0001")
                    : t.marketing ? WHATSAPP_RATE.getOrDefault(c.getCountry(), new BigDecimal("0.08")) : new BigDecimal("0.005"));
        }
        String first = p.ok().isEmpty() ? (email ? "Anna" : "Emma") : first(p.ok().get(0).getName());
        return new OfferAudience(p.all(), p.ok().size(), p.all() - p.ok().size(), first, cost.setScale(4, RoundingMode.HALF_UP));
    }

    @Transactional(readOnly = true)
    public OfferList list() {
        List<OfferRow> rows = jdbc.query("""
                select o.code, o.channel, o.name, o.subject, o.countries, o.gems, o.sent_at, u.name, o.status, o.sent, o.delivered,
                       o.opened, o.clicked, o.unsubscribed
                from offer o left join app_user u on u.id = o.sent_by
                order by o.status = 'DRAFT' desc, o.sent_at desc nulls first, o.id desc""", (rs, i) -> {
            Timestamp at = rs.getTimestamp(7);
            return new OfferRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    audienceText(split(rs.getString(5)), split(rs.getString(6))), at == null ? null : at.toInstant(), rs.getString(8),
                    rs.getString(9), rs.getInt(10), rs.getInt(11), rs.getInt(12), rs.getInt(13), rs.getInt(14));
        });
        int offers = 0;
        int[] t = new int[5];
        for (OfferRow r : rows) {
            if ("SENT".equals(r.status())) {
                offers++;
                t[0] += r.sent();
                t[1] += r.delivered();
                t[2] += r.opened();
                t[3] += r.clicked();
                t[4] += r.unsubscribed();
            }
        }
        return new OfferList(rows, new OfferTotals(offers, t[0], t[1], t[2], t[3], t[4]));
    }

    @Transactional(readOnly = true)
    public OfferDetail detail(String code) {
        return jdbc.query("""
                select code, channel, name, subject, template, body, picture, photo, photo_type, countries, gems, status
                from offer where code = ?""", (rs, i) -> {
            byte[] photo = rs.getBytes(8);
            return new OfferDetail(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                    rs.getString(7), photo == null ? null : "data:" + rs.getString(9) + ";base64," + Base64.getEncoder().encodeToString(photo),
                    split(rs.getString(10)), split(rs.getString(11)), rs.getString(12));
        }, code).stream().findFirst().orElseThrow(() -> ApiException.notFound("Offer " + code + " not found."));
    }

    // ------------------------------------------------------------------ drafts

    @Transactional
    public OfferDetail saveDraft(OfferForm f) {
        String code = save(f);
        log.add("Offer draft saved", code + " · " + f.name().trim(), null);
        return detail(code);
    }

    @Transactional
    public void deleteDraft(String code) {
        String status = status(code);
        if (!"DRAFT".equals(status)) {
            throw ApiException.conflict(NOT_DRAFT, "Only a draft can be deleted. Sent offers stay for the results.");
        }
        jdbc.update("delete from offer where code = ?", code);
        log.add("Offer draft deleted", code, null);
    }

    // ------------------------------------------------------------------ sending

    /** Saves the offer and sends it to everyone chosen. Returns the offer's row in the results. */
    @Transactional
    public OfferRow send(OfferForm f) {
        boolean email = "EMAIL".equals(f.channel());
        ready(email, true);
        Picked p = pick(email, f.countries(), f.gems());
        if (p.ok().isEmpty()) {
            throw ApiException.conflict(NO_RECIPIENTS, "No customers with permission match these filters.");
        }
        String code = save(f);
        Stored o = stored(code);
        Instant now = Instant.now();
        int sent = 0;
        int delivered = 0;
        for (Customer c : p.ok()) {
            String token = token();
            String status = email ? messages.offerEmail(c.getId(), code, rich(o, c, token))
                    : messages.offerWhatsapp(c.getId(), c.getWhatsapp(), code, whatsappText(o, c), photoName(o), o.photo(), token);
            boolean ok = !"FAILED".equals(status);
            // test mode / email accepted = delivered; real WhatsApp waits for Meta's "delivered" status
            boolean del = ok && (email || "TEST_MODE".equals(status));
            jdbc.update("""
                    insert into offer_recipient (offer_id, customer_id, recipient, token, status, sent_at, delivered_at, created_by,
                                                 updated_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)""", o.id(), c.getId(), email ? c.getEmail() : c.getWhatsapp(), token, status,
                    Timestamp.from(now), del ? Timestamp.from(now) : null, by(), by());
            sent += ok ? 1 : 0;
            delivered += del ? 1 : 0;
        }
        jdbc.update("""
                update offer set status = 'SENT', sent_at = ?, sent_by = ?, sent = ?, delivered = ?, updated_at = now(), updated_by = ?,
                       version = version + 1 where id = ?""", Timestamp.from(now), current.require().id(), sent, delivered, by(), o.id());
        log.add("Offer sent", code + " · " + o.name() + " · " + (email ? "email" : "WhatsApp") + " · " + sent + " recipient(s)", null);
        return list().offers().stream().filter(r -> r.code().equals(code)).findFirst().orElseThrow();
    }

    /** "Send test to me": to the signed-in owner's email or phone. Not counted; written to the message log. */
    @Transactional
    public OfferTestResult test(OfferForm f) {
        boolean email = "EMAIL".equals(f.channel());
        ready(email, false);
        check(f);
        UserAccount me = users.findById(current.require().id()).orElseThrow();
        Photo photo = photo(f.photo());
        Stored o = new Stored(0, "TEST", f.name().trim(), trim(f.subject()), trim(f.template()), f.body().trim(),
                photo == null ? null : photo.bytes(), photo == null ? null : photo.type());
        if (email) {
            MailService.Rich r = new MailService.Rich(me.getEmail(), "[Test] " + o.subject(), text(o, me.getName(), "#"),
                    html(o, me.getName(), null, "#"), o.photo(), o.photoType(), null, true);
            String status = messages.offerEmail(null, "TEST", r);
            if ("FAILED".equals(status)) {
                throw ApiException.conflict(Messages.NOT_SENT, "The test email could not be sent. Check the email settings.");
            }
            return new OfferTestResult(me.getEmail(), "TEST_MODE".equals(status));
        }
        if (blank(me.getPhone())) {
            throw ApiException.conflict("NO_PHONE", "Add your mobile number in Users first (Settings → Users).");
        }
        String status = messages.offerWhatsapp(null, me.getPhone(), "TEST", fillWhatsapp(o, me.getName()), photoName(o), o.photo(), null);
        return new OfferTestResult(me.getPhone(), "TEST_MODE".equals(status));
    }

    // ------------------------------------------------------------------ results (public tracking, Meta statuses, unsubscribes)

    /** The picture pixel in the email was loaded. */
    @Transactional
    public void opened(String token) {
        recipient(token).ifPresent(r -> mark(r, "opened"));
    }

    /** The link in the email was clicked. Returns where to go (the shop's website). */
    @Transactional
    public String clicked(String token) {
        recipient(token).ifPresent(r -> {
            mark(r, "opened");
            mark(r, "clicked");
        });
        String w = website();
        return w == null ? appUrl : w;
    }

    /** WhatsApp status from Meta (P6-03): "delivered" or "read" for the recipient with this callback token. */
    @Transactional
    public void whatsappStatus(String token, String status) {
        if (!"delivered".equals(status) && !"read".equals(status)) {
            return;
        }
        recipient(token).ifPresent(r -> {
            mark(r, "delivered");
            if ("read".equals(status)) {
                mark(r, "opened");
            }
        });
    }

    /** A customer unsubscribed: counted on the last offer they got in the 30 days before. */
    @EventListener
    public void onUnsubscribed(CustomerBook.Unsubscribed e) {
        Instant at = e.at() == null ? Instant.now() : e.at();
        jdbc.query("""
                select id, offer_id from offer_recipient
                where customer_id = ? and sent_at between ? and ? and unsubscribed_at is null
                order by sent_at desc limit 1""", rs -> {
            long id = rs.getLong(1);
            jdbc.update("update offer_recipient set unsubscribed_at = ?, updated_at = now(), updated_by = 'system', version = version + 1"
                    + " where id = ?", Timestamp.from(at), id);
            jdbc.update("update offer set unsubscribed = unsubscribed + 1 where id = ?", rs.getLong(2));
        }, e.customerId(), Timestamp.from(at.minus(Duration.ofDays(30))), Timestamp.from(at));
    }

    // ------------------------------------------------------------------ helpers

    record Picked(int all, List<Customer> ok) {}

    record Stored(long id, String code, String name, String subject, String template, String body, byte[] photo, String photoType) {}

    record Photo(byte[] bytes, String type) {}

    record Recipient(long id, long offerId) {}

    /** Customers matching the countries and gems; ok = permission, not unsubscribed, with an email / number (rule 8). */
    Picked pick(boolean email, List<String> countries, List<String> gems) {
        Set<String> cs = clean(countries);
        Set<String> gs = clean(gems);
        Map<Long, Set<String>> bought = new HashMap<>();
        if (!gs.isEmpty()) {
            jdbc.query("""
                    select distinct s.customer_id, g.gem_type from sale s join sale_item i on i.sale_id = s.id join gem g on g.id = i.gem_id
                    where s.status = 'COMPLETED' and not i.released""",
                    rs -> {
                        bought.computeIfAbsent(rs.getLong(1), k -> new HashSet<>()).add(rs.getString(2));
                    });
        }
        int all = 0;
        List<Customer> ok = new ArrayList<>();
        for (Customer c : customers.findAllByOrderByNameAsc()) {
            if (!cs.isEmpty() && !cs.contains(c.getCountry())) {
                continue;
            }
            if (!gs.isEmpty() && bought.getOrDefault(c.getId(), Set.of()).stream().noneMatch(gs::contains)) {
                continue;
            }
            all++;
            if (c.canMessage() && !blank(email ? c.getEmail() : c.getWhatsapp())) {
                ok.add(c);
            }
        }
        return new Picked(all, ok);
    }

    private void ready(boolean email, boolean bulk) {
        MessageSettings m = messages.settings();
        if (email && (!m.emailOn() || (bulk && !m.ses().enabled()))) {
            throw ApiException.conflict(OFFER_EMAIL_OFF, !m.emailOn() ? "Email messages are switched off in Email & WhatsApp settings."
                    : "Amazon SES for offers is off. Turn it on in Email & WhatsApp settings.");
        }
        if (!email && !m.whatsappReady()) {
            throw ApiException.conflict(Messages.WHATSAPP_OFF, "WhatsApp is off or not verified. Turn it on in Email & WhatsApp settings.");
        }
    }

    private void check(OfferForm f) {
        if ("EMAIL".equals(f.channel()) && blank(f.subject())) {
            throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Please check the highlighted fields.",
                    "subject", "Enter the email subject.");
        }
        if ("WHATSAPP".equals(f.channel())) {
            if (template(f.template()).isEmpty()) {
                throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                        "Please check the highlighted fields.", "template", "Choose a template.");
            }
            if (f.body().trim().length() > 600) {
                throw ApiException.field(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                        "Please check the highlighted fields.", "body", "Keep the WhatsApp text under 600 characters.");
            }
        }
    }

    /** Creates or updates the draft; returns its code. */
    private String save(OfferForm f) {
        check(f);
        Photo photo = photo(f.photo());
        boolean noPicture = blank(f.picture());
        String countries = String.join("|", clean(f.countries()));
        String gems = String.join("|", clean(f.gems()));
        boolean email = "EMAIL".equals(f.channel());
        String subject = email ? trim(f.subject()) : null;
        String template = email ? null : trim(f.template());
        if (!blank(f.code())) {
            String status = status(f.code());
            if (!"DRAFT".equals(status)) {
                throw ApiException.conflict(NOT_DRAFT, "Offer " + f.code() + " was already sent. Start a new offer.");
            }
            jdbc.update("""
                    update offer set channel = ?, name = ?, subject = ?, template = ?, body = ?, picture = ?, countries = ?, gems = ?,
                           photo = case when ? then null when ? then ? else photo end,
                           photo_type = case when ? then null when ? then ? else photo_type end,
                           updated_at = now(), updated_by = ?, version = version + 1
                    where code = ?""", f.channel(), f.name().trim(), subject, template, f.body().trim(), trim(f.picture()), countries, gems,
                    noPicture, photo != null, photo == null ? null : photo.bytes(), noPicture, photo != null,
                    photo == null ? null : photo.type(), by(), f.code());
            return f.code();
        }
        String code = numbers.next(Counter.OFFER);
        jdbc.update("""
                insert into offer (code, channel, name, subject, template, body, picture, photo, photo_type, countries, gems,
                                   created_at, created_by, updated_at, updated_by)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, now(), ?)""", code, f.channel(), f.name().trim(), subject, template,
                f.body().trim(), trim(f.picture()), noPicture || photo == null ? null : photo.bytes(),
                noPicture || photo == null ? null : photo.type(), countries, gems, by(), by());
        return code;
    }

    private String status(String code) {
        return jdbc.query("select status from offer where code = ?", (rs, i) -> rs.getString(1), code).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Offer " + code + " not found."));
    }

    private Stored stored(String code) {
        return jdbc.queryForObject("select id, code, name, subject, template, body, photo, photo_type from offer where code = ?",
                (rs, i) -> new Stored(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getBytes(7), rs.getString(8)), code);
    }

    /** "data:image/png;base64,…" → the picture (PNG / JPEG, up to 2 MB), or null. */
    static Photo photo(String dataUrl) {
        if (blank(dataUrl)) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^data:(image/(?:png|jpeg));base64,(.+)$", java.util.regex.Pattern.DOTALL)
                .matcher(dataUrl.trim());
        byte[] bytes;
        try {
            bytes = m.matches() ? Base64.getMimeDecoder().decode(m.group(2)) : null;
        } catch (IllegalArgumentException e) {
            bytes = null;
        }
        if (bytes == null || bytes.length == 0) {
            throw ApiException.badRequest(OFFER_PHOTO, "The picture must be a PNG or JPEG photo.");
        }
        if (bytes.length > MAX_PHOTO) {
            throw ApiException.badRequest(OFFER_PHOTO, "The picture is too large (2 MB at most).");
        }
        return new Photo(bytes, m.group(1));
    }

    private MailService.Rich rich(Stored o, Customer c, String token) {
        String unsubscribe = book.unsubscribeLink(c);
        return new MailService.Rich(c.getEmail(), o.subject(), text(o, c.getName(), unsubscribe), html(o, c.getName(), token, unsubscribe), o.photo(),
                o.photoType(), unsubscribe, true);
    }

    String text(Stored o, String customerName, String unsubscribe) {
        ShopSettings shop = shops.current().orElse(null);
        String w = website();
        return o.body().replace("{name}", first(customerName)) + (w == null ? "" : "\n\n" + w) + "\n\n--\n" + shopName()
                + (shop == null || blank(shop.getAddress()) ? "" : "\n" + shop.getAddress())
                + "\nYou receive this because you agreed at our shop. Unsubscribe: " + unsubscribe;
    }

    /** The offer email: shop name, picture, subject, text, a button to the website, address and unsubscribe; the open pixel. */
    String html(Stored o, String customerName, String token, String unsubscribe) {
        ShopSettings shop = shops.current().orElse(null);
        String w = website();
        String esc = HtmlUtils.htmlEscape(o.body().replace("{name}", first(customerName))).replace("\n", "<br>");
        String track = token == null ? null : appUrl + "/api/public/offers/" + token;
        String link = w == null ? null : track == null ? w : track + "/click";
        StringBuilder h = new StringBuilder();
        h.append("<div style=\"background:#eef1f6;padding:16px;font-family:Segoe UI,Arial,sans-serif\">")
                .append("<div style=\"max-width:560px;margin:0 auto;background:#fff;border-radius:8px;overflow:hidden\">")
                .append("<div style=\"background:#0b1f3a;color:#fff;padding:14px 18px;font-weight:bold\">")
                .append(HtmlUtils.htmlEscape(shopName())).append("</div>");
        if (o.photo() != null) {
            h.append("<img src=\"cid:picture\" alt=\"\" style=\"display:block;width:100%;max-height:260px;object-fit:contain;background:#f4f6fb\">");
        }
        h.append("<div style=\"padding:16px 18px;color:#0f172a;font-size:14px\"><div style=\"font-size:17px;font-weight:bold\">")
                .append(HtmlUtils.htmlEscape(o.subject() == null ? "" : o.subject())).append("</div><p>").append(esc).append("</p>");
        if (link != null) {
            h.append("<a href=\"").append(HtmlUtils.htmlEscape(link))
                    .append("\" style=\"display:inline-block;background:#c9a227;color:#0b1f3a;padding:10px 18px;border-radius:6px;")
                    .append("text-decoration:none;font-weight:bold\">View our gems</a>");
        }
        h.append("</div><div style=\"padding:10px 18px;background:#f8fafc;font-size:11px;color:#64748b\">")
                .append(shop == null || blank(shop.getAddress()) ? "" : HtmlUtils.htmlEscape(shop.getAddress()) + "<br>")
                .append("You receive this because you agreed at our shop. <a href=\"").append(HtmlUtils.htmlEscape(unsubscribe))
                .append("\" style=\"color:#64748b\">Unsubscribe</a></div></div>");
        if (track != null) {
            h.append("<img src=\"").append(track).append("/open.gif\" width=\"1\" height=\"1\" alt=\"\" style=\"display:block\">");
        }
        return h.append("</div>").toString();
    }

    private String whatsappText(Stored o, Customer c) {
        return fillWhatsapp(o, c.getName());
    }

    String fillWhatsapp(Stored o, String customerName) {
        String t = template(o.template()).map(x -> x.text).orElse("{text}");
        return t.replace("{text}", o.body()).replace("{name}", first(customerName)).replace("{shop}", shopName());
    }

    private static String photoName(Stored o) {
        return o.photo() == null ? null : "offer." + ("image/png".equals(o.photoType()) ? "png" : "jpg");
    }

    private Optional<Recipient> recipient(String token) {
        if (blank(token) || token.length() > 64) {
            return Optional.empty();
        }
        return jdbc.query("select id, offer_id from offer_recipient where token = ?", (rs, i) -> new Recipient(rs.getLong(1), rs.getLong(2)),
                token).stream().findFirst();
    }

    /** Sets delivered / opened / clicked once and counts it on the offer. */
    private void mark(Recipient r, String what) {
        int n = jdbc.update("update offer_recipient set " + what + "_at = now(), updated_at = now(), updated_by = 'system', version = version + 1"
                + " where id = ? and " + what + "_at is null", r.id());
        if (n > 0) {
            jdbc.update("update offer set " + what + " = " + what + " + 1 where id = ?", r.offerId());
        }
    }

    static Optional<Template> template(String key) {
        return Arrays.stream(Template.values()).filter(t -> t.name().equals(key)).findFirst();
    }

    static String audienceText(List<String> countries, List<String> gems) {
        String s = String.join(" · ", List.of(String.join(", ", countries), String.join(", ", gems)).stream().filter(x -> !x.isEmpty()).toList());
        return s.isEmpty() ? "All with permission" : s;
    }

    static List<String> split(String s) {
        return s == null || s.isEmpty() ? List.of() : List.of(s.split("\\|"));
    }

    private static Set<String> clean(List<String> list) {
        Set<String> out = new java.util.LinkedHashSet<>();
        if (list != null) {
            for (String s : list) {
                if (!blank(s)) {
                    out.add(s.trim().replace("|", ""));
                }
            }
        }
        return out;
    }

    private String website() {
        String w = shops.current().map(ShopSettings::getWebsite).orElse(null);
        return blank(w) ? null : w.trim().matches("(?i)^https?://.*") ? w.trim() : "https://" + w.trim();
    }

    private String shopName() {
        return shops.current().map(ShopSettings::getName).orElse("our shop");
    }

    private static String token() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String first(String name) {
        return name == null ? "" : name.trim().split("\\s+")[0];
    }

    private static String by() {
        return CurrentUser.get().map(CurrentUser.Who::email).orElse("system");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return blank(s) ? null : s.trim();
    }
}
