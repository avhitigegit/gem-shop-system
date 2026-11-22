package lk.nuvi.gemshop.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lk.nuvi.gemshop.audit.alert.AlertType;
import lk.nuvi.gemshop.audit.alert.Alerts;
import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalRepository;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.audit.approval.Approvals;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.CurrentUser.Who;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.commissions.Guide;
import lk.nuvi.gemshop.commissions.Guides;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import lk.nuvi.gemshop.common.money.Currency;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.customers.Customer;
import lk.nuvi.gemshop.customers.Customers;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.gems.GemPrints;
import lk.nuvi.gemshop.gems.Gems;
import lk.nuvi.gemshop.sales.api.SalesDtos.BillForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.BillLine;
import lk.nuvi.gemshop.sales.api.SalesDtos.BillView;
import lk.nuvi.gemshop.sales.api.SalesDtos.CancelRequest;
import lk.nuvi.gemshop.sales.api.SalesDtos.DocShop;
import lk.nuvi.gemshop.sales.api.SalesDtos.CustomerForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.CustomerPick;
import lk.nuvi.gemshop.sales.api.SalesDtos.GuidePick;
import lk.nuvi.gemshop.sales.api.SalesDtos.HeldView;
import lk.nuvi.gemshop.sales.api.SalesDtos.HoldForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.LineForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.PaymentForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.PaymentLine;
import lk.nuvi.gemshop.sales.api.SalesDtos.PosGem;
import lk.nuvi.gemshop.sales.api.SalesDtos.SaleForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.SaleLine;
import lk.nuvi.gemshop.sales.api.SalesDtos.SaleRow;
import lk.nuvi.gemshop.sales.api.SalesDtos.SaleView;
import lk.nuvi.gemshop.sales.api.SalesDtos.SellSummary;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.commission.Commission;
import lk.nuvi.gemshop.settings.rates.Rates;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * New sale (BRD F1–F6, G1–G2, H3; rules 2, 3, 5, 10). Gems are added by tag; each has an agreed price. Below the minimum
 * the salesperson sends the bill to the owner (held sale, gems "On hold"); the owner may sell below the minimum directly.
 * Completing the sale takes payment (cash LKR / USD / EUR, card, or a mix – same price), gives the invoice number, marks
 * every gem Sold for good, works out the commissions and tells the owner.
 */
@Service
public class Sales {

    public static final String ALREADY_SOLD = "ALREADY_SOLD";
    public static final String NOT_SELLABLE = "NOT_SELLABLE";
    public static final String NOT_HERE = "NOT_HERE";
    public static final String BELOW_MIN = "BELOW_MIN";
    public static final String NOT_BELOW_MIN = "NOT_BELOW_MIN";
    public static final String HELD_WAITING = "HELD_WAITING";
    public static final String HELD_CHANGED = "HELD_CHANGED";
    public static final String NOT_ENOUGH = "NOT_ENOUGH";
    public static final String TOO_MUCH_CARD = "TOO_MUCH_CARD";
    public static final String INVOICE_LINK = "/sell/invoices/";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SaleRepository sales;
    private final HeldSaleRepository held;
    private final Gems gems;
    private final Customers customers;
    private final Guides guides;
    private final Commission commission;
    private final Rates rates;
    private final ShopSettingsRepository shops;
    private final Numbers numbers;
    private final Approvals approvals;
    private final ApprovalRepository approvalRepo;
    private final ActivityLog log;
    private final Alerts alerts;
    private final CurrentUser current;
    private final EntityManager em;
    private final GemPrints prints;
    private final ZoneId zone;

    public Sales(SaleRepository sales, HeldSaleRepository held, Gems gems, Customers customers, Guides guides,
            Commission commission, Rates rates, ShopSettingsRepository shops, Numbers numbers, Approvals approvals,
            ApprovalRepository approvalRepo, ActivityLog log, Alerts alerts, CurrentUser current, EntityManager em,
            AppProperties app, GemPrints prints) {
        this.sales = sales;
        this.held = held;
        this.gems = gems;
        this.customers = customers;
        this.guides = guides;
        this.commission = commission;
        this.rates = rates;
        this.shops = shops;
        this.numbers = numbers;
        this.approvals = approvals;
        this.approvalRepo = approvalRepo;
        this.log = log;
        this.alerts = alerts;
        this.current = current;
        this.em = em;
        this.prints = prints;
        this.zone = ZoneId.of(app.timezone());
    }

