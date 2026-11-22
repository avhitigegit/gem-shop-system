package lk.nuvi.gemshop.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.settings.branch.Branch;

/** A finished stock check of one branch (BRD D4): how many gems were expected and found, and which were not. */
@Entity
@Table(name = "stock_check")
public class StockCheck extends BaseEntity {

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(nullable = false)
    private Instant checkedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_by")
    private UserAccount checkedBy;

    @Column(nullable = false)
    private int expected;

    @Column(nullable = false)
    private int found;

    @Column(nullable = false, length = 4000)
    private String missing = "";

    @Column(nullable = false, length = 4000)
    private String wrong = "";

    @Column(nullable = false, length = 2000)
    private String unknown = "";

    @Column(length = 500)
    private String notes;

    protected StockCheck() {}

    public StockCheck(String number, Branch branch, Instant checkedAt, UserAccount checkedBy, int expected, int found,
            List<String> missing, List<String> wrong, List<String> unknown, String notes) {
        this.number = number;
        this.branch = branch;
        this.checkedAt = checkedAt;
        this.checkedBy = checkedBy;
        this.expected = expected;
        this.found = found;
        this.missing = String.join(",", missing);
        this.wrong = String.join(",", wrong);
        this.unknown = cut(String.join(",", unknown), 2000);
        this.notes = notes;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static List<String> list(String s) {
        return s == null || s.isBlank() ? List.of() : Arrays.asList(s.split(","));
    }

    public String getNumber() { return number; }
    public Branch getBranch() { return branch; }
    public Instant getCheckedAt() { return checkedAt; }
    public UserAccount getCheckedBy() { return checkedBy; }
    public int getExpected() { return expected; }
    public int getFound() { return found; }
    public List<String> getMissing() { return list(missing); }
    public List<String> getWrong() { return list(wrong); }
    public List<String> getUnknown() { return list(unknown); }
    public String getNotes() { return notes; }
}
