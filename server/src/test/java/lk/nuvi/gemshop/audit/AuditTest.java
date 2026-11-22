package lk.nuvi.gemshop.audit;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static lk.nuvi.gemshop.SignIn.STAFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import lk.nuvi.gemshop.audit.alert.AlertType;
import lk.nuvi.gemshop.audit.alert.Alerts;
import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalHandler;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.audit.approval.Approvals;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** P1-04: activity log (read only), owner approvals and alerts. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuditTest.TestApi.class, AuditTest.ExpenseHandler.class})
class AuditTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ExpenseHandler handler;

    private final Map<String, Signed> who = new LinkedHashMap<>();

    @BeforeEach
    void signIn() throws Exception {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ?",
                encoder.encode(SignIn.PASSWORD));
        for (String e : new String[] {OWNER, SALES, STAFF, ACCOUNTANT}) {
            who.put(e, SignIn.login(mvc, e));
        }
        handler.calls.clear();
        handler.fail = false;
    }

    // ---------------------------------------------------------------- sample data

    @Test
    void sampleDataHasThePrototypeApprovalsAlertsAndLog() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from approval where number <= 'AP-0014'", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select status from approval where number = 'AP-0001'", String.class)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("select note from approval where number = 'AP-0001'", String.class)).isEqualTo("OK, refund by card");
        assertThat(jdbc.queryForObject("select ref from approval where number = 'AP-0010'", String.class)).isEqualTo("HS-0001");
        assertThat(jdbc.queryForObject("select count(*) from alert", Integer.class)).isGreaterThanOrEqualTo(49);
        assertThat(jdbc.queryForObject("select count(*) from activity_log", Integer.class)).isGreaterThanOrEqualTo(71);
        assertThat(jdbc.queryForObject("select link from alert where text = 'Cancel invoice INV-000140'", String.class))
                .isEqualTo("/approvals#AP-0012");
        assertThat(jdbc.queryForObject("select link from alert where text like 'Sale INV-000138 %'", String.class))
                .isEqualTo("/sell/invoices/INV-000138");
        // the bell: 5 waiting in the prototype
        mvc.perform(get("/api/approvals/waiting").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.count").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.latest.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(5)));
    }

    // ---------------------------------------------------------------- activity log

    @Test
    void logCannotBeChangedOrDeleted() throws Exception {
        assertThatThrownBy(() -> jdbc.update("update activity_log set action = 'x'")).hasMessageContaining("cannot be edited or deleted");
        assertThatThrownBy(() -> jdbc.update("delete from activity_log")).hasMessageContaining("cannot be edited or deleted");
        assertThatThrownBy(() -> jdbc.execute("truncate activity_log")).hasMessageContaining("cannot be edited or deleted");
        // and there is no API for it
        mvc.perform(delete("/api/activity-log").with(who.get(OWNER).bearer())).andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/activity-log").with(who.get(OWNER).bearer())).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void onlyTheOwnerSeesTheLog() throws Exception {
        mvc.perform(get("/api/activity-log").with(who.get(OWNER).bearer())).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").exists()).andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThan(0)));
        for (String e : new String[] {SALES, STAFF, ACCOUNTANT}) {
            mvc.perform(get("/api/activity-log").with(who.get(e).bearer())).andExpect(status().isForbidden());
        }
    }

    @Test
    void logSearchByTextUserAndBranch() throws Exception {
        long nimal = jdbc.queryForObject("select id from app_user where email = ?", Long.class, SALES);
        mvc.perform(get("/api/activity-log").param("q", "day CLOSED").param("size", "300").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.items[*].action", everyItem(is("Day closed"))))
                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(10)));
        mvc.perform(get("/api/activity-log").param("user", String.valueOf(nimal)).param("size", "300").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.items[*].userName", everyItem(is("Nimal Silva"))));
        // Branch 2 picked: only whole-shop lines (exchange rates) – nothing happened in Branch 2 yet
        mvc.perform(get("/api/activity-log").header(CurrentUser.BRANCH_HEADER, "b2").param("size", "300").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.items[*].branch", everyItem(is("all"))))
                .andExpect(jsonPath("$.items[*].action", hasItem("Exchange rates updated")));
        mvc.perform(get("/api/activity-log").param("from", "2026-12-02").param("to", "2026-12-01").with(who.get(OWNER).bearer()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_DATES"));
    }

    @Test
    void automaticEntriesShowAsSystem() throws Exception {
        mvc.perform(post("/api/test/audit/system-line").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/activity-log").param("user", "system").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.items[0].userName").value("System (automatic)"))
                .andExpect(jsonPath("$.items[0].system").value(true))
                .andExpect(jsonPath("$.items[0].action").value("Test job"));
    }

    @Test
    void passwordChangeIsLogged() throws Exception {
        mvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + SignIn.PASSWORD + "\",\"newPassword\":\"" + SignIn.PASSWORD + "\"}")
                        .with(who.get(STAFF).bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/activity-log").param("q", "password changed").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.items[0].userName").value("Kasun Bandara"))
                .andExpect(jsonPath("$.items[0].branch").value("ella"));
    }

    // ---------------------------------------------------------------- approvals

    @Test
    void requestRaisesAnAlertAndALogLine() throws Exception {
        String no = request(ACCOUNTANT, "ella", "Large expense: Repairs LKR 60,000.00");
        assertThat(no).matches("AP-\\d{4}");
        assertThat(Integer.parseInt(no.substring(3))).isGreaterThan(14); // numbers continue after the prototype
        assertThat(jdbc.queryForObject("select type from alert where link = ?", String.class, "/approvals#" + no)).isEqualTo("EXPENSE");
        mvc.perform(get("/api/activity-log").param("q", "Repairs LKR 60,000.00").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.items[0].action").value("Approval requested"))
                .andExpect(jsonPath("$.items[0].userName").value("Dilani Fernando"));
        mvc.perform(get("/api/approvals/" + no + "/status").with(who.get(ACCOUNTANT).bearer()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void ownerApprovesWithANoteAndTheActionRuns() throws Exception {
        String no = request(ACCOUNTANT, "ella", "Large expense: test approve");
        for (String e : new String[] {SALES, STAFF, ACCOUNTANT}) {
            mvc.perform(post("/api/approvals/" + no + "/approve").with(who.get(e).bearer())).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/approvals/" + no + "/approve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"OK, pay by bank\"}").with(who.get(OWNER).bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("Ruwan Perera")).andExpect(jsonPath("$.note").value("OK, pay by bank"));
        assertThat(handler.calls).containsExactly(no + "=true");
        // the person who asked sees the answer on the waiting screen
        mvc.perform(get("/api/approvals/" + no + "/status").with(who.get(ACCOUNTANT).bearer()))
                .andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.note").value("OK, pay by bank"));
        mvc.perform(get("/api/activity-log").param("q", "test approve").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.items[0].action").value("Approved"))
                .andExpect(jsonPath("$.items[0].detail").value("Large expense: test approve — OK, pay by bank"));
        // only once
        mvc.perform(post("/api/approvals/" + no + "/reject").with(who.get(OWNER).bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_DECIDED"));
        assertThat(handler.calls).hasSize(1);
    }

    @Test
    void ownerRejects() throws Exception {
        String no = request(ACCOUNTANT, "ella", "Large expense: test reject");
        mvc.perform(post("/api/approvals/" + no + "/reject").with(who.get(OWNER).bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(handler.calls).containsExactly(no + "=false");
        mvc.perform(get("/api/approvals").param("tab", "done").param("type", "EXPENSE").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.items[*].number", hasItem(no)));
    }

    @Test
    void whenTheActionFailsNothingIsDecided() throws Exception {
        String no = request(ACCOUNTANT, "ella", "Large expense: test fail");
        handler.fail = true;
        mvc.perform(post("/api/approvals/" + no + "/approve").with(who.get(OWNER).bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXPENSE_GONE"));
        mvc.perform(get("/api/approvals/" + no + "/status").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(jdbc.queryForObject("select count(*) from activity_log where action = 'Approved' and detail like '%test fail%'",
                Integer.class)).isZero();
    }

    @Test
    void everybodySeesTheirBranchOnlyTheOwnerDecides() throws Exception {
        String b2 = request(OWNER, "b2", "Large expense: Branch 2 fit-out");
        String ella = request(STAFF, "ella", "Large expense: Ella stock check");
        // salesperson and staff: their branch only
        for (String e : new String[] {SALES, STAFF}) {
            mvc.perform(get("/api/approvals/" + b2 + "/status").with(who.get(e).bearer())).andExpect(status().isNotFound());
            mvc.perform(get("/api/approvals/" + ella + "/status").with(who.get(e).bearer())).andExpect(status().isOk());
            mvc.perform(get("/api/approvals").param("size", "200").with(who.get(e).bearer()))
                    .andExpect(jsonPath("$.items[*].branch", everyItem(isOneOf("ella", "all"))));
        }
        // owner and accountant: all branches, or the one picked in the top bar
        mvc.perform(get("/api/approvals").param("size", "200").with(who.get(ACCOUNTANT).bearer()))
                .andExpect(jsonPath("$.items[*].number", hasItem(b2)));
        mvc.perform(get("/api/approvals").header(CurrentUser.BRANCH_HEADER, "b2").param("size", "200").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.items[*].branch", everyItem(isOneOf("b2", "all"))))
                .andExpect(jsonPath("$.pending").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        // a salesperson cannot send a request for another branch
        mvc.perform(post("/api/test/audit/request").param("branch", "b2").param("title", "x").with(who.get(SALES).bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void bellCountsWhatIsWaiting() throws Exception {
        long before = waiting(OWNER);
        String no = request(STAFF, "ella", "Large expense: bell test");
        assertThat(waiting(OWNER)).isEqualTo(before + 1);
        mvc.perform(get("/api/approvals/waiting").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.latest[0].number").value(no))
                .andExpect(jsonPath("$.latest[0].typeLabel").value("Large expense"))
                .andExpect(jsonPath("$.latest[0].requestedBy.name").value("Kasun Bandara"));
        mvc.perform(post("/api/approvals/" + no + "/approve").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        assertThat(waiting(OWNER)).isEqualTo(before);
    }

    // ---------------------------------------------------------------- alerts

    @Test
    void onlyTheOwnerSeesAlerts() throws Exception {
        for (String e : new String[] {SALES, STAFF, ACCOUNTANT}) {
            mvc.perform(get("/api/alerts").with(who.get(e).bearer())).andExpect(status().isForbidden());
            mvc.perform(get("/api/alerts/unread-count").with(who.get(e).bearer())).andExpect(status().isForbidden());
        }
    }

    @Test
    void alertsByGroupAndUnread() throws Exception {
        mvc.perform(get("/api/alerts").param("group", "sale").param("size", "200").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.items[*].type", everyItem(is("SALE"))))
                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(40)));
        mvc.perform(get("/api/alerts").param("group", "cancel").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.items[*].type", everyItem(isOneOf("CANCEL", "CANCEL_INVOICE"))));
        mvc.perform(get("/api/alerts").param("unread", "true").param("size", "200").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.items[*].read", everyItem(is(false))));
        mvc.perform(get("/api/alerts").param("group", "nope").with(who.get(OWNER).bearer())).andExpect(status().isBadRequest());
        // the licence reminder belongs to the whole shop: it shows in every branch
        mvc.perform(get("/api/alerts").header(CurrentUser.BRANCH_HEADER, "b2").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.items[*].type", hasItem("LICENCE")));
    }

    @Test
    void markReadAndMarkAllRead() throws Exception {
        mvc.perform(post("/api/test/audit/alert").param("branch", "b2").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        long b2Unread = unread("b2");
        assertThat(b2Unread).isGreaterThanOrEqualTo(1);
        JsonNode page = JSON.readTree(mvc.perform(get("/api/alerts").header(CurrentUser.BRANCH_HEADER, "b2").param("unread", "true")
                .with(who.get(OWNER).bearer())).andReturn().getResponse().getContentAsString());
        long id = page.path("items").get(0).path("id").asLong();
        mvc.perform(post("/api/alerts/" + id + "/read").with(who.get(OWNER).bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
        assertThat(unread("b2")).isEqualTo(b2Unread - 1);
        assertThat(jdbc.queryForObject("select u.name from alert a join app_user u on u.id = a.read_by where a.id = ?", String.class, id))
                .isEqualTo("Ruwan Perera");

        // "Mark all read" with Branch 2 picked leaves Ella's alerts unread
        mvc.perform(post("/api/test/audit/alert").param("branch", "ella").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        long ellaUnread = unread("ella");
        mvc.perform(post("/api/alerts/read-all").header(CurrentUser.BRANCH_HEADER, "b2").with(who.get(OWNER).bearer()))
                .andExpect(status().isOk());
        assertThat(unread("b2")).isZero();
        long whole = jdbc.queryForObject("select count(*) from alert where branch_id is null and read_at is null", Long.class);
        assertThat(whole).isZero(); // whole-shop alerts were part of Branch 2's list
        assertThat(unread("ella")).isGreaterThan(0).isLessThanOrEqualTo(ellaUnread);
        mvc.perform(post("/api/alerts/read-all").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        assertThat(unread("all")).isZero();
    }

    // ---------------------------------------------------------------- helpers

    private String request(String email, String branch, String title) throws Exception {
        String body = mvc.perform(post("/api/test/audit/request").param("branch", branch).param("title", title)
                        .with(who.get(email).bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return body;
    }

    private long waiting(String email) throws Exception {
        return JSON.readTree(mvc.perform(get("/api/approvals/waiting").with(who.get(email).bearer())).andReturn().getResponse()
                .getContentAsString()).path("count").asLong();
    }

    private long unread(String branch) throws Exception {
        return JSON.readTree(mvc.perform(get("/api/alerts/unread-count").header(CurrentUser.BRANCH_HEADER, branch)
                .with(who.get(OWNER).bearer())).andReturn().getResponse().getContentAsString()).path("unread").asLong();
    }

    /** Stands in for the expenses module (P5-01): records what the owner decided, or fails on purpose. */
    @Component
    static class ExpenseHandler implements ApprovalHandler {
        final List<String> calls = new ArrayList<>();
        volatile boolean fail;

        @Override
        public ApprovalType type() {
            return ApprovalType.EXPENSE;
        }

        @Override
        public void decided(Approval a, boolean approved) {
            if (fail) {
                throw ApiException.conflict("EXPENSE_GONE", "The expense was deleted meanwhile.");
            }
            calls.add(a.getNumber() + "=" + approved);
        }
    }

    /** What a module would do: send a request, raise an alert, write an automatic log line. */
    @RestController
    static class TestApi {
        private final Approvals approvals;
        private final Alerts alerts;
        private final lk.nuvi.gemshop.audit.log.ActivityLog log;
        private final BranchRepository branches;

        TestApi(Approvals approvals, Alerts alerts, lk.nuvi.gemshop.audit.log.ActivityLog log, BranchRepository branches) {
            this.approvals = approvals;
            this.alerts = alerts;
            this.log = log;
            this.branches = branches;
        }

        @PreAuthorize(Roles.ANY)
        @PostMapping("/api/test/audit/request")
        String request(@RequestParam String branch, @RequestParam String title) {
            long id = branches.findByCode(branch).orElseThrow().getId();
            return approvals.request(new Approvals.Request(ApprovalType.EXPENSE, id, title, "Test", new BigDecimal("60000"),
                    "EXP-TEST")).getNumber();
        }

        @PreAuthorize(Roles.OWNER)
        @PostMapping("/api/test/audit/alert")
        String alert(@RequestParam String branch) {
            alerts.raise(AlertType.SALE, "Sale TEST · LKR 1,000", null, branches.findByCode(branch).orElseThrow().getId());
            return "ok";
        }

        @PreAuthorize(Roles.OWNER)
        @PostMapping("/api/test/audit/system-line")
        String system() {
            log.addSystem("Test job", "Automatic", null);
            return "ok";
        }
    }
}
