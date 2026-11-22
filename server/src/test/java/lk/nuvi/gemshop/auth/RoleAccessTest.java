package lk.nuvi.gemshop.auth;

import static lk.nuvi.gemshop.SignIn.ACCOUNTANT;
import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static lk.nuvi.gemshop.SignIn.STAFF;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lk.nuvi.gemshop.SignIn;
import lk.nuvi.gemshop.SignIn.Signed;
import lk.nuvi.gemshop.TestcontainersConfiguration;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.common.branch.BranchScope;
import lk.nuvi.gemshop.common.json.OwnerOnly;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** P1-02: allowed and blocked calls for each role, the branch check, and cost hidden from non-owners. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, RoleAccessTest.RoleApi.class})
class RoleAccessTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    private final Map<String, Signed> who = new LinkedHashMap<>();

    @BeforeEach
    void signIn() throws Exception {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ?",
                encoder.encode(SignIn.PASSWORD));
        for (String e : new String[] {OWNER, SALES, STAFF, ACCOUNTANT}) {
            who.put(e, SignIn.login(mvc, e));
        }
    }

    /** Which role may call which test API (O = owner, S = salesperson, T = shop staff, A = accountant). */
    @Test
    void allowedAndBlockedCallsForEachRole() throws Exception {
        String[][] matrix = {
            // api            owner  sales  staff  accountant
            {"owner",          "200", "403", "403", "403"},
            {"owner-sales",    "200", "200", "403", "403"},
            {"owner-staff",    "200", "403", "200", "403"},
            {"owner-accountant", "200", "403", "403", "200"},
            {"anyone",         "200", "200", "200", "200"},
        };
        String[] people = {OWNER, SALES, STAFF, ACCOUNTANT};
        for (String[] row : matrix) {
            for (int i = 0; i < people.length; i++) {
                mvc.perform(get("/api/test/roles/" + row[0]).with(who.get(people[i]).bearer()))
                        .andExpect(status().is(Integer.parseInt(row[i + 1])));
            }
            mvc.perform(get("/api/test/roles/" + row[0])).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/test/roles/owner").with(who.get(SALES).bearer()))
                .andExpect(jsonPath("$.code").value("FORBIDDEN")).andExpect(jsonPath("$.message").value("Your login cannot do this."));
    }

    @Test
    void salespersonAndStaffOnlyWorkInTheirOwnBranch() throws Exception {
        mvc.perform(get("/api/test/branch/ella").with(who.get(SALES).bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/test/branch/b2").with(who.get(SALES).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/test/branch/b2").with(who.get(STAFF).bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/test/branch/b2").with(who.get(OWNER).bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/test/branch/b2").with(who.get(ACCOUNTANT).bearer())).andExpect(status().isOk());
    }

    @Test
    void myBranchOrAllBranches() throws Exception {
        // a salesperson asking for Branch 2 still gets Ella
        mvc.perform(get("/api/test/scope").header(CurrentUser.BRANCH_HEADER, "b2").with(who.get(SALES).bearer()))
                .andExpect(content().string("ella"));
        mvc.perform(get("/api/test/scope").header(CurrentUser.BRANCH_HEADER, "all").with(who.get(STAFF).bearer()))
                .andExpect(content().string("ella"));
        mvc.perform(get("/api/test/scope").header(CurrentUser.BRANCH_HEADER, "b2").with(who.get(OWNER).bearer()))
                .andExpect(content().string("b2"));
        mvc.perform(get("/api/test/scope").header(CurrentUser.BRANCH_HEADER, "all").with(who.get(ACCOUNTANT).bearer()))
                .andExpect(content().string("all"));
        mvc.perform(get("/api/test/scope").with(who.get(OWNER).bearer())).andExpect(content().string("all"));
        mvc.perform(get("/api/test/scope").header(CurrentUser.BRANCH_HEADER, "nope").with(who.get(OWNER).bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void costAndProfitOnlyForTheOwner() throws Exception {
        mvc.perform(get("/api/test/gem").with(who.get(OWNER).bearer()))
                .andExpect(jsonPath("$.price").value(450000)).andExpect(jsonPath("$.cost").value(300000))
                .andExpect(jsonPath("$.profit").value(150000));
        // the accountant sees profit (BRD reports) but not the cost price of a gem
        mvc.perform(get("/api/test/gem").with(who.get(ACCOUNTANT).bearer()))
                .andExpect(jsonPath("$.price").value(450000)).andExpect(jsonPath("$.cost").doesNotExist())
                .andExpect(jsonPath("$.profit").value(150000));
        for (String e : new String[] {SALES, STAFF}) {
            mvc.perform(get("/api/test/gem").with(who.get(e).bearer()))
                    .andExpect(jsonPath("$.price").value(450000)).andExpect(jsonPath("$.cost").doesNotExist())
                    .andExpect(jsonPath("$.profit").doesNotExist());
        }
    }

    public record Gem(String code, BigDecimal price, @OwnerOnly BigDecimal cost, @OwnerOnly(accountant = true) BigDecimal profit) {}

    @RestController
    static class RoleApi {
        private final CurrentUser current;
        private final BranchRepository branches;

        RoleApi(CurrentUser current, BranchRepository branches) {
            this.current = current;
            this.branches = branches;
        }

        @PreAuthorize(Roles.OWNER) @GetMapping("/api/test/roles/owner") String owner() { return "ok"; }
        @PreAuthorize(Roles.OWNER_SALES) @GetMapping("/api/test/roles/owner-sales") String sales() { return "ok"; }
        @PreAuthorize(Roles.OWNER_STAFF) @GetMapping("/api/test/roles/owner-staff") String staff() { return "ok"; }
        @PreAuthorize(Roles.OWNER_ACCOUNTANT) @GetMapping("/api/test/roles/owner-accountant") String acc() { return "ok"; }
        @PreAuthorize(Roles.ANY) @GetMapping("/api/test/roles/anyone") String any() { return "ok"; }

        @PreAuthorize(Roles.ANY)
        @GetMapping("/api/test/branch/{code}")
        String branch(@PathVariable String code) {
            current.checkBranch(branches.findByCode(code).orElseThrow().getId());
            return "ok";
        }

        @PreAuthorize(Roles.ANY)
        @GetMapping("/api/test/scope")
        String scope() {
            BranchScope s = current.scope();
            return s.isAll() ? "all" : branches.findById(s.branchId()).orElseThrow().getCode();
        }

        @PreAuthorize(Roles.ANY)
        @GetMapping("/api/test/gem")
        Gem gem() {
            return new Gem("GEM-00101", new BigDecimal("450000"), new BigDecimal("300000"), new BigDecimal("150000"));
        }
    }
}
