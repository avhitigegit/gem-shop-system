package lk.nuvi.gemshop.settings;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static lk.nuvi.gemshop.SignIn.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import lk.nuvi.gemshop.SignIn;
import lk.nuvi.gemshop.SignIn.Signed;
import lk.nuvi.gemshop.TestcontainersConfiguration;
import lk.nuvi.gemshop.common.mail.MailService;
import lk.nuvi.gemshop.settings.commission.Commission;
import lk.nuvi.gemshop.settings.lists.ListName;
import lk.nuvi.gemshop.settings.lists.Lists;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * P1-06: Settings – shop details, staff logins, exchange rates, commission rules, branches, drop-down lists.
 * Runs on its own database (own test context): the logins and branches it adds must not show up in other tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.test.own-database=settings")
class SettingsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired Commission commission;
    @Autowired Lists lists;
    @MockitoSpyBean MailService mail;

    private final Map<String, Signed> who = new LinkedHashMap<>();

    @BeforeEach
    void signIn() throws Exception {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ? "
                + "where email like '%@ceylongemhouse.lk' or email like '%@fernandoassociates.lk'", encoder.encode(SignIn.PASSWORD));
        for (String e : new String[] {OWNER, SALES, STAFF, ACCOUNTANT}) {
            who.put(e, SignIn.login(mvc, e));
        }
    }

    /** Leave the sample data as other tests expect it (2 open branches, sample people). */
    @AfterEach
    void tidy() {
        jdbc.update("update branch set status = 'INACTIVE' where code not in ('ella', 'b2')");
        jdbc.update("update branch set status = 'OPENING' where code = 'b2'");
        jdbc.update("update app_user set active = false where email like '%@test.lk'");
    }

    // ---------------------------------------------------------------- who may use Settings

    @Test
    void onlyTheOwnerChangesSettings() throws Exception {
        for (String e : new String[] {SALES, STAFF, ACCOUNTANT}) {
            for (String path : List.of("/api/settings/summary", "/api/settings/shop", "/api/settings/staff", "/api/settings/rates",
                    "/api/settings/commission", "/api/settings/branches", "/api/settings/lists")) {
                mvc.perform(get(path).with(who.get(e).bearer())).andExpect(status().isForbidden());
            }
            mvc.perform(json(post("/api/settings/rates"), Map.of("usd", 1, "eur", 1), e)).andExpect(status().isForbidden());
        }
        // every form can read the drop-down values
        mvc.perform(get("/api/lists").with(who.get(STAFF).bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'GEM_TYPE')].values[0]").value("Blue Sapphire"))
                .andExpect(jsonPath("$[?(@.name == 'EXPENSE_TYPE')].values.length()").value(11));
        mvc.perform(get("/api/lists")).andExpect(status().isUnauthorized());
    }

    @Test
    void settingsHome() throws Exception {
        mvc.perform(get("/api/settings/summary").with(who.get(OWNER).bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.staffLogins").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.branches").value(2))
                .andExpect(jsonPath("$.licenceDaysLeft").isNumber())
                .andExpect(jsonPath("$.licenceRemindDays").value(60))
                .andExpect(jsonPath("$.defaultGuidePct").value(10.0))
                .andExpect(jsonPath("$.listValues").value(72));
    }

    // ---------------------------------------------------------------- shop details

    @Test
    void shopDetailsWithLogoAndNoVat() throws Exception {
        JsonNode before = read(get("/api/settings/shop"));
        assertThat(before.path("name").asString()).isEqualTo("Ceylon Gem House");
        assertThat(before.path("cardFeePct").decimalValue()).isEqualByComparingTo("3");
        assertThat(before.has("vatNo") || before.has("vatPct")).isFalse();

        String logo = upload("LOGO", png());
        Map<String, Object> form = shopForm(before);
        form.put("name", "Ceylon Gem House Ella");
        form.put("cardFeePct", 2.5);
        form.put("logoFileId", logo);
        JsonNode saved = JSON.readTree(mvc.perform(json(put("/api/settings/shop"), form, OWNER)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(saved.path("logoFileId").asString()).isEqualTo(logo);
        mvc.perform(get(saved.path("logoUrl").asString())).andExpect(status().isOk());
        // the new name shows in the top bar after the next renewal / sign-in
        mvc.perform(get("/api/auth/me").with(who.get(SALES).bearer())).andExpect(jsonPath("$.shop.name").value("Ceylon Gem House Ella"));
        assertThat(lastLog()).isEqualTo("Shop settings saved");

        // a card fee over 10 % and an empty licence are refused
        Map<String, Object> bad = shopForm(before);
        bad.put("cardFeePct", 12);
        bad.put("ngjaLicenceNo", "");
        mvc.perform(json(put("/api/settings/shop"), bad, OWNER)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field").value(org.hamcrest.Matchers.hasItems("cardFeePct", "ngjaLicenceNo")));

        // back to the default logo and the sample details
        Map<String, Object> back = shopForm(before);
        back.put("removeLogo", true);
        mvc.perform(json(put("/api/settings/shop"), back, OWNER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.logoFileId").doesNotExist()).andExpect(jsonPath("$.name").value("Ceylon Gem House"));
        assertThat(jdbc.queryForObject("select count(*) from stored_file where owner_type = 'SHOP'", Integer.class)).isZero();
    }

    // ---------------------------------------------------------------- staff logins

    @Test
    void addAStaffLoginWhoChoosesTheirPassword() throws Exception {
        String email = "chamari." + System.nanoTime() + "@test.lk";
        JsonNode u = JSON.readTree(mvc.perform(json(post("/api/settings/staff"), staff("Chamari Jayasuriya", email, "SALESPERSON", "ella", true),
                OWNER)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(u.path("hasPassword").asBoolean()).isFalse();
        assertThat(u.path("branch").asString()).isEqualTo("ella");

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq(email), eq("Your login for Ceylon Gem House"), text.capture());
        assertThat(text.getValue()).contains("Hello Chamari").contains("Salesperson").contains("72 hours");
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(text.getValue());
        assertThat(m.find()).isTrue();
        mvc.perform(json(post("/api/auth/reset-password"), Map.of("token", m.group(1), "newPassword", "Sapphire2026"), null))
                .andExpect(status().isNoContent());
        Signed chamari = SignIn.login(mvc, email, "Sapphire2026", false);
        assertThat(chamari.body()).contains("\"role\":\"SALESPERSON\"");

        // the same email twice is refused
        mvc.perform(json(post("/api/settings/staff"), staff("Other", email.toUpperCase(), "STAFF", "ella", true), OWNER))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
        // salesperson and staff need a branch
        mvc.perform(json(post("/api/settings/staff"), staff("No branch", "nb" + System.nanoTime() + "@test.lk", "STAFF", "all", true), OWNER))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BRANCH_NEEDED"));

        // switched off: cannot sign in, other devices signed out
        long id = u.path("id").asLong();
        mvc.perform(json(put("/api/settings/staff/" + id), staff("Chamari Jayasuriya", email, "SALESPERSON", "ella", false), OWNER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(json(post("/api/auth/login"), Map.of("email", email, "password", "Sapphire2026", "remember", false), null))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        mvc.perform(post("/api/auth/refresh").cookie(chamari.refresh())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/settings/staff/" + id + "/reset-password").with(who.get(OWNER).bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOGIN_OFF"));
    }

    @Test
    void ownerCannotLockThemselvesOut() throws Exception {
        long me = jdbc.queryForObject("select id from app_user where email = ?", Long.class, OWNER);
        mvc.perform(json(put("/api/settings/staff/" + me), staff("Ruwan Perera", OWNER, "OWNER", null, false), OWNER))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OWN_LOGIN"));
        mvc.perform(json(put("/api/settings/staff/" + me), staff("Ruwan Perera", OWNER, "ACCOUNTANT", null, true), OWNER))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/settings/staff").with(who.get(OWNER).bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("OWNER")).andExpect(jsonPath("$[0].me").value(true));
    }

    @Test
    void resetPasswordAndUnlock() throws Exception {
        long nimal = jdbc.queryForObject("select id from app_user where email = ?", Long.class, SALES);
        jdbc.update("update app_user set failed_logins = 5, locked_until = now() + interval '10 minutes' where id = ?", nimal);
        mvc.perform(get("/api/settings/staff").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$[?(@.email == '" + SALES + "')].locked").value(true));
        mvc.perform(post("/api/settings/staff/" + nimal + "/unlock").with(who.get(OWNER).bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));
        mvc.perform(post("/api/settings/staff/" + nimal + "/reset-password").with(who.get(OWNER).bearer()))
                .andExpect(status().isNoContent());
        verify(mail).send(eq(SALES), eq("Reset your password"), anyString());
        assertThat(lastLog()).isEqualTo("Password reset sent");
    }

    // ---------------------------------------------------------------- exchange rates

    @Test
    void ratesOfTheDayWithHistory() throws Exception {
        JsonNode r = JSON.readTree(mvc.perform(json(post("/api/settings/rates"), Map.of("usd", 301.5, "eur", 326), OWNER))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(r.path("usd").decimalValue()).isEqualByComparingTo("301.5");
        assertThat(r.path("updatedBy").asString()).isEqualTo("Ruwan Perera");
        assertThat(r.path("history").get(0).path("date").asString()).isEqualTo(LocalDate.now(ZoneId.of("Asia/Colombo")).toString());
        assertThat(r.path("history").get(0).path("eur").decimalValue()).isEqualByComparingTo("326");
        assertThat(r.path("history").size()).isGreaterThan(5);
        // today's rate is used by everyone at once
        mvc.perform(get("/api/auth/me").with(who.get(SALES).bearer())).andExpect(jsonPath("$.rates.usd").value(301.5));
        mvc.perform(get("/api/settings/summary").with(who.get(OWNER).bearer())).andExpect(jsonPath("$.ratesUpdatedToday").value(true));
        mvc.perform(json(post("/api/settings/rates"), Map.of("usd", 0, "eur", 326), OWNER)).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/settings/rates"), Map.of("usd", 300, "eur", 325), OWNER)).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- commission rules

    @Test
    void commissionLevelsMustGoUp() throws Exception {
        // BRD page 9: guide 10 % of LKR 300,000 = 30,000; staff level 3 = 3,000
        assertThat(commission.staffAmount(new BigDecimal("300000"))).isEqualByComparingTo("3000");
        assertThat(commission.staffAmount(new BigDecimal("50000"))).isEqualByComparingTo("500");
        assertThat(commission.staffAmount(new BigDecimal("900000"))).isEqualByComparingTo("5000");
        assertThat(Commission.guideAmount(new BigDecimal("300000"), BigDecimal.TEN)).isEqualByComparingTo("30000");

        var levels = List.of(Map.of("upTo", 100000, "amount", 1000), Map.of("upTo", 50000, "amount", 2000), Map.of("amount", 4000));
        mvc.perform(json(put("/api/settings/commission"), Map.of("defaultGuidePct", 12, "levels", levels), OWNER))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LEVELS_ORDER"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("levels[1].upTo"));
        var good = List.of(Map.of("upTo", 100000, "amount", 1000), Map.<String, Object>of("upTo", 999, "amount", 4000));
        mvc.perform(json(put("/api/settings/commission"), Map.of("defaultGuidePct", 12.5, "levels", good), OWNER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.defaultGuidePct").value(12.5))
                .andExpect(jsonPath("$.levels.length()").value(2))
                .andExpect(jsonPath("$.levels[1].upTo").doesNotExist()) // the last level is "above …"
                .andExpect(jsonPath("$.exampleLevels.length()").value(4));
        assertThat(commission.staffAmount(new BigDecimal("300000"))).isEqualByComparingTo("4000");
        mvc.perform(json(put("/api/settings/commission"), Map.of("defaultGuidePct", 60, "levels", good), OWNER))
                .andExpect(status().isBadRequest());
        commission.save(BigDecimal.TEN, Commission.BRD_EXAMPLE);
    }

    // ---------------------------------------------------------------- branches

    @Test
    void openingASecondBranchIsASettingChange() throws Exception {
        JsonNode b = JSON.readTree(mvc.perform(json(post("/api/settings/branches"), Map.of("name", "Kandy Lake Road", "shortName", "Kandy",
                        "address", "12 Lake Road, Kandy", "phone", "+94 81 222 0000", "openedOn", "2027-06-01"), OWNER))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String code = b.path("code").asString();
        assertThat(code).startsWith("kandy");
        assertThat(b.path("status").asString()).isEqualTo("OPENING");
        // a salesperson can be linked to it before it opens
        String email = "kandy." + System.nanoTime() + "@test.lk";
        mvc.perform(json(post("/api/settings/staff"), staff("Kandy Seller", email, "SALESPERSON", code, true), OWNER))
                .andExpect(status().isCreated());
        mvc.perform(json(post("/api/settings/branches/" + code + "/status"), Map.of("status", "ACTIVE"), OWNER))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.openedOn").value(LocalDate.now(ZoneId.of("Asia/Colombo")).toString()))
                .andExpect(jsonPath("$.staff[0].name").value("Kandy Seller"));
        // cannot close it while it has active staff
        mvc.perform(json(post("/api/settings/branches/" + code + "/status"), Map.of("status", "INACTIVE"), OWNER))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BRANCH_HAS_STAFF"));
        mvc.perform(json(put("/api/settings/branches/" + code), Map.of("name", "Kandy", "address", "14 Lake Road, Kandy",
                "openedOn", "2027-06-01"), OWNER)).andExpect(status().isOk()).andExpect(jsonPath("$.address").value("14 Lake Road, Kandy"))
                .andExpect(jsonPath("$.code").value(code));
        mvc.perform(get("/api/settings/branches").with(who.get(OWNER).bearer())).andExpect(jsonPath("$[0].code").value("ella"));
    }

    @Test
    void theLastOpenBranchStaysOpen() throws Exception {
        jdbc.update("update app_user set active = false where branch_id = (select id from branch where code = 'ella') and email like '%@test.lk'");
        mvc.perform(json(post("/api/settings/branches/ella/status"), Map.of("status", "OPENING"), OWNER))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_BRANCH"));
    }

    // ---------------------------------------------------------------- drop-down lists

    @Test
    void dropDownLists() throws Exception {
        JsonNode all = read(get("/api/settings/lists"));
        assertThat(all.size()).isEqualTo(7);
        JsonNode gems = find(all, "GEM_TYPE");
        assertThat(gems.path("label").asString()).isEqualTo("Gem types");
        assertThat(gems.path("items").size()).isEqualTo(13);

        // add, no duplicates (whatever the capitals)
        JsonNode added = JSON.readTree(mvc.perform(json(post("/api/settings/lists/GEM_TYPE"), Map.of("value", "  Zircon "), OWNER))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        JsonNode items = find(added, "GEM_TYPE").path("items");
        JsonNode zircon = items.get(items.size() - 1);
        assertThat(zircon.path("value").asString()).isEqualTo("Zircon");
        assertThat(zircon.path("usedBy").asLong()).isZero();
        mvc.perform(json(post("/api/settings/lists/GEM_TYPE"), Map.of("value", "zircon"), OWNER))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LIST_DUPLICATE"));
        assertThat(lists.check(ListName.GEM_TYPE, "ZIRCON", "gem")).isEqualTo("Zircon");

        long id = zircon.path("id").asLong();
        mvc.perform(json(put("/api/settings/lists/values/" + id), Map.of("value", "White Zircon"), OWNER)).andExpect(status().isOk());
        assertThat(lastLog()).isEqualTo("List value renamed");
        mvc.perform(post("/api/settings/lists/values/" + id + "/up").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        assertThat(lists.values(ListName.GEM_TYPE)).containsSubsequence("White Zircon", "Other");
        assertThat(lists.values(ListName.GEM_TYPE).indexOf("White Zircon")).isEqualTo(12);
        mvc.perform(delete("/api/settings/lists/values/" + id).with(who.get(OWNER).bearer())).andExpect(status().isOk());
        assertThat(lists.values(ListName.GEM_TYPE)).hasSize(13).doesNotContain("White Zircon").endsWith("Other");
        mvc.perform(json(post("/api/settings/lists/COLOUR"), Map.of("value", ""), OWNER)).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- helpers

    private JsonNode read(MockHttpServletRequestBuilder req) throws Exception {
        return JSON.readTree(mvc.perform(req.with(who.get(OWNER).bearer())).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, Object body, String as) throws Exception {
        req.contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
        return as == null ? req : req.with(who.get(as).bearer());
    }

    private static JsonNode find(JsonNode lists, String name) {
        for (JsonNode l : lists) {
            if (l.path("name").asString().equals(name)) {
                return l;
            }
        }
        throw new AssertionError("no list " + name);
    }

    private static Map<String, Object> staff(String name, String email, String role, String branch, boolean active) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("email", email);
        m.put("phone", "+94 77 000 0000");
        m.put("role", role);
        m.put("branch", branch);
        m.put("active", active);
        return m;
    }

    private static Map<String, Object> shopForm(JsonNode s) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : List.of("name", "tagline", "address", "phone", "email", "website", "ngjaLicenceNo", "ngjaLicenceExpiry")) {
            m.put(k, s.path(k).isNull() ? null : s.path(k).asString());
        }
        m.put("ngjaRemindDays", s.path("ngjaRemindDays").asInt());
        m.put("printLicenceOnInvoice", s.path("printLicenceOnInvoice").asBoolean());
        m.put("largeExpenseLimit", s.path("largeExpenseLimit").decimalValue());
        m.put("cardFeePct", s.path("cardFeePct").decimalValue());
        m.put("returnPolicyDays", s.path("returnPolicyDays").asInt());
        return m;
    }

    private String upload(String kind, byte[] bytes) throws Exception {
        ResultActions r = mvc.perform(multipart("/api/files").file(new MockMultipartFile("file", "logo.png", "image/png", bytes))
                .param("kind", kind).with(who.get(OWNER).bearer())).andExpect(status().isCreated());
        return JSON.readTree(r.andReturn().getResponse().getContentAsString()).path("id").asString();
    }

    private static byte[] png() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    private String lastLog() {
        return jdbc.queryForObject("select action from activity_log order by id desc limit 1", String.class);
    }
}
