package lk.nuvi.gemshop.purchasing;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static lk.nuvi.gemshop.SignIn.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P2-03 Suppliers and purchases. Own database: it adds gems, suppliers and purchases. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=purchasing")
class PurchasingTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Colombo"));

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
    void sampleSuppliersPurchasesAndReturnAreLoaded() throws Exception {
        JsonNode list = read(get("/api/suppliers"), ACCOUNTANT);
        assertThat(list.size()).isGreaterThanOrEqualTo(5);
        JsonNode ratna = find(list, "SUP-01");
        assertThat(ratna.path("name").asString()).isEqualTo("Ratna Gems & Lapidary");
        assertThat(ratna.path("idType").asString()).isEqualTo("BR");
        assertThat(ratna.path("idNo").asString()).isEqualTo("PV 00213456");
        assertThat(ratna.path("purchases").asLong()).isPositive();
        // the NIC / BR number is encrypted in the database
        assertThat(jdbc.queryForObject("select id_no from supplier where code = 'SUP-01'", String.class)).startsWith("v1:");

        assertThat(jdbc.queryForObject("select count(*) from purchase", Integer.class)).isGreaterThanOrEqualTo(14);
        assertThat(jdbc.queryForObject("select count(*) from purchase_line", Integer.class)).isGreaterThanOrEqualTo(87);
        JsonNode p = read(get("/api/purchases/PUR-0009"), OWNER);
        assertThat(p.path("supplierCode").asString()).isEqualTo("SUP-01");
        assertThat(p.path("gems").size()).isEqualTo(10);
        assertThat(p.path("total").decimalValue()).isEqualByComparingTo("1711000");
        assertThat(p.path("gems").get(0).path("code").asString()).isEqualTo("GEM-00044");

        JsonNode rets = read(get("/api/supplier-returns"), OWNER);
        JsonNode sr = rets.get(rets.size() - 1);
        assertThat(sr.path("number").asString()).isEqualTo("SR-0001");
        assertThat(sr.path("gemCode").asString()).isEqualTo("GEM-00100");
        assertThat(sr.path("refundMethod").asString()).isEqualTo("CASH");

        JsonNode one = read(get("/api/suppliers/SUP-03"), OWNER);
        assertThat(one.path("returns").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(one.path("refunded").decimalValue()).isGreaterThanOrEqualTo(new java.math.BigDecimal("70000"));
        assertThat(one.path("purchases").size()).isPositive();
    }

    @Test
    void onlyTheOwnerBuysAndTheAccountantMayLook() throws Exception {
        for (String e : new String[] {SALES, STAFF}) {
            mvc.perform(get("/api/suppliers").with(who.get(e).bearer())).andExpect(status().isForbidden());
            mvc.perform(get("/api/purchases").with(who.get(e).bearer())).andExpect(status().isForbidden());
        }
        read(get("/api/purchases"), ACCOUNTANT);
        read(get("/api/supplier-returns"), ACCOUNTANT);
        mvc.perform(json(post("/api/suppliers"), supplier("Accountant Gems"), ACCOUNTANT)).andExpect(status().isForbidden());
        mvc.perform(json(post("/api/purchases"), purchase("SUP-01", upload(), line("x", false)), ACCOUNTANT))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/supplier-returns/gems").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isForbidden());
    }

    @Test
    void addAndEditASupplier() throws Exception {
        JsonNode s = JSON.readTree(mvc.perform(json(post("/api/suppliers"), supplier("Beruwala Gem Traders"), OWNER))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String code = s.path("code").asString();
        assertThat(code).matches("SUP-\\d{2,}");
        assertThat(s.path("idNo").asString()).isEqualTo("199012345678");
        assertThat(lastLog()).isEqualTo("Supplier added");

        Map<String, Object> f = supplier("Beruwala Gem Traders (Pvt) Ltd");
        f.put("idType", "BR");
        f.put("idNo", "pv 123");
        JsonNode e = read(json(put("/api/suppliers/" + code), f, OWNER), OWNER);
        assertThat(e.path("name").asString()).isEqualTo("Beruwala Gem Traders (Pvt) Ltd");
        assertThat(e.path("idNo").asString()).isEqualTo("PV 123");
        assertThat(lastLog()).isEqualTo("Supplier edited");

        Map<String, Object> bad = supplier("");
        bad.remove("phone");
        mvc.perform(json(post("/api/suppliers"), bad, OWNER)).andExpect(status().isBadRequest());
    }

    @Test
    void buyingAddsEveryGemToStockWithItsOwnCode() throws Exception {
        String bill = upload();
        Map<String, Object> f = purchase("SUP-02", bill, line("Bought Ruby", false), line("Rough Sapphire", true));
        JsonNode p = JSON.readTree(mvc.perform(json(post("/api/purchases"), f, OWNER)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String number = p.path("number").asString();
        assertThat(number).matches("PUR-\\d{4}");
        assertThat(p.path("gems").size()).isEqualTo(2);
        assertThat(p.path("total").decimalValue()).isEqualByComparingTo("400000");
        assertThat(p.path("paidBy").asString()).isEqualTo("CASH");
        assertThat(p.path("bill").path("kind").asString()).isEqualTo("BILL_PHOTO");
        assertThat(lastLog()).isEqualTo("Purchase recorded");

        String first = p.path("gems").get(0).path("code").asString();
        String second = p.path("gems").get(1).path("code").asString();
        assertThat(first).isNotEqualTo(second);
        JsonNode g1 = read(get("/api/gems/" + first), OWNER);
        assertThat(g1.path("status").asString()).isEqualTo("IN_STOCK");
        assertThat(g1.path("supplierCode").asString()).isEqualTo("SUP-02");
        assertThat(g1.path("purchaseNo").asString()).isEqualTo(number);
        assertThat(g1.path("history").get(0).path("detail").asString()).contains("Bought from").contains(number);
        JsonNode g2 = read(get("/api/gems/" + second), OWNER);
        assertThat(g2.path("status").asString()).isEqualTo("NOT_READY");
        assertThat(g2.path("history").get(0).path("detail").asString()).contains("not ready to sell");

        // purchase history by supplier and date
        JsonNode hist = read(get("/api/purchases").param("supplier", "SUP-02").param("from", TODAY.toString())
                .param("to", TODAY.toString()), OWNER);
        assertThat(hist.path("items").get(0).path("number").asString()).isEqualTo(number);
        assertThat(hist.path("items").get(0).path("hasBill").asBoolean()).isTrue();
        JsonNode other = read(get("/api/purchases").param("supplier", "SUP-04").param("from", TODAY.toString()), OWNER);
        assertThat(other.path("items").findValuesAsString("number")).doesNotContain(number);
        assertThat(read(get("/api/purchases/summary"), OWNER).path("purchasesThisMonth").asLong()).isPositive();
    }

    @Test
    void aMistakeOnOneGemSavesNothing() throws Exception {
        int gems = jdbc.queryForObject("select count(*) from gem", Integer.class);
        int purchases = jdbc.queryForObject("select count(*) from purchase", Integer.class);
        Map<String, Object> bad = line("Too cheap", false);
        bad.put("min", 100000); // below cost
        mvc.perform(json(post("/api/purchases"), purchase("SUP-01", upload(), line("Fine", false), bad), OWNER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICES"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Gem 2:")))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[1].min"));
        assertThat(jdbc.queryForObject("select count(*) from gem", Integer.class)).isEqualTo(gems);
        assertThat(jdbc.queryForObject("select count(*) from purchase", Integer.class)).isEqualTo(purchases);

        Map<String, Object> future = purchase("SUP-01", upload(), line("Tomorrow", false));
        future.put("date", TODAY.plusDays(1).toString());
        mvc.perform(json(post("/api/purchases"), future, OWNER)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FUTURE_DATE"));

        Map<String, Object> noBill = purchase("SUP-01", null, line("No bill", false));
        mvc.perform(json(post("/api/purchases"), noBill, OWNER)).andExpect(status().isBadRequest());
        Map<String, Object> noGems = purchase("SUP-01", upload());
        mvc.perform(json(post("/api/purchases"), noGems, OWNER)).andExpect(status().isBadRequest());
    }

    @Test
    void returnAGemToItsSupplier() throws Exception {
        JsonNode p = JSON.readTree(mvc.perform(json(post("/api/purchases"), purchase("SUP-05", upload(), line("Faulty Spinel", false)),
                OWNER)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String code = p.path("gems").get(0).path("code").asString();
        JsonNode can = read(get("/api/supplier-returns/gems").param("supplier", "SUP-05"), OWNER);
        assertThat(can.findValuesAsString("code")).contains(code);

        Map<String, Object> f = new HashMap<>();
        f.put("gem", code);
        f.put("reasonType", "Faulty / damaged");
        f.put("details", "Chip on the girdle");
        f.put("refund", 200000);
        f.put("refundMethod", "CREDIT");
        JsonNode r = JSON.readTree(mvc.perform(json(post("/api/supplier-returns"), f, OWNER)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(r.path("number").asString()).matches("SR-\\d{4}");
        assertThat(r.path("supplierCode").asString()).isEqualTo("SUP-05");
        assertThat(r.path("reason").asString()).isEqualTo("Faulty / damaged: Chip on the girdle");
        assertThat(lastLog()).isEqualTo("Returned to supplier");

        JsonNode g = read(get("/api/gems/" + code), OWNER);
        assertThat(g.path("status").asString()).isEqualTo("RETURNED_SUPPLIER");
        assertThat(g.path("history").findValuesAsString("action")).contains("Returned to supplier");
        // left stock: not in the stock list, not returnable again
        assertThat(read(get("/api/gems").param("q", code), OWNER).path("total").asInt()).isZero();
        assertThat(read(get("/api/supplier-returns/gems"), OWNER).findValuesAsString("code")).doesNotContain(code);
        mvc.perform(json(post("/api/supplier-returns"), f, OWNER)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_RETURN"));

        // a sold gem cannot go back
        f.put("gem", "GEM-00044");
        mvc.perform(json(post("/api/supplier-returns"), f, OWNER)).andExpect(status().isConflict());
    }

    @Test
    void aGemAddedByHandCanBeLinkedToASupplier() throws Exception {
        Map<String, Object> f = line("Old stock Ruby", false);
        f.put("photos", List.of());
        f.put("branch", "ella");
        f.put("supplier", "SUP-03");
        JsonNode g = JSON.readTree(mvc.perform(json(post("/api/gems"), f, OWNER)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        assertThat(g.path("supplierCode").asString()).isEqualTo("SUP-03");
        assertThat(g.path("purchaseNo").isNull() || g.path("purchaseNo").isMissingNode()).isTrue();
        String code = g.path("code").asString();

        f.put("supplier", "");
        JsonNode e = read(json(put("/api/gems/" + code), f, OWNER), OWNER);
        assertThat(e.path("supplierCode").isNull() || e.path("supplierCode").isMissingNode()).isTrue();
        assertThat(e.path("history").findValuesAsString("detail")).anyMatch(d -> d.contains("Supplier SUP-03 → not linked"));

        f.put("supplier", "SUP-99");
        mvc.perform(json(put("/api/gems/" + code), f, OWNER)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("supplier"));

        // a bought gem keeps its supplier whatever the form says
        JsonNode p = JSON.readTree(mvc.perform(json(post("/api/purchases"), purchase("SUP-01", upload(), line("Bought", false)),
                OWNER)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String bought = p.path("gems").get(0).path("code").asString();
        f.put("supplier", "SUP-02");
        assertThat(read(json(put("/api/gems/" + bought), f, OWNER), OWNER).path("supplierCode").asString()).isEqualTo("SUP-01");
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> supplier(String name) {
        Map<String, Object> f = new HashMap<>();
        f.put("name", name);
        f.put("contact", "K. Perera");
        f.put("phone", "+94 77 123 4567");
        f.put("address", "12 Galle Road, Beruwala");
        f.put("idType", "NIC");
        f.put("idNo", "199012345678");
        return f;
    }

    private static Map<String, Object> line(String name, boolean notReady) {
        Map<String, Object> f = new HashMap<>();
        f.put("name", name);
        f.put("productType", "Loose gem");
        f.put("gemType", "Ruby");
        f.put("carat", 1.25);
        f.put("colour", "Royal Blue");
        f.put("shape", "Oval");
        f.put("treatment", "Heated");
        f.put("certType", "SHOP");
        f.put("cost", 200000);
        f.put("price", 320000);
        f.put("min", 270000);
        f.put("notReady", notReady);
        return f;
    }

    @SafeVarargs
    private static Map<String, Object> purchase(String supplier, String bill, Map<String, Object>... lines) {
        Map<String, Object> f = new HashMap<>();
        f.put("date", TODAY.toString());
        f.put("supplier", supplier);
        f.put("branch", "ella");
        f.put("billPhoto", bill);
        f.put("notes", "Test purchase");
        f.put("lines", List.of(lines));
        return f;
    }

    private static JsonNode find(JsonNode list, String code) {
        for (JsonNode x : list) {
            if (x.path("code").asString().equals(code)) {
                return x;
            }
        }
        throw new AssertionError(code + " not in list");
    }

    private JsonNode read(MockHttpServletRequestBuilder req, String as) throws Exception {
        return JSON.readTree(mvc.perform(req.with(who.get(as).bearer())).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body, String as) throws Exception {
        req.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        return req.with(who.get(as).bearer());
    }

    private String upload() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "png", out);
        return JSON.readTree(mvc.perform(multipart("/api/files")
                .file(new MockMultipartFile("file", "bill.png", "image/png", out.toByteArray()))
                .param("kind", "BILL_PHOTO").with(who.get(OWNER).bearer())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("id").asString();
    }

    private String lastLog() {
        return jdbc.queryForObject("select action from activity_log order by id desc limit 1", String.class);
    }
}
