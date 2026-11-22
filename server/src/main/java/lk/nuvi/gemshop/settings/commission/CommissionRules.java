package lk.nuvi.gemshop.settings.commission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/** The one row of commission settings: the guide / driver % new guides start with (BRD H1). */
@Entity
@Table(name = "commission_rules")
public class CommissionRules extends BaseEntity {

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultGuidePct;

    protected CommissionRules() {}

    public CommissionRules(BigDecimal defaultGuidePct) {
        this.defaultGuidePct = defaultGuidePct;
    }

    public BigDecimal getDefaultGuidePct() { return defaultGuidePct; }

    void setDefaultGuidePct(BigDecimal pct) {
        this.defaultGuidePct = pct;
    }
}
