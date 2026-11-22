package lk.nuvi.gemshop.stock;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.audit.approval.Approvals;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.CurrentUser.Who;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.gems.Gems;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.stock.StockCorrection.Kind;
import lk.nuvi.gemshop.stock.api.StockDtos.CorrectionForm;
import lk.nuvi.gemshop.stock.api.StockDtos.CorrectionView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fix a stock mistake (BRD D7, rule 7): status (lost / damaged → written off, or found → in stock), branch (wrong
 * place) or carat weight (data error). Every correction needs a reason and the owner's approval; the gem changes only
 * when the owner approves ({@link StockCorrectionApproval}).
 */
@Service
public class StockCorrections {

    public static final String CANNOT_CORRECT = "CANNOT_CORRECT";
    public static final String NOTHING_TO_CHANGE = "NOTHING_TO_CHANGE";
    public static final String ALREADY_WAITING = "ALREADY_WAITING";
    public static final String CHANGED_MEANWHILE = "CHANGED_MEANWHILE";

    private final StockCorrectionRepository corrections;
    private final StockGems stockGems;
    private final Gems gems;
    private final BranchRepository branches;
    private final Approvals approvals;
    private final Numbers numbers;
    private final CurrentUser current;
    private final EntityManager em;

    public StockCorrections(StockCorrectionRepository corrections, StockGems stockGems, Gems gems, BranchRepository branches,
            Approvals approvals, Numbers numbers, CurrentUser current, EntityManager em) {
        this.corrections = corrections;
        this.stockGems = stockGems;
        this.gems = gems;
        this.branches = branches;
        this.approvals = approvals;
        this.numbers = numbers;
        this.current = current;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public List<CorrectionView> list() {
        return corrections.forBranch(current.scope().branchId()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public long waiting() {
        return corrections.forBranch(current.scope().branchId()).stream()
                .filter(c -> c.getStatus() == StockCorrection.Status.PENDING).count();
    }

    /** Sends the correction for the owner's approval. Nothing changes on the gem yet. */
    @Transactional
    public CorrectionView request(CorrectionForm f) {
        Who who = current.require();
        Gem g = stockGems.require(f.gem());
        if (g.getStatus() == GemStatus.SOLD) {
            throw ApiException.conflict(CANNOT_CORRECT, g.getCode() + " is sold – a customer return is done in Returns.");
        }
        if (g.getStatus() == GemStatus.IN_TRANSIT) {
            throw ApiException.conflict(CANNOT_CORRECT, g.getCode() + " is on the way – receive the transfer first.");
        }
        if (g.getStatus() == GemStatus.RETURNED_SUPPLIER) {
            throw ApiException.conflict(CANNOT_CORRECT, g.getCode() + " was returned to the supplier.");
        }
        if (corrections.existsByGemAndStatus(g, StockCorrection.Status.PENDING)) {
            throw ApiException.conflict(ALREADY_WAITING, g.getCode() + " already has a correction waiting for the owner.");
        }
        String from = current(g, f.kind());
        String to = target(f.kind(), f.to());
        // staff: a gem of their branch, or a gem found at their branch (wrong place)
        Long branchId = who.branchId() != null ? who.branchId() : g.getBranch().getId();
        if (who.branchId() != null && !who.branchId().equals(g.getBranch().getId())
                && !(f.kind() == Kind.BRANCH && branches.findByCode(to).map(Branch::getId).orElse(-1L).equals(who.branchId()))) {
            throw ApiException.forbidden(g.getCode() + " belongs to another branch.");
        }
        if (same(f.kind(), from, to)) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, NOTHING_TO_CHANGE, "The new value is the same as the current one.",
                    "to", "Same as now.");
        }
        String reason = f.reason().trim();
        StockCorrection c = corrections.save(new StockCorrection(numbers.next(Counter.STOCK_CORRECTION), g, f.kind(), from, to,
                reason, Instant.now(), em.getReference(UserAccount.class, who.id())));
        String change = change(c);
        Approval a = approvals.request(new Approvals.Request(ApprovalType.STOCK_CORRECTION, branchId,
                cut("Stock correction: " + g.getCode() + " " + change, 200), cut(reason, 1000), g.getCost(), c.getNumber()));
        c.askedIn(a.getNumber());
        gems.addHistory(g, "Correction requested", cut(c.getNumber() + " · " + change + " (" + reason + ")", 500));
        return view(c);
    }

    // ------------------------------------------------------------------ values

    static String current(Gem g, Kind k) {
        return switch (k) {
            case STATUS -> g.getStatus().name();
            case BRANCH -> g.getBranch().getCode();
            case CARAT -> g.getCarat().setScale(2, RoundingMode.HALF_UP).toPlainString();
        };
    }

    private String target(Kind k, String raw) {
        String v = raw.trim();
        return switch (k) {
            case STATUS -> {
                String s = v.toUpperCase(Locale.ROOT);
                if (!s.equals(GemStatus.WRITTEN_OFF.name()) && !s.equals(GemStatus.IN_STOCK.name())) {
                    throw bad("Choose Written off (lost / damaged) or In stock (found).");
                }
                yield s;
            }
            case BRANCH -> gems.branch(v).getCode();
            case CARAT -> {
                BigDecimal c;
                try {
                    c = new BigDecimal(v);
                } catch (NumberFormatException e) {
                    throw bad("Enter the carat weight, e.g. 5.01.");
                }
                if (c.signum() <= 0 || c.compareTo(new BigDecimal("9999.99")) > 0) {
                    throw bad("Enter a carat weight between 0.01 and 9999.99.");
                }
                yield c.setScale(2, RoundingMode.HALF_UP).toPlainString();
            }
        };
    }

    static boolean same(Kind k, String a, String b) {
        if (k == Kind.CARAT) {
            return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0;
        }
        return Objects.equals(a, b);
    }

    static String label(BranchRepository branches, Kind k, String v) {
        return switch (k) {
            case STATUS -> GemStatus.valueOf(v).label();
            case BRANCH -> branches.findByCode(v).map(Branch::getDisplayName).orElse(v);
            case CARAT -> v + " ct";
        };
    }

    private String change(StockCorrection c) {
        return change(branches, c);
    }

    static String change(BranchRepository branches, StockCorrection c) {
        String what = switch (c.getKind()) {
            case STATUS -> "status";
            case BRANCH -> "branch";
            case CARAT -> "carat";
        };
        return what + ": " + label(branches, c.getKind(), c.getFrom()) + " → " + label(branches, c.getKind(), c.getTo());
    }

    private CorrectionView view(StockCorrection c) {
        return new CorrectionView(c.getNumber(), c.getRequestedAt(), StockGems.view(c.getGem()), c.getKind(), c.getFrom(),
                c.getTo(), change(c), c.getReason(), Transfers.name(c.getRequestedBy()), c.getStatus(), c.getApprovalNo());
    }

    private static ApiException bad(String msg) {
        return ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, msg, "to", msg);
    }

    static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
