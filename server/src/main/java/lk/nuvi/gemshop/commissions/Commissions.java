package lk.nuvi.gemshop.commissions;

import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import java.awt.Color;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lk.nuvi.gemshop.accountant.MonthLock;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.CurrentUser.Who;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.CommissionRow;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.CommissionSaleList;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.CommissionSaleRow;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.CommissionSlip;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.CommissionSlipLine;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.OwedPerson;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.PaidView;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.PayForm;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.Payable;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.PayableShow;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.PayeeOption;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.SaleCommission;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.StaffLevel;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.dailycash.DayLock;
import lk.nuvi.gemshop.files.pdf.PdfBody;
import lk.nuvi.gemshop.files.pdf.PdfDocuments;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.sales.SaleRepository;
import lk.nuvi.gemshop.sales.Sales;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.commission.Commission;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commission per sale, paying guides / drivers and staff, and the commission slip (BRD H1–H5, I2; rule 5). Every sale keeps
 * the commission worked out when it was made ({@link CommissionBook}). A line is owed until it is paid; a "taken back"
 * line (return / cancelled invoice) is taken off the person's next payment.
 */
@Service
public class Commissions {

    public static final String NOTHING_TO_PAY = "NOTHING_TO_PAY";
    public static final String ALREADY_PAID = "COMMISSION_ALREADY_PAID";
    public static final String ONE_BRANCH = "ONE_BRANCH_FOR_DRAWER";
    public static final String WALK_IN = "walkin";

    private static final Instant EARLIEST = Instant.parse("2000-01-01T00:00:00Z");

    private final CommissionLineRepository lines;
    private final CommissionPaymentRepository payments;
    private final SaleRepository sales;
    private final GuideRepository guideRepo;
    private final Guides guides;
    private final UserAccountRepository users;
    private final Commission commission;
    private final Sales sell;
    private final Numbers numbers;
    private final PdfDocuments pdfs;
    private final DayLock lock;
    private final ActivityLog log;
    private final CurrentUser current;
    private final ZoneId zone;
    private final MonthLock monthLock;

    public Commissions(CommissionLineRepository lines, CommissionPaymentRepository payments, SaleRepository sales, GuideRepository guideRepo,
            Guides guides, UserAccountRepository users, Commission commission, Sales sell, Numbers numbers, PdfDocuments pdfs,
            DayLock lock, ActivityLog log, CurrentUser current, MonthLock monthLock, AppProperties app) {
        this.lines = lines;
        this.payments = payments;
        this.sales = sales;
        this.guideRepo = guideRepo;
        this.guides = guides;
        this.users = users;
        this.commission = commission;
        this.sell = sell;
        this.numbers = numbers;
        this.pdfs = pdfs;
        this.lock = lock;
        this.log = log;
        this.current = current;
        this.zone = ZoneId.of(app.timezone());
        this.monthLock = monthLock;
    }

    // ------------------------------------------------------------------ commission per sale

    /** Completed sales with their commission. Guide: a code, {@value #WALK_IN} or empty (all). Staff: a user id or empty. */
    @Transactional(readOnly = true)
    public CommissionSaleList sales(LocalDate from, LocalDate to, String guide, Long staff) {
        Instant f = from == null ? EARLIEST : from.atStartOfDay(zone).toInstant();
        Instant t = to == null ? Instant.now().plusSeconds(86_400) : to.plusDays(1).atStartOfDay(zone).toInstant();
        String g = guide == null || guide.isBlank() ? null : guide.trim();
        List<CommissionSaleRow> rows = sales.search(current.scope().branchId(), f, t, Sale.Status.COMPLETED).stream()
                .filter(s -> g == null || (WALK_IN.equalsIgnoreCase(g) ? s.getGuide() == null
                        : s.getGuide() != null && s.getGuide().getCode().equalsIgnoreCase(g)))
                .filter(s -> staff == null || (s.getStaff() != null && s.getStaff().getId().equals(staff)))
                .map(s -> new CommissionSaleRow(s.getNumber(), s.getSoldAt(), s.getCustomer().getName(),
                        s.getGuide() == null ? null : s.getGuide().getName(), name(s.getStaff()), s.getTotal(),
                        s.getGuideComm().add(s.getStaffComm())))
                .toList();
        return new CommissionSaleList(rows, guideOptions(), staffOptions());
    }

