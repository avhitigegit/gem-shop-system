package lk.nuvi.gemshop.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.customers.Customer;
import lk.nuvi.gemshop.customers.CustomerBook;
import lk.nuvi.gemshop.customers.CustomerRepository;
import lk.nuvi.gemshop.messaging.api.MessageDtos.BirthdayList;
import lk.nuvi.gemshop.messaging.api.MessageDtos.BirthdayRow;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Birthday wishes (BRD J8): on the customer's birthday (day + month, shop time) a wish goes out by email and / or WhatsApp at
 * the time the owner chose – only to customers who gave permission and did not unsubscribe (rule 8), once a year. The job
 * runs every 10 minutes and sends when the time has come; the owner can also press "Send today's wishes now". Each wish is
 * logged as "System (automatic)". 29 February birthdays get their wish on 28 February in other years.
 */
@Service
public class Birthdays {

    public static final String BIRTHDAYS_OFF = "BIRTHDAYS_OFF";

    private static final Logger LOG = LoggerFactory.getLogger(Birthdays.class);

    private final Messages messages;
    private final CustomerRepository customers;
    private final ShopSettingsRepository shops;
    private final JdbcTemplate jdbc;
    private final ActivityLog log;
    private final ZoneId zone;

    public Birthdays(Messages messages, CustomerRepository customers, ShopSettingsRepository shops, JdbcTemplate jdbc, ActivityLog log,
            AppProperties app) {
        this.messages = messages;
        this.customers = customers;
        this.shops = shops;
        this.jdbc = jdbc;
        this.log = log;
        this.zone = ZoneId.of(app.timezone());
    }

    /** Birthdays in the next 30 days with what happens to each, this year's count and a preview. */
    @Transactional(readOnly = true)
    public BirthdayList list() {
        MessageSettings.Birthday bd = messages.settings().birthday();
        LocalDate today = LocalDate.now(zone);
        Map<Long, Instant> sent = sentThisYear(today.getYear());
        List<BirthdayRow> rows = new ArrayList<>();
        for (Customer c : customers.findAllByOrderByNameAsc()) {
            Integer in = CustomerBook.birthdayIn(c.getBirthday(), today);
            if (in == null || in > 30) {
                continue;
            }
            Instant at = in == 0 ? sent.get(c.getId()) : null;
            String status = !c.canMessage() ? "NO_PERMISSION" : at != null ? "SENT" : !bd.on() ? "OFF" : "WILL_SEND";
            rows.add(new BirthdayRow(c.getCode(), c.getName(), c.getCountry(), c.getBirthday(), in, status, at));
        }
        rows.sort(Comparator.comparingInt(BirthdayRow::birthdayIn).thenComparing(BirthdayRow::name));
        String shop = shopName();
        return new BirthdayList(rows, sent.size(), fill(bd.subject(), "Anna Müller", shop), fill(bd.text(), "Anna Müller", shop));
    }

    /** The owner's "Send today's wishes now". */
    @Transactional
    public int sendNow() {
        if (!messages.settings().birthday().on()) {
            throw ApiException.conflict(BIRTHDAYS_OFF, "Birthday wishes are switched off. Turn them on first.");
        }
        int n = run(true);
        log.add("Birthday wishes sent now", n + " wish(es)", null);
        return n;
    }

    /** The daily job: every 10 minutes, sends once the chosen time has come (shop time). */
    @Scheduled(cron = "0 */10 * * * *", zone = "Asia/Colombo")
    @Transactional
    public void job() {
        try {
            int n = run(false);
            if (n > 0) {
                LOG.info("{} birthday wish(es) sent", n);
            }
        } catch (RuntimeException e) {
            LOG.warn("Birthday wishes not sent: {}", e.toString());
        }
    }

    /**
     * Today's birthdays with permission and no wish this year yet.
     *
     * @param force true = the owner pressed the button (no waiting for the time)
     * @return how many wishes were sent
     */
    @Transactional
    public int run(boolean force) {
        MessageSettings m = messages.settings();
        MessageSettings.Birthday bd = m.birthday();
        if (!bd.on()) {
            return 0;
        }
        LocalDate today = LocalDate.now(zone);
        if (!force && LocalTime.now(zone).isBefore(bd.time())) {
            return 0;
        }
        Map<Long, Instant> sent = sentThisYear(today.getYear());
        String shop = shopName();
        int n = 0;
        for (Customer c : customers.findAllByOrderByNameAsc()) {
            Integer in = CustomerBook.birthdayIn(c.getBirthday(), today);
            if (in == null || in != 0 || !c.canMessage() || sent.containsKey(c.getId())) {
                continue;
            }
            String subject = fill(bd.subject(), c.getName(), shop);
            String text = fill(bd.text(), c.getName(), shop);
            String email = !bd.email() || !m.emailOn() ? "email off" : blank(c.getEmail()) ? "no email"
                    : messages.email(Messages.Kind.BIRTHDAY, c.getId(), c.getEmail(), String.valueOf(today.getYear()), subject, text, null,
                            null, true) ? "sent" : "failed";
            String wa = !bd.whatsapp() || !m.whatsappReady() ? "WhatsApp off" : blank(c.getWhatsapp()) ? "no number"
                    : messages.whatsapp(Messages.Kind.BIRTHDAY, c.getId(), c.getWhatsapp(), String.valueOf(today.getYear()), text, null,
                            null, true) ? "sent" : "failed";
            jdbc.update("""
                    insert into birthday_wish (customer_id, year, at, email, whatsapp, created_at, created_by, updated_at, updated_by)
                    values (?, ?, ?, ?, ?, now(), 'system', now(), 'system') on conflict (customer_id, year) do nothing""",
                    c.getId(), today.getYear(), Timestamp.from(Instant.now()), email, wa);
            log.addSystem("Birthday wish sent", c.getCode() + " · " + c.getName() + " · email: " + email + " · WhatsApp: " + wa, null);
            n++;
        }
        return n;
    }

    /** {name} = the customer's first name, {shop} = the shop name. */
    static String fill(String template, String customerName, String shop) {
        String first = customerName == null ? "" : customerName.trim().split("\\s+")[0];
        return (template == null ? "" : template).replace("{name}", first).replace("{shop}", shop);
    }

    /** One customer's birthday wishes, newest first: at, email status, WhatsApp status (the customer page). */
    @Transactional(readOnly = true)
    public List<Object[]> ofCustomer(long customerId) {
        return jdbc.query("select at, email, whatsapp from birthday_wish where customer_id = ? order by at desc",
                (rs, i) -> new Object[] {rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getString(3)}, customerId);
    }

    private Map<Long, Instant> sentThisYear(int year) {
        Map<Long, Instant> by = new HashMap<>();
        jdbc.query("select customer_id, at from birthday_wish where year = ?",
                rs -> {
                    by.put(rs.getLong(1), rs.getTimestamp(2).toInstant());
                }, year);
        return by;
    }

    private String shopName() {
        return shops.current().map(ShopSettings::getName).orElse("our shop");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
