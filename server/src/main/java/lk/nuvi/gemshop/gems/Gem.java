package lk.nuvi.gemshop.gems;

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
import java.util.UUID;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.settings.branch.Branch;

/** One gem product (BRD C1–C9). The code never changes; prices only by the owner. */
@Entity
@Table(name = "gem")
public class Gem extends BaseEntity {

    /** The details a person enters (form) – same for add and edit. */
    public record Details(String name, String productType, String gemType, BigDecimal carat, String colour, String shape,
            String treatment, String description) {}

    /** Certificate: type, and for a lab certificate the lab, number, date and scan. */
    public record Certificate(CertType type, String lab, String number, LocalDate date, UUID scan) {}

    /** Prices in LKR: cost (owner only), selling, minimum (lowest price without owner approval). */
    public record Prices(BigDecimal cost, BigDecimal price, BigDecimal min) {}

    @Column(nullable = false, length = 12, updatable = false)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 80)
    private String productType;

    @Column(nullable = false, length = 80)
    private String gemType;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal carat;

    @Column(nullable = false, length = 80)
    private String colour;

    @Column(nullable = false, length = 80)
    private String shape;

    @Column(nullable = false, length = 80)
    private String treatment;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CertType certType;

    @Column(length = 80)
    private String certLab;

    @Column(length = 60)
    private String certNo;

    private LocalDate certDate;

    private UUID certScan;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cost;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "min_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal minPrice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GemStatus status;

    @Column(length = 20)
    private String supplierCode;

    @Column(length = 20)
    private String purchaseNo;

    @Column(length = 20)
    private String saleNo;

    private Instant soldAt;

    @Column(nullable = false)
    private Instant addedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private UserAccount addedBy;

    protected Gem() {}

    public Gem(String code, Details d, Certificate c, Prices p, Branch branch, GemStatus status, Instant addedAt,
            UserAccount addedBy) {
        this.code = code;
        this.branch = branch;
        this.status = status;
        this.addedAt = addedAt;
        this.addedBy = addedBy;
        details(d);
        certificate(c);
        prices(p);
    }

    public void details(Details d) {
        this.name = d.name();
        this.productType = d.productType();
        this.gemType = d.gemType();
        this.carat = d.carat();
        this.colour = d.colour();
        this.shape = d.shape();
        this.treatment = d.treatment();
        this.description = d.description();
    }

    public void certificate(Certificate c) {
        this.certType = c.type();
        boolean lab = c.type() == CertType.LAB;
        this.certLab = lab ? c.lab() : null;
        this.certNo = c.type() == CertType.NONE ? null : c.number();
        this.certDate = lab ? c.date() : null;
        this.certScan = lab ? c.scan() : null;
    }

    public void prices(Prices p) {
        this.cost = p.cost();
        this.price = p.price();
        this.minPrice = p.min();
    }

    /** Linked to the purchase it was bought in (P2-03). */
    public void boughtIn(String supplierCode, String purchaseNo) {
        this.supplierCode = supplierCode;
        this.purchaseNo = purchaseNo;
    }

    /** Sold on an invoice (P3). */
    public void sold(String saleNo, Instant at) {
        this.saleNo = saleNo;
        this.soldAt = at;
        this.status = GemStatus.SOLD;
    }

    public void setStatus(GemStatus status) {
        this.status = status;
    }

    public void moveTo(Branch branch) {
        this.branch = branch;
    }

    /** Used by Settings → Drop-down lists when a value is renamed. */
    void renameValue(String field, String to) {
        switch (field) {
            case "productType" -> productType = to;
            case "gemType" -> gemType = to;
            case "colour" -> colour = to;
            case "shape" -> shape = to;
            case "treatment" -> treatment = to;
            case "certLab" -> certLab = to;
            default -> throw new IllegalArgumentException(field);
        }
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getProductType() { return productType; }
    public String getGemType() { return gemType; }
    public BigDecimal getCarat() { return carat; }
    public String getColour() { return colour; }
    public String getShape() { return shape; }
    public String getTreatment() { return treatment; }
    public String getDescription() { return description; }
    public CertType getCertType() { return certType; }
    public String getCertLab() { return certLab; }
    public String getCertNo() { return certNo; }
    public LocalDate getCertDate() { return certDate; }
    public UUID getCertScan() { return certScan; }
    public BigDecimal getCost() { return cost; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getMinPrice() { return minPrice; }
    public Branch getBranch() { return branch; }
    public GemStatus getStatus() { return status; }
    public String getSupplierCode() { return supplierCode; }
    public String getPurchaseNo() { return purchaseNo; }
    public String getSaleNo() { return saleNo; }
    public Instant getSoldAt() { return soldAt; }
    public Instant getAddedAt() { return addedAt; }
    public UserAccount getAddedBy() { return addedBy; }

    public Details getDetails() {
        return new Details(name, productType, gemType, carat, colour, shape, treatment, description);
    }

    public Prices getPrices() {
        return new Prices(cost, price, minPrice);
    }
}
