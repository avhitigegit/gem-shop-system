package lk.nuvi.gemshop.messaging;

import java.time.Instant;
import java.time.LocalTime;

/**
 * The shop's message settings (one row, table {@code message_settings}). Secrets are already decrypted here; they never
 * leave the server (the screen only sees whether one is saved).
 */
public record MessageSettings(
        boolean emailOn,
        Smtp smtp,
        Ses ses,
        Wa wa,
        Auto auto,
        Birthday birthday) {

    public record Smtp(String host, Integer port, String security, String user, String password, String from) {}

    public record Ses(boolean enabled, String domain, String region, String from, String accessKey, String secret, boolean spf,
            boolean dkim) {}

    public record Wa(boolean enabled, String phone, String accountId, String token, boolean verified, Instant verifiedAt) {}

    /** J5: invoice by email / WhatsApp after every purchase, thank-you the next day, day-end report to the owner. */
    public record Auto(boolean invoiceEmail, boolean invoiceWhatsapp, boolean thankYou, boolean ownerClosing) {}

    /** J8: on / off, channels, the time it goes out (shop time), subject and text with {name} and {shop}. */
    public record Birthday(boolean on, boolean email, boolean whatsapp, LocalTime time, String subject, String text) {}

    /** WhatsApp can be used: switched on and the connection verified. */
    public boolean whatsappReady() {
        return wa.enabled() && wa.verified();
    }
}
