package lk.nuvi.gemshop.commissions;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static lk.nuvi.gemshop.SignIn.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.nuvi.gemshop.SignIn;
import lk.nuvi.gemshop.SignIn.Signed;
import lk.nuvi.gemshop.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P3-05 Guides and commission. Own database: it adds guides, sells, pays and takes commission back. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=commissions")
class CommissionsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final Map<String, Signed> who = new LinkedHashMap<>();

    @BeforeEach
    void signIn() throws Exception {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ? "
                + "where email like '%@ceylongemhouse.lk' or email like '%@fernandoassociates.lk'", encoder.encode(SignIn.PASSWORD));
        for (String e : new String[] {OWNER, SALES, STAFF, ACCOUNTANT}) {
            who.put(e, SignIn.login(mvc, e));
        }
    }

    @Test
    void sampleGuidesAndCommissionsAreLoaded() throws Exception {
        JsonNode list = read(get("/api/guides"), SALES);
        assertThat(list.path("guides").findValuesAsString("code")).contains("G-01", "G-02", "G-03", "G-04", "G-05", "G-06");
        assertThat(list.path("guides").get(0).path("earned").isNull()).isTrue();      // salesperson: no amounts
        JsonNode acc = read(get("/api/guides"), ACCOUNTANT);
        assertThat(acc.path("guides").get(0).path("earned").isNull()).isFalse();
        assertThat(acc.path("defaultPct").decimalValue()).isEqualByComparingTo("10");

        // RT-0001 took back the guide's 16,500 on INV-000110
        JsonNode reversed = read(get("/api/commissions").param("show", "REVERSED"), ACCOUNTANT);
        JsonNode back = find(reversed.path("lines"), "person", "G:G-01");
        assertThat(back.path("sale").asString()).isEqualTo("INV-000110");
        assertThat(back.path("amount").decimalValue()).isEqualByComparingTo("-16500");
        assertThat(back.path("note").asString()).contains("RT-0001");

        JsonNode payable = read(get("/api/commissions"), OWNER);
        assertThat(payable.path("owed").decimalValue()).isPositive();
        assertThat(payable.path("byPerson").size()).isPositive();
        // old guide lines were paid "Cash (owner)", recent ones are owed (prototype rule)
        JsonNode paid = read(get("/api/commissions").param("show", "PAID"), OWNER);
        assertThat(paid.path("lines").findValuesAsString("method")).contains("Cash (owner)", "With salary");

        // commission per sale: walk-in filter and one sale's breakdown
        JsonNode walkIn = read(get("/api/commissions/sales").param("guide", "walkin"), ACCOUNTANT);
        walkIn.path("sales").forEach(s -> assertThat(s.path("guide").isNull()).isTrue());
        JsonNode some = read(get("/api/commissions/sales").param("guide", "G-01"), ACCOUNTANT).path("sales").get(0);
        JsonNode one = read(get("/api/commissions/sales/" + some.path("number").asString()), ACCOUNTANT);
        assertThat(one.path("guide").asString()).isEqualTo("Sunil Kumara");
        assertThat(one.path("guideComm").decimalValue())
                .isEqualByComparingTo(one.path("total").decimalValue().movePointLeft(1));   // 10 %
        assertThat(one.path("levels").size()).isEqualTo(4);
        assertThat(one.path("lines").size()).isGreaterThanOrEqualTo(2);

        // profile, statement slip and PDF
        JsonNode g1 = read(get("/api/guides/G-01").param("from", LocalDate.now().minusDays(60).toString()), ACCOUNTANT);
        assertThat(g1.path("guide").path("name").asString()).isEqualTo("Sunil Kumara");
        assertThat(g1.path("lines").size()).isPositive();
        JsonNode slip = read(get("/api/commissions/slips/G-01"), ACCOUNTANT);
        assertThat(slip.path("number").isNull()).isTrue();
        assertThat(slip.path("guideVehicleNo").asString()).isEqualTo("UVA QA-4521");
        byte[] pdf = mvc.perform(get("/api/commissions/slips/G-01/pdf").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        mvc.perform(get("/api/commissions/export").param("format", "xlsx").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/commissions/export").param("format", "pdf").with(who.get(OWNER).bearer())).andExpect(status().isOk());

        // roles
        mvc.perform(get("/api/commissions").with(who.get(SALES).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/guides").with(who.get(STAFF).bearer())).andExpect(status().isForbidden());
    }

    @Test
    void ownerAddsAndEditsGuidesAndTheirPercent() throws Exception {
        Map<String, Object> form = new LinkedHashMap<>(Map.of("name", "Kasun Perera", "phone", "+94 77 000 1122", "vehicle", "Van",
                "vehicleNo", "sp pq-1234"));
        mvc.perform(json(post("/api/guides"), form, SALES)).andExpect(status().isForbidden());
        JsonNode g = created(json(post("/api/guides"), form, OWNER));
        String code = g.path("code").asString();
        assertThat(code).matches("G-\\d{2}");
        assertThat(g.path("pct").decimalValue()).isEqualByComparingTo("10");          // the default %
        assertThat(g.path("vehicleNo").asString()).isEqualTo("SP PQ-1234");
        assertThat(g.path("since").asString()).isEqualTo(LocalDate.now().toString());
        // same phone twice → refused; a bad vehicle → refused
        mvc.perform(json(post("/api/guides"), form, OWNER)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_GUIDE"));
        Map<String, Object> bad = new LinkedHashMap<>(form);
        bad.put("phone", "+94 77 999 0000");
        bad.put("vehicle", "Plane");
        mvc.perform(json(post("/api/guides"), bad, OWNER)).andExpect(status().isBadRequest());

        form.put("pct", 12.5);
        assertThat(read(json(put("/api/guides/" + code), form, OWNER)).path("pct").decimalValue()).isEqualByComparingTo("12.5");

        // Settings → Commission lists each guide's own % and saves it
        JsonNode rules = read(get("/api/settings/commission"), OWNER);
        assertThat(find(rules.path("guides"), "code", code).path("pct").decimalValue()).isEqualByComparingTo("12.5");
        Map<String, Object> save = new LinkedHashMap<>();
        save.put("defaultGuidePct", rules.path("defaultGuidePct").decimalValue());
        save.put("levels", JSON.convertValue(rules.path("levels"), List.class));
        save.put("guides", List.of(Map.of("code", code, "pct", 15)));
        JsonNode saved = read(json(put("/api/settings/commission"), save, OWNER));
        assertThat(find(saved.path("guides"), "code", code).path("pct").decimalValue()).isEqualByComparingTo("15");

        // a sale with the new guide earns 15 %
        Sold s = sell(1, code);
        JsonNode lines = read(get("/api/commissions/sales/" + s.invoice), OWNER).path("lines");
        JsonNode gl = find(lines, "payee", "GUIDE");
        assertThat(gl.path("amount").decimalValue()).isEqualByComparingTo(s.price.multiply(new BigDecimal("0.15")));
        assertThat(gl.path("status").asString()).isEqualTo("UNPAID");
        assertThat(find(lines, "payee", "STAFF").path("name").asString()).isEqualTo("Nimal Silva");
    }

    @Test
    void payGuideAndPrintSlip() throws Exception {
        Sold a = sell(2, "G-03");
        Sold b = sell(3, "G-03");
        List<Long> ids = owedFor("G:G-03", a.invoice, b.invoice);
        assertThat(ids).hasSize(2);
        Map<String, Object> pay = Map.of("lines", ids, "paidOn", LocalDate.now().toString(), "method", "DRAWER");
        mvc.perform(json(post("/api/commissions/pay"), pay, SALES)).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/commissions/pay"), Map.of("lines", ids, "paidOn", LocalDate.now().plusDays(1).toString(),
                "method", "BANK"), OWNER)).andExpect(status().isBadRequest());
        JsonNode p = read(json(post("/api/commissions/pay"), pay, ACCOUNTANT));
        String cp = p.path("number").asString();
        assertThat(cp).startsWith("CP-");
        assertThat(p.path("amount").decimalValue()).isEqualByComparingTo(a.price.add(b.price).multiply(new BigDecimal("0.08")));
        assertThat(p.path("payee").asString()).isEqualTo("Pradeep Herath");
        // paid twice → refused
        mvc.perform(json(post("/api/commissions/pay"), pay, OWNER)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMISSION_ALREADY_PAID"));

        JsonNode slip = read(get("/api/commissions/slips/" + cp), OWNER);
        assertThat(slip.path("method").asString()).isEqualTo("Cash (drawer)");
        assertThat(slip.path("lines").size()).isEqualTo(2);
        assertThat(slip.path("total").decimalValue()).isEqualByComparingTo(p.path("amount").decimalValue());
        assertThat(slip.path("paidBy").asString()).isNotBlank();
        mvc.perform(get("/api/commissions/slips/" + cp + "/pdf").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        JsonNode line = find(read(get("/api/commissions").param("show", "PAID").param("person", "G:G-03"), OWNER).path("lines"),
                "sale", a.invoice);
        assertThat(line.path("payment").asString()).isEqualTo(cp);
        assertThat(jdbc.queryForObject("select count(*) from activity_log where action = 'Commission paid' and detail like ?",
                Integer.class, cp + "%")).isEqualTo(1);
    }

    @Test
    void returnAndCancelTakeCommissionBack() throws Exception {
        // paid first, then the gem comes back: the guide's 10 % is taken off the next payment
        Sold s = sell(4, "G-04");
        read(json(post("/api/commissions/pay"), Map.of("lines", owedFor("G:G-04", s.invoice), "paidOn", LocalDate.now().toString(),
                "method", "OWNER_CASH"), OWNER));
        JsonNode r = created(json(post("/api/returns"), Map.of("sale", s.invoice, "gem", s.gem, "kind", "REFUND",
                "reason", "Changed mind"), SALES));
        mvc.perform(json(post("/api/approvals/" + r.path("approvalNo").asString() + "/approve"), Map.of("note", "OK"), OWNER))
                .andExpect(status().isOk());
        JsonNode lines = read(get("/api/commissions/sales/" + s.invoice), OWNER).path("lines");
        List<JsonNode> back = new ArrayList<>();
        lines.forEach(l -> { if (l.path("status").asString().equals("REVERSED")) back.add(l); });
        assertThat(back).hasSize(2);                                   // guide + salesperson
        JsonNode gBack = back.stream().filter(l -> l.path("payee").asString().equals("GUIDE")).findFirst().orElseThrow();
        assertThat(gBack.path("amount").decimalValue()).isEqualByComparingTo(s.price.movePointLeft(1).negate());
        assertThat(gBack.path("owed").asBoolean()).isTrue();
        // paying only the taken-back line → nothing to pay
        mvc.perform(json(post("/api/commissions/pay"), Map.of("lines", List.of(gBack.path("id").asLong()), "paidOn",
                LocalDate.now().toString(), "method", "OWNER_CASH"), OWNER))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOTHING_TO_PAY"));

        // a cancelled invoice: everything on it is taken back (net 0 per person)
        Sold c = sell(5, "G-02");
        JsonNode req = read(json(post("/api/sales/" + c.invoice + "/cancel"), Map.of("reason", "Wrong customer"), SALES));
        mvc.perform(json(post("/api/approvals/" + req.path("cancel").path("number").asString() + "/approve"), Map.of("note", "OK"), OWNER))
                .andExpect(status().isOk());
        BigDecimal net = jdbc.queryForObject("select sum(amount) from commission_line l join sale s on s.id = l.sale_id where s.number = ?",
                BigDecimal.class, c.invoice);
        assertThat(net).isEqualByComparingTo("0");
        assertThat(read(get("/api/commissions/sales").param("guide", "G-02"), OWNER).path("sales").findValuesAsString("number"))
                .doesNotContain(c.invoice);
    }

    // ------------------------------------------------------------------ helpers

    private record Sold(String invoice, String gem, BigDecimal price) {}

    private Sold sell(int n, String guide) throws Exception {
        record Stock(String code, BigDecimal price) {}
        Stock g = jdbc.queryForObject("""
                select g.code, g.price from gem g join branch b on b.id = g.branch_id
                where b.code = 'ella' and g.status = 'IN_STOCK' and g.price < 600000
                order by g.code desc offset ? limit 1""", (rs, i) -> new Stock(rs.getString(1), rs.getBigDecimal(2)), n * 2);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(Map.of("gem", g.code, "agreed", g.price)));
        body.put("customer", Map.of("name", "Commission Test " + n, "country", "Germany"));
        body.put("guide", guide);
        body.put("payments", List.of(Map.of("method", "CASH", "currency", "LKR", "amount", g.price)));
        JsonNode s = created(json(post("/api/sales"), body, SALES));
        return new Sold(s.path("number").asString(), g.code, g.price);
    }

    private List<Long> owedFor(String person, String... invoices) throws Exception {
        JsonNode rows = read(get("/api/commissions").param("person", person), OWNER).path("lines");
        List<Long> ids = new ArrayList<>();
        rows.forEach(r -> {
            if (List.of(invoices).contains(r.path("sale").asString())) {
                ids.add(r.path("id").asLong());
            }
        });
        return ids;
    }

    private static JsonNode find(JsonNode array, String field, String value) {
        for (JsonNode n : array) {
            if (value.equals(n.path(field).asString())) {
                return n;
            }
        }
        throw new AssertionError("No " + field + " = " + value + " in " + array);
    }

    private JsonNode read(MockHttpServletRequestBuilder req, String as) throws Exception {
        return read(req.with(who.get(as).bearer()));
    }

    private JsonNode read(MockHttpServletRequestBuilder req) throws Exception {
        return JSON.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode created(MockHttpServletRequestBuilder req) throws Exception {
        return JSON.readTree(mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body, String as) throws Exception {
        req.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        return req.with(who.get(as).bearer());
    }
}
