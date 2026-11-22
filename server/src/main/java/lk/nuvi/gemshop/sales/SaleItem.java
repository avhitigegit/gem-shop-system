package lk.nuvi.gemshop.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.gems.Gem;

/** One gem on a sale with its agreed price (BRD F2). */
@Entity
@Table(name = "sale_item")
public class SaleItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Column(nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gem_id")
    private Gem gem;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal listPrice;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal agreed;

    @Column(precision = 14, scale = 2)
    private BigDecimal minPrice;

    @Column(length = 20)
    private String approvalNo;

    @Column(nullable = false)
    private boolean released;

    protected SaleItem() {}

    SaleItem(Sale sale, int lineNo, Gem gem, BigDecimal listPrice, BigDecimal agreed, BigDecimal minPrice, String approvalNo) {
        this.sale = sale;
        this.lineNo = lineNo;
        this.gem = gem;
        this.listPrice = listPrice;
        this.agreed = agreed;
        this.minPrice = minPrice;
        this.approvalNo = approvalNo;
    }

    /** Returned or invoice cancelled: the gem may be sold again (P3-02, P3-04). */
    public void release() {
        this.released = true;
    }

    public Sale getSale() { return sale; }
    public int getLineNo() { return lineNo; }
    public Gem getGem() { return gem; }
    public BigDecimal getListPrice() { return listPrice; }
    public BigDecimal getAgreed() { return agreed; }
    public BigDecimal getMinPrice() { return minPrice; }
    public String getApprovalNo() { return approvalNo; }
    public boolean isReleased() { return released; }
}
