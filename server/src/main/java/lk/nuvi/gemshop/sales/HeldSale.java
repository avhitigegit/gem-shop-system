package lk.nuvi.gemshop.sales;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.commissions.Guide;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.customers.Customer;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.settings.branch.Branch;

/**
 * A sale below the minimum price, waiting for the owner (BRD F3, rule 2). The gems are "On hold" meanwhile. Approved →
 * the salesperson takes payment; rejected → the gems are back in stock and a new price must be agreed.
 */
@Entity
@Table(name = "held_sale")
public class HeldSale extends BaseEntity {

    public enum Status { PENDING, APPROVED, REJECTED, COMPLETED, CANCELLED }

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @Column(nullable = false)
    private Instant heldAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private UserAccount staff;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guide_id")
    private Guide guide;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.PENDING;

    @Column(length = 20)
    private String approvalNo;

    @Column(length = 20)
    private String saleNo;

    @OneToMany(mappedBy = "held", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<HeldSaleItem> items = new ArrayList<>();

    protected HeldSale() {}

    public HeldSale(String number, Instant heldAt, Branch branch, UserAccount staff, Customer customer, Guide guide) {
        this.number = number;
        this.heldAt = heldAt;
        this.branch = branch;
        this.staff = staff;
        this.customer = customer;
        this.guide = guide;
    }

    public void add(Gem gem, BigDecimal listPrice, BigDecimal agreed, BigDecimal minPrice) {
        items.add(new HeldSaleItem(this, items.size() + 1, gem, listPrice, agreed, minPrice));
    }

    public void askedIn(String approvalNo) {
        this.approvalNo = approvalNo;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void completed(String saleNo) {
        this.status = Status.COMPLETED;
        this.saleNo = saleNo;
    }

    public String getNumber() { return number; }
    public Instant getHeldAt() { return heldAt; }
    public Branch getBranch() { return branch; }
    public UserAccount getStaff() { return staff; }
    public Customer getCustomer() { return customer; }
    public Guide getGuide() { return guide; }
    public Status getStatus() { return status; }
    public String getApprovalNo() { return approvalNo; }
    public String getSaleNo() { return saleNo; }
    public List<HeldSaleItem> getItems() { return items; }
}
