package lk.nuvi.gemshop.stock;

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

/** P2-04 Stock. Own database: it moves, writes off and re-weighs gems. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=stock")
class StockTest {

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
    void sampleTransferCorrectionsAndCheckAreLoaded() throws Exception {
        JsonNode list = read(get("/api/transfers"), OWNER);
        JsonNode tr = find(list, "number", "TR-0001");
        assertThat(tr.path("from").asString()).isEqualTo("ella");
        assertThat(tr.path("to").asString()).isEqualTo("b2");
        assertThat(tr.path("status").asString()).isEqualTo("IN_TRANSIT");
        assertThat(tr.path("gems").findValuesAsString("code")).containsExactly("GEM-00109", "GEM-00112", "GEM-00119");

        JsonNode cs = read(get("/api/stock-corrections"), OWNER);
        JsonNode sc1 = find(cs, "number", "SC-0001");
        assertThat(sc1.path("status").asString()).isEqualTo("APPROVED");
        assertThat(sc1.path("change").asString()).isEqualTo("carat: 5.10 ct → 5.01 ct");
        JsonNode sc2 = find(cs, "number", "SC-0002");
        assertThat(sc2.path("status").asString()).isEqualTo("PENDING");
        assertThat(sc2.path("approvalNo").asString()).isEqualTo("AP-0013");
        assertThat(sc2.path("change").asString()).isEqualTo("status: In stock → Written off");

        JsonNode checks = read(get("/api/stock-checks"), STAFF);
        JsonNode sck = find(checks, "number", "SCK-0001");
        assertThat(sck.path("missing").get(0).asString()).isEqualTo("GEM-00116");
        assertThat(sck.path("found").asInt()).isEqualTo(26);

        JsonNode waiting = read(get("/api/stock/waiting"), OWNER);
        assertThat(waiting.path("transfersOnTheWay").asLong()).isPositive();
        assertThat(waiting.path("correctionsWaiting").asLong()).isPositive();
    }

    @Test
    void whoMayDoWhat() throws Exception {
        for (String e : new String[] {SALES, ACCOUNTANT}) {
            mvc.perform(get("/api/transfers").with(who.get(e).bearer())).andExpect(status().isForbidden());
            mvc.perform(get("/api/stock-corrections").with(who.get(e).bearer())).andExpect(status().isForbidden());
            mvc.perform(get("/api/stock-checks/start").param("branch", "ella").with(who.get(e).bearer()))
                    .andExpect(status().isForbidden());
        }
        for (String e : new String[] {SALES, STAFF, ACCOUNTANT}) {
            mvc.perform(get("/api/stock/value").with(who.get(e).bearer())).andExpect(status().isForbidden());
        }
        // staff of Ella cannot check or send from Branch 2
        mvc.perform(get("/api/stock-checks/start").param("branch", "b2").with(who.get(STAFF).bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/transfers/gems").param("branch", "b2").with(who.get(STAFF).bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void sendAndReceiveBetweenBranches() throws Exception {
        List<String> codes = sendable("ella").subList(0, 2);
        JsonNode t = created(json(post("/api/transfers"), Map.of("from", "ella", "to", "b2", "gems", codes, "notes", "Test move"),
                STAFF));
        String no = t.path("number").asString();
        assertThat(no).matches("TR-\\d{4}");
        assertThat(t.path("status").asString()).isEqualTo("IN_TRANSIT");
        assertThat(t.path("canReceive").asBoolean()).isFalse(); // Kasun works at Ella
        assertThat(lastLog()).isEqualTo("Transfer sent");
        JsonNode g = read(get("/api/gems/" + codes.get(0)), OWNER);
        assertThat(g.path("status").asString()).isEqualTo("IN_TRANSIT");
        assertThat(g.path("branch").asString()).isEqualTo("ella");
        assertThat(sendable("ella")).doesNotContain(codes.get(0));
        // cannot be sent twice, cannot be moved on the gem form while on the way
        mvc.perform(json(post("/api/transfers"), Map.of("from", "ella", "to", "b2", "gems", codes), OWNER))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_AVAILABLE"));

        // Ella staff cannot confirm at Branch 2
        mvc.perform(json(post("/api/transfers/" + no + "/receive"), Map.of("gems", codes), STAFF))
                .andExpect(status().isForbidden());
        // every gem must be ticked
        mvc.perform(json(post("/api/transfers/" + no + "/receive"), Map.of("gems", codes.subList(0, 1)), OWNER))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_ALL_TICKED"));
        JsonNode r = read(json(post("/api/transfers/" + no + "/receive"),
                Map.of("gems", List.of(codes.get(0).toLowerCase(), codes.get(1) + "|CGH-SC-1")), OWNER), OWNER);
        assertThat(r.path("status").asString()).isEqualTo("RECEIVED");
        assertThat(r.path("receivedBy").asString()).isEqualTo("Ruwan Perera");
        assertThat(lastLog()).isEqualTo("Transfer received");
        g = read(get("/api/gems/" + codes.get(0)), OWNER);
        assertThat(g.path("status").asString()).isEqualTo("IN_STOCK");
        assertThat(g.path("branch").asString()).isEqualTo("b2");
        assertThat(g.path("history").findValuesAsString("action")).contains("Moved", "Received");
        mvc.perform(json(post("/api/transfers/" + no + "/receive"), Map.of("gems", codes), OWNER)).andExpect(status().isConflict());

        // a sold gem and the same branch are refused
        mvc.perform(json(post("/api/transfers"), Map.of("from", "ella", "to", "b2", "gems", List.of("GEM-00044")), OWNER))
                .andExpect(status().isConflict());
        mvc.perform(json(post("/api/transfers"), Map.of("from", "ella", "to", "ella", "gems", sendable("ella").subList(0, 1)),
                OWNER)).andExpect(status().isBadRequest());
    }

    @Test
    void stockCheckFindsMissingWrongAndUnknown() throws Exception {
        JsonNode start = read(get("/api/stock-checks/start").param("branch", "ella"), STAFF);
        List<String> expected = new ArrayList<>(start.path("expected").findValuesAsString("code"));
        assertThat(expected).isNotEmpty().doesNotContain("GEM-00109"); // on the way to Branch 2
        String first = expected.get(0);
        assertThat(read(get("/api/stock-checks/scan").param("branch", "ella").param("code", first), STAFF).path("result").asString())
                .isEqualTo("FOUND");
        JsonNode sold = read(get("/api/stock-checks/scan").param("branch", "ella").param("code", "GEM-00044"), STAFF);
        assertThat(sold.path("result").asString()).isEqualTo("WRONG_PLACE");
        assertThat(sold.path("note").asString()).startsWith("Sold");
        assertThat(read(get("/api/stock-checks/scan").param("branch", "ella").param("code", "HELLO"), STAFF).path("result")
                .asString()).isEqualTo("UNKNOWN");

        List<String> scanned = new ArrayList<>(expected.subList(0, expected.size() - 2));
        scanned.add("GEM-00044");
        scanned.add("hello");
        scanned.add(first); // scanned twice
        JsonNode c = created(json(post("/api/stock-checks"), Map.of("branch", "ella", "scanned", scanned), STAFF));
        assertThat(c.path("number").asString()).matches("SCK-\\d{4}");
        assertThat(c.path("expected").asInt()).isEqualTo(expected.size());
        assertThat(c.path("found").asInt()).isEqualTo(expected.size() - 2);
        assertThat(c.path("missing").size()).isEqualTo(2);
        assertThat(c.path("wrong").get(0).asString()).isEqualTo("GEM-00044");
        assertThat(c.path("unknown").get(0).asString()).isEqualTo("HELLO");
        assertThat(lastLog()).isEqualTo("Stock check finished");
        assertThat(read(get("/api/stock-checks"), OWNER).get(0).path("number").asString()).isEqualTo(c.path("number").asString());
    }

    @Test
    void stockValueForTheOwner() throws Exception {
        JsonNode v = read(get("/api/stock/value"), OWNER);
        BigDecimal cost = v.path("total").path("cost").decimalValue();
        BigDecimal price = v.path("total").path("price").decimalValue();
        assertThat(cost).isPositive();
        assertThat(price).isGreaterThan(cost);
        BigDecimal byType = BigDecimal.ZERO;
        long items = 0;
        for (JsonNode r : v.path("byGemType")) {
            byType = byType.add(r.path("price").decimalValue());
            items += r.path("items").asLong();
        }
        assertThat(byType).isEqualByComparingTo(price);
        assertThat(items).isEqualTo(v.path("total").path("items").asLong());
        assertThat(v.path("byBranch").findValuesAsString("group")).contains("ella");
        Integer inDb = jdbc.queryForObject("select count(*) from gem where status in "
                + "('IN_STOCK','NOT_READY','RESERVED','ON_HOLD','RETURNED','IN_TRANSIT')", Integer.class);
        assertThat(items).isEqualTo(inDb.longValue());
    }

    @Test
    void correctionsChangeTheGemOnlyAfterTheOwnerApproves() throws Exception {
        String code = sendable("ella").get(0);
        JsonNode c = created(json(post("/api/stock-corrections"),
                Map.of("gem", code, "kind", "STATUS", "to", "WRITTEN_OFF", "reason", "Dropped and cracked"), STAFF));
        assertThat(c.path("status").asString()).isEqualTo("PENDING");
        assertThat(c.path("change").asString()).isEqualTo("status: In stock → Written off");
        String ap = c.path("approvalNo").asString();
        assertThat(ap).matches("AP-\\d{4}");
        assertThat(read(get("/api/gems/" + code), OWNER).path("status").asString()).isEqualTo("IN_STOCK");
        mvc.perform(json(post("/api/stock-corrections"), Map.of("gem", code, "kind", "CARAT", "to", "1.5", "reason", "x"), STAFF))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_WAITING"));

        mvc.perform(post("/api/approvals/" + ap + "/approve").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        JsonNode g = read(get("/api/gems/" + code), OWNER);
        assertThat(g.path("status").asString()).isEqualTo("WRITTEN_OFF");
        assertThat(g.path("history").findValuesAsString("action")).contains("Correction requested", "Stock correction");
        assertThat(find(read(get("/api/stock-corrections"), OWNER), "number", c.path("number").asString()).path("status").asString())
                .isEqualTo("APPROVED");

        // found again: back in stock
        JsonNode back = created(json(post("/api/stock-corrections"),
                Map.of("gem", code, "kind", "STATUS", "to", "IN_STOCK", "reason", "Found in the repair tray"), OWNER));
        mvc.perform(post("/api/approvals/" + back.path("approvalNo").asString() + "/approve").with(who.get(OWNER).bearer()))
                .andExpect(status().isOk());
        assertThat(read(get("/api/gems/" + code), OWNER).path("status").asString()).isEqualTo("IN_STOCK");

        // carat: rejected → nothing changes
        BigDecimal carat = read(get("/api/gems/" + code), OWNER).path("carat").decimalValue();
        JsonNode cr = created(json(post("/api/stock-corrections"),
                Map.of("gem", code, "kind", "CARAT", "to", "9.99", "reason", "Re-weighed"), STAFF));
        assertThat(cr.path("to").asString()).isEqualTo("9.99");
        mvc.perform(post("/api/approvals/" + cr.path("approvalNo").asString() + "/reject").with(who.get(OWNER).bearer()))
                .andExpect(status().isOk());
        assertThat(read(get("/api/gems/" + code), OWNER).path("carat").decimalValue()).isEqualByComparingTo(carat);

        // branch: approved → moved
        JsonNode br = created(json(post("/api/stock-corrections"),
                Map.of("gem", code, "kind", "BRANCH", "to", "b2", "reason", "It was on the Branch 2 shelf"), OWNER));
        mvc.perform(post("/api/approvals/" + br.path("approvalNo").asString() + "/approve").with(who.get(OWNER).bearer()))
                .andExpect(status().isOk());
        assertThat(read(get("/api/gems/" + code), OWNER).path("branch").asString()).isEqualTo("b2");

        // the sample request AP-0013 (GEM-00116 broken) can be approved
        mvc.perform(post("/api/approvals/AP-0013/approve").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        assertThat(read(get("/api/gems/GEM-00116"), OWNER).path("status").asString()).isEqualTo("WRITTEN_OFF");

        // refused: no change, sold gem, bad values
        String other = sendable("ella").get(0);
        String now = read(get("/api/gems/" + other), OWNER).path("carat").decimalValue().toPlainString();
        mvc.perform(json(post("/api/stock-corrections"), Map.of("gem", other, "kind", "CARAT", "to", now, "reason", "x"), OWNER))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOTHING_TO_CHANGE"));
        mvc.perform(json(post("/api/stock-corrections"), Map.of("gem", "GEM-00044", "kind", "STATUS", "to", "WRITTEN_OFF",
                "reason", "x"), OWNER)).andExpect(status().isConflict());
        mvc.perform(json(post("/api/stock-corrections"), Map.of("gem", other, "kind", "STATUS", "to", "SOLD", "reason", "x"), OWNER))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/stock-corrections"), Map.of("gem", other, "kind", "CARAT", "to", "abc", "reason", "x"), OWNER))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/stock-corrections"), Map.of("gem", "GEM-99999", "kind", "CARAT", "to", "1", "reason", "x"),
                OWNER)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ helpers

    private List<String> sendable(String branch) throws Exception {
        return new ArrayList<>(read(get("/api/transfers/gems").param("branch", branch), OWNER).findValuesAsString("code"));
    }

    private static JsonNode find(JsonNode list, String key, String value) {
        for (JsonNode x : list) {
            if (x.path(key).asString().equals(value)) {
                return x;
            }
        }
        throw new AssertionError(value + " not in list");
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