    @Transactional(readOnly = true)
    public SaleCommission sale(String number) {
        Sale s = sales.findByNumber(number == null ? "" : number.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("No invoice " + number + "."));
        current.checkBranch(s.getBranch().getId());
        List<Commission.Level> levels = commission.current().levels();
        List<CommissionRow> ls = lines.findBySaleIdOrderByIdAsc(s.getId()).stream().map(l -> Rows.row(l, true)).toList();
        return new SaleCommission(s.getNumber(), s.getSoldAt(), s.getBranch().getCode(), s.getCustomer().getName(), name(s.getStaff()),
                s.getTotal(), s.getGuide() == null ? null : s.getGuide().getName(), s.getGuide() == null ? null : s.getGuide().getVehicle(),
                s.getGuidePct(), s.getGuideComm(), s.getStaffComm(), Commission.levelIndex(levels, s.getTotal()),
                levels.stream().map(l -> new StaffLevel(l.upTo(), l.amount())).toList(), ls);
    }

    // ------------------------------------------------------------------ pay guides & drivers (and staff)

    @Transactional(readOnly = true)
    public Payable payable(PayableShow show, String person) {
        List<CommissionLine> all = lines.list(current.scope().branchId());
        List<CommissionLine> owed = all.stream().filter(CommissionLine::isOwed).toList();
        Map<String, List<CommissionLine>> by = owed.stream()
                .collect(Collectors.groupingBy(CommissionLine::personKey, LinkedHashMap::new, Collectors.toList()));
        List<OwedPerson> people = by.values().stream()
                .map(ls -> new OwedPerson(ls.get(0).personKey(), ls.get(0).personName(), ls.get(0).getPayee(), ls.size(), Rows.sum(ls)))
                .sorted(Comparator.comparing(OwedPerson::amount).reversed()).toList();
        List<CommissionLine> guideOwed = owed.stream().filter(l -> l.getPayee() == CommissionLine.Payee.GUIDE).toList();
        PayableShow sh = show == null ? PayableShow.OWED : show;
        String p = person == null || person.isBlank() ? null : person.trim();
        List<CommissionRow> rows = all.stream()
                .filter(l -> switch (sh) {
                    case OWED -> l.isOwed();
                    case PAID -> l.getStatus() == CommissionLine.Status.PAID;
                    case REVERSED -> l.getStatus() == CommissionLine.Status.REVERSED;
                    case ALL -> true;
                })
                .filter(l -> p == null || l.personKey().equalsIgnoreCase(p))
                .map(l -> Rows.row(l, true)).toList();
        List<PayeeOption> options = new ArrayList<>(guideOptions());
        options.addAll(staffOptions());
        return new Payable(Rows.sum(owed), owed.size(), Rows.sum(guideOwed),
                (int) guideOwed.stream().map(CommissionLine::personKey).distinct().count(),
                Rows.sum(owed.stream().filter(l -> l.getPayee() == CommissionLine.Payee.STAFF).toList()), people, rows, options);
    }

