package lk.nuvi.gemshop.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.commissions.CommissionLine;
import lk.nuvi.gemshop.commissions.CommissionLineRepository;
import lk.nuvi.gemshop.commissions.Guide;
import lk.nuvi.gemshop.commissions.GuideRepository;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.dailycash.DayClosing;
import lk.nuvi.gemshop.dailycash.DayClosingRepository;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemRepository;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.gems.Gems;
import lk.nuvi.gemshop.purchasing.Purchase;
import lk.nuvi.gemshop.purchasing.PurchaseLine;
import lk.nuvi.gemshop.purchasing.PurchaseRepository;
import lk.nuvi.gemshop.purchasing.SupplierReturn;
import lk.nuvi.gemshop.purchasing.SupplierReturnRepository;
import lk.nuvi.gemshop.reports.api.ReportDtos.CashReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.CashReportRow;
import lk.nuvi.gemshop.reports.api.ReportDtos.CommissionPersonRow;
import lk.nuvi.gemshop.reports.api.ReportDtos.CommissionReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.DrawerAmounts;
import lk.nuvi.gemshop.reports.api.ReportDtos.ProfitReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.ProfitRow;
import lk.nuvi.gemshop.reports.api.ReportDtos.PurchaseReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.ReportOption;
import lk.nuvi.gemshop.reports.api.ReportDtos.ReportsSummary;
import lk.nuvi.gemshop.reports.api.ReportDtos.SalesBy;
import lk.nuvi.gemshop.reports.api.ReportDtos.SalesReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.SalesRow;
import lk.nuvi.gemshop.reports.api.ReportDtos.StockReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.StockReportRow;
import lk.nuvi.gemshop.reports.api.ReportDtos.SupplierReportRow;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.sales.SaleItem;
import lk.nuvi.gemshop.sales.SalePayment;
import lk.nuvi.gemshop.sales.SaleRepository;
import lk.nuvi.gemshop.settings.lists.ListName;
import lk.nuvi.gemshop.settings.lists.Lists;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reports (BRD L1–L6, prototype l-reports/*). Every report reads the branch at the top (All branches = the whole
 * shop) and a period; cancelled invoices and returned gems are left out. Amounts are LKR.
 * <ul>
 *   <li>Sales – grouped by day, month, branch, gem type, salesperson, guide, payment method or customer country.</li>
 *   <li>Profit – per gem sold: agreed price − cost − its share of the bill's commission (shared by price).</li>
 *   <li>Daily cash – every day closing, counted against expected, differences highlighted.</li>
 *   <li>Stock – gems still in the shop with photo, days in stock and value; slow-moving = in stock longer than N days.</li>
 *   <li>Commissions – per guide / staff member: earned, taken back, paid, not yet paid.</li>
 *   <li>Purchases by supplier – bought, how much of it is sold, returned to the supplier.</li>
 * </ul>
 */
@Service
public class Reports {

    public static final String WALK_IN = "walkin";
    static final List<String> PAY_TYPES = List.of("Card", "Cash LKR", "Cash USD", "Cash EUR", "Credit note", "Split");
    private static final BigDecimal HALF = new BigDecimal("0.50");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final SaleRepository sales;
    private final CommissionLineRepository commissions;
    private final GuideRepository guides;
    private final UserAccountRepository users;
    private final DayClosingRepository closings;
    private final GemRepository gems;
    private final Gems gemViews;
    private final PurchaseRepository purchases;
    private final SupplierReturnRepository supplierReturns;
    private final Lists lists;
    private final CurrentUser current;
    private final ZoneId zone;

    public Reports(SaleRepository sales, CommissionLineRepository commissions, GuideRepository guides, UserAccountRepository users,
            DayClosingRepository closings, GemRepository gems, Gems gemViews, PurchaseRepository purchases,
            SupplierReturnRepository supplierReturns, Lists lists, CurrentUser current, AppProperties app) {
        this.sales = sales;
        this.commissions = commissions;
        this.guides = guides;
        this.users = users;
        this.closings = closings;
        this.gems = gems;
        this.gemViews = gemViews;
        this.purchases = purchases;
        this.supplierReturns = supplierReturns;
        this.lists = lists;
        this.current = current;
        this.zone = ZoneId.of(app.timezone());
    }

    /** A period; empty dates = the last {@code days} days up to today. */
    public record Period(LocalDate from, LocalDate to) {}

    public Period period(LocalDate from, LocalDate to, int days) {
        LocalDate t = to == null ? today() : to;
        LocalDate f = from == null ? t.minusDays(days) : from;
        if (f.isAfter(t)) {
            throw ApiException.badRequest("BAD_PERIOD", "The From date is after the To date.");
        }
        if (ChronoUnit.DAYS.between(f, t) > 3700) {
            throw ApiException.badRequest("BAD_PERIOD", "Please choose at most 10 years.");
        }
        return new Period(f, t);
    }

    // ------------------------------------------------------------------ home

    @Transactional(readOnly = true)
    public ReportsSummary summary() {
        List<Sale> today = completed(new Period(today(), today()));
        return new ReportsSummary(sum(today, Sale::getTotal), today.size());
    }

    // ------------------------------------------------------------------ sales

    /** Filters of the sales report; null / blank = all. */
    public record SalesFilter(String gemType, Long staff, String guide, String pay, SalesBy by) {}

    private record Line(Sale sale, SaleItem item) {}

    @Transactional(readOnly = true)
    public SalesReport sales(Period p, SalesFilter f) {
        SalesBy by = f.by() == null ? SalesBy.DAY : f.by();
        if (!blank(f.pay()) && !PAY_TYPES.contains(f.pay())) {
            throw ApiException.badRequest("BAD_PAY", "Unknown payment type: " + f.pay());
        }
        List<Line> lines = new ArrayList<>();
        for (Sale s : completed(p)) {
            if (f.staff() != null && (s.getStaff() == null || s.getStaff().getId() != f.staff().longValue())) {
                continue;
            }
            if (!blank(f.guide()) && !guideMatches(s, f.guide())) {
                continue;
            }
            if (!blank(f.pay()) && !payType(s).equals(f.pay())) {
                continue;
            }
            for (SaleItem i : s.getItems()) {
                if (!i.isReleased() && (blank(f.gemType()) || f.gemType().equals(i.getGem().getGemType()))) {
                    lines.add(new Line(s, i));
                }
            }
        }
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, List<Line>> groups = new LinkedHashMap<>();
        for (Line l : lines) {
            String[] k = key(by, l.sale(), l.item());
            labels.putIfAbsent(k[0], k[1]);
            groups.computeIfAbsent(k[0], x -> new ArrayList<>()).add(l);
        }
        List<SalesRow> rows = new ArrayList<>(groups.entrySet().stream().map(e -> new SalesRow(e.getKey(), labels.get(e.getKey()),
                bills(e.getValue()), e.getValue().size(), sum(e.getValue(), l -> l.item().getAgreed()))).toList());
        rows.sort(by == SalesBy.DAY || by == SalesBy.MONTH ? Comparator.comparing(SalesRow::key).reversed()
                : Comparator.comparing(SalesRow::total).reversed().thenComparing(SalesRow::label));
        int bills = bills(lines);
        BigDecimal total = sum(lines, l -> l.item().getAgreed());
        BigDecimal avg = bills == 0 ? BigDecimal.ZERO.setScale(2) : total.divide(BigDecimal.valueOf(bills), 2, RoundingMode.HALF_UP);
        List<ReportOption> staff = users.findAll().stream()
                .filter(u -> u.getRole() == Role.OWNER || u.getRole() == Role.SALESPERSON)
                .sorted(Comparator.comparing(UserAccount::getName))
                .map(u -> new ReportOption(String.valueOf(u.getId()), u.getName())).toList();
        List<ReportOption> guideOptions = guides.findAll().stream().sorted(Comparator.comparing(Guide::getName))
                .map(g -> new ReportOption(g.getCode(), g.getName())).toList();
        return new SalesReport(p.from(), p.to(), by, rows, bills, lines.size(), total, avg, gemTypes(), staff, guideOptions, PAY_TYPES);
    }

    private String[] key(SalesBy by, Sale s, SaleItem i) {
        LocalDate d = s.getSoldAt().atZone(zone).toLocalDate();
        return switch (by) {
            case DAY -> new String[] {d.toString(), DMY.format(d)};
            case MONTH -> new String[] {d.toString().substring(0, 7), MONTH.format(d)};
            case BRANCH -> same(s.getBranch().getDisplayName());
            case GEM -> same(i.getGem().getGemType());
            case STAFF -> same(s.getStaff() == null ? "—" : s.getStaff().getName());
            case GUIDE -> same(s.getGuide() == null ? "Walk-in" : s.getGuide().getName());
            case PAY -> same(payType(s));
            case COUNTRY -> same(blank(s.getCustomer().getCountry()) ? "—" : s.getCustomer().getCountry());
        };
    }

    private static String[] same(String v) {
        return new String[] {v, v};
    }

    private static boolean guideMatches(Sale s, String guide) {
        return WALK_IN.equalsIgnoreCase(guide) ? s.getGuide() == null
                : s.getGuide() != null && s.getGuide().getCode().equalsIgnoreCase(guide.trim());
    }

    /** "Split" for more than one payment, otherwise Card / Cash LKR / Cash USD / Cash EUR / Credit note. */
    static String payType(Sale s) {
        List<SalePayment> ps = s.getPayments();
        if (ps.size() != 1) {
            return ps.isEmpty() ? "—" : "Split";
        }
        SalePayment p = ps.get(0);
        return switch (p.getMethod()) {
            case CARD -> "Card";
            case CREDIT -> "Credit note";
            case CASH -> "Cash " + p.getCurrency().name();
        };
    }

    private static int bills(List<Line> lines) {
        Set<String> n = new HashSet<>();
        lines.forEach(l -> n.add(l.sale().getNumber()));
        return n.size();
    }

    // ------------------------------------------------------------------ profit

    @Transactional(readOnly = true)
    public ProfitReport profit(Period p, String gemType) {
        List<Sale> sold = completed(p);
        Map<Long, BigDecimal> comm = new HashMap<>();
        if (!sold.isEmpty()) {
            for (CommissionLine l : commissions.findBySaleIdIn(sold.stream().map(Sale::getId).toList())) {
                comm.merge(l.getSale().getId(), l.getAmount(), BigDecimal::add);
            }
        }
        List<Sale> sorted = new ArrayList<>(sold);
        sorted.sort(Comparator.comparing(Sale::getSoldAt).reversed());
        List<SaleItem> picked = new ArrayList<>();
        Map<SaleItem, BigDecimal> share = new HashMap<>();
        for (Sale s : sorted) {
            List<SaleItem> active = s.getItems().stream().filter(i -> !i.isReleased()).toList();
            BigDecimal net = sum(active, SaleItem::getAgreed);
            BigDecimal c = comm.getOrDefault(s.getId(), BigDecimal.ZERO).max(BigDecimal.ZERO);
            for (SaleItem i : active) {
                if (blank(gemType) || gemType.equals(i.getGem().getGemType())) {
                    picked.add(i);
                    share.put(i, net.signum() == 0 ? BigDecimal.ZERO
                            : c.multiply(i.getAgreed()).divide(net, 2, RoundingMode.HALF_UP));
                }
            }
        }
        Map<String, String> photos = gemViews.firstPhotos(picked.stream().map(i -> i.getGem().getCode()).distinct().toList());
        List<ProfitRow> rows = picked.stream().map(i -> {
            Gem g = i.getGem();
            BigDecimal c = share.get(i);
            BigDecimal profit = i.getAgreed().subtract(g.getCost()).subtract(c);
            return new ProfitRow(g.getCode(), g.getName(), g.getGemType(), g.getProductType(), photos.get(g.getCode()),
                    i.getSale().getSoldAt(), i.getSale().getNumber(), i.getAgreed(), g.getCost(), c, profit,
                    margin(profit, i.getAgreed()), i.getMinPrice() != null && i.getAgreed().compareTo(i.getMinPrice()) < 0);
        }).toList();
        BigDecimal price = sum(rows, ProfitRow::price);
        BigDecimal profit = sum(rows, ProfitRow::profit);
        return new ProfitReport(p.from(), p.to(), rows, price, sum(rows, ProfitRow::cost), sum(rows, ProfitRow::commission), profit,
                margin(profit, price), gemTypes());
    }

    private static BigDecimal margin(BigDecimal profit, BigDecimal price) {
        return price.signum() == 0 ? BigDecimal.ZERO.setScale(1) : profit.multiply(HUNDRED).divide(price, 1, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------ daily cash

    @Transactional(readOnly = true)
    public CashReport dailyCash(Period p, boolean onlyDifferences) {
        List<CashReportRow> rows = closings.since(current.scope().branchId(), p.from()).stream()
                .filter(c -> !c.getDay().isAfter(p.to()))
                .map(this::cashRow)
                .filter(r -> !onlyDifferences || !r.balanced())
                .toList();
        return new CashReport(p.from(), p.to(), rows, rows.size(), (int) rows.stream().filter(r -> !r.balanced()).count(),
                sum(rows, CashReportRow::difference));
    }

    private CashReportRow cashRow(DayClosing c) {
        var e = c.expected();
        var n = c.counted();
        return new CashReportRow(c.getNumber(), c.getDay(), c.getBranch().getDisplayName(), c.getClosedBy().getName(), c.getClosedAt(),
                new DrawerAmounts(e.lkr(), e.usd(), e.eur(), c.getCardExpected()),
                new DrawerAmounts(n.lkr(), n.usd(), n.eur(), c.getCardCounted()),
                c.getDifference(), c.getDifference().abs().compareTo(HALF) < 0, c.getNote(), c.isSentToOwner());
    }

    // ------------------------------------------------------------------ stock

    @Transactional(readOnly = true)
    public StockReport stock(String gemType, boolean slow, int slowDays) {
        if (slowDays < 1 || slowDays > 3650) {
            throw ApiException.badRequest("BAD_DAYS", "Days must be between 1 and 3650.");
        }
        Long branch = current.scope().branchId();
        List<Gem> stock = branch == null ? gems.findByStatusInOrderByCodeAsc(GemStatus.STOCK)
                : gems.findByBranch_IdAndStatusInOrderByCodeAsc(branch, GemStatus.STOCK);
        LocalDate today = today();
        List<Gem> picked = stock.stream()
                .filter(g -> blank(gemType) || gemType.equals(g.getGemType()))
                .filter(g -> !slow || age(g, today) > slowDays)
                .sorted(Comparator.comparing(Gem::getAddedAt).thenComparing(Gem::getCode))
                .toList();
        Map<String, String> photos = gemViews.firstPhotos(picked.stream().map(Gem::getCode).toList());
        List<StockReportRow> rows = picked.stream().map(g -> new StockReportRow(g.getCode(), g.getName(), g.getGemType(),
                g.getProductType(), g.getCarat(), g.getBranch().getCode(), g.getStatus(), g.getAddedAt(), age(g, today), g.getCost(),
                g.getPrice(), photos.get(g.getCode()))).toList();
        long avg = rows.isEmpty() ? 0 : Math.round(rows.stream().mapToLong(StockReportRow::days).average().orElse(0));
        return new StockReport(slow, slowDays, rows, rows.size(), sum(rows, StockReportRow::cost), sum(rows, StockReportRow::price), avg,
                gemTypes());
    }

    private long age(Gem g, LocalDate today) {
        return Math.max(0, ChronoUnit.DAYS.between(g.getAddedAt().atZone(zone).toLocalDate(), today));
    }

    // ------------------------------------------------------------------ commissions

    /** Payee: null / blank = guides and staff, GUIDE or STAFF. */
    @Transactional(readOnly = true)
    public CommissionReport commissions(Period p, String payee) {
        CommissionLine.Payee only = null;
        if (!blank(payee)) {
            try {
                only = CommissionLine.Payee.valueOf(payee.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("BAD_PAYEE", "Show must be GUIDE or STAFF.");
            }
        }
        CommissionLine.Payee want = only;
        List<CommissionLine> ls = commissions.list(current.scope().branchId()).stream()
                .filter(l -> want == null || l.getPayee() == want)
                .filter(l -> inPeriod(l.getEarnedAt(), p))
                .toList();
        Map<String, List<CommissionLine>> by = new LinkedHashMap<>();
        ls.forEach(l -> by.computeIfAbsent(l.personKey(), k -> new ArrayList<>()).add(l));
        List<CommissionPersonRow> rows = new ArrayList<>(by.values().stream().map(this::person).toList());
        rows.sort(Comparator.comparing(CommissionPersonRow::earned).reversed().thenComparing(CommissionPersonRow::name));
        return new CommissionReport(p.from(), p.to(), rows, rows.stream().mapToInt(CommissionPersonRow::sales).sum(),
                sum(rows, CommissionPersonRow::salesAmount), sum(rows, CommissionPersonRow::earned), sum(rows, CommissionPersonRow::reversed),
                sum(rows, CommissionPersonRow::paid), sum(rows, CommissionPersonRow::unpaid));
    }

    private CommissionPersonRow person(List<CommissionLine> ls) {
        CommissionLine first = ls.get(0);
        List<CommissionLine> earned = ls.stream().filter(l -> l.getAmount().signum() > 0).toList();
        Map<String, BigDecimal> saleTotals = new LinkedHashMap<>();
        earned.forEach(l -> saleTotals.putIfAbsent(l.getSale().getNumber(), l.getSale().getTotal()));
        boolean guide = first.getPayee() == CommissionLine.Payee.GUIDE;
        String detail;
        if (guide) {
            Guide g = first.getGuide();
            detail = "Guide / driver" + (blank(g.getVehicle()) ? "" : " · " + g.getVehicle()) + " · "
                    + g.getPct().stripTrailingZeros().toPlainString() + "%";
        } else {
            detail = "Staff · fixed by price level";
        }
        return new CommissionPersonRow(first.getPayee().name(), guide ? first.getGuide().getCode() : String.valueOf(first.getStaff().getId()),
                first.personName(), detail, saleTotals.size(), saleTotals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add),
                sum(earned, CommissionLine::getAmount),
                sum(ls.stream().filter(l -> l.getAmount().signum() < 0).toList(), CommissionLine::getAmount),
                sum(ls.stream().filter(l -> !l.isOwed()).toList(), CommissionLine::getAmount),
                sum(ls.stream().filter(CommissionLine::isOwed).toList(), CommissionLine::getAmount));
    }

    // ------------------------------------------------------------------ purchases

    @Transactional(readOnly = true)
    public PurchaseReport purchases(Period p) {
        Long branch = current.scope().branchId();
        List<Purchase> ps = purchases.search(null, branch, p.from(), p.to());
        Map<String, List<Purchase>> bySupplier = new LinkedHashMap<>();
        ps.forEach(x -> bySupplier.computeIfAbsent(x.getSupplier().getCode(), k -> new ArrayList<>()).add(x));
        Map<String, BigDecimal> returned = new HashMap<>();
        for (SupplierReturn r : supplierReturns.findAllByOrderByReturnedAtDescIdDesc()) {
            if (inPeriod(r.getReturnedAt(), p) && (branch == null || r.getGem().getBranch().getId().equals(branch))) {
                returned.merge(r.getSupplier().getCode(), r.getRefund(), BigDecimal::add);
            }
        }
        List<SupplierReportRow> rows = new ArrayList<>();
        bySupplier.forEach((code, list) -> {
            var s = list.get(0).getSupplier();
            List<Gem> items = list.stream().flatMap(x -> x.getLines().stream()).map(PurchaseLine::getGem).toList();
            rows.add(new SupplierReportRow(code, s.getName(), s.getAddress(), list.size(), items.size(),
                    (int) items.stream().filter(g -> g.getStatus() == GemStatus.SOLD).count(), sum(list, Purchase::getTotal),
                    returned.getOrDefault(code, BigDecimal.ZERO),
                    list.stream().map(Purchase::getDate).max(Comparator.naturalOrder()).orElse(null)));
        });
        rows.sort(Comparator.comparing(SupplierReportRow::cost).reversed().thenComparing(SupplierReportRow::name));
        return new PurchaseReport(p.from(), p.to(), rows, rows.stream().mapToInt(SupplierReportRow::purchases).sum(),
                rows.stream().mapToInt(SupplierReportRow::products).sum(), rows.stream().mapToInt(SupplierReportRow::sold).sum(),
                sum(rows, SupplierReportRow::cost), sum(rows, SupplierReportRow::returned));
    }

    // ------------------------------------------------------------------ helpers

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    public ZoneId zone() {
        return zone;
    }

    /** Completed (not cancelled) sales of the branch at the top in the period. */
    private List<Sale> completed(Period p) {
        Instant from = p.from().atStartOfDay(zone).toInstant();
        Instant to = p.to().plusDays(1).atStartOfDay(zone).toInstant();
        return sales.search(current.scope().branchId(), from, to, Sale.Status.COMPLETED);
    }

    private boolean inPeriod(Instant at, Period p) {
        LocalDate d = at.atZone(zone).toLocalDate();
        return !d.isBefore(p.from()) && !d.isAfter(p.to());
    }

    private List<String> gemTypes() {
        return lists.values(ListName.GEM_TYPE);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> f) {
        return rows.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
