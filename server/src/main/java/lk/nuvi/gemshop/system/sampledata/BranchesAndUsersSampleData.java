package lk.nuvi.gemshop.system.sampledata;

import java.util.Locale;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.settings.branch.BranchStatus;
import lk.nuvi.gemshop.common.config.AppProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Prototype branches (Ella, Branch 2) and the 4 users (one per role). */
@Component
class BranchesAndUsersSampleData implements SampleDataPart {

    private final BranchRepository branches;
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final String password;

    BranchesAndUsersSampleData(BranchRepository branches, UserAccountRepository users, PasswordEncoder encoder, AppProperties app) {
        this.branches = branches;
        this.users = users;
        this.encoder = encoder;
        this.password = app.sampleData().password();
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public String name() {
        return "branches + users";
    }

    @Override
    public int catchUp(SampleData data) {
        if (password == null || password.isBlank()) {
            return 0;
        }
        int n = 0;
        for (JsonNode u : data.node("users").values()) {
            var user = users.findByEmail(lk.nuvi.gemshop.auth.user.UserAccount.normalise(u.path("email").asString())).orElse(null);
            if (user != null && user.getPasswordHash() == null) {
                user.changePassword(encoder.encode(password), java.time.Instant.now());
                n++;
            }
        }
        return n;
    }

    @Override
    public int load(SampleData data) {
        int n = 0;
        for (JsonNode b : data.node("branches").values()) {
            Branch branch = new Branch(b.path("id").asString(), b.path("name").asString(),
                    BranchStatus.valueOf(b.path("status").asString().toUpperCase(Locale.ROOT)))
                    .details(SampleData.text(b.path("short")), SampleData.text(b.path("address")),
                            SampleData.text(b.path("phone")), data.date(b.path("opened")));
            data.ref(branch.getCode(), branches.save(branch));
            n++;
        }
        for (JsonNode u : data.node("users").values()) {
            Role role = role(u.path("role").asString());
            String branchCode = u.path("branch").asString();
            Branch branch = "all".equals(branchCode) ? null : data.ref(branchCode, Branch.class);
            UserAccount user = new UserAccount(u.path("name").asString(), u.path("email").asString(), role, branch)
                    .phone(SampleData.text(u.path("phone")));
            user.setActive(u.path("active").asBoolean(true));
            if (password != null && !password.isBlank()) {
                user.changePassword(encoder.encode(password), java.time.Instant.now()); // same sample password for all 4
            }
            user.loggedIn(data.instant(u.path("lastLogin")));
            data.ref(u.path("id").asString(), users.save(user));
            n++;
        }
        return n;
    }

    private static Role role(String prototypeRole) {
        return switch (prototypeRole) {
            case "owner" -> Role.OWNER;
            case "salesperson" -> Role.SALESPERSON;
            case "staff" -> Role.STAFF;
            case "accountant" -> Role.ACCOUNTANT;
            default -> throw new IllegalStateException("Unknown role " + prototypeRole);
        };
    }
}
