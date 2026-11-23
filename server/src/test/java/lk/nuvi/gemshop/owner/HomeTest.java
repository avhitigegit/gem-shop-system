package lk.nuvi.gemshop.owner;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static lk.nuvi.gemshop.SignIn.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P4-01 Home figures per role and the owner phone view. Only reads. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HomeTest {

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
    void homeFiguresPerRole() throws Exception {
        long stock = jdbc.queryForObject("select count(*) from gem where status in "
                + "('IN_STOCK','NOT_READY','RESERVED','ON_HOLD','RETURNED','IN_TRANSIT')", Long.class);
        BigDecimal todayTotal = jdbc.queryForObject("select coalesce(sum(total), 0) from sale where status = 'COMPLETED' "
                + "and (sold_at at time zone 'Asia/Colombo')::date = (now() at time zone 'Asia/Colombo')::date", BigDecimal.class);

        JsonNode owner = read(get("/api/home"), OWNER);
        assertThat(owner.path("salesTotal").decimalValue()).isEqualByComparingTo(todayTotal);
        assertThat(owner.path("gemsInStock").asLong()).isEqualTo(stock);
        assertThat(owner.path("cash").path("lkr").isNumber()).isTrue();
        assertThat(owner.path("approvalsWaiting").asLong()).isPositive();
        assertThat(owner.path("soldToday").size()).isGreaterThanOrEqualTo(owner.path("bills").asInt());

        JsonNode sales = read(get("/api/home"), SALES);
        assertThat(sales.path("salesTotal").isNumber()).isTrue();
        assertThat(sales.path("dayStarted").isBoolean()).isTrue();      // salesperson: always their branch
        assertThat(sales.path("licenceDays").isNull()).isTrue();

        JsonNode staff = read(get("/api/home"), STAFF);
        assertThat(staff.path("salesTotal").isNull()).isTrue();          // no money on the staff home
        assertThat(staff.path("cash").isNull()).isTrue();
        assertThat(staff.path("soldToday").size()).isZero();
        assertThat(staff.path("gemsInStock").asLong()).isPositive();
    }

    @Test
    void ownerPhoneView() throws Exception {
        JsonNode all = read(get("/api/home/phone"), OWNER);
        assertThat(all.path("branch").asString()).isEqualTo("all");
        assertThat(all.path("stockCount").asLong()).isPositive();
        assertThat(all.path("stockValue").decimalValue()).isGreaterThan(all.path("stockCost").decimalValue());
        assertThat(all.path("approvals").size()).isLessThanOrEqualTo(3);
        assertThat(all.path("alerts").size()).isBetween(1, 5);

        JsonNode ella = read(get("/api/home/phone").param("branch", "ella"), OWNER);
        assertThat(ella.path("branch").asString()).isEqualTo("ella");
        assertThat(ella.path("stockCount").asLong()).isLessThanOrEqualTo(all.path("stockCount").asLong());

        mvc.perform(get("/api/home/phone").with(who.get(SALES).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/home/phone").param("branch", "nope").with(who.get(OWNER).bearer())).andExpect(status().isNotFound());
    }

    private JsonNode read(MockHttpServletRequestBuilder req, String as) throws Exception {
        return JSON.readTree(mvc.perform(req.with(who.get(as).bearer())).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }
}
