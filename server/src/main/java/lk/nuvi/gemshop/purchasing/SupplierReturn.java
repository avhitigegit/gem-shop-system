package lk.nuvi.gemshop.purchasing;

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
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.gems.Gem;

/** A gem given back to its supplier (BRD B4): reason, refund and how it was refunded. The gem leaves stock. */
@Entity
@Table(name = "supplier_return")
public class SupplierReturn extends BaseEntity {

    public enum RefundMethod { CASH, REPLACEMENT, CREDIT }

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @Column(nullable = false)
    private Instant returnedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gem_id")
    private Gem gem;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal refund;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RefundMethod refundMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private UserAccount recordedBy;

    protected SupplierReturn() {}

    public SupplierReturn(String number, Instant returnedAt, Supplier supplier, Gem gem, String reason, BigDecimal refund,
            RefundMethod refundMethod, UserAccount recordedBy) {
        this.number = number;
        this.returnedAt = returnedAt;
        this.supplier = supplier;
        this.gem = gem;
        this.reason = reason;
        this.refund = refund;
        this.refundMethod = refundMethod;
        this.recordedBy = recordedBy;
    }

    public String getNumber() { return number; }
    public Instant getReturnedAt() { return returnedAt; }
    public Supplier getSupplier() { return supplier; }
    public Gem getGem() { return gem; }
    public String getReason() { return reason; }
    public BigDecimal getRefund() { return refund; }
    public RefundMethod getRefundMethod() { return refundMethod; }
    public UserAccount getRecordedBy() { return recordedBy; }
}
