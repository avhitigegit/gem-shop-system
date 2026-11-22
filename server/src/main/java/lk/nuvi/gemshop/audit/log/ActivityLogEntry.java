package lk.nuvi.gemshop.audit.log;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.settings.branch.Branch;
import org.hibernate.annotations.Immutable;

/**
 * One line of the activity log (K4): when, who, what, detail, branch.
 * Never changed or deleted – there is no API for it and database triggers stop it.
 */
@Entity
@Immutable
@Table(name = "activity_log")
public class ActivityLogEntry extends BaseEntity {

    public static final String SYSTEM = "System (automatic)";
    public static final int DETAIL_MAX = 1000;

    @Column(nullable = false, updatable = false)
    private Instant at;

    /** Empty for automatic jobs ("System (automatic)"). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false)
    private UserAccount user;

    @Column(nullable = false, updatable = false, length = 120)
    private String userName;

    @Column(nullable = false, updatable = false, length = 80)
    private String action;

    @Column(nullable = false, updatable = false, length = DETAIL_MAX)
    private String detail;

    /** Empty = the whole shop (all branches), e.g. exchange rates. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", updatable = false)
    private Branch branch;

    protected ActivityLogEntry() {}

    /**
     * @param user     the person (a reference is enough), or null for "System (automatic)"
     * @param userName the person's name now
     */
    public ActivityLogEntry(Instant at, UserAccount user, String userName, String action, String detail, Branch branch) {
        this.at = at;
        this.user = user;
        this.userName = user == null ? SYSTEM : userName;
        this.action = action;
        this.detail = detail == null ? "" : cut(detail, DETAIL_MAX);
        this.branch = branch;
    }

    static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public Instant getAt() { return at; }
    public UserAccount getUser() { return user; }
    public String getUserName() { return userName; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public Branch getBranch() { return branch; }
}
