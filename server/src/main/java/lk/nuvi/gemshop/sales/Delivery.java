package lk.nuvi.gemshop.sales;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import lk.nuvi.gemshop.common.mail.MailService;
import lk.nuvi.gemshop.customers.Customer;
import lk.nuvi.gemshop.messaging.WhatsApp;
import lk.nuvi.gemshop.sales.api.SalesDtos.SendForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.SendResult;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Sends a document PDF (invoice, quotation) to the customer by email (shop SMTP) and / or WhatsApp (test mode until
 * P6-03). Checks the address and the number; an email that cannot be sent stops with 409 NOT_SENT.
 */
@Component
class Delivery {

    static final String NOT_SENT = "NOT_SENT";

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9 ()-]{7,20}$");

    private final MailService mail;
    private final WhatsApp whatsapp;
    private final ShopSettingsRepository shops;

    Delivery(MailService mail, WhatsApp whatsapp, ShopSettingsRepository shops) {
        this.mail = mail;
        this.whatsapp = whatsapp;
        this.shops = shops;
    }

    /** What was sent and to where ("email (a@b.lk) and WhatsApp (+49 …) – test mode"). */
    record Sent(boolean email, boolean whatsapp, String by, SendResult result) {}

    /**
     * @param doc      "invoice INV-000141" / "quotation QT-0003" (used in the message)
     * @param branch   the branch name, e.g. "Ella"
     */
    Sent send(SendForm f, Customer c, String branch, String doc, String fileName, byte[] pdf) {
        if (!f.email() && !f.whatsapp()) {
            throw ApiException.badRequest("NOTHING_CHOSEN", "Choose email or WhatsApp.");
        }
        String email = f.email() ? trim(f.emailTo(), c.getEmail()) : null;
        String phone = f.whatsapp() ? trim(f.whatsappTo(), c.getWhatsapp()) : null;
        if (f.email() && (email == null || !EMAIL.matcher(email).matches())) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Enter a valid email address.", "emailTo",
                    "e.g. anna@example.com");
        }
        if (f.whatsapp() && (phone == null || !PHONE.matcher(phone).matches())) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "Enter the WhatsApp number with the country code.", "whatsappTo", "e.g. +49 151 2345 6789");
        }
        ShopSettings shop = shops.current().orElse(null);
        String shopName = shop == null ? "our shop" : shop.getName();
        String first = c.getName().trim().split("\\s+")[0];
        String text = "Dear " + first + ",\n\nThank you for visiting " + shopName + ", " + branch + ". Please find your " + doc
                + " attached as a PDF.\n\nSafe travels!\n" + shopName;
        boolean mailed = false;
        if (f.email()) {
            String subject = "Your " + doc + " – " + shopName;
            mailed = mail.send(email, subject, text, fileName, pdf, "application/pdf");
            if (!mailed) {
                throw ApiException.conflict(NOT_SENT, "The email could not be sent. Check the email settings, or try again.");
            }
        }
        boolean wa = f.whatsapp() && whatsapp.send(phone, text, fileName, pdf);
        List<String> by = new ArrayList<>();
        if (mailed) {
            by.add("email (" + email + ")");
        }
        if (wa) {
            by.add("WhatsApp (" + phone + ")" + (whatsapp.testMode() ? " – test mode" : ""));
        }
        String joined = String.join(" and ", by);
        return new Sent(mailed, wa, joined, new SendResult(mailed, wa, wa && whatsapp.testMode(), "Sent by " + joined + "."));
    }

    private static String trim(String given, String fallback) {
        String v = given == null || given.isBlank() ? fallback : given;
        return v == null || v.isBlank() ? null : v.trim();
    }
}
