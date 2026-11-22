package lk.nuvi.gemshop.settings.commission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/** Staff commission level (BRD H2): sale total up to {@code upTo} → fixed {@code amount}. Last level: upTo empty. */
@Entity
@Table(name = "commission_level")
public class CommissionLevel extends BaseEntity {

    @Column(nullable = false)
    private int levelNo;

    @Column(precision = 14, scale = 2)
    private BigDecimal upTo;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    protected CommissionLevel() {}

    public CommissionLevel(int levelNo, BigDecimal upTo, BigDecimal amount) {
        this.levelNo = levelNo;
        this.upTo = upTo;
        this.amount = amount;
    }

    public int getLevelNo() { return levelNo; }
    public BigDecimal getUpTo() { return upTo; }
    public BigDecimal getAmount() { return amount; }
}
