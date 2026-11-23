package lk.nuvi.gemshop.system.sampledata;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The prototype's 3 offers (2 sent with their results, 1 email draft). CP-0001… become OF-0001… (CP- is used by
 * commission payments). The gem picture is kept by name; the screen draws it.
 */
@Component
class OffersSampleData implements SampleDataPart {

    private final JdbcTemplate jdbc;
    private final UserAccountRepository users;
    private final Numbers numbers;

    OffersSampleData(JdbcTemplate jdbc, UserAccountRepository users, Numbers numbers) {
        this.jdbc = jdbc;
        this.users = users;
        this.numbers = numbers;
    }

    @Override
    public int order() {
        return 370;
    }

    @Override
    public String name() {
        return "offers";
    }

    @Override
    public int catchUp(SampleData data) {
        Integer n = jdbc.queryForObject("select count(*) from offer", Integer.class);
        Integer c = jdbc.queryForObject("select count(*) from customer", Integer.class);
        return n != null && n == 0 && c != null && c > 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        Map<String, Long> people = new HashMap<>();
        for (JsonNode u : data.node("users").values()) {
            users.findByEmail(UserAccount.normalise(u.path("email").asString())).ifPresent(x -> people.put(u.path("id").asString(), x.getId()));
        }
        int n = 0;
        long last = 0;
        for (JsonNode o : data.node("campaigns").values()) {
            String id = o.path("id").asString();
            long no = Long.parseLong(id.replaceAll("[^0-9]", ""));
            last = Math.max(last, no);
            String code = String.format("OF-%04d", no);
            boolean email = "email".equalsIgnoreCase(o.path("channel").asString());
            boolean sent = "sent".equalsIgnoreCase(o.path("status").asString());
            Instant at = sent ? data.instant(o.path("sentAt")) : null;
            JsonNode st = o.path("stats");
            String subject = SampleData.text(o.path("subject"));
            n += jdbc.update("""
                    insert into offer (code, channel, name, subject, template, body, picture, countries, gems, status, sent_at, sent_by,
                                       sent, delivered, opened, clicked, unsubscribed, created_at, created_by, updated_at, updated_by)
                    values (?, ?, ?, ?, null, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'system', ?, 'system')""",
                    code, email ? "EMAIL" : "WHATSAPP", o.path("name").asString(), email ? subject : null, o.path("body").asString(),
                    SampleData.text(o.path("image")), join(o.path("audience").path("countries")), join(o.path("audience").path("gems")),
                    sent ? "SENT" : "DRAFT", at == null ? null : Timestamp.from(at), sent ? people.get(o.path("by").asString()) : null,
                    st.path("sent").asInt(0), st.path("delivered").asInt(0), st.path("opened").asInt(0), st.path("clicked").asInt(0),
                    st.path("unsubscribed").asInt(0), Timestamp.from(at == null ? Instant.now() : at),
                    Timestamp.from(at == null ? Instant.now() : at));
        }
        numbers.continueAfter(Counter.OFFER, last);
        return n;
    }

    private static String join(JsonNode list) {
        List<String> out = new ArrayList<>();
        list.values().forEach(v -> out.add(v.asString()));
        return String.join("|", out);
    }
}
