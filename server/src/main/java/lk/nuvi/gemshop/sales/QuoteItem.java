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

/** One gem on a reservation or quotation with the price offered. */
@Entity
@Table(name = "quote_item")
public class QuoteItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_id")
    private Quote quote;

    @Column(nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gem_id")
    private Gem gem;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    protected QuoteItem() {}

    QuoteItem(Quote quote, int lineNo, Gem gem, BigDecimal price) {
        this.quote = quote;
        this.lineNo = lineNo;
        this.gem = gem;
        this.price = price;
    }

    public Quote getQuote() { return quote; }
    public int getLineNo() { return lineNo; }
    public Gem getGem() { return gem; }
    public BigDecimal getPrice() { return price; }
}