    // ------------------------------------------------------------------ look-ups for the screen

    /** A scanned tag: the gem and whether it can go on the bill (rule 3: a sold gem never again). */
    @Transactional(readOnly = true)
    public PosGem scan(String code) {
        Gem g = gems.visible(code);
        return new PosGem(gems.card(g), g.getStatus().sellable(), g.getStatus().sellable() ? null : why(g));
    }

    @Transactional(readOnly = true)
    public List<CustomerPick> customerList() {
        Map<Long, Long> counts = purchases();
        return customers.all().stream().map(c -> pick(c, counts.getOrDefault(c.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public List<GuidePick> guideList() {
        return guides.active().stream().map(Sales::pick).toList();
    }

    /** Totals, commissions and which prices are below the minimum, while the bill is being made. */
    @Transactional(readOnly = true)
    public BillView check(BillForm f) {
        List<Line> lines = lines(f.items(), branchFor(f.branch(), f.items()), Set.of());
        Guide guide = guides.orWalkIn(f.guide());
        BigDecimal total = total(lines);
        BigDecimal pct = guide == null ? BigDecimal.ZERO : guide.getPct();
        List<Commission.Level> levels = commission.current().levels();
        return new BillView(lines.stream().map(l -> new BillLine(l.gem.getCode(), l.belowMin())).toList(),
                lines.stream().anyMatch(Line::belowMin), total, pct, Commission.guideAmount(total, pct),
                lines.isEmpty() ? BigDecimal.ZERO : commission.staffAmount(total),
                lines.isEmpty() ? 0 : Commission.levelIndex(levels, total) + 1);
    }

    // ------------------------------------------------------------------ below minimum: the owner is asked

    /** The salesperson's bill has a price below the minimum: hold it and ask the owner (rule 2). */
    @Transactional
    public HeldView hold(HoldForm f) {
        Who w = current.require();
        if (w.role() == Role.OWNER) {
            throw ApiException.badRequest(NOT_BELOW_MIN, "The owner can sell below the minimum directly – take payment.");
        }
        Branch branch = branchFor(f.branch(), f.items());
        List<Line> lines = lines(f.items(), branch, Set.of());
        List<Line> below = lines.stream().filter(Line::belowMin).toList();
        if (below.isEmpty()) {
            throw ApiException.badRequest(NOT_BELOW_MIN, "Every price is at or above the minimum – take payment.");
        }
        Instant now = Instant.now();
        Customer customer = customer(f.customer(), now);
        Guide guide = guides.orWalkIn(f.guide());
        HeldSale h = new HeldSale(numbers.next(Counter.HELD_SALE), now, branch, user(w), customer, guide);
        lines.forEach(l -> h.add(l.gem, l.gem.getPrice(), l.agreed, l.gem.getMinPrice()));
        held.save(h);

        StringBuilder title = new StringBuilder("Below minimum: ");
        StringBuilder detail = new StringBuilder();
        for (Line l : below) {
            title.append(title.length() > 15 ? ", " : "").append(l.gem.getName()).append(" (").append(l.gem.getCode()).append(')');
            detail.append(detail.isEmpty() ? "" : " | ").append(l.gem.getCode()).append(": agreed ").append(lkr(l.agreed))
                    .append(" · minimum ").append(lkr(l.gem.getMinPrice())).append(" · list ").append(lkr(l.gem.getPrice()));
        }
        detail.append(" · Customer ").append(customer.getName()).append(" (").append(customer.getCountry()).append(')')
                .append(guide == null ? ", walk-in" : ", guide " + guide.getName());
        Approval a = approvals.request(new Approvals.Request(ApprovalType.BELOW_MIN, branch.getId(), cut(title.toString(), 200),
                cut(detail.toString(), 1000), total(lines), h.getNumber()));
        h.askedIn(a.getNumber());
        for (Line l : lines) {
            l.gem.setStatus(GemStatus.ON_HOLD);
            gems.addHistory(l.gem, "On hold", "Below-minimum price " + lkr(l.agreed) + " – waiting for owner approval ("
                    + a.getNumber() + ")");
        }
        return view(h, a);
    }

    /** Held sales waiting for the owner or approved (Sell home, New sale). */
    @Transactional(readOnly = true)
    public List<HeldView> heldList() {
        return held.inStates(current.scope().branchId(), List.of(HeldSale.Status.PENDING, HeldSale.Status.APPROVED)).stream()
                .map(h -> view(h, approval(h))).toList();
    }

    /** One held sale – the waiting screen asks every few seconds. */
    @Transactional(readOnly = true)
    public HeldView held(String number) {
        HeldSale h = requireHeld(number);
        return view(h, approval(h));
    }

    /** Start a new bill instead: the held gems go back in stock. */
    @Transactional
    public HeldView cancelHeld(String number) {
        HeldSale h = requireHeld(number);
        if (h.getStatus() != HeldSale.Status.PENDING && h.getStatus() != HeldSale.Status.APPROVED) {
            throw ApiException.conflict("NOT_OPEN", h.getNumber() + " is already " + h.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
        h.setStatus(HeldSale.Status.CANCELLED);
        for (HeldSaleItem i : h.getItems()) {
            if (i.getGem().getStatus() == GemStatus.ON_HOLD) {
                i.getGem().setStatus(GemStatus.IN_STOCK);
                gems.addHistory(i.getGem(), "Back in stock", "Held sale " + h.getNumber() + " was not finished");
            }
        }
        log.add("Held sale cancelled", h.getNumber(), h.getBranch().getId());
        return view(h, approval(h));
    }

    // ------------------------------------------------------------------ complete the sale

    @Transactional
    public SaleView complete(SaleForm f) {
        Who w = current.require();
        Instant now = Instant.now();
        HeldSale h = f.held() == null || f.held().isBlank() ? null : requireHeld(f.held());
        Branch branch;
        List<Line> lines;
        Customer customer;
        Guide guide;
        if (h != null) {
            switch (h.getStatus()) {
                case APPROVED -> { }
                case PENDING -> throw ApiException.conflict(HELD_WAITING, "Still waiting for the owner's approval.");
                case REJECTED -> throw ApiException.conflict(HELD_CHANGED,
                        "The owner rejected the price. Agree a new price at or above the minimum.");
                default -> throw ApiException.conflict(HELD_CHANGED, h.getNumber() + " is already "
                        + h.getStatus().name().toLowerCase(Locale.ROOT) + ".");
            }
            branch = h.getBranch();
            Set<Long> onHold = new HashSet<>();
            h.getItems().forEach(i -> onHold.add(i.getGem().getId()));
            lines = lines(f.items(), branch, onHold);
            sameAsHeld(h, lines);
            customer = h.getCustomer();
            guide = h.getGuide();
        } else {
            branch = branchFor(f.branch(), f.items());
            lines = lines(f.items(), branch, Set.of());
            List<Line> below = lines.stream().filter(Line::belowMin).toList();
            if (!below.isEmpty() && w.role() != Role.OWNER) {
                throw ApiException.conflict(BELOW_MIN, "A price is below the minimum – send the bill to the owner for approval.");
            }
            customer = customer(f.customer(), now);
            guide = guides.orWalkIn(f.guide());
            if (!below.isEmpty()) {
                log.add("Below-minimum sale by owner", String.join(", ", below.stream()
                        .map(l -> l.gem.getCode() + " at " + lkr(l.agreed)).toList()), branch.getId());
            }
        }

        BigDecimal total = total(lines);
        BigDecimal pct = guide == null ? BigDecimal.ZERO : guide.getPct();
        Rates.Today today = rates.today();
        BigDecimal cardPct = shops.current().map(ShopSettings::getCardFeePct).orElse(new BigDecimal("3.00"));
        List<Paid> paid = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        int n = 0;
        for (PaymentForm p : f.payments()) {
            n++;
            if (p.method() == SalePayment.Method.CARD && p.currency() != Currency.LKR) {
                throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Card payments are in LKR.",
                        "payments[" + (n - 1) + "].currency", "LKR only");
            }
            BigDecimal amount = p.amount().setScale(2, RoundingMode.HALF_UP);
            BigDecimal rate = today.of(p.currency());
            BigDecimal lkr = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal fee = p.method() == SalePayment.Method.CARD
                    ? lkr.multiply(cardPct).divide(HUNDRED, 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            paid.add(new Paid(p.method(), p.currency(), amount, rate, lkr,
                    p.method() == SalePayment.Method.CARD ? cardPct : BigDecimal.ZERO, fee));
            sum = sum.add(lkr);
            fees = fees.add(fee);
            if (p.method() == SalePayment.Method.CASH) {
                cash = cash.add(lkr);
            }
        }
        if (sum.compareTo(total) < 0) {
            throw ApiException.badRequest(NOT_ENOUGH, "Paid " + lkr(sum) + " – " + lkr(total.subtract(sum)) + " still to pay.");
        }
        BigDecimal change = sum.subtract(total);
        if (change.compareTo(cash) > 0) {
            throw ApiException.badRequest(TOO_MUCH_CARD, "The card amount is more than the amount to pay.");
        }

        Sale.Money money = new Sale.Money(total, pct, Commission.guideAmount(total, pct), commission.staffAmount(total), fees,
                change, today.usd(), today.eur());
        String no = numbers.next(Counter.INVOICE);
        Sale s = new Sale(no, now, branch, customer, guide, user(w), money, h == null ? null : h.getNumber(), blank(f.notes()));
        for (Line l : lines) {
            s.add(l.gem, l.gem.getPrice(), l.agreed, l.gem.getMinPrice(), h == null ? null : h.getApprovalNo());
        }
        paid.forEach(p -> s.pay(p.method, p.currency, p.amount, p.rate, p.lkr, p.feePct, p.fee));
        sales.save(s);
        for (Line l : lines) {
            l.gem.sold(no, now);
            gems.addHistory(l.gem, "Sold", "Invoice " + no + " · " + lkr(l.agreed));
        }
        if (h != null) {
            h.completed(no);
        }
        log.add("Sale completed", no + " · " + customer.getName() + " · " + lkr(total), branch.getId());
        alerts.raise(AlertType.SALE, "Sale " + no + " · " + lkr(total) + " · " + customer.getCountry(), INVOICE_LINK + no,
                branch.getId());
        return view(s);
    }

    @Transactional(readOnly = true)
    public SaleView sale(String number) {
        Sale s = sales.findByNumber(number == null ? "" : number.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("No invoice " + number + "."));
        current.checkBranch(s.getBranch().getId());
        return view(s);
    }

    /** Sell home: sales today, held sales, the 6 latest invoices. */
    @Transactional(readOnly = true)
    public SellSummary summary() {
        Long branch = current.scope().branchId();
        Instant from = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        List<Sale> today = sales.since(branch, from);
        BigDecimal total = today.stream().map(Sale::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<SaleRow> latest = sales.latest(branch, PageRequest.of(0, 6)).stream().map(Sales::row).toList();
        return new SellSummary(today.size(), total, heldList(), latest);
    }

    // ------------------------------------------------------------------ checks

    private record Line(Gem gem, BigDecimal agreed) {
        boolean belowMin() {
            return gem.getMinPrice() != null && agreed.compareTo(gem.getMinPrice()) < 0;
        }
    }

    private record Paid(SalePayment.Method method, Currency currency, BigDecimal amount, BigDecimal rate, BigDecimal lkr,
            BigDecimal feePct, BigDecimal fee) {}

    /** The selling branch: the one asked, else the salesperson's, else the first gem's. */
    private Branch branchFor(String code, List<LineForm> items) {
        Who w = current.require();
        Branch b;
        if (code != null && !code.isBlank() && !CurrentUser.ALL.equalsIgnoreCase(code)) {
            b = gems.branch(code);
        } else if (w.branchId() != null) {
            b = em.getReference(Branch.class, w.branchId());
        } else if (!items.isEmpty()) {
            b = gems.visible(items.get(0).gem()).getBranch();
        } else {
            return null;
        }
        current.checkBranch(b.getId());
        return b;
    }

    /** Each gem once, at this branch, and sellable (or on hold for this held sale). */
    private List<Line> lines(List<LineForm> items, Branch branch, Set<Long> onHoldOk) {
        List<Line> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int i = 0;
        for (LineForm f : items) {
            Gem g = gems.visible(f.gem());
            if (!seen.add(g.getCode())) {
                throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, g.getCode() + " is on the bill twice.",
                        "items[" + i + "].gem", "Twice on the bill");
            }
            boolean held = onHoldOk.contains(g.getId()) && g.getStatus() == GemStatus.ON_HOLD;
            if (!held && !g.getStatus().sellable()) {
                throw ApiException.conflict(g.getStatus() == GemStatus.SOLD ? ALREADY_SOLD : NOT_SELLABLE,
                        g.getCode() + ": " + why(g));
            }
            if (branch != null && !g.getBranch().getId().equals(branch.getId())) {
                throw ApiException.conflict(NOT_HERE, g.getCode() + " is at " + g.getBranch().getDisplayName() + ", not here.");
            }
            out.add(new Line(g, f.agreed().setScale(2, RoundingMode.HALF_UP)));
            i++;
        }
        return out;
    }

    /** Finishing a held sale: the same gems at the same prices as the owner approved. */
    private static void sameAsHeld(HeldSale h, List<Line> lines) {
        Map<String, BigDecimal> approved = new HashMap<>();
        h.getItems().forEach(i -> approved.put(i.getGem().getCode(), i.getAgreed()));
        boolean same = approved.size() == lines.size() && lines.stream()
                .allMatch(l -> approved.containsKey(l.gem.getCode()) && approved.get(l.gem.getCode()).compareTo(l.agreed) == 0);
        if (!same) {
            throw ApiException.conflict(HELD_CHANGED, "The bill is not the one the owner approved (" + h.getNumber()
                    + "). Use the same gems and prices, or start a new bill.");
        }
    }

    private Customer customer(CustomerForm f, Instant now) {
        if (f.code() != null && !f.code().isBlank()) {
            return customers.require(f.code());
        }
        return customers.add(new Customer.Details(f.name(), f.country(), f.email(), f.whatsapp(), f.birthday(),
                Boolean.TRUE.equals(f.consent())), now);
    }

    private HeldSale requireHeld(String number) {
        HeldSale h = held.findByNumber(number == null ? "" : number.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("No held sale " + number + "."));
        current.checkBranch(h.getBranch().getId());
        return h;
    }

    private Approval approval(HeldSale h) {
        return h.getApprovalNo() == null ? null : approvalRepo.findByNumber(h.getApprovalNo()).orElse(null);
    }

    /** Why a gem cannot go on the bill. */
    private String why(Gem g) {
        return switch (g.getStatus()) {
            case SOLD -> "already sold" + (g.getSoldAt() == null ? "" : " on " + DMY.format(g.getSoldAt().atZone(zone)))
                    + (g.getSaleNo() == null ? "" : " (" + g.getSaleNo() + ")") + " – it cannot be sold again.";
            case RESERVED -> "reserved for a customer. Open it from Reserved & quotes to sell.";
            case ON_HOLD -> "on hold – a below-minimum price is waiting for the owner.";
            case NOT_READY -> "not ready to sell yet.";
            case IN_TRANSIT -> "on the way to the other branch.";
            default -> g.getStatus().label().toLowerCase(Locale.ROOT) + " – not in stock.";
        };
    }

    private Map<Long, Long> purchases() {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] r : sales.countPerCustomer()) {
            m.put((Long) r[0], (Long) r[1]);
        }
        return m;
    }

    // ------------------------------------------------------------------ views

    SaleView view(Sale s) {
        Role role = CurrentUser.role().orElse(null);
        boolean money = role == Role.OWNER || role == Role.ACCOUNTANT;
        BigDecimal paid = s.getPayments().stream().map(SalePayment::getLkr).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SaleView(s.getNumber(), s.getSoldAt(), s.getBranch().getCode(), s.getStatus(),
                pick(s.getCustomer(), purchases().getOrDefault(s.getCustomer().getId(), 0L)),
                s.getGuide() == null ? null : pick(s.getGuide()), name(s.getStaff()),
                s.getItems().stream().map(i -> line(i.getGem(), i.getListPrice(), i.getAgreed(), i.getApprovalNo(), i.isReleased()))
                        .toList(),
                s.getPayments().stream().map(p -> new PaymentLine(p.getMethod(), p.getCurrency(), p.getAmount(), p.getRate(),
                        p.getLkr())).toList(),
                s.getTotal(), paid, s.getChangeDue(), s.getGuidePct(), s.getGuideComm(), s.getStaffComm(),
                money ? s.getCardFee() : null, s.getHeldNo(), s.isSentEmail(), s.isSentWhatsapp(), s.getNotes(),
                s.getRateUsd(), s.getRateEur(), s.getCancelledAt(), s.getCancelledBy(), s.getCancelReason(), cancelRequest(s),
                docShop());
    }

    /** Shop details for the invoice header and footer. */
    private DocShop docShop() {
        ShopSettings sh = shops.current().orElse(null);
        if (sh == null) {
            return new DocShop("Gem Shop", null, null, null, null, null, null, 7, null);
        }
        boolean licence = sh.isPrintLicenceOnInvoice();
        return new DocShop(sh.getName(), sh.getTagline(), sh.getAddress(), sh.getPhone(), sh.getEmail(),
                licence ? sh.getNgjaLicenceNo() : null, licence ? sh.getNgjaLicenceExpiry() : null, sh.getReturnPolicyDays(),
                prints.logoUrl(sh));
    }

    /** The latest cancellation request of an invoice and the owner's answer. */
    CancelRequest cancelRequest(Sale s) {
        Approval a = s.getCancelApprovalNo() == null ? null : approvalRepo.findByNumber(s.getCancelApprovalNo()).orElse(null);
        if (a == null) {
            return null;
        }
        return new CancelRequest(a.getNumber(), a.getStatus(), s.getCancelReason() == null ? a.getDetail() : s.getCancelReason(),
                name(a.getRequestedBy()), a.getRequestedAt(), a.isPending() ? null : a.getNote());
    }

    private HeldView view(HeldSale h, Approval a) {
        boolean decided = a != null && !a.isPending();
        return new HeldView(h.getNumber(), h.getHeldAt(), h.getBranch().getCode(), h.getStatus(), h.getApprovalNo(),
                decided ? a.getNote() : null, decided ? name(a.getDecidedBy()) : null, decided ? a.getDecidedAt() : null,
                name(h.getStaff()), pick(h.getCustomer(), 0L), h.getGuide() == null ? null : pick(h.getGuide()),
                h.getItems().stream().map(i -> line(i.getGem(), i.getListPrice(), i.getAgreed(), null, false)).toList(),
                h.getItems().stream().map(HeldSaleItem::getAgreed).reduce(BigDecimal.ZERO, BigDecimal::add), h.getSaleNo());
    }

    private static SaleRow row(Sale s) {
        Gem first = s.getItems().isEmpty() ? null : s.getItems().get(0).getGem();
        return new SaleRow(s.getNumber(), s.getSoldAt(), s.getCustomer().getName(), s.getCustomer().getCountry(),
                s.getItems().size(), first == null ? "" : first.getGemType(), first == null ? "" : first.getProductType(),
                s.getTotal(), s.getStatus());
    }

    private SaleLine line(Gem g, BigDecimal list, BigDecimal agreed, String approvalNo, boolean released) {
        return new SaleLine(g.getCode(), g.getName(), g.getGemType(), g.getProductType(), g.getCarat(), list, agreed, approvalNo,
                g.getColour(), g.getShape(), g.getTreatment(), g.getCertType(), g.getCertLab(), prints.certNo(g), released,
                gems.card(g).photo());
    }

    static CustomerPick pick(Customer c, long purchases) {
        return new CustomerPick(c.getCode(), c.getName(), c.getCountry(), c.getEmail(), c.getWhatsapp(), c.getBirthday(),
                c.isConsent() && !c.isUnsubscribed(), purchases);
    }

    static GuidePick pick(Guide g) {
        return new GuidePick(g.getCode(), g.getName(), g.getVehicle(), g.getVehicleNo(), g.getPhone(), g.getPct());
    }

    private static BigDecimal total(List<Line> lines) {
        return lines.stream().map(Line::agreed).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private UserAccount user(Who w) {
        return em.getReference(UserAccount.class, w.id());
    }

    static String name(UserAccount u) {
        return u == null ? "—" : u.getName();
    }

    /** "LKR 448,000.00". */
    public static String lkr(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return "LKR " + f.format(v == null ? BigDecimal.ZERO : v);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
