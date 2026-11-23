package lk.nuvi.gemshop.expenses;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.settings.branch.Branch;

/**
 * A shop expense (EXP-…, BRD M1–M7) with a bill photo (rule 11). Above the owner's limit it waits for approval (M6). Paid
 * "Cash (drawer)" it comes off the day's expected cash (M4, I2). P3-06 records drawer expenses; P5-01 adds the rest.
 */
@Entity
@Table(name = "expense")
public class Expense extends BaseEntity {

    public static final String OWNER_TYPE = "EXPENSE";

    public enum Method { DRAWER, OWNER_CASH, BANK, CARD }

    public enum Status { APPROVED, PENDING, REJECTED }

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @Column(nullable = false)
    private Instant spentAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(nullable = false, length = 80)
    private String expenseType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 120)
    private String paidTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Status status;

    @Column(length = 20)
    private String approvalNo;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private UserAccount staff;

    protected Expense() {}

    public Expense(String number, Instant spentAt, Branch branch, String expenseType, BigDecimal amount, String paidTo, Method method,
            Status status, String note, UserAccount staff) {
        this.number = number;
        this.spentAt = spentAt;
        this.branch = branch;
        this.expenseType = expenseType;
        this.amount = amount;
        this.paidTo = paidTo;
        this.method = method;
        this.status = status;
        this.note = note;
        this.staff = staff;
    }

    public void asked(String approvalNo) {
        this.approvalNo = approvalNo;
    }

    public void decided(boolean approved) {
        this.status = approved ? Status.APPROVED : Status.REJECTED;
    }

    public String getNumber() { return number; }
    public Instant getSpentAt() { return spentAt; }
    public Branch getBranch() { return branch; }
    public String getExpenseType() { return expenseType; }
    public BigDecimal getAmount() { return amount; }
    public String getPaidTo() { return paidTo; }
    public Method getMethod() { return method; }
    public Status getStatus() { return status; }
    public String getApprovalNo() { return approvalNo; }
    public String getNote() { return note; }
    public UserAccount getStaff() { return staff; }
}