    /** Records a payment for the chosen lines (BRD H4). "Cash (drawer)" goes into that day's cash closing (I2, P3-06). */
    @Transactional
    public PaidView pay(PayForm f) {
        Who w = current.require();
        LocalDate today = LocalDate.now(zone);
        if (f.paidOn().isAfter(today)) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "The payment date cannot be in the future.",
                    "paidOn", "Today or earlier.");
        }
        monthLock.checkOpen(f.paidOn());
        List<CommissionLine> chosen = new ArrayList<>(lines.findAllById(new LinkedHashSet<>(f.lines())));
        if (chosen.size() != new LinkedHashSet<>(f.lines()).size()) {
            throw ApiException.notFound("Some commission lines were not found – reload the page.");
        }
        Set<Long> branchIds = new LinkedHashSet<>();
        for (CommissionLine l : chosen) {
            current.checkBranch(l.getBranch().getId());
            if (!l.isOwed()) {
                throw ApiException.conflict(ALREADY_PAID, "Invoice " + l.getSale().getNumber() + " (" + l.personName()
                        + ") was already paid – reload the page.");
            }
            branchIds.add(l.getBranch().getId());
        }
        chosen.sort(Comparator.comparing(CommissionLine::getEarnedAt));
        BigDecimal total = Rows.sum(chosen);
        if (total.signum() <= 0) {
            throw ApiException.badRequest(NOTHING_TO_PAY, "Nothing to pay: the commission taken back is " + Sales.lkr(total.negate())
                    + " more than what is owed. It comes off the next payment.");
        }
        if (f.method() == PayMethod.DRAWER && branchIds.size() > 1) {
            throw ApiException.badRequest(ONE_BRANCH, "Cash from the drawer is paid in one branch – choose the branch at the top first.");
        }
        Branch branch = branchIds.size() == 1 ? chosen.get(0).getBranch() : null;
        if (f.method() == PayMethod.DRAWER) {
            lock.checkOpen(branch.getId(), f.paidOn());
        }
        String payee = chosen.stream().map(CommissionLine::personName).distinct().collect(Collectors.joining(", "));
        Instant at = f.paidOn().equals(today) ? Instant.now() : f.paidOn().atTime(LocalTime.NOON).atZone(zone).toInstant();
        CommissionPayment p = payments.save(new CommissionPayment(numbers.next(Counter.COMMISSION_PAYMENT), f.paidOn(), at, f.method(),
                total, payee.length() > 300 ? payee.substring(0, 299) + "…" : payee, branch,
                users.getReferenceById(w.id())));
        chosen.forEach(l -> l.paid(p));
        log.add("Commission paid", p.getNumber() + " · " + payee + " · " + Sales.lkr(total) + " · " + f.method().label(),
                branch == null ? null : branch.getId());
        return new PaidView(p.getNumber(), total, payee, chosen.size());
    }

    // ------------------------------------------------------------------ commission slip

    /** A payment (CP-…) or a guide's statement of the last 30 days (G-…). */
    @Transactional(readOnly = true)
    public CommissionSlip slip(String id) {
        String key = id == null ? "" : id.trim().toUpperCase(Locale.ROOT);
        if (key.startsWith("CP-")) {
            CommissionPayment p = payments.findByNumber(key).orElseThrow(() -> ApiException.notFound("No commission payment " + id + "."));
            if (p.getBranch() != null) {
                current.checkBranch(p.getBranch().getId());
            }
            List<CommissionLine> ls = lines.findByPaymentIdOrderByEarnedAtAsc(p.getId());
            Guide g = ls.stream().map(CommissionLine::getGuide).filter(x -> x != null).distinct().count() == 1
                    && ls.stream().allMatch(l -> l.getPayee() == CommissionLine.Payee.GUIDE) ? ls.get(0).getGuide() : null;
            return slip(p.getNumber(), p.getPaidAt(), p.getMethod().label(), p.getPayee(), g, p.getPaidBy().getName(), ls, p.getAmount());
        }
        Guide g = guides.require(key);
        LocalDate since = LocalDate.now(zone).minusDays(30);
        List<CommissionLine> ls = lines.ofGuide(g.getId(), current.scope().branchId()).stream()
                .filter(l -> !l.getEarnedAt().atZone(zone).toLocalDate().isBefore(since))
                .sorted(Comparator.comparing(CommissionLine::getEarnedAt)).toList();
        return slip(null, Instant.now(), null, g.getName(), g, current.require().name(), ls, Rows.sum(ls));
    }

    @Transactional(readOnly = true)
    public byte[] slipPdf(String id) {
        CommissionSlip s = slip(id);
        List<PdfDocuments.Line> head = new ArrayList<>();
        head.add(new PdfDocuments.Line("Slip", s.number() == null ? "STATEMENT" : s.number()));
        head.add(new PdfDocuments.Line("Date", when(s.date())));
        if (s.method() != null) {
            head.add(new PdfDocuments.Line("Paid by", s.method()));
        }
        return pdfs.render(new PdfDocuments.Spec("COMMISSION SLIP", head,
                "Commission is calculated per sale (rule 5). Keep this slip for the daily cash closing and the accountant.", false, null),
                body -> {
                    Font normal = PdfBody.font(9.5f, Font.NORMAL, Color.BLACK);
                    Font bold = PdfBody.font(9.5f, Font.BOLD, Color.BLACK);
                    Paragraph to = new Paragraph();
                    to.add(new com.lowagie.text.Chunk("Paid to: ", bold));
                    to.add(new com.lowagie.text.Chunk(PdfBody.clean(s.payee() + (s.guideVehicle() == null ? "" : " · " + s.guideVehicle()
                            + " " + nz(s.guideVehicleNo()) + " · " + nz(s.guidePhone()) + " · " + Guides.pct(s.guidePct()))), normal));
                    to.setSpacingAfter(8);
                    body.document().add(to);
                    var t = body.table(new float[] {16, 18, 24, 24, 18}, "Date", "Invoice", ">Sale amount", ">Commission", "Status");
                    for (CommissionSlipLine l : s.lines()) {
                        t.row(dmy(l.date()), l.sale(), l.saleAmount(), l.amount(), status(l.status()));
                    }
                    t.end();
                    body.total("Total", Sales.lkr(s.total()));
                    PdfPTable sign = new PdfPTable(new float[] {45, 10, 45});
                    sign.setWidthPercentage(100);
                    sign.setSpacingBefore(50);
                    sign.addCell(signCell("Received by: " + s.payee()));
                    PdfPCell gap = new PdfPCell();
                    gap.setBorder(0);
                    sign.addCell(gap);
                    sign.addCell(signCell("Paid by: " + s.paidBy()));
                    body.document().add(sign);
                });
    }

    // ------------------------------------------------------------------ helpers

    private CommissionSlip slip(String no, Instant date, String method, String payee, Guide g, String paidBy, List<CommissionLine> ls,
            BigDecimal total) {
        return new CommissionSlip(no, date, method, payee, g == null ? null : g.getVehicle(), g == null ? null : g.getVehicleNo(),
                g == null ? null : g.getPhone(), g == null ? null : g.getPct(), paidBy,
                ls.stream().map(l -> new CommissionSlipLine(l.getEarnedAt(), l.getSale().getNumber(), l.getSale().getTotal(), l.getAmount(),
                        l.getStatus(), l.getNote())).toList(), total, sell.docShop());
    }

    private List<PayeeOption> guideOptions() {
        return guideRepo.findAll().stream().sorted(Comparator.comparing(Guide::getName))
                .map(g -> new PayeeOption("G:" + g.getCode(), g.getName(), CommissionLine.Payee.GUIDE)).toList();
    }

    private List<PayeeOption> staffOptions() {
        return users.findAll().stream().filter(u -> u.getRole() == Role.OWNER || u.getRole() == Role.SALESPERSON)
                .sorted(Comparator.comparing(UserAccount::getName))
                .map(u -> new PayeeOption("S:" + u.getId(), u.getName(), CommissionLine.Payee.STAFF)).toList();
    }

    public String when(Instant at) {
        return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(at.atZone(zone));
    }

    private String dmy(Instant at) {
        return java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy").format(at.atZone(zone));
    }

    public static String status(CommissionLine.Status s) {
        return switch (s) {
            case UNPAID -> "Not yet paid";
            case PAID -> "Paid";
            case REVERSED -> "Taken back";
        };
    }

    private static PdfPCell signCell(String text) {
        PdfPCell c = new PdfPCell(new Paragraph(PdfBody.clean(text), PdfBody.font(8.5f, Font.NORMAL, Color.BLACK)));
        c.setBorder(com.lowagie.text.Rectangle.TOP);
        c.setBorderColorTop(Color.DARK_GRAY);
        c.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
        c.setPaddingTop(4);
        return c;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String name(UserAccount u) {
        return u == null ? "—" : u.getName();
    }
}
