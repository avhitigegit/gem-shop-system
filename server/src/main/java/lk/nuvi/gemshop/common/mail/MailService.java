package lk.nuvi.gemshop.common.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends email through the shop's own SMTP (invoices, quotations, password reset).
 * On your own computer it goes to MailHog (http://localhost:8025). Offers use Amazon SES later (P6-03).
 * The sender is the shop: "Ceylon Gem House &lt;sales@…&gt;" from Shop details.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender sender;
    private final ShopSettingsRepository shops;
    private final String fallbackFrom;

    public MailService(JavaMailSender sender, ShopSettingsRepository shops,
            @Value("${app.mail.from:Gem Shop System <no-reply@localhost>}") String fallbackFrom) {
        this.sender = sender;
        this.shops = shops;
        this.fallbackFrom = fallbackFrom;
    }

    /** Plain text email (also sent as simple HTML). Returns false when sending failed (it is logged). */
    public boolean send(String to, String subject, String text) {
        try {
            MimeMessage m = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, true, StandardCharsets.UTF_8.name());
            h.setFrom(from());
            h.setTo(to);
            h.setSubject(subject);
            h.setText(text, html(text));
            sender.send(m);
            return true;
        } catch (Exception e) {
            log.warn("Email to {} not sent ({}): {}", to, subject, e.getMessage());
            return false;
        }
    }

    /** Email with one attachment (an invoice or quotation PDF). Returns false when sending failed (it is logged). */
    public boolean send(String to, String subject, String text, String fileName, byte[] file, String contentType) {
        try {
            MimeMessage m = sender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(m, true, StandardCharsets.UTF_8.name());
            h.setFrom(from());
            h.setTo(to);
            h.setSubject(subject);
            h.setText(text, html(text));
            h.addAttachment(fileName, new org.springframework.core.io.ByteArrayResource(file), contentType);
            sender.send(m);
            return true;
        } catch (Exception e) {
            log.warn("Email to {} not sent ({}): {}", to, subject, e.getMessage());
            return false;
        }
    }

    InternetAddress from() throws Exception {
        ShopSettings s = shops.current().orElse(null);
        if (s != null && s.getEmail() != null && !s.getEmail().isBlank()) {
            return new InternetAddress(s.getEmail(), s.getName(), StandardCharsets.UTF_8.name());
        }
        return new InternetAddress(fallbackFrom);
    }

    private static String html(String text) {
        String body = HtmlUtils.htmlEscape(text).replace("\n", "<br>")
                .replaceAll("(https?://[^\\s<]+)", "<a href=\"$1\">$1</a>");
        return "<div style=\"font-family:Segoe UI,Arial,sans-serif;font-size:14px;color:#0f172a\">" + body + "</div>";
    }
}
