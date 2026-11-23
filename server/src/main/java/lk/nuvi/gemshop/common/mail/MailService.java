package lk.nuvi.gemshop.common.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends email through the shop's own SMTP (invoices, quotations, password reset, messages).
 * Until {@code app.mail.use-settings} is on (P6-03) every email goes to the test mail server from {@code spring.mail}
 * (MailHog on your own computer, http://localhost:8025). With it on, the SMTP saved on the Email &amp; WhatsApp settings
 * screen is used. Offers use Amazon SES (P4-05 / P6-03).
 * The sender is the shop: "Ceylon Gem House &lt;sales@…&gt;" (the SMTP "From", else Shop details).
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender testSender;
    private final ShopSettingsRepository shops;
    private final String fallbackFrom;
    private final ObjectProvider<SmtpSource> source;
    private final boolean useSettings;
    private volatile SmtpSource.Smtp cachedFor;
    private volatile JavaMailSenderImpl cached;
    private volatile SmtpSource.Smtp cachedSesFor;
    private volatile JavaMailSenderImpl cachedSes;

    /**
     * An offer email (P4-05): text + HTML, an optional picture shown in the email (inline, {@code cid:picture}) and the
     * unsubscribe link as the List-Unsubscribe header. {@code bulk} = goes through Amazon SES when real sending is on.
     */
    public record Rich(String to, String subject, String text, String html, byte[] image, String imageType, String unsubscribeUrl,
            boolean bulk) {}

    public MailService(JavaMailSender sender, ShopSettingsRepository shops,
            @Value("${app.mail.from:Gem Shop System <no-reply@localhost>}") String fallbackFrom, ObjectProvider<SmtpSource> source,
            @Value("${app.mail.use-settings:false}") boolean useSettings) {
        this.testSender = sender;
        this.shops = shops;
        this.fallbackFrom = fallbackFrom;
        this.source = source;
        this.useSettings = useSettings;
    }

    /** True while emails go to the test mail server, not through the shop's SMTP. */
    public boolean testMode() {
        return settings().isEmpty();
    }

    /** "smtp.gmail.com" or "the test mail server". */
    public String via() {
        return settings().map(SmtpSource.Smtp::host).orElse("the test mail server");
    }

    /** Plain text email (also sent as simple HTML). Returns false when sending failed (it is logged). */
    public boolean send(String to, String subject, String text) {
        try {
            MimeMessage m = sender().createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, true, StandardCharsets.UTF_8.name());
            h.setFrom(from());
            h.setTo(to);
            h.setSubject(subject);
            h.setText(text, html(text));
            sender().send(m);
            return true;
        } catch (Exception e) {
            log.warn("Email to {} not sent ({}): {}", to, subject, e.getMessage());
            return false;
        }
    }

    /** Email with one attachment (an invoice or quotation PDF). Returns false when sending failed (it is logged). */
    public boolean send(String to, String subject, String text, String fileName, byte[] file, String contentType) {
        try {
            MimeMessage m = sender().createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, true, StandardCharsets.UTF_8.name());
            h.setFrom(from());
            h.setTo(to);
            h.setSubject(subject);
            h.setText(text, html(text));
            h.addAttachment(fileName, new org.springframework.core.io.ByteArrayResource(file), contentType);
            sender().send(m);
            return true;
        } catch (Exception e) {
            log.warn("Email to {} not sent ({}): {}", to, subject, e.getMessage());
            return false;
        }
    }

    /** True while offer emails go to the test mail server, not through Amazon SES. */
    public boolean bulkTestMode() {
        return ses().isEmpty();
    }

    /** Sends an offer email. Returns false when sending failed (it is logged). */
    public boolean send(Rich r) {
        try {
            Optional<SmtpSource.Smtp> ses = r.bulk() ? ses() : Optional.empty();
            JavaMailSender via = ses.isPresent() ? sesSender(ses.get()) : sender();
            MimeMessage m = via.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            h.setFrom(ses.isPresent() && ses.get().from() != null ? InternetAddress.parse(ses.get().from(), false)[0] : from());
            h.setTo(r.to());
            h.setSubject(r.subject());
            h.setText(r.text(), r.html());
            if (r.image() != null && r.image().length > 0) {
                h.addInline("picture", new org.springframework.core.io.ByteArrayResource(r.image()), r.imageType());
            }
            if (r.unsubscribeUrl() != null) {
                m.setHeader("List-Unsubscribe", "<" + r.unsubscribeUrl() + ">");
                m.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            }
            via.send(m);
            return true;
        } catch (Exception e) {
            log.warn("Email to {} not sent ({}): {}", r.to(), r.subject(), e.getMessage());
            return false;
        }
    }

    private Optional<SmtpSource.Smtp> ses() {
        if (!useSettings) {
            return Optional.empty();
        }
        SmtpSource src = source.getIfAvailable();
        return src == null ? Optional.empty() : src.ses();
    }

    private JavaMailSender sesSender(SmtpSource.Smtp x) {
        if (!x.equals(cachedSesFor)) {
            cachedSes = build(x);
            cachedSesFor = x;
        }
        return cachedSes;
    }

    InternetAddress from() throws Exception {
        Optional<SmtpSource.Smtp> smtp = settings();
        if (smtp.isPresent() && smtp.get().from() != null && !smtp.get().from().isBlank()) {
            return InternetAddress.parse(smtp.get().from(), false)[0];
        }
        ShopSettings s = shops.current().orElse(null);
        if (s != null && s.getEmail() != null && !s.getEmail().isBlank()) {
            return new InternetAddress(s.getEmail(), s.getName(), StandardCharsets.UTF_8.name());
        }
        return new InternetAddress(fallbackFrom);
    }

    private Optional<SmtpSource.Smtp> settings() {
        if (!useSettings) {
            return Optional.empty();
        }
        SmtpSource src = source.getIfAvailable();
        return src == null ? Optional.empty() : src.smtp().filter(x -> x.host() != null && !x.host().isBlank());
    }

    private JavaMailSender sender() {
        Optional<SmtpSource.Smtp> smtp = settings();
        if (smtp.isEmpty()) {
            return testSender;
        }
        SmtpSource.Smtp x = smtp.get();
        if (!x.equals(cachedFor)) {
            cached = build(x);
            cachedFor = x;
        }
        return cached;
    }

    private static JavaMailSenderImpl build(SmtpSource.Smtp x) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(x.host());
        s.setPort(x.port());
        s.setUsername(blankToNull(x.user()));
        s.setPassword(blankToNull(x.password()));
        s.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties p = s.getJavaMailProperties();
        p.put("mail.smtp.auth", String.valueOf(x.user() != null && !x.user().isBlank()));
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "15000");
        p.put("mail.smtp.writetimeout", "15000");
        if ("SSL".equals(x.security())) {
            p.put("mail.smtp.ssl.enable", "true");
        } else if ("STARTTLS".equals(x.security())) {
            p.put("mail.smtp.starttls.enable", "true");
            p.put("mail.smtp.starttls.required", "true");
        }
        return s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String html(String text) {
        String body = HtmlUtils.htmlEscape(text).replace("\n", "<br>")
                .replaceAll("(https?://[^\\s<]+)", "<a href=\"$1\">$1</a>");
        return "<div style=\"font-family:Segoe UI,Arial,sans-serif;font-size:14px;color:#0f172a\">" + body + "</div>";
    }
}
