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

/** One gem on a held sale with the price the customer agreed. */
@Entity
@Table(name = "held_sale_item")
public class HeldSaleItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "held_id")
    private HeldSale held;

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

    protected HeldSaleItem() {}

    HeldSaleItem(HeldSale held, int lineNo, Gem gem, BigDecimal listPrice, BigDecimal agreed, BigDecimal minPrice) {
        this.held = held;
        this.lineNo = lineNo;
        this.gem = gem;
        this.listPrice = listPrice;
        this.agreed = agreed;
        this.minPrice = minPrice;
    }

    public HeldSale getHeld() { return held; }
    public int getLineNo() { return lineNo; }
    public Gem getGem() { return gem; }
    public BigDecimal getListPrice() { return listPrice; }
    public BigDecimal getAgreed() { return agreed; }
    public BigDecimal getMinPrice() { return minPrice; }
}
