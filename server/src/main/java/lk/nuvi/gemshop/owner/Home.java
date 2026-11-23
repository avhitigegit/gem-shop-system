package lk.nuvi.gemshop.owner;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lk.nuvi.gemshop.audit.alert.Alerts;
import lk.nuvi.gemshop.audit.approval.Approvals;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.common.branch.BranchScope;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.customers.CustomerRepository;
import lk.nuvi.gemshop.dailycash.CashOpeningRepository;
import lk.nuvi.gemshop.dailycash.DailyCash;
import lk.nuvi.gemshop.dailycash.DayLock;
import lk.nuvi.gemshop.dailycash.Money3;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemRepository;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.gems.Gems;
import lk.nuvi.gemshop.owner.api.HomeDtos.DrawerNow;
import lk.nuvi.gemshop.owner.api.HomeDtos.HomeFigures;
import lk.nuvi.gemshop.owner.api.HomeDtos.PhoneView;
import lk.nuvi.gemshop.owner.api.HomeDtos.SoldGem;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.sales.SaleItem;
import lk.nuvi.gemshop.sales.SaleRepository;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.settings.branch.BranchStatus;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import lk.nuvi.gemshop.stock.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Home figures per role (prototype dashboard/index.html) and the owner's phone view (k-owner-control/owner-mobile.html,
 * BRD K1). Today's sales count COMPLETED sales; cash in the drawer is the expected cash of the day (P3-06), summed over the
 * trading branches on "All branches".
 */
@Service
public class Home {

    private final SaleRepository sales;
    private final GemRepository gems;
    private final Gems gemService;
    private final TransferRepository transfers;
    private final CustomerRepository customers;
    private final BranchRepository branches;
    private final ShopSettingsRepository shops;
    private final CashOpeningRepository openings;
    private final DailyCash cash;
    private final DayLock lock;
    private final Approvals approvals;
    private final Alerts alerts;
    private final CurrentUser current;
    private final ZoneId zone;

    public Home(SaleRepository sales, GemRepository gems, Gems gemService, TransferRepository transfers, CustomerRepository customers,
            BranchRepository branches, ShopSettingsRepository shops, CashOpeningRepository openings, DailyCash cash, DayLock lock,
            Approvals approvals, Alerts alerts, CurrentUser current, AppProperties app) {
        this.sales = sales;
        this.gems = gems;
        this.gemService = gemService;
        this.transfers = transfers;
        this.customers = customers;
        this.branches = branches;
        this.shops = shops;
        this.openings = openings;
        this.cash = cash;
        this.lock = lock;
        this.approvals = approvals;
        this.alerts = alerts;
        this.current = current;
        this.zone = ZoneId.of(app.timezone());
    }

    @Transactional(readOnly = true)
    public HomeFigures home() {
        Role role = current.require().role();
        BranchScope scope = current.scope();
        Long branch = scope.branchId();
        LocalDate today = LocalDate.now(zone);
        boolean money = role == Role.OWNER || role == Role.SALESPERSON;
        List<Sale> todays = money ? today(branch, today) : List.of();
        Integer licenceDays = null;
        LocalDate expiry = null;
        if (role == Role.OWNER) {
            ShopSettings s = shops.current().orElse(null);
            if (s != null && s.getNgjaLicenceExpiry() != null) {
                long days = ChronoUnit.DAYS.between(today, s.getNgjaLicenceExpiry());
                if (days <= s.getNgjaRemindDays()) {
                    licenceDays = (int) days;
                    expiry = s.getNgjaLicenceExpiry();
                }
            }
        }
        return new HomeFigures(today, money ? total(todays) : null, todays.size(), money ? drawer(branch, today) : null,
                approvals.waiting(scope).count(), stockCount(branch), transfers.onTheWayTo(branch), customers.count(),
                money && branch != null ? openings.findByBranchIdAndDay(branch, today).isPresent() : null,
                money && branch != null ? lock.isClosed(branch, today) : null, licenceDays, expiry,
                money ? sold(todays) : List.of());
    }

