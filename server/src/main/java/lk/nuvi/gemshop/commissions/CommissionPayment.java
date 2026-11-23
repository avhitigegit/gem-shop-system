package lk.nuvi.gemshop.commissions;

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
import java.time.LocalDate;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.settings.branch.Branch;

/** One commission payment (CP-…, BRD H4): the lines it paid point to it. Printed as the commission slip. */
@Entity
@Table(name = "commission_payment")
public class CommissionPayment extends BaseEntity {

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @Column(nullable = false, updatable = false)
    private LocalDate paidOn;

    @Column(nullable = false, updatable = false)
    private Instant paidAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12, updatable = false)
    private PayMethod method;

    @Column(nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 300, updatable = false)
    private String payee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", updatable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paid_by_id", updatable = false)
    private UserAccount paidBy;

    protected CommissionPayment() {}

    public CommissionPayment(String number, LocalDate paidOn, Instant paidAt, PayMethod method, BigDecimal amount, String payee,
            Branch branch, UserAccount paidBy) {
        this.number = number;
        this.paidOn = paidOn;
        this.paidAt = paidAt;
        this.method = method;
        this.amount = amount;
        this.payee = payee;
        this.branch = branch;
        this.paidBy = paidBy;
    }

    public String getNumber() { return number; }
    public LocalDate getPaidOn() { return paidOn; }
    public Instant getPaidAt() { return paidAt; }
    public PayMethod getMethod() { return method; }
    public BigDecimal getAmount() { return amount; }
    public String getPayee() { return payee; }
    public Branch getBranch() { return branch; }
    public UserAccount getPaidBy() { return paidBy; }
}
