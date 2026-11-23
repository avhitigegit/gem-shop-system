package lk.nuvi.gemshop.reports;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static lk.nuvi.gemshop.SignIn.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
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

/** P5-02 Reports on the sample data: figures match the database, roles, branch at the top, PDF / Excel downloads. Only reads. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ZoneId SHOP = ZoneId.of("Asia/Colombo");

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
    void salesReportMatchesTheInvoices() throws Exception {
        LocalDate from = LocalDate.now(SHOP).minusDays(90);
        String sql = "select coalesce(sum(i.agreed), 0) from sale_item i join sale s on s.id = i.sale_id "
                + "where s.status = 'COMPLETED' and not i.released and (s.sold_at at time zone 'Asia/Colombo')::date >= ?";
        BigDecimal expected = jdbc.queryForObject(sql, BigDecimal.class, from);
        long bills = jdbc.queryForObject("select count(distinct s.id) from sale s join sale_item i on i.sale_id = s.id "
                + "where s.status = 'COMPLETED' and not i.released and (s.sold_at at time zone 'Asia/Colombo')::date >= ?", Long.class, from);

        JsonNode r = read(get("/api/reports/sales").param("from", from.toString()), OWNER);
        assertThat(r.path("total").decimalValue()).isEqualByComparingTo(expected);
        assertThat(r.path("bills").asLong()).isEqualTo(bills);
        assertThat(sumOf(r.path("rows"), "total")).isEqualByComparingTo(expected);
        assertThat(r.path("gemTypes").size()).isPositive();
        assertThat(r.path("payTypes").size()).isEqualTo(6);

        // every grouping adds up to the same total
        for (String by : new String[] {"MONTH", "BRANCH", "GEM", "STAFF", "GUIDE", "PAY", "COUNTRY"}) {
            JsonNode g = read(get("/api/reports/sales").param("from", from.toString()).param("by", by), ACCOUNTANT);
            assertThat(sumOf(g.path("rows"), "total")).as(by).isEqualByComparingTo(expected);
        }
        // walk-in + guided = all
        BigDecimal walkIn = read(get("/api/reports/sales").param("from", from.toString()).param("guide", "walkin"), OWNER)
                .path("total").decimalValue();
        JsonNode byGuide = read(get("/api/reports/sales").param("from", from.toString()).param("by", "GUIDE"), OWNER);
        for (JsonNode row : byGuide.path("rows")) {
            if (row.path("label").asString().equals("Walk-in")) {
                assertThat(row.path("total").decimalValue()).isEqualByComparingTo(walkIn);
            }
        }
        // a branch at the top gives at most the whole shop
        JsonNode ella = read(get("/api/reports/sales").param("from", from.toString()).header("X-Branch", "ella"), OWNER);
        assertThat(ella.path("total").decimalValue()).isLessThanOrEqualTo(expected);

        mvc.perform(get("/api/reports/sales").param("pay", "Bitcoin").with(who.get(OWNER).bearer())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/reports/sales").param("from", "2026-02-01").param("to", "2026-01-01").with(who.get(OWNER).bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profitIsPriceMinusCostMinusCommission() throws Exception {
        LocalDate from = LocalDate.now(SHOP).minusDays(90);
        JsonNode r = read(get("/api/reports/profit").param("from", from.toString()), ACCOUNTANT);
        assertThat(r.path("rows").size()).isPositive();
        for (JsonNode row : r.path("rows")) {
            BigDecimal p = row.path("price").decimalValue().subtract(row.path("cost").decimalValue())
                    .subtract(row.path("commission").decimalValue());
            assertThat(row.path("profit").decimalValue()).isEqualByComparingTo(p);
        }
        assertThat(r.path("sales").decimalValue()).isEqualByComparingTo(sumOf(r.path("rows"), "price"));
        assertThat(r.path("profit").decimalValue()).isEqualByComparingTo(sumOf(r.path("rows"), "profit"));
        JsonNode sales = read(get("/api/reports/sales").param("from", from.toString()), OWNER);
        assertThat(r.path("sales").decimalValue()).isEqualByComparingTo(sales.path("total").decimalValue());
    }

    @Test
    void dailyCashStockCommissionsPurchases() throws Exception {
        LocalDate from = LocalDate.now(SHOP).minusDays(365);
        long closings = jdbc.queryForObject("select count(*) from day_closing where day >= ?", Long.class, from);
        JsonNode cash = read(get("/api/reports/daily-cash").param("from", from.toString()), OWNER);
        assertThat(cash.path("days").asLong()).isEqualTo(closings);
        JsonNode diff = read(get("/api/reports/daily-cash").param("from", from.toString()).param("onlyDifferences", "true"), ACCOUNTANT);
        assertThat(diff.path("days").asInt()).isEqualTo(cash.path("daysWithDifference").asInt());
        diff.path("rows").forEach(x -> assertThat(x.path("balanced").asBoolean()).isFalse());

        long stock = jdbc.queryForObject("select count(*) from gem where status in "
                + "('IN_STOCK','NOT_READY','RESERVED','ON_HOLD','RETURNED','IN_TRANSIT')", Long.class);
        JsonNode st = read(get("/api/reports/stock"), OWNER);
        assertThat(st.path("items").asLong()).isEqualTo(stock);
        assertThat(st.path("price").decimalValue()).isGreaterThan(st.path("cost").decimalValue());
        JsonNode slow = read(get("/api/reports/stock").param("slow", "true").param("slowDays", "30"), OWNER);
        assertThat(slow.path("items").asLong()).isLessThanOrEqualTo(stock);
        slow.path("rows").forEach(x -> assertThat(x.path("days").asLong()).isGreaterThan(30));

        JsonNode com = read(get("/api/reports/commissions").param("from", from.toString()), ACCOUNTANT);
        assertThat(com.path("rows").size()).isPositive();
        assertThat(com.path("earned").decimalValue().add(com.path("reversed").decimalValue()))
                .isEqualByComparingTo(com.path("paid").decimalValue().add(com.path("unpaid").decimalValue()));
        JsonNode guides = read(get("/api/reports/commissions").param("from", from.toString()).param("payee", "GUIDE"), OWNER);
        guides.path("rows").forEach(x -> assertThat(x.path("payee").asString()).isEqualTo("GUIDE"));
        mvc.perform(get("/api/reports/commissions").param("payee", "x").with(who.get(OWNER).bearer())).andExpect(status().isBadRequest());

        BigDecimal bought = jdbc.queryForObject("select coalesce(sum(total), 0) from purchase where purchase_date >= ?", BigDecimal.class, from);
        JsonNode pur = read(get("/api/reports/purchases").param("from", from.toString()), OWNER);
        assertThat(pur.path("cost").decimalValue()).isEqualByComparingTo(bought);
        assertThat(pur.path("sold").asInt()).isLessThanOrEqualTo(pur.path("products").asInt());
    }

    @Test
    void onlyOwnerAndAccountant_stockOwnerOnly() throws Exception {
        for (String path : new String[] {"/api/reports/summary", "/api/reports/sales", "/api/reports/profit", "/api/reports/daily-cash",
                "/api/reports/commissions", "/api/reports/purchases", "/api/reports/stock"}) {
            mvc.perform(get(path).with(who.get(SALES).bearer())).andExpect(status().isForbidden());
            mvc.perform(get(path).with(who.get(STAFF).bearer())).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/reports/stock").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/stock/export").param("format", "pdf").with(who.get(ACCOUNTANT).bearer()))
                .andExpect(status().isForbidden());
        assertThat(read(get("/api/reports/summary"), ACCOUNTANT).path("salesToday").isNumber()).isTrue();
    }

    @Test
    void everyReportDownloadsAsPdfAndExcel() throws Exception {
        for (String path : new String[] {"sales", "profit", "daily-cash", "stock", "commissions", "purchases", "expenses"}) {
            byte[] pdf = download("/api/reports/" + path + "/export", "pdf");
            assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).as(path).isEqualTo("%PDF-");
            byte[] xlsx = download("/api/reports/" + path + "/export", "xlsx");
            assertThat(new String(xlsx, 0, 2, StandardCharsets.ISO_8859_1)).as(path).isEqualTo("PK");
        }
        byte[] slow = download("/api/reports/stock/export?slow=true&slowDays=10", "pdf");
        assertThat(slow.length).isPositive();
        mvc.perform(get("/api/reports/sales/export").param("format", "doc").with(who.get(OWNER).bearer())).andExpect(status().isBadRequest());
    }

    private byte[] download(String path, String format) throws Exception {
        var res = mvc.perform(get(path).param("format", format).with(who.get(OWNER).bearer())).andExpect(status().isOk()).andReturn()
                .getResponse();
        assertThat(res.getHeader("Content-Disposition")).contains("attachment");
        return res.getContentAsByteArray();
    }

    private static BigDecimal sumOf(JsonNode rows, String field) {
        BigDecimal s = BigDecimal.ZERO;
        for (JsonNode r : rows) {
            s = s.add(r.path(field).decimalValue());
        }
        return s;
    }

    private JsonNode read(MockHttpServletRequestBuilder req, String as) throws Exception {
        return JSON.readTree(mvc.perform(req.with(who.get(as).bearer())).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }
}
