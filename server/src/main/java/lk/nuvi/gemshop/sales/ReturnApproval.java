package lk.nuvi.gemshop.sales;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lk.nuvi.gemshop.audit.alert.AlertType;
import lk.nuvi.gemshop.audit.alert.Alerts;
import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalHandler;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.gems.Gems;
import lk.nuvi.gemshop.settings.commission.Commission;
import org.springframework.stereotype.Component;

/**
 * The owner decided a return / exchange (BRD E1, E2, D2; rule 7). Approved → the gem is back in stock as "Returned" (may
 * be sold again), its invoice line is released, a credit note is made for the price paid, and the commissions for that gem
 * are taken back (the sale's commissions with and without it – prototype Commissions.reverseFor). Rejected → the gem stays
 * sold.
 */
@Component
class ReturnApproval implements ApprovalHandler {

    private final SaleReturnRepository returns;
    private final CreditNoteRepository creditNotes;
    private final Gems gems;
    private final Commission commission;
    private final Numbers numbers;
    private final Alerts alerts;

    ReturnApproval(SaleReturnRepository returns, CreditNoteRepository creditNotes, Gems gems, Commission commission, Numbers numbers,
            Alerts alerts) {
        this.returns = returns;
        this.creditNotes = creditNotes;
        this.gems = gems;
        this.commission = commission;
        this.numbers = numbers;
        this.alerts = alerts;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.RETURN;
    }

    @Override
    public void decided(Approval a, boolean approved) {
        SaleReturn r = returns.findByNumber(a.getRef()).orElse(null);
        if (r == null || !r.isPending()) {
            return;
        }
        r.decided(approved);
        if (!approved) {
            return;
        }
        Sale s = r.getSale();
        SaleItem item = r.getItem();
        BigDecimal guide = BigDecimal.ZERO;
        BigDecimal staff = BigDecimal.ZERO;
        if (s.getStatus() == Sale.Status.COMPLETED && !item.isReleased()) {
            List<SaleItem> live = s.getItems().stream().filter(i -> !i.isReleased()).toList();
            BigDecimal before = live.stream().map(SaleItem::getAgreed).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal after = before.subtract(item.getAgreed());
            BigDecimal pct = s.getGuide() == null ? BigDecimal.ZERO : s.getGuidePct();
            guide = Commission.guideAmount(before, pct)
                    .subtract(after.signum() > 0 ? Commission.guideAmount(after, pct) : BigDecimal.ZERO);
            staff = commission.staffAmount(before).subtract(after.signum() > 0 ? commission.staffAmount(after) : BigDecimal.ZERO);
        }
        item.release();
        CreditNote cn = creditNotes.save(new CreditNote(numbers.next(Counter.CREDIT_NOTE),
                a.getDecidedAt() == null ? Instant.now() : a.getDecidedAt(), r, guide, staff, Sales.name(a.getDecidedBy())));
        Gem g = r.getGem();
        if (g.getStatus() == GemStatus.SOLD && s.getNumber().equals(g.getSaleNo())) {
            g.returnedByCustomer();
        }
        gems.addHistory(g, "Returned", "Customer return approved (" + r.getNumber() + "), back in stock · credit note " + cn.getNumber());
        alerts.raise(AlertType.RETURN, "Return approved: " + g.getCode() + " · credit note " + cn.getNumber(),
                Returns.CREDIT_LINK + cn.getNumber(), r.getBranch().getId());
    }
}
