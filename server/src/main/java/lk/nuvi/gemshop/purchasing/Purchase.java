package lk.nuvi.gemshop.purchasing;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.settings.branch.Branch;

/** A cash purchase from a supplier (BRD B2, B3): the gems bought, what each cost, and the photo of the bill. */
@Entity
@Table(name = "purchase")
public class Purchase extends BaseEntity {

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    private String paidBy = "CASH";

    private UUID billPhoto;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private UserAccount recordedBy;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<PurchaseLine> lines = new ArrayList<>();

    protected Purchase() {}

    public Purchase(String number, LocalDate date, Supplier supplier, Branch branch, UUID billPhoto, String notes,
            UserAccount recordedBy) {
        this.number = number;
        this.date = date;
        this.supplier = supplier;
        this.branch = branch;
        this.billPhoto = billPhoto;
        this.notes = notes;
        this.recordedBy = recordedBy;
    }

    public void add(Gem gem, BigDecimal cost) {
        lines.add(new PurchaseLine(this, lines.size() + 1, gem, cost));
        total = total.add(cost);
    }

    public String getNumber() { return number; }
    public LocalDate getDate() { return date; }
    public Supplier getSupplier() { return supplier; }
    public Branch getBranch() { return branch; }
    public BigDecimal getTotal() { return total; }
    public String getPaidBy() { return paidBy; }
    public UUID getBillPhoto() { return billPhoto; }
    public String getNotes() { return notes; }
    public UserAccount getRecordedBy() { return recordedBy; }
    public List<PurchaseLine> getLines() { return lines; }
}
