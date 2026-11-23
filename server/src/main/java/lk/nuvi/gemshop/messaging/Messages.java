package lk.nuvi.gemshop.messaging;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.common.crypto.FieldCrypto;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.mail.MailService;
import lk.nuvi.gemshop.common.mail.SmtpSource;
import lk.nuvi.gemshop.messaging.api.MessageDtos.AutoView;
import lk.nuvi.gemshop.messaging.api.MessageDtos.BirthdayForm;
import lk.nuvi.gemshop.messaging.api.MessageDtos.BirthdayView;
import lk.nuvi.gemshop.messaging.api.MessageDtos.MessageLogRow;
import lk.nuvi.gemshop.messaging.api.MessageDtos.MessageSettingsView;
import lk.nuvi.gemshop.messaging.api.MessageDtos.MessageStatus;
import lk.nuvi.gemshop.messaging.api.MessageDtos.SesForm;
import lk.nuvi.gemshop.messaging.api.MessageDtos.SesView;
import lk.nuvi.gemshop.messaging.api.MessageDtos.SmtpForm;
import lk.nuvi.gemshop.messaging.api.MessageDtos.SmtpView;
import lk.nuvi.gemshop.messaging.api.MessageDtos.SwitchForm;
import lk.nuvi.gemshop.messaging.api.MessageDtos.TestEmailResult;
import lk.nuvi.gemshop.messaging.api.MessageDtos.VerifyResult;
import lk.nuvi.gemshop.messaging.api.MessageDtos.WhatsappForm;
import lk.nuvi.gemshop.messaging.api.MessageDtos.WhatsappView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email &amp; WhatsApp settings (BRD J5, J7; tech stack §6.1, §6.2): the shop's SMTP, Amazon SES for offers, WhatsApp
 * Business, the on / off switches, the automatic messages and the message log. Every customer message is written to the log.
 * Until P6-03 email goes to the test mail server and WhatsApp is in test mode (nothing leaves the system).
 */
@Service
public class Messages implements SmtpSource {

    public static final String EMAIL_OFF = "EMAIL_OFF";
    public static final String WHATSAPP_OFF = "WHATSAPP_OFF";
    public static final String NOT_SENT = "NOT_SENT";

    /** What a message is about (message_log.kind). */
    public enum Kind { INVOICE, QUOTATION, CREDIT_NOTE, THANK_YOU, CLOSING_REPORT, BIRTHDAY, TEST }

    private final JdbcTemplate jdbc;
    private final FieldCrypto crypto;
    private final MailService mail;
    private final WhatsApp whatsapp;
    private final ActivityLog log;
    private final CurrentUser current;

