package lk.nuvi.gemshop.system.sampledata;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import lk.nuvi.gemshop.customers.Customer;
import lk.nuvi.gemshop.customers.CustomerRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The prototype's message settings (SMTP, Amazon SES, WhatsApp Business off, automatic messages, birthday wishes at 09:00)
 * and its birthday wishes already sent. The prototype's passwords are only dots, so none is saved – the owner types the
 * real one on the settings screen.
 */
@Component
class MessagingSampleData implements SampleDataPart {

    private final JdbcTemplate jdbc;
    private final CustomerRepository customers;

    MessagingSampleData(JdbcTemplate jdbc, CustomerRepository customers) {
        this.jdbc = jdbc;
        this.customers = customers;
    }

    @Override
    public int order() {
        return 360;
    }

    @Override
    public String name() {
        return "message settings and birthday wishes";
    }

    @Override
    public int catchUp(SampleData data) {
        Integer wishes = jdbc.queryForObject("select count(*) from birthday_wish", Integer.class);
        Integer smtp = jdbc.queryForObject("select count(*) from message_settings where smtp_host is not null", Integer.class);
        return customers.count() > 0 && wishes != null && wishes == 0 && smtp != null && smtp == 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        JsonNode m = data.node("messaging");
        JsonNode smtp = m.path("smtp");
        JsonNode ses = m.path("ses");
        JsonNode wa = m.path("wa");
        JsonNode auto = m.path("auto");
        JsonNode bd = m.path("birthday");
        String security = SampleData.text(smtp.path("security"));
        String domain = SampleData.text(ses.path("domain"));
        jdbc.update("""
                update message_settings set email_on = ?, smtp_host = ?, smtp_port = ?, smtp_security = ?, smtp_user = ?, smtp_from = ?,
                       ses_enabled = ?, ses_domain = ?, ses_region = ?, ses_from = ?, ses_spf = ?, ses_dkim = ?,
                       wa_enabled = ?, wa_phone = ?, wa_account_id = ?, wa_verified = ?,
                       auto_invoice_email = ?, auto_invoice_whatsapp = ?, auto_thank_you = ?, auto_owner_closing = ?,
                       bd_on = ?, bd_email = ?, bd_whatsapp = ?, bd_time = ?, bd_subject = ?, bd_text = ?
                where id = 1""",
                m.path("emailOn").asBoolean(true), SampleData.text(smtp.path("host")), smtp.path("port").asInt(587),
                security == null ? "STARTTLS" : security.startsWith("SSL") ? "SSL" : security.equalsIgnoreCase("None") ? "NONE" : "STARTTLS",
                SampleData.text(smtp.path("user")), SampleData.text(smtp.path("from")),
                ses.path("enabled").asBoolean(false), domain, SampleData.text(ses.path("region")),
                domain == null ? null : "Ceylon Gem House <offers@" + domain + ">", ses.path("spf").asBoolean(false),
                ses.path("dkim").asBoolean(false),
                wa.path("enabled").asBoolean(false), SampleData.text(wa.path("phone")), SampleData.text(wa.path("accountId")),
                false, // no real token in the sample: Verify Connection first
                auto.path("invoiceEmail").asBoolean(true), auto.path("invoiceWhatsapp").asBoolean(true), auto.path("thankYou").asBoolean(true),
                auto.path("ownerClosing").asBoolean(true),
                bd.path("on").asBoolean(true), bd.path("email").asBoolean(true), bd.path("whatsapp").asBoolean(true),
                Time.valueOf(LocalTime.parse(bd.path("time").asString("09:00"))),
                bd.path("subject").asString("Happy birthday from {shop}!"), bd.path("text").asString("Happy birthday, {name}!"));
        int n = 1;
        ZoneId colombo = ZoneId.of("Asia/Colombo");
        for (JsonNode w : data.node("birthdayWishes").values()) {
            Customer c = customers.findByCode(w.path("customerId").asString()).orElse(null);
            Instant at = data.instant(w.path("at"));
            if (c == null || at == null) {
                continue;
            }
            n += jdbc.update("""
                    insert into birthday_wish (customer_id, year, at, email, whatsapp, created_at, created_by, updated_at, updated_by)
                    values (?, ?, ?, ?, ?, ?, 'system', ?, 'system') on conflict (customer_id, year) do nothing""",
                    c.getId(), at.atZone(colombo).getYear(), Timestamp.from(at), w.path("email").asString("sent"),
                    w.path("whatsapp").asString("WhatsApp off"), Timestamp.from(at), Timestamp.from(at));
        }
        return n;
    }
}
