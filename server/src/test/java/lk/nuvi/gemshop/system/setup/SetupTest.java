package lk.nuvi.gemshop.system.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import lk.nuvi.gemshop.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** P1-02: owner first-time setup on an empty system (no sample data). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.sample-data.enabled=false")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SetupTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    private Map<String, Object> request(String password, boolean terms) {
        Map<String, Object> r = new HashMap<>();
        r.put("owner", Map.of("name", "Ruwan Perera", "email", "Ruwan@Shop.lk", "phone", "+94 77 123 4567", "password", password));
        r.put("shop", Map.of("name", "Ceylon Gem House", "address", "Ella, Sri Lanka", "ngjaLicenceNo", "NGJA/DL/2024/0457",
                "ngjaLicenceExpiry", "2027-06-30"));
        r.put("branch", Map.of("name", "Ella"));
        r.put("rates", Map.of("usd", 300, "eur", 325));
        r.put("terms", terms);
        return r;
    }

    private org.springframework.test.web.servlet.ResultActions run(Map<String, Object> r) throws Exception {
        return mvc.perform(post("/api/setup").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(r)));
    }

    @Test
    @Order(1)
    void emptySystemNeedsSetup() throws Exception {
        mvc.perform(get("/api/setup/status")).andExpect(jsonPath("$.needed").value(true));
    }

    @Test
    @Order(2)
    void weakPasswordAndNoAgreementAreRefused() throws Exception {
        run(request("short", true)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("owner.password"));
        run(request("Strong2026", false)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("terms"));
        assertThat(jdbc.queryForObject("select count(*) from app_user", Integer.class)).isZero();
    }

    @Test
    @Order(3)
    void setupCreatesOwnerShopBranchAndRatesAndSignsIn() throws Exception {
        run(request("Strong2026", true)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.me.user.role").value("OWNER"))
                .andExpect(jsonPath("$.me.user.email").value("ruwan@shop.lk"))
                .andExpect(jsonPath("$.me.shop.name").value("Ceylon Gem House"))
                .andExpect(jsonPath("$.me.branches[0].code").value("ella"))
                .andExpect(jsonPath("$.me.rates.usd").value(300.0));
        assertThat(jdbc.queryForObject("select ngja_licence_no from shop_settings", String.class)).isEqualTo("NGJA/DL/2024/0457");
        assertThat(jdbc.queryForObject("select count(*) from exchange_rate", Integer.class)).isEqualTo(2);
        mvc.perform(get("/api/setup/status")).andExpect(jsonPath("$.needed").value(false));
        // the owner can sign in with the new password
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ruwan@shop.lk\",\"password\":\"Strong2026\",\"remember\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    void setupWorksOnlyOnce() throws Exception {
        run(request("Strong2026", true)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SETUP_DONE"));
    }

    @Test
    void branchCodes() {
        assertThat(SetupService.code("Ella")).isEqualTo("ella");
        assertThat(SetupService.code("Branch 2 – Kandy")).isEqualTo("branch-2-kandy");
        assertThat(SetupService.code("!!")).isEqualTo("main");
    }
}
