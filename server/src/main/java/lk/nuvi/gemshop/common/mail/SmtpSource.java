package lk.nuvi.gemshop.common.mail;

import java.util.Optional;

/**
 * Where the shop's own SMTP settings come from (Email &amp; WhatsApp settings, P4-04). Used by {@link MailService} only when
 * {@code app.mail.use-settings} is on (real email, P6-03); until then every email goes to the test mail server (MailHog).
 */
public interface SmtpSource {

    /** STARTTLS (port 587), SSL (port 465) or NONE. */
    record Smtp(String host, int port, String security, String user, String password, String from) {}

    /** The saved settings, or empty when the host is not filled in. */
    Optional<Smtp> smtp();
}
