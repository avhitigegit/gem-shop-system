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
import lk.nuvi.gemshop.auth.security.CurrentUser.Who;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemRepository;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.gems.Gems;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.stock.api.StockDtos.ReceiveForm;
import lk.nuvi.gemshop.stock.api.StockDtos.StockGem;
import lk.nuvi.gemshop.stock.api.StockDtos.TransferForm;
import lk.nuvi.gemshop.stock.api.StockDtos.TransferView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Send / receive between branches (BRD D3, rule 9). Sending puts the gems "On the way"; they count at the other branch
 * only after it confirms every gem (ticked or scanned). A missing gem is reported as a stock correction.
 */
@Service
public class Transfers {

    public static final String LINK = "/stock/transfers";
    public static final String NOT_AVAILABLE = "NOT_AVAILABLE";
    public static final String NOT_ALL_TICKED = "NOT_ALL_TICKED";

    /** What can be sent: on the shelf and not promised to anyone. */
    static final Set<GemStatus> SENDABLE = EnumSet.of(GemStatus.IN_STOCK, GemStatus.RETURNED);

    private final TransferRepository transfers;
    private final GemRepository gemRepo;
    private final Gems gems;
    private final StockGems stockGems;
    private final Numbers numbers;
    private final CurrentUser current;
    private final ActivityLog log;
    private final Alerts alerts;
    private final EntityManager em;

    public Transfers(TransferRepository transfers, GemRepository gemRepo, Gems gems, StockGems stockGems, Numbers numbers,
            CurrentUser current, ActivityLog log, Alerts alerts, EntityManager em) {
        this.transfers = transfers;
        this.gemRepo = gemRepo;
        this.gems = gems;
        this.stockGems = stockGems;
        this.numbers = numbers;
        this.current = current;
        this.log = log;
        this.alerts = alerts;
        this.em = em;
    }

    /** Transfers from or to the branch in the top bar (staff: their branch), newest first. */
    @Transactional(readOnly = true)
    public List<TransferView> list() {
        Who who = current.require();
        return transfers.forBranch(current.scope().branchId()).stream().map(t -> view(t, who)).toList();
    }

    /** Gems that can be sent from a branch. */
    @Transactional(readOnly = true)
    public List<StockGem> available(String branch) {
        Branch b = gems.branch(branch);
        current.checkBranch(b.getId());
        return gemRepo.findByBranch_IdAndStatusInOrderByCodeAsc(b.getId(), SENDABLE).stream().map(StockGems::view).toList();
    }

    @Transactional
    public TransferView send(TransferForm f) {
        Who who = current.require();
        Branch from = gems.branch(f.from());
        current.checkBranch(from.getId());
        Branch to = gems.branch(f.to());
        if (from.getId().equals(to.getId())) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Choose the other branch.", "to",
                    "Must be another branch.");
        }
        Transfer t = new Transfer(numbers.next(Counter.TRANSFER), from, to, Instant.now(), user(who), blank(f.notes()));
        Set<String> seen = new LinkedHashSet<>();
        for (String code : f.gems()) {
            Gem g = stockGems.require(code);
            if (!seen.add(g.getCode())) {
                continue;
            }
            if (!g.getBranch().getId().equals(from.getId()) || !SENDABLE.contains(g.getStatus())) {
                throw ApiException.conflict(NOT_AVAILABLE, g.getCode() + " cannot be sent: it is "
                        + g.getStatus().label().toLowerCase(Locale.ROOT) + " at " + g.getBranch().getDisplayName() + ".");
            }
            g.setStatus(GemStatus.IN_TRANSIT);
            gems.addHistory(g, "Moved", "Sent " + from.getDisplayName() + " → " + to.getDisplayName() + " (" + t.getNumber() + ")");
            t.add(g);
        }
        transfers.save(t);
        int n = t.getItems().size();
        log.add("Transfer sent", t.getNumber() + " · " + n + " gem(s) " + from.getDisplayName() + " → " + to.getDisplayName(),
                from.getId());
        alerts.raise(AlertType.STOCK, "Transfer " + t.getNumber() + " sent: " + n + " gem(s) to " + to.getDisplayName(), LINK,
                to.getId());
        return view(t, who);
    }

    /** The receiving branch confirms: every gem must be ticked; they then count at that branch, in stock. */
    @Transactional
    public TransferView receive(String number, ReceiveForm f) {
        Who who = current.require();
        Transfer t = transfers.findByNumber(number == null ? "" : number.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("Transfer not found."));
        current.checkBranch(t.getTo().getId());
        if (t.getStatus() != Transfer.Status.IN_TRANSIT) {
            throw ApiException.conflict("ALREADY_RECEIVED", t.getNumber() + " was already received.");
        }
        Set<String> ticked = new LinkedHashSet<>();
        for (String c : f.gems()) {
            String n = Gems.normaliseCode(c);
            if (n != null) {
                ticked.add(n);
            }
        }
        List<String> missing = new ArrayList<>();
        for (TransferItem i : t.getItems()) {
            if (!ticked.contains(i.getGem().getCode())) {
                missing.add(i.getGem().getCode());
            }
        }
        if (!missing.isEmpty()) {
            throw ApiException.badRequest(NOT_ALL_TICKED, (t.getItems().size() - missing.size()) + " of " + t.getItems().size()
                    + " confirmed. Tick every gem first – a missing gem must be reported as a stock correction ("
                    + String.join(", ", missing) + ").");
        }
        t.received(Instant.now(), user(who));
        for (TransferItem i : t.getItems()) {
            Gem g = i.getGem();
            g.moveTo(t.getTo());
            g.setStatus(GemStatus.IN_STOCK);
            gems.addHistory(g, "Received", t.getNumber() + " at " + t.getTo().getDisplayName());
        }
        log.add("Transfer received", t.getNumber() + " · " + t.getItems().size() + " gem(s) at " + t.getTo().getDisplayName(),
                t.getTo().getId());
        alerts.raise(AlertType.STOCK, "Transfer " + t.getNumber() + " received at " + t.getTo().getDisplayName(), LINK,
                t.getTo().getId());
        return view(t, who);
    }

    @Transactional(readOnly = true)
    public long onTheWay() {
        return transfers.onTheWayTo(current.scope().branchId());
    }

    private TransferView view(Transfer t, Who who) {
        boolean canReceive = t.getStatus() == Transfer.Status.IN_TRANSIT
                && (who.branchId() == null || who.branchId().equals(t.getTo().getId()));
        return new TransferView(t.getNumber(), t.getFrom().getCode(), t.getTo().getCode(), t.getStatus(), t.getSentAt(),
                name(t.getSentBy()), t.getReceivedAt(), t.getReceivedBy() == null ? null : t.getReceivedBy().getName(), t.getNotes(),
                canReceive, t.getItems().stream().map(i -> StockGems.view(i.getGem())).toList());
    }

    private UserAccount user(Who who) {
        return em.getReference(UserAccount.class, who.id());
    }

    static String name(UserAccount u) {
        return u == null ? "System" : u.getName();
    }

    static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