    public Messages(JdbcTemplate jdbc, FieldCrypto crypto, MailService mail, WhatsApp whatsapp, ActivityLog log, CurrentUser current) {
        this.jdbc = jdbc;
        this.crypto = crypto;
        this.mail = mail;
        this.whatsapp = whatsapp;
        this.log = log;
        this.current = current;
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public MessageSettings settings() {
        return jdbc.queryForObject("select * from message_settings where id = 1", (rs, i) -> read(rs));
    }

    @Override
    public Optional<Smtp> smtp() {
        MessageSettings.Smtp s = settings().smtp();
        if (s.host() == null || s.host().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Smtp(s.host(), s.port() == null ? 587 : s.port(), s.security(), s.user(), s.password(), s.from()));
    }

    @Transactional(readOnly = true)
    public MessageSettingsView view() {
        MessageSettings m = settings();
        MessageSettings.Smtp s = m.smtp();
        MessageSettings.Ses e = m.ses();
        MessageSettings.Wa w = m.wa();
        MessageSettings.Auto a = m.auto();
        MessageSettings.Birthday b = m.birthday();
        return new MessageSettingsView(m.emailOn(), mail.testMode(), whatsapp.testMode(),
                new SmtpView(s.host(), s.port(), s.security(), s.user(), present(s.password()), s.from()),
                new SesView(e.enabled(), e.domain(), e.region(), e.from(), e.accessKey(), present(e.secret()), e.spf(), e.dkim()),
                new WhatsappView(w.enabled(), w.phone(), w.accountId(), present(w.token()), w.verified(), w.verifiedAt()),
                new AutoView(a.invoiceEmail(), a.invoiceWhatsapp(), a.thankYou(), a.ownerClosing()),
                new BirthdayView(b.on(), b.email(), b.whatsapp(), hhmm(b.time()), b.subject(), b.text()));
    }

    @Transactional(readOnly = true)
    public MessageStatus status() {
        MessageSettings m = settings();
        return new MessageStatus(m.emailOn(), m.whatsappReady(), whatsapp.testMode(), m.emailOn() && m.auto().invoiceEmail(),
                m.whatsappReady() && m.auto().invoiceWhatsapp());
    }

    // ------------------------------------------------------------------ saving (owner)

    @Transactional
    public MessageSettingsView saveSmtp(SmtpForm f) {
        String pw = blank(f.password()) ? null : crypto.encrypt(f.password());
        jdbc.update("""
                update message_settings set smtp_host = ?, smtp_port = ?, smtp_security = ?, smtp_user = ?,
                       smtp_password = coalesce(?, smtp_password), smtp_from = ?, updated_at = now(), updated_by = ?, version = version + 1
                where id = 1""", f.host().trim(), f.port(), f.security(), f.user().trim(), pw, f.from().trim(), by());
        log.add("Email settings saved", f.host().trim() + ":" + f.port() + " · " + f.security(), null);
        return view();
    }

    @Transactional
    public MessageSettingsView saveSes(SesForm f) {
        if (f.enabled() && (blank(f.domain()) || blank(f.region()) || blank(f.from()))) {
            throw ApiException.badRequest("SES_INCOMPLETE", "Fill in the sending domain, the region and the From address first.");
        }
        String secret = blank(f.secret()) ? null : crypto.encrypt(f.secret());
        jdbc.update("""
                update message_settings set ses_enabled = ?, ses_domain = ?, ses_region = ?, ses_from = ?, ses_access_key = ?,
                       ses_secret = coalesce(?, ses_secret), updated_at = now(), updated_by = ?, version = version + 1
                where id = 1""", f.enabled(), trim(f.domain()), trim(f.region()), trim(f.from()), trim(f.accessKey()), secret, by());
        log.add("Amazon SES settings saved", (f.enabled() ? "on" : "off") + (blank(f.domain()) ? "" : " · " + f.domain().trim()), null);
        return view();
    }

    /**
     * Saves the WhatsApp Business details and checks them. In test mode (until P6-03) the check is on the form of the details
     * only: the account ID is a number and the token looks like a Meta token. P6-03 asks Meta.
     */
    @Transactional
    public VerifyResult verifyWhatsapp(WhatsappForm f) {
        MessageSettings m = settings();
        String token = blank(f.token()) ? m.wa().token() : f.token().trim();
        String phone = f.phone().trim();
        String account = f.accountId().trim();
        String problem = phone.replaceAll("\\D", "").length() < 7 ? "Enter the business phone number with the country code."
                : !account.matches("\\d{6,20}") ? "The Business Account ID is a number (6 digits or more)."
                : token == null || token.length() < 10 ? "Enter the access token from Meta Business Manager."
                : null;
        boolean ok = problem == null;
        jdbc.update("""
                update message_settings set wa_enabled = true, wa_phone = ?, wa_account_id = ?, wa_token = ?, wa_verified = ?,
                       wa_verified_at = ?, updated_at = now(), updated_by = ?, version = version + 1
                where id = 1""", phone, account, token == null ? null : crypto.encrypt(token), ok, ok ? Timestamp.from(Instant.now()) : null,
                by());
        log.add(ok ? "WhatsApp connection verified" : "WhatsApp connection failed", phone, null);
        String msg = ok ? "WhatsApp Business " + phone + " is ready" + (whatsapp.testMode() ? " (test mode – messages are not sent yet)." : ".")
                : problem;
        return new VerifyResult(ok, msg, view());
    }

    /** The on / off switches. Only the ones sent are changed. */
    @Transactional
    public MessageSettingsView switches(SwitchForm f) {
        List<String> said = new ArrayList<>();
        set("email_on", f.emailOn(), "Email messages", said);
        if (f.whatsappOn() != null) {
            jdbc.update("update message_settings set wa_enabled = ?, wa_verified = wa_verified and ?, updated_at = now(), updated_by = ?,"
                    + " version = version + 1 where id = 1", f.whatsappOn(), f.whatsappOn(), by());
            said.add("WhatsApp Premium " + onOff(f.whatsappOn()));
        }
        set("auto_invoice_email", f.invoiceEmail(), "Invoice by email", said);
        set("auto_invoice_whatsapp", f.invoiceWhatsapp(), "Invoice on WhatsApp", said);
        set("auto_thank_you", f.thankYou(), "Thank-you message", said);
        set("auto_owner_closing", f.ownerClosing(), "Day-end report to the owner", said);
        set("bd_on", f.birthdays(), "Birthday wishes", said);
        for (String s : said) {
            log.add(s, "", null);
        }
        return view();
    }

    @Transactional
    public MessageSettingsView saveBirthday(BirthdayForm f) {
        jdbc.update("""
                update message_settings set bd_email = ?, bd_whatsapp = ?, bd_time = ?, bd_subject = ?, bd_text = ?,
                       updated_at = now(), updated_by = ?, version = version + 1
                where id = 1""", f.email(), f.whatsapp(), java.sql.Time.valueOf(LocalTime.parse(f.time())), f.subject().trim(),
                f.text().trim(), by());
        log.add("Birthday wish settings saved", "at " + f.time() + (f.email() ? " · email" : "") + (f.whatsapp() ? " · WhatsApp" : ""), null);
        return view();
    }

    /** "Send test email": to the signed-in owner, through the email that is in use. */
    public TestEmailResult testEmail() {
        if (!settings().emailOn()) {
            throw ApiException.conflict(EMAIL_OFF, "Email messages are switched off. Turn them on first.");
        }
        String to = current.require().email();
        boolean ok = email(Kind.TEST, null, to, null, "Test email from the Gem Shop System",
                "This is a test email. If you can read it, the email settings work.", null, null, false);
        if (!ok) {
            throw ApiException.conflict(NOT_SENT, "The test email could not be sent. Check the SMTP host, port, user and password.");
        }
        return new TestEmailResult(to, mail.via(), mail.testMode());
    }

    // ------------------------------------------------------------------ sending (logged)

    /** Sends an email and writes it to the message log. Does not check the switches – the caller does. */
    public boolean email(Kind kind, Long customerId, String to, String ref, String subject, String text, String fileName, byte[] file,
            boolean automatic) {
        boolean ok = file == null ? mail.send(to, subject, text) : mail.send(to, subject, text, fileName, file, "application/pdf");
        write(kind, "EMAIL", customerId, to, ref, subject, !ok ? "FAILED" : mail.testMode() ? "TEST_MODE" : "SENT", automatic);
        return ok;
    }

    /** Sends a WhatsApp message (test mode until P6-03) and writes it to the message log. */
    public boolean whatsapp(Kind kind, Long customerId, String to, String ref, String text, String fileName, byte[] file,
            boolean automatic) {
        boolean ok = whatsapp.send(to, text, fileName, file);
        write(kind, "WHATSAPP", customerId, to, ref, null, !ok ? "FAILED" : whatsapp.testMode() ? "TEST_MODE" : "SENT", automatic);
        return ok;
    }

    public boolean whatsappTestMode() {
        return whatsapp.testMode();
    }

    /** Already sent (or tried) for this kind and reference, e.g. the thank-you for INV-000141. */
    public boolean logged(Kind kind, String ref) {
        Integer n = jdbc.queryForObject("select count(*) from message_log where kind = ? and ref = ?", Integer.class, kind.name(), ref);
        return n != null && n > 0;
    }

    @Transactional(readOnly = true)
    public List<MessageLogRow> recent(int limit) {
        return jdbc.query("""
                select l.at, l.kind, l.channel, l.recipient, c.name, l.ref, l.subject, l.status, l.automatic
                from message_log l left join customer c on c.id = l.customer_id
                order by l.at desc, l.id desc limit ?""", (rs, i) -> new MessageLogRow(rs.getTimestamp(1).toInstant(), rs.getString(2),
                rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getBoolean(9)),
                Math.max(1, Math.min(limit, 500)));
    }

    /** One customer's thank-you messages (the customer page, P4-03). Rows: at, kind, email sent, WhatsApp sent. */
    @Transactional(readOnly = true)
    public List<Object[]> customerMessages(long customerId) {
        return jdbc.query("""
                select max(at), kind, ref, bool_or(channel = 'EMAIL' and status <> 'FAILED'), bool_or(channel = 'WHATSAPP' and status <> 'FAILED')
                from message_log where customer_id = ? and kind = 'THANK_YOU'
                group by kind, ref""", (rs, i) -> new Object[] {rs.getTimestamp(1).toInstant(), rs.getString(2), rs.getString(3),
                rs.getBoolean(4), rs.getBoolean(5)}, customerId);
    }

    void write(Kind kind, String channel, Long customerId, String to, String ref, String subject, String status, boolean automatic) {
        String who = automatic ? "system" : by();
        jdbc.update("""
                insert into message_log (at, kind, channel, customer_id, recipient, ref, subject, status, automatic,
                                         created_at, created_by, updated_at, updated_by)
                values (now(), ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, now(), ?)""", kind.name(), channel, customerId, cut(to, 200),
                cut(ref, 30), cut(subject, 200), status, automatic, who, who);
    }

    // ------------------------------------------------------------------ helpers

    private void set(String column, Boolean value, String label, List<String> said) {
        if (value == null) {
            return;
        }
        jdbc.update("update message_settings set " + column + " = ?, updated_at = now(), updated_by = ?, version = version + 1 where id = 1",
                value, by());
        said.add(label + " " + onOff(value));
    }

    private MessageSettings read(ResultSet rs) throws SQLException {
        Timestamp verifiedAt = rs.getTimestamp("wa_verified_at");
        Integer port = rs.getObject("smtp_port", Integer.class);
        return new MessageSettings(rs.getBoolean("email_on"),
                new MessageSettings.Smtp(rs.getString("smtp_host"), port,
                        rs.getString("smtp_security"), rs.getString("smtp_user"), decrypt(rs.getString("smtp_password")),
                        rs.getString("smtp_from")),
                new MessageSettings.Ses(rs.getBoolean("ses_enabled"), rs.getString("ses_domain"), rs.getString("ses_region"),
                        rs.getString("ses_from"), rs.getString("ses_access_key"), decrypt(rs.getString("ses_secret")),
                        rs.getBoolean("ses_spf"), rs.getBoolean("ses_dkim")),
                new MessageSettings.Wa(rs.getBoolean("wa_enabled"), rs.getString("wa_phone"), rs.getString("wa_account_id"),
                        decrypt(rs.getString("wa_token")), rs.getBoolean("wa_verified"), verifiedAt == null ? null : verifiedAt.toInstant()),
                new MessageSettings.Auto(rs.getBoolean("auto_invoice_email"), rs.getBoolean("auto_invoice_whatsapp"),
                        rs.getBoolean("auto_thank_you"), rs.getBoolean("auto_owner_closing")),
                new MessageSettings.Birthday(rs.getBoolean("bd_on"), rs.getBoolean("bd_email"), rs.getBoolean("bd_whatsapp"),
                        rs.getTime("bd_time").toLocalTime(), rs.getString("bd_subject"), rs.getString("bd_text")));
    }

    private String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        try {
            return crypto.decrypt(stored);
        } catch (RuntimeException e) {
            return null; // ENCRYPTION_KEY changed: the secret must be typed again
        }
    }

    static String hhmm(LocalTime t) {
        return String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
    }

    private static String by() {
        return CurrentUser.get().map(CurrentUser.Who::email).orElse("system");
    }

    private static boolean present(String s) {
        return s != null && !s.isEmpty();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return blank(s) ? null : s.trim();
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static String onOff(boolean on) {
        return on ? "on" : "off";
    }
}
