package lk.nuvi.gemshop.sales;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static lk.nuvi.gemshop.SignIn.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.HashMap;
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

/** P3-01 New sale. Own database: it sells gems and adds customers. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=sales")
class SalesTest {

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
    void sampleSalesCustomersGuidesAndHeldSaleAreLoaded() throws Exception {
        JsonNode inv = read(get("/api/sales/INV-000101"), OWNER);
        assertThat(inv.path("total").decimalValue()).isEqualByComparingTo("448000");
        assertThat(inv.path("customer").path("name").asString()).isEqualTo("Anna Müller");
        assertThat(inv.path("guide").path("code").asString()).isEqualTo("G-06");
        assertThat(inv.path("items").get(0).path("code").asString()).isEqualTo("GEM-00044");
        assertThat(inv.path("payments").get(0).path("method").asString()).isEqualTo("CARD");
        assertThat(inv.path("cardFee").decimalValue()).isEqualByComparingTo("13440");
        assertThat(read(get("/api/sales/INV-000101"), SALES).path("cardFee").isNull()).isTrue();

        assertThat(read(get("/api/sales/customers"), SALES).size()).isGreaterThanOrEqualTo(25);
        assertThat(read(get("/api/sales/guides"), SALES).size()).isEqualTo(6);
        Integer sales = jdbc.queryForObject("select count(*) from sale where number like 'INV-0001%'", Integer.class);
        assertThat(sales).isGreaterThanOrEqualTo(40);

        JsonNode sum = read(get("/api/sales/summary"), ACCOUNTANT);
        assertThat(sum.path("latest").size()).isEqualTo(6);
        assertThat(read(get("/api/held-sales/HS-0001"), SALES).path("approvalNo").asString()).isEqualTo("AP-0010");
    }

    @Test
    void whoMaySell() throws Exception {
        Map<String, Object> sale = Map.of("items", List.of(line("GEM-00101", BigDecimal.TEN)), "customer", Map.of("code", "C-001"),
                "payments", List.of(pay("CASH", "LKR", BigDecimal.TEN)));
        for (String e : new String[] {STAFF, ACCOUNTANT}) {
            mvc.perform(get("/api/sales/gems/GEM-00044").with(who.get(e).bearer())).andExpect(status().isForbidden());
            mvc.perform(json(post("/api/sales"), sale, e)).andExpect(status().isForbidden());
            mvc.perform(json(post("/api/held-sales"), sale, e)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/sales/summary").with(who.get(STAFF).bearer())).andExpect(status().isForbidden());
    }

    @Test
    void scanTellsWhatCanBeSold() throws Exception {
        JsonNode sold = read(get("/api/sales/gems/gem-00044"), SALES);
        assertThat(sold.path("sellable").asBoolean()).isFalse();
        assertThat(sold.path("note").asString()).startsWith("already sold on").contains("INV-000101").contains("cannot be sold again");
        String code = inStock("ella", 0).code;
        JsonNode ok = read(get("/api/sales/gems/" + code.substring(4)), SALES);
        assertThat(ok.path("sellable").asBoolean()).isTrue();
        assertThat(ok.path("gem").path("min").isNull()).isTrue();
        assertThat(read(get("/api/sales/gems/" + code), OWNER).path("gem").path("min").decimalValue()).isPositive();
    }

    @Test
    void completeASaleWithANewCustomerAndCashChange() throws Exception {
        Stock g = inStock("ella", 1);
        Map<String, Object> customer = newCustomer("Test Buyer", "Germany");
        customer.put("birthday", "04-17");
        customer.put("consent", true);
        customer.put("email", "Test.Buyer@Example.com");

        JsonNode check = read(json(post("/api/sales/check"), Map.of("items", List.of(line(g.code, g.price)), "guide", "G-01"),
                SALES), SALES);
        assertThat(check.path("belowMin").asBoolean()).isFalse();
        assertThat(check.path("guidePct").decimalValue()).isEqualByComparingTo("10");
        assertThat(check.path("guideComm").decimalValue()).isEqualByComparingTo(g.price.divide(BigDecimal.TEN));
        assertThat(check.path("staffComm").decimalValue()).isPositive();

        BigDecimal handed = g.price.add(new BigDecimal("1000"));
        JsonNode s = created(json(post("/api/sales"), Map.of("items", List.of(line(g.code, g.price)), "customer", customer,
                "guide", "G-01", "payments", List.of(pay("CASH", "LKR", handed))), SALES));
        String no = s.path("number").asString();
        assertThat(no).matches("INV-\\d{6}");
        assertThat(s.path("change").decimalValue()).isEqualByComparingTo("1000");
        assertThat(s.path("customer").path("code").asString()).matches("C-\\d{3}");
        assertThat(s.path("customer").path("email").asString()).isEqualTo("test.buyer@example.com");
        assertThat(s.path("customer").path("birthday").asString()).isEqualTo("04-17");
        assertThat(s.path("customer").path("consent").asBoolean()).isTrue();
        assertThat(s.path("staff").asString()).isEqualTo("Nimal Silva");
        assertThat(lastLog()).isEqualTo("Sale completed");
        assertThat(jdbc.queryForObject("select count(*) from alert where link = ?", Integer.class, "/sell/invoices/" + no)).isOne();

        JsonNode gem = read(get("/api/gems/" + g.code), OWNER);
        assertThat(gem.path("status").asString()).isEqualTo("SOLD");
        assertThat(gem.path("history").findValuesAsString("action")).contains("Sold");

        // rule 3: never again
        mvc.perform(json(post("/api/sales"), Map.of("items", List.of(line(g.code, g.price)), "customer", Map.of("code", "C-001"),
                "payments", List.of(pay("CASH", "LKR", g.price))), SALES))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_SOLD"));
    }

    @Test
    void mixedPaymentsCardFeeIsAShopCost() throws Exception {
        Stock g = inStock("ella", 2);
        BigDecimal usdRate = jdbc.queryForObject("select rate from exchange_rate where currency = 'USD' order by effective_at desc limit 1",
                BigDecimal.class);
        BigDecimal usd = new BigDecimal("100");
        BigDecimal card = g.price.subtract(usd.multiply(usdRate)).setScale(2, java.math.RoundingMode.HALF_UP);
        JsonNode s = created(json(post("/api/sales"), Map.of("items", List.of(line(g.code, g.price)), "customer", Map.of("code", "C-002"),
                "payments", List.of(pay("CASH", "USD", usd), pay("CARD", "LKR", card))), OWNER));
        assertThat(s.path("paid").decimalValue()).isEqualByComparingTo(g.price);
        assertThat(s.path("change").decimalValue()).isZero();
        assertThat(s.path("cardFee").decimalValue()).isEqualByComparingTo(card.multiply(new BigDecimal("0.03"))
                .setScale(2, java.math.RoundingMode.HALF_UP));
        assertThat(s.path("total").decimalValue()).isEqualByComparingTo(g.price); // same price for card

        Stock h = inStock("ella", 3);
        mvc.perform(json(post("/api/sales"), Map.of("items", List.of(line(h.code, h.price)), "customer", Map.of("code", "C-002"),
                "payments", List.of(pay("CASH", "LKR", h.price.subtract(BigDecimal.ONE)))), OWNER))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_ENOUGH"));
        mvc.perform(json(post("/api/sales"), Map.of("items", List.of(line(h.code, h.price)), "customer", Map.of("code", "C-002"),
                "payments", List.of(pay("CARD", "LKR", h.price.add(BigDecimal.TEN)))), OWNER))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOO_MUCH_CARD"));
        mvc.perform(json(post("/api/sales"), Map.of("items", List.of(line(h.code, h.price)), "customer", Map.of("code", "C-002"),
                "payments", List.of(pay("CARD", "USD", BigDecimal.TEN))), OWNER)).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/sales"), Map.of("items", List.of(line(h.code, h.price)), "customer", newCustomer("No Country", ""),
                "payments", List.of(pay("CASH", "LKR", h.price))), OWNER))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("customer.country"));
        Map<String, Object> bad = newCustomer("Bad Day", "France");
        bad.put("birthday", "02-30");
        mvc.perform(json(post("/api/sales"), Map.of("items", List.of(line(h.code, h.price)), "customer", bad,
                "payments", List.of(pay("CASH", "LKR", h.price))), OWNER)).andExpect(status().isBadRequest());
        assertThat(read(get("/api/gems/" + h.code), OWNER).path("status").asString()).isEqualTo("IN_STOCK");
    }

    @Test
    void belowMinimumIsHeldUntilTheOwnerApproves() throws Exception {
        Stock g = inStock("ella", 4);
        BigDecimal low = g.min.subtract(new BigDecimal("1000"));
        Map<String, Object> body = new HashMap<>(Map.of("items", List.of(line(g.code, low)), "customer", Map.of("code", "C-003"),
                "payments", List.of(pay("CASH", "LKR", low))));
        assertThat(read(json(post("/api/sales/check"), Map.of("items", List.of(line(g.code, low))), SALES), SALES)
                .path("lines").get(0).path("belowMin").asBoolean()).isTrue();
        mvc.perform(json(post("/api/sales"), body, SALES)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BELOW_MIN"));

        JsonNode h = created(json(post("/api/held-sales"), Map.of("items", List.of(line(g.code, low)), "customer",
                Map.of("code", "C-003"), "guide", "G-02"), SALES));
        String hs = h.path("number").asString();
        String ap = h.path("approvalNo").asString();
        assertThat(h.path("status").asString()).isEqualTo("PENDING");
        assertThat(read(get("/api/gems/" + g.code), OWNER).path("status").asString()).isEqualTo("ON_HOLD");
        assertThat(jdbc.queryForObject("select type from approval where number = ?", String.class, ap)).isEqualTo("BELOW_MIN");

        body.put("held", hs);
        mvc.perform(json(post("/api/sales"), body, SALES)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELD_WAITING"));
        mvc.perform(post("/api/approvals/" + ap + "/approve").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        assertThat(read(get("/api/held-sales/" + hs), SALES).path("status").asString()).isEqualTo("APPROVED");

        Map<String, Object> changed = new HashMap<>(body);
        changed.put("items", List.of(line(g.code, low.subtract(BigDecimal.ONE))));
        changed.put("payments", List.of(pay("CASH", "LKR", low)));
        mvc.perform(json(post("/api/sales"), changed, SALES)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HELD_CHANGED"));
        JsonNode s = created(json(post("/api/sales"), body, SALES));
        assertThat(s.path("heldNo").asString()).isEqualTo(hs);
        assertThat(s.path("items").get(0).path("approvalNo").asString()).isEqualTo(ap);
        assertThat(s.path("guide").path("code").asString()).isEqualTo("G-02");
        assertThat(read(get("/api/held-sales/" + hs), SALES).path("status").asString()).isEqualTo("COMPLETED");
        assertThat(read(get("/api/gems/" + g.code), OWNER).path("status").asString()).isEqualTo("SOLD");

        // rejected → back in stock
        Stock r = inStock("ella", 5);
        JsonNode h2 = created(json(post("/api/held-sales"), Map.of("items", List.of(line(r.code, r.min.subtract(BigDecimal.TEN))),
                "customer", newCustomer("Held Buyer", "India")), SALES));
        mvc.perform(post("/api/approvals/" + h2.path("approvalNo").asString() + "/reject").with(who.get(OWNER).bearer()))
                .andExpect(status().isOk());
        assertThat(read(get("/api/held-sales/" + h2.path("number").asString()), SALES).path("status").asString())
                .isEqualTo("REJECTED");
        assertThat(read(get("/api/gems/" + r.code), OWNER).path("status").asString()).isEqualTo("IN_STOCK");

        // cancelled by the salesperson → back in stock
        JsonNode h3 = created(json(post("/api/held-sales"), Map.of("items", List.of(line(r.code, r.min.subtract(BigDecimal.TEN))),
                "customer", Map.of("code", "C-004")), SALES));
        read(json(post("/api/held-sales/" + h3.path("number").asString() + "/cancel"), Map.of(), SALES), SALES);
        assertThat(read(get("/api/gems/" + r.code), OWNER).path("status").asString()).isEqualTo("IN_STOCK");

        // the owner sells below the minimum directly
        JsonNode o = created(json(post("/api/sales"), Map.of("items", List.of(line(r.code, r.min.subtract(BigDecimal.TEN))),
                "customer", Map.of("code", "C-004"), "payments", List.of(pay("CASH", "LKR", r.min))), OWNER));
        assertThat(o.path("number").asString()).startsWith("INV-");
        assertThat(jdbc.queryForObject("select count(*) from activity_log where action = 'Below-minimum sale by owner'",
                Integer.class)).isPositive();
        mvc.perform(json(post("/api/held-sales"), Map.of("items", List.of(line(inStock("ella", 6).code, BigDecimal.ONE)),
                "customer", Map.of("code", "C-004")), OWNER)).andExpect(status().isBadRequest());

        // the prototype's held sale HS-0001 (AP-0010) can be approved
        mvc.perform(post("/api/approvals/AP-0010/approve").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        assertThat(read(get("/api/held-sales/HS-0001"), SALES).path("status").asString()).isEqualTo("APPROVED");
    }

    // ------------------------------------------------------------------ helpers

    private record Stock(String code, BigDecimal price, BigDecimal min) {}

    /** The n-th in-stock gem at a branch (each test uses its own n). */
    private Stock inStock(String branch, int n) {
        return jdbc.queryForObject("""
                select g.code, g.price, g.min_price from gem g join branch b on b.id = g.branch_id
                where b.code = ? and g.status = 'IN_STOCK' and g.min_price < g.price
                order by g.code offset ? limit 1""", (rs, i) -> new Stock(rs.getString(1), rs.getBigDecimal(2), rs.getBigDecimal(3)),
                branch, n * 2);
    }

    private static Map<String, Object> line(String gem, BigDecimal agreed) {
        return Map.of("gem", gem, "agreed", agreed);
    }

    private static Map<String, Object> pay(String method, String currency, BigDecimal amount) {
        return Map.of("method", method, "currency", currency, "amount", amount);
    }

    private static Map<String, Object> newCustomer(String name, String country) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("country", country);
        return m;
    }

    private JsonNode created(MockHttpServletRequestBuilder req) throws Exception {
        return JSON.readTree(mvc.perform(req).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode read(MockHttpServletRequestBuilder req, String as) throws Exception {
        return JSON.readTree(mvc.perform(req.with(who.get(as).bearer())).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body, String as) throws Exception {
        req.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        return req.with(who.get(as).bearer());
    }

    private String lastLog() {
        return jdbc.queryForObject("select action from activity_log order by id desc limit 1", String.class);
    }
}
