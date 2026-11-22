package lk.nuvi.gemshop.gems;

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
import java.util.ArrayList;
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

/** P2-01 Gems. Runs on its own database: the gems it adds must not change counts in other tests. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=gems")
class GemsTest {

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
    void sampleGemsAreLoaded() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from gem", Integer.class)).isGreaterThanOrEqualTo(87);
        JsonNode all = read(get("/api/gems").param("status", "all").param("size", "200"), OWNER);
        assertThat(all.path("total").asInt()).isGreaterThanOrEqualTo(87);
        JsonNode g = read(get("/api/gems/GEM-00044"), OWNER);
        assertThat(g.path("status").asString()).isEqualTo("SOLD");
        assertThat(g.path("saleNo").asString()).isEqualTo("INV-000101");
        assertThat(g.path("certType").asString()).isEqualTo("LAB");
        assertThat(g.path("certLab").asString()).isEqualTo("NGJA Gem Testing Laboratory");
        assertThat(g.path("cost").decimalValue()).isEqualByComparingTo("294000");
        assertThat(g.path("history").size()).isEqualTo(2);
        assertThat(g.path("canSell").asBoolean()).isFalse();
        // shop certificate number from the prototype
        assertThat(read(get("/api/gems/GEM-00049"), OWNER).path("certNo").asString()).isEqualTo("CGH-SC-00049");
        // the next gem continues after the prototype
        assertThat(read(get("/api/gems/next-code"), OWNER).path("code").asString()).matches("GEM-00\\d{3}");
    }

    @Test
    void costAndMinimumOnlyForTheOwner() throws Exception {
        for (String e : new String[] {SALES, STAFF}) {
            JsonNode g = read(get("/api/gems/GEM-00044"), e);
            assertThat(g.path("price").decimalValue()).isEqualByComparingTo("475000");
            assertThat(g.path("cost").isNull() || g.path("cost").isMissingNode()).isTrue();
            assertThat(g.path("min").isNull() || g.path("min").isMissingNode()).isTrue();
            assertThat(g.path("supplierCode").isNull() || g.path("supplierCode").isMissingNode()).isTrue();
            JsonNode page = read(get("/api/gems"), e);
            assertThat(page.path("totalCost").isNull() || page.path("totalCost").isMissingNode()).isTrue();
            assertThat(page.path("items").get(0).path("cost").isNull()).isTrue();
        }
        // only the owner adds and edits; the accountant does not open All gems
        mvc.perform(json(post("/api/gems"), form("Test", "ella"), SALES)).andExpect(status().isForbidden());
        mvc.perform(json(put("/api/gems/GEM-00044"), form("Test", "ella"), STAFF)).andExpect(status().isForbidden());
        mvc.perform(get("/api/gems").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/gems/summary").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isOk());
    }

    @Test
    void searchAndScan() throws Exception {
        JsonNode stock = read(get("/api/gems").param("size", "200"), OWNER);
        for (JsonNode g : stock.path("items")) {
            assertThat(g.path("status").asString()).isNotIn("SOLD", "RETURNED_SUPPLIER", "WRITTEN_OFF");
        }
        assertThat(stock.path("totalPrice").decimalValue().signum()).isPositive();
        assertThat(stock.path("totalCost").decimalValue().signum()).isPositive();

        JsonNode rubies = read(get("/api/gems").param("gemType", "Ruby").param("status", "all").param("size", "200"), OWNER);
        assertThat(rubies.path("total").asInt()).isPositive();
        rubies.path("items").forEach(g -> assertThat(g.path("gemType").asString()).isEqualTo("Ruby"));

        JsonNode sold = read(get("/api/gems").param("status", "SOLD").param("cert", "LAB").param("size", "200"), OWNER);
        sold.path("items").forEach(g -> {
            assertThat(g.path("status").asString()).isEqualTo("SOLD");
            assertThat(g.path("certType").asString()).isEqualTo("LAB");
        });

        // typed / scanned codes
        assertThat(read(get("/api/gems").param("q", "126").param("status", "all"), OWNER).path("items").get(0).path("code").asString())
                .isEqualTo("GEM-00126");
        assertThat(read(get("/api/gems/gem-126"), STAFF).path("code").asString()).isEqualTo("GEM-00126");
        mvc.perform(get("/api/gems/GEM-99999").with(who.get(OWNER).bearer())).andExpect(status().isNotFound());
        mvc.perform(get("/api/gems").param("status", "LOST").with(who.get(OWNER).bearer())).andExpect(status().isBadRequest());

        JsonNode home = read(get("/api/gems/summary"), SALES);
        assertThat(home.path("inStock").asLong()).isPositive();
        assertThat(home.path("newest").size()).isEqualTo(6);
    }

    @Test
    void ownerAddsAGemWithPhotosAndLabCertificate() throws Exception {
        String photo1 = upload("PRODUCT_PHOTO"), photo2 = upload("PRODUCT_PHOTO"), scan = upload("CERTIFICATE");
        Map<String, Object> f = form("Blue Sapphire Ring", "ella");
        f.put("certType", "LAB");
        f.put("certLab", "GIA");
        f.put("certNo", "GIA-2201");
        f.put("certDate", "2026-10-01");
        f.put("certScan", scan);
        f.put("photos", List.of(photo1, photo2));
        JsonNode g = JSON.readTree(mvc.perform(json(post("/api/gems"), f, OWNER)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String code = g.path("code").asString();
        assertThat(code).startsWith("GEM-");
        assertThat(g.path("status").asString()).isEqualTo("IN_STOCK");
        assertThat(g.path("photos").size()).isEqualTo(2);
        assertThat(g.path("certScan").path("id").asString()).isEqualTo(scan);
        assertThat(g.path("history").get(0).path("action").asString()).isEqualTo("Added");
        assertThat(lastLog()).isEqualTo("Gem added");
        mvc.perform(get(g.path("photos").get(0).path("url").asString())).andExpect(status().isOk());
        // the first photo shows in All gems
        JsonNode card = read(get("/api/gems").param("q", code), SALES).path("items").get(0);
        assertThat(card.path("photo").asString()).startsWith("/api/files/");

        // Settings → Drop-down lists: "Used by" counts this gem's lab
        JsonNode lists = read(get("/api/settings/lists"), OWNER);
        for (JsonNode l : lists) {
            if (l.path("name").asString().equals("LAB")) {
                for (JsonNode v : l.path("values")) {
                    if (v.path("value").asString().equals("GIA")) {
                        assertThat(v.path("usedBy").asLong()).isPositive();
                    }
                }
            }
        }

        // edit: one photo removed, price changed → history and log
        f.put("photos", List.of(photo2));
        f.put("price", 520000);
        JsonNode e = read(json(put("/api/gems/" + code), f, OWNER), OWNER);
        assertThat(e.path("photos").size()).isEqualTo(1);
        assertThat(e.path("history").get(1).path("action").asString()).isEqualTo("Price changed");
        assertThat(e.path("history").get(1).path("detail").asString()).contains("Selling price LKR 480,000 → 520,000");
        assertThat(lastLog()).isEqualTo("Gem price changed");
        assertThat(jdbc.queryForObject("select count(*) from stored_file where owner_ref = ?", Integer.class, code)).isEqualTo(2);
    }

    @Test
    void formChecks() throws Exception {
        Map<String, Object> f = form("Bad prices", "ella");
        f.put("min", 500000); // above selling price
        mvc.perform(json(post("/api/gems"), f, OWNER)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRICES")).andExpect(jsonPath("$.fieldErrors[0].field").value("min"));
        f.put("min", 100000); // below cost
        mvc.perform(json(post("/api/gems"), f, OWNER)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PRICES"));

        Map<String, Object> l = form("Not in list", "ella");
        l.put("gemType", "Kryptonite");
        mvc.perform(json(post("/api/gems"), l, OWNER)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LIST_VALUE")).andExpect(jsonPath("$.fieldErrors[0].field").value("gemType"));

        Map<String, Object> c = form("Lab without number", "ella");
        c.put("certType", "LAB");
        c.put("certLab", "GIA");
        mvc.perform(json(post("/api/gems"), c, OWNER)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CERT_NEEDED"));

        Map<String, Object> n = form("", "ella");
        mvc.perform(json(post("/api/gems"), n, OWNER)).andExpect(status().isBadRequest());
    }

    @Test
    void notReadyThenMarkReady() throws Exception {
        Map<String, Object> f = form("Rough Ruby", "ella");
        f.put("notReady", true);
        f.put("gemType", "Ruby");
        f.put("certType", "SHOP");
        JsonNode g = JSON.readTree(mvc.perform(json(post("/api/gems"), f, OWNER)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String code = g.path("code").asString();
        assertThat(g.path("status").asString()).isEqualTo("NOT_READY");
        assertThat(g.path("canSell").asBoolean()).isFalse();
        assertThat(g.path("certNo").asString()).isEqualTo("CGH-SC-" + code.substring(4));
        // "Not ready to sell" filter and the home count
        assertThat(read(get("/api/gems").param("status", "NOT_READY"), OWNER).path("total").asInt()).isPositive();
        assertThat(read(get("/api/gems/summary"), OWNER).path("notReady").asLong()).isPositive();

        mvc.perform(post("/api/gems/" + code + "/ready").with(who.get(SALES).bearer())).andExpect(status().isForbidden());
        mvc.perform(post("/api/gems/" + code + "/ready").with(who.get(STAFF).bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_STOCK")).andExpect(jsonPath("$.canSell").value(true));
        assertThat(lastLog()).isEqualTo("Gem ready to sell");
        mvc.perform(post("/api/gems/" + code + "/ready").with(who.get(OWNER).bearer())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_NOT_READY"));
    }

    @Test
    void salespersonSeesOnlyTheirBranch() throws Exception {
        JsonNode g = JSON.readTree(mvc.perform(json(post("/api/gems"), form("Branch 2 gem", "b2"), OWNER))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String code = g.path("code").asString();
        mvc.perform(get("/api/gems/" + code).with(who.get(SALES).bearer())).andExpect(status().isNotFound());
        JsonNode page = read(get("/api/gems").param("q", code), SALES);
        assertThat(page.path("total").asInt()).isZero();
        assertThat(read(get("/api/gems").param("q", code), OWNER).path("total").asInt()).isEqualTo(1);
    }

    @Test
    void renamingAListValueRenamesTheGems() throws Exception {
        Map<String, Object> f = form("Shape test", "ella");
        f.put("shape", "Cushion");
        String code = JSON.readTree(mvc.perform(json(post("/api/gems"), f, OWNER)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("code").asString();
        Long id = jdbc.queryForObject("select id from lookup_value where list_name = 'SHAPE' and value = 'Cushion'", Long.class);
        mvc.perform(json(put("/api/settings/lists/values/" + id), Map.of("value", "Cushion cut"), OWNER)).andExpect(status().isOk());
        assertThat(read(get("/api/gems/" + code), OWNER).path("shape").asString()).isEqualTo("Cushion cut");
        // a value used by gems cannot be removed
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/settings/lists/values/" + id)
                .with(who.get(OWNER).bearer())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LIST_IN_USE"));
        mvc.perform(json(put("/api/settings/lists/values/" + id), Map.of("value", "Cushion"), OWNER)).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- helpers

    private static Map<String, Object> form(String name, String branch) {
        Map<String, Object> f = new HashMap<>();
        f.put("name", name);
        f.put("productType", "Ring");
        f.put("gemType", "Blue Sapphire");
        f.put("carat", 2.15);
        f.put("colour", "Royal Blue");
        f.put("shape", "Oval");
        f.put("treatment", "Heated");
        f.put("description", "Test gem");
        f.put("certType", "NONE");
        f.put("photos", new ArrayList<>());
        f.put("cost", 300000);
        f.put("price", 480000);
        f.put("min", 400000);
        f.put("branch", branch);
        return f;
    }

    private JsonNode read(MockHttpServletRequestBuilder req, String as) throws Exception {
        return JSON.readTree(mvc.perform(req.with(who.get(as).bearer())).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body, String as) throws Exception {
        req.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        return req.with(who.get(as).bearer());
    }

    private String upload(String kind) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB), "png", out);
        return JSON.readTree(mvc.perform(multipart("/api/files")
                .file(new MockMultipartFile("file", "gem.png", "image/png", out.toByteArray()))
                .param("kind", kind).with(who.get(OWNER).bearer())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("id").asString();
    }

    private String lastLog() {
        return jdbc.queryForObject("select action from activity_log order by id desc limit 1", String.class);
    }
}