    /** Owner phone view for one branch ({@code code}) or all ({@code null} / "all"). */
    @Transactional(readOnly = true)
    public PhoneView phone(String code) {
        Long branch = null;
        if (code != null && !code.isBlank() && !CurrentUser.ALL.equalsIgnoreCase(code)) {
            branch = branches.findByCode(code).map(Branch::getId).orElseThrow(() -> ApiException.notFound("No branch " + code + "."));
        }
        BranchScope scope = branch == null ? BranchScope.all() : BranchScope.of(branch);
        LocalDate today = LocalDate.now(zone);
        List<Sale> todays = today(branch, today);
        BigDecimal profit = BigDecimal.ZERO;
        int items = 0;
        for (Sale s : todays) {
            BigDecimal cost = s.getItems().stream().map(i -> i.getGem().getCost()).reduce(BigDecimal.ZERO, BigDecimal::add);
            profit = profit.add(s.getTotal()).subtract(cost).subtract(s.getGuideComm()).subtract(s.getStaffComm());
            items += s.getItems().size();
        }
        List<Gem> stock = branch == null ? gems.findByStatusInOrderByCodeAsc(GemStatus.STOCK)
                : gems.findByBranch_IdAndStatusInOrderByCodeAsc(branch, GemStatus.STOCK);
        var waiting = approvals.waiting(scope);
        return new PhoneView(branch == null ? CurrentUser.ALL : code, today, total(todays), todays.size(), items, profit,
                drawer(branch, today), stock.size(), stock.stream().map(Gem::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add),
                stock.stream().map(Gem::getCost).reduce(BigDecimal.ZERO, BigDecimal::add), waiting.count(),
                waiting.latest().stream().limit(3).toList(), sold(todays), alerts.list(scope, null, false, 0, 5).items());
    }

    // ------------------------------------------------------------------ helpers

    private List<Sale> today(Long branch, LocalDate day) {
        Instant from = day.atStartOfDay(zone).toInstant();
        return sales.search(branch, from, day.plusDays(1).atStartOfDay(zone).toInstant(), Sale.Status.COMPLETED);
    }

    private static BigDecimal total(List<Sale> list) {
        return list.stream().map(Sale::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Expected cash now and card today: the branch, or every trading branch added up. */
    private DrawerNow drawer(Long branch, LocalDate day) {
        List<Long> ids = branch != null ? List.of(branch)
                : branches.findAll().stream().filter(b -> b.getStatus() == BranchStatus.ACTIVE).map(Branch::getId).toList();
        Money3 m = Money3.ZERO;
        BigDecimal card = BigDecimal.ZERO;
        for (Long id : ids) {
            m = m.plus(cash.expected(id, day));
            card = card.add(cash.cardExpected(id, day));
        }
        return new DrawerNow(m.lkr(), m.usd(), m.eur(), card);
    }

    private long stockCount(Long branch) {
        return branch == null ? gems.findByStatusInOrderByCodeAsc(GemStatus.STOCK).size()
                : gems.findByBranch_IdAndStatusInOrderByCodeAsc(branch, GemStatus.STOCK).size();
    }

    private List<SoldGem> sold(List<Sale> todays) {
        List<SoldGem> out = new ArrayList<>();
        Map<String, String> photos = gemService.firstPhotos(todays.stream().flatMap(s -> s.getItems().stream()).map(i -> i.getGem().getCode()).toList());
        for (Sale s : todays) {
            for (SaleItem i : s.getItems()) {
                Gem g = i.getGem();
                out.add(new SoldGem(s.getNumber(), s.getSoldAt(), g.getCode(), g.getName(), g.getGemType(), photos.get(g.getCode()), i.getAgreed(),
                        i.getAgreed().compareTo(i.getMinPrice()) < 0, s.getCustomer() == null ? null : s.getCustomer().getCountry(),
                        s.getStaff().getName(), s.getGuide() == null ? null : s.getGuide().getName()));
            }
        }
        return out;
    }
}
