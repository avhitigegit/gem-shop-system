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
import java.time.LocalDate;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.settings.branch.Branch;

/** A monthly bill (BRD M5): rent, phone, salaries – added as an expense automatically each month on its day (1–28). */
@Entity
@Table(name = "monthly_bill")
public class MonthlyBill extends BaseEntity {

    @Column(nullable = false, length = 8, updatable = false)
    private String code;

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
    private Expense.Method method;

    @Column(nullable = false)
    private int day;

    @Column(nullable = false)
    private boolean active = true;

    private LocalDate lastAdded;

    protected MonthlyBill() {}

    public MonthlyBill(String code, Branch branch, String expenseType, BigDecimal amount, String paidTo, Expense.Method method, int day) {
        this.code = code;
        change(branch, expenseType, amount, paidTo, method, day);
    }

    public void change(Branch branch, String expenseType, BigDecimal amount, String paidTo, Expense.Method method, int day) {
        this.branch = branch;
        this.expenseType = expenseType;
        this.amount = amount;
        this.paidTo = paidTo;
        this.method = method;
        this.day = day;
    }

    public void active(boolean on) {
        this.active = on;
    }

    public void added(LocalDate on) {
        this.lastAdded = on;
    }

    public String getCode() { return code; }
    public Branch getBranch() { return branch; }
    public String getExpenseType() { return expenseType; }
    public BigDecimal getAmount() { return amount; }
    public String getPaidTo() { return paidTo; }
    public Expense.Method getMethod() { return method; }
    public int getDay() { return day; }
    public boolean isActive() { return active; }
    public LocalDate getLastAdded() { return lastAdded; }
}
