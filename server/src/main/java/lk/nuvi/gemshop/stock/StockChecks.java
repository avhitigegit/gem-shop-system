package lk.nuvi.gemshop.stock;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lk.nuvi.gemshop.audit.alert.AlertType;
import lk.nuvi.gemshop.audit.alert.Alerts;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemRepository;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.gems.Gems;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.stock.api.StockDtos.CheckForm;
import lk.nuvi.gemshop.stock.api.StockDtos.CheckStart;
import lk.nuvi.gemshop.stock.api.StockDtos.CheckView;
import lk.nuvi.gemshop.stock.api.StockDtos.ScanResult;
import lk.nuvi.gemshop.stock.api.StockDtos.ScanView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock check of one branch (BRD D4): scan every tag; the system lists what is found, missing, in the wrong place or
 * unknown. Finishing saves the result, logs it and tells the owner. Missing gems are then reported as corrections.
 */
@Service
public class StockChecks {

    public static final String LINK = "/stock/check";

    /** Should be on the shelf of the branch (on the way to another branch is not). */
    static final Set<GemStatus> ON_SHELF = EnumSet.of(GemStatus.IN_STOCK, GemStatus.NOT_READY, GemStatus.RESERVED,
            GemStatus.ON_HOLD, GemStatus.RETURNED);

    private final StockCheckRepository checks;
    private final GemRepository gemRepo;
    private final Gems gems;
    private final StockGems stockGems;
    private final Numbers numbers;
    private final CurrentUser current;
    private final ActivityLog log;
    private final Alerts alerts;
    private final EntityManager em;

    public StockChecks(StockCheckRepository checks, GemRepository gemRepo, Gems gems, StockGems stockGems, Numbers numbers,
            CurrentUser current, ActivityLog log, Alerts alerts, EntityManager em) {
        this.checks = checks;
        this.gemRepo = gemRepo;
        this.gems = gems;
        this.stockGems = stockGems;
        this.numbers = numbers;
        this.current = current;
        this.log = log;
        this.alerts = alerts;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public CheckStart start(String branch) {
        Branch b = branch(branch);
        return new CheckStart(b.getCode(), b.getDisplayName(), expected(b).stream().map(StockGems::view).toList());
    }

    /** One scanned tag at a branch. */
    @Transactional(readOnly = true)
    public ScanView scan(String branch, String code) {
        Branch b = branch(branch);
        Gem g = stockGems.find(code);
        String text = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (g == null) {
            return new ScanView(text, ScanResult.UNKNOWN, null, "Not a gem tag of this shop.");
        }
        if (g.getBranch().getId().equals(b.getId()) && ON_SHELF.contains(g.getStatus())) {
            return new ScanView(g.getCode(), ScanResult.FOUND, StockGems.view(g), "");
        }
        return new ScanView(g.getCode(), ScanResult.WRONG_PLACE, StockGems.view(g), where(g));
    }

    @Transactional
    public CheckView finish(CheckForm f) {
        var who = current.require();
        Branch b = branch(f.branch());
        List<Gem> expected = expected(b);
        Set<String> scanned = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String s : f.scanned()) {
            if (s == null || s.isBlank()) {
                continue;
            }
            Gem g = stockGems.find(s);
            if (g == null) {
                String t = s.trim().toUpperCase(Locale.ROOT);
                if (!unknown.contains(t)) {
                    unknown.add(t.length() > 40 ? t.substring(0, 40) : t);
                }
            } else {
                scanned.add(g.getCode());
            }
        }
        Set<String> want = new LinkedHashSet<>();
        expected.forEach(g -> want.add(g.getCode()));
        List<String> missing = want.stream().filter(c -> !scanned.contains(c)).toList();
        List<String> wrong = scanned.stream().filter(c -> !want.contains(c)).toList();
        int found = (int) want.stream().filter(scanned::contains).count();
        StockCheck c = checks.save(new StockCheck(numbers.next(Counter.STOCK_CHECK), b, Instant.now(),
                em.getReference(UserAccount.class, who.id()), want.size(), found, missing, wrong, unknown,
                Transfers.blank(f.notes())));
        String result = c.getNumber() + " · " + found + "/" + want.size() + " found · missing "
                + (missing.isEmpty() ? "none" : String.join(", ", missing));
        log.add("Stock check finished", result + (wrong.isEmpty() ? "" : " · wrong place " + String.join(", ", wrong)), b.getId());
        alerts.raise(AlertType.STOCK, "Stock check " + c.getNumber() + " at " + b.getDisplayName() + ": " + missing.size()
                + " missing", LINK, b.getId());
        return view(c);
    }

    @Transactional(readOnly = true)
    public List<CheckView> previous() {
        return checks.forBranch(current.scope().branchId()).stream().limit(20).map(StockChecks::view).toList();
    }

    private List<Gem> expected(Branch b) {
        return gemRepo.findByBranch_IdAndStatusInOrderByCodeAsc(b.getId(), ON_SHELF);
    }

    private Branch branch(String code) {
        Branch b = gems.branch(code);
        current.checkBranch(b.getId());
        return b;
    }

    private static String where(Gem g) {
        return switch (g.getStatus()) {
            case SOLD, WRITTEN_OFF, RETURNED_SUPPLIER -> g.getStatus().label() + " – should not be on the shelf";
            case IN_TRANSIT -> "On the way to another branch";
            default -> "Should be at " + g.getBranch().getDisplayName() + " (" + g.getStatus().label() + ")";
        };
    }

    private static CheckView view(StockCheck c) {
        return new CheckView(c.getNumber(), c.getBranch().getCode(), c.getCheckedAt(), Transfers.name(c.getCheckedBy()),
                c.getExpected(), c.getFound(), c.getMissing(), c.getWrong(), c.getUnknown(), c.getNotes());
    }
}
