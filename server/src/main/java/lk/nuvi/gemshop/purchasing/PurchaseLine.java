package lk.nuvi.gemshop.purchasing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.gems.Gem;

/** One gem on a purchase, with what it cost. */
@Entity
@Table(name = "purchase_line")
public class PurchaseLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id")
    private Purchase purchase;

    @Column(nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gem_id")
    private Gem gem;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal cost;

    protected PurchaseLine() {}

    PurchaseLine(Purchase purchase, int lineNo, Gem gem, BigDecimal cost) {
        this.purchase = purchase;
        this.lineNo = lineNo;
        this.gem = gem;
        this.cost = cost;
    }

    public Purchase getPurchase() { return purchase; }
    public int getLineNo() { return lineNo; }
    public Gem getGem() { return gem; }
    public BigDecimal getCost() { return cost; }
}
