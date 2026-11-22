package lk.nuvi.gemshop.auth.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.settings.branch.Branch;

/** A person who logs in (Settings → Staff logins). Owner and Accountant have no branch (= all branches). */
@Entity
@Table(name = "app_user")
public class UserAccount extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    /** Login name. Stored in lower case, unique. */
    @Column(nullable = false, length = 160)
    private String email;

    @Column(length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(nullable = false)
    private boolean active = true;

    /** BCrypt hash, set in P1-02. Empty until the person sets a password. */
    @Column(length = 100)
    private String passwordHash;

    private Instant lastLoginAt;

    protected UserAccount() {}

    public UserAccount(String name, String email, Role role, Branch branch) {
        this.name = name;
        this.email = normalise(email);
        this.role = role;
        assign(role, branch);
    }

    public static String normalise(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Salesperson and Shop Staff need a branch; Owner and Accountant see all branches. */
    public void assign(Role role, Branch branch) {
        if (!role.seesAllBranches() && branch == null) {
            throw new IllegalArgumentException(role.label() + " must belong to a branch");
        }
        this.role = role;
        this.branch = role.seesAllBranches() ? null : branch;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public Role getRole() {
        return role;
    }

    public Branch getBranch() {
        return branch;
    }

    public boolean isActive() {
        return active;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public UserAccount phone(String phone) {
        this.phone = phone;
        return this;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void loggedIn(Instant at) {
        this.lastLoginAt = at;
    }
}
