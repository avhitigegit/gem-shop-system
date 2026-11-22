package lk.nuvi.gemshop.sales;

import java.time.Instant;
import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalHandler;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.gems.Gems;
import org.springframework.stereotype.Component;

/**
 * The owner decided an invoice cancellation (BRD G6, rule 7). Approved → the invoice stays on record marked CANCELLED,
 * its gems go back in stock (and may be sold again) and its commissions no longer count. Rejected → nothing changes;
 * the salesperson may ask again with a new reason.
 */
@Component
class CancelInvoiceApproval implements ApprovalHandler {

    private final SaleRepository sales;
    private final Gems gems;

    CancelInvoiceApproval(SaleRepository sales, Gems gems) {
        this.sales = sales;
        this.gems = gems;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.CANCEL_INVOICE;
    }

    @Override
    public void decided(Approval approval, boolean approved) {
        Sale s = sales.findByNumber(approval.getRef()).orElse(null);
        if (s == null || s.getStatus() == Sale.Status.CANCELLED || !approved) {
            return;
        }
        s.cancel(approval.getDecidedAt() == null ? Instant.now() : approval.getDecidedAt(), Sales.name(approval.getDecidedBy()));
        for (SaleItem i : s.getItems()) {
            if (i.isReleased()) {
                continue;
            }
            i.release();
            Gem g = i.getGem();
            if (g.getStatus() == GemStatus.SOLD && s.getNumber().equals(g.getSaleNo())) {
                g.unsold();
                gems.addHistory(g, "Back in stock", "Invoice " + s.getNumber() + " cancelled (" + approval.getNumber() + ")");
            }
        }
    }
}
