package lk.nuvi.gemshop.sales;

import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalHandler;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.gems.Gems;
import org.springframework.stereotype.Component;

/**
 * The owner decided a below-minimum price (rule 2): approved → the salesperson can take payment (the gems stay on hold
 * for this sale); rejected → the gems are back in stock and a new price must be agreed. A held sale that was cancelled
 * meanwhile is left as it is.
 */
@Component
class BelowMinApproval implements ApprovalHandler {

    private final HeldSaleRepository held;
    private final Gems gems;

    BelowMinApproval(HeldSaleRepository held, Gems gems) {
        this.held = held;
        this.gems = gems;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.BELOW_MIN;
    }

    @Override
    public void decided(Approval approval, boolean approved) {
        HeldSale h = held.findByNumber(approval.getRef()).orElse(null);
        if (h == null || h.getStatus() != HeldSale.Status.PENDING) {
            return;
        }
        h.setStatus(approved ? HeldSale.Status.APPROVED : HeldSale.Status.REJECTED);
        for (HeldSaleItem i : h.getItems()) {
            Gem g = i.getGem();
            if (approved) {
                gems.addHistory(g, "Price approved", "Owner approved " + Sales.lkr(i.getAgreed()) + " (" + approval.getNumber() + ")");
            } else if (g.getStatus() == GemStatus.ON_HOLD) {
                g.setStatus(GemStatus.IN_STOCK);
                gems.addHistory(g, "Back in stock", "Owner rejected the price " + Sales.lkr(i.getAgreed()) + " ("
                        + approval.getNumber() + ")");
            }
        }
    }
}
