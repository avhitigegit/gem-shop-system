package lk.nuvi.gemshop.sales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lk.nuvi.gemshop.common.money.Currency;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/**
 * One payment line (BRD G1, G2): cash in LKR / USD / EUR, or card in LKR. {@code lkr} = amount × the day's rate. The
 * card fee is what the bank keeps – the shop pays it, the customer pays the same price.
 */
@Entity
@Table(name = "sale_payment")
public class SalePayment extends BaseEntity {

    /** CREDIT = an exchange credit note (CN-…) paying for the new gem. */
    public enum Method { CASH, CARD, CREDIT }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Column(nullable = false)
    private int lineNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Method method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal rate;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal lkr;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal feePct;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal fee;

    @Column(length = 20)
    private String creditNoteNo;

    protected SalePayment() {}

    SalePayment(Sale sale, int lineNo, Method method, Currency currency, BigDecimal amount, BigDecimal rate, BigDecimal lkr,
            BigDecimal feePct, BigDecimal fee) {
        this.sale = sale;
        this.lineNo = lineNo;
        this.method = method;
        this.currency = currency;
        this.amount = amount;
        this.rate = rate;
        this.lkr = lkr;
        this.feePct = feePct;
        this.fee = fee;
    }

    void creditNote(String no) {
        this.creditNoteNo = no;
    }

    public String getCreditNoteNo() { return creditNoteNo; }
    public Sale getSale() { return sale; }
    public int getLineNo() { return lineNo; }
    public Method getMethod() { return method; }
    public Currency getCurrency() { return currency; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getRate() { return rate; }
    public BigDecimal getLkr() { return lkr; }
    public BigDecimal getFeePct() { return feePct; }
    public BigDecimal getFee() { return fee; }
}
