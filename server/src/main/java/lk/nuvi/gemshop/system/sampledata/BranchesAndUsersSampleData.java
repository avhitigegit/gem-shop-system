package lk.nuvi.gemshop.system.sampledata;

import java.util.Locale;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.settings.branch.BranchStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Prototype branches (Ella, Branch 2) and the 4 users (one per role). */
@Component
class BranchesAndUsersSampleData implements SampleDataPart {

    private final BranchRepository branches;
    private final UserAccountRepository users;

    BranchesAndUsersSampleData(BranchRepository branches, UserAccountRepository users) {
        this.branches = branches;
        this.users = users;
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
