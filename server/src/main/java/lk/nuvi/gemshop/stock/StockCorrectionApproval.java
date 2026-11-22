package lk.nuvi.gemshop.stock;

import java.math.BigDecimal;
import java.util.Locale;
import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalHandler;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.gems.Gems;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import org.springframework.stereotype.Component;

/**
 * The owner decided a stock correction: approved → the change is made on the gem (with a history line); rejected →
 * nothing changes. Runs in the decision's transaction: if the gem changed meanwhile, the owner sees why and can reject.
 */
@Component
class StockCorrectionApproval implements ApprovalHandler {

    private final StockCorrectionRepository corrections;
    private final Gems gems;
    private final BranchRepository branches;

    StockCorrectionApproval(StockCorrectionRepository corrections, Gems gems, BranchRepository branches) {
        this.corrections = corrections;
        this.gems = gems;
        this.branches = branches;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.STOCK_CORRECTION;
    }

    @Override
    public void decided(Approval approval, boolean approved) {
        StockCorrection c = corrections.findByNumber(approval.getRef()).orElse(null);
        if (c == null || c.getStatus() != StockCorrection.Status.PENDING) {
            return;
        }
        c.decided(approved);
        Gem g = c.getGem();
        if (!approved) {
            gems.addHistory(g, "Correction rejected", c.getNumber() + " · " + StockCorrections.change(branches, c));
            return;
        }
        if (!StockCorrections.same(c.getKind(), StockCorrections.current(g, c.getKind()), c.getFrom())) {
            throw ApiException.conflict(StockCorrections.CHANGED_MEANWHILE, g.getCode() + " changed since the request (now "
                    + StockCorrections.label(branches, c.getKind(), StockCorrections.current(g, c.getKind())) + "). Reject it and ask again.");
        }
        if (g.getStatus() == GemStatus.SOLD || g.getStatus() == GemStatus.IN_TRANSIT) {
            throw ApiException.conflict(StockCorrections.CHANGED_MEANWHILE, g.getCode() + " is now " + g.getStatus().label().toLowerCase(Locale.ROOT)
                    + ". Reject the correction.");
        }
        switch (c.getKind()) {
            case STATUS -> g.setStatus(GemStatus.valueOf(c.getTo()));
            case BRANCH -> g.moveTo(branches.findByCode(c.getTo())
                    .orElseThrow(() -> ApiException.conflict(StockCorrections.CHANGED_MEANWHILE, "The branch no longer exists.")));
            case CARAT -> {
                Gem.Details d = g.getDetails();
                g.details(new Gem.Details(d.name(), d.productType(), d.gemType(), new BigDecimal(c.getTo()), d.colour(), d.shape(),
                        d.treatment(), d.description()));
            }
        }
        gems.addHistory(g, "Stock correction", StockCorrections.cut(c.getNumber() + " · " + StockCorrections.change(branches, c) + " (" + c.getReason() + ")", 500));
    }
}
