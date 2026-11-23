package lk.nuvi.gemshop.sales;

import com.lowagie.text.Chunk;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import java.awt.Color;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalRepository;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.audit.approval.Approvals;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.CurrentUser.Who;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.customers.Customer;
import lk.nuvi.gemshop.files.pdf.PdfBody;
import lk.nuvi.gemshop.files.pdf.PdfDocuments;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemPrints;
import lk.nuvi.gemshop.sales.api.SalesDtos.CreditNoteView;
import lk.nuvi.gemshop.sales.api.SalesDtos.CustomerReturnForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.ReturnGem;
import lk.nuvi.gemshop.sales.api.SalesDtos.ReturnSale;
import lk.nuvi.gemshop.sales.api.SalesDtos.ReturnView;
import lk.nuvi.gemshop.sales.api.SalesDtos.SendForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.SendResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer brings a gem back (BRD E1, E2, D2; rules 3, 4, 7). The salesperson asks with a reason; the owner approves
 * (Approvals → {@link ReturnApproval}). Then the gem is back in stock ("Returned", may be sold again), a credit note is
 * made for the price paid (a card fee is not given back – it stays a shop cost) and the commissions for that gem are taken
 * back. An exchange credit note pays for the new gem on New sale.
 */
@Service
public class Returns {

    public static final String NOT_RETURNABLE = "NOT_RETURNABLE";
    public static final String RETURN_WAITING = "RETURN_WAITING";
    public static final String CREDIT_LINK = "/sell/credit-notes/";

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SaleRepository sales;
    private final SaleReturnRepository returns;
    private final CreditNoteRepository creditNotes;
    private final Sales sell;
    private final Invoices invoices;
    private final GemPrints prints;
    private final Numbers numbers;
    private final Approvals approvals;
    private final ApprovalRepository approvalRepo;
    private final PdfDocuments pdfs;
    private final Delivery delivery;
    private final ActivityLog log;
    private final CurrentUser current;
    private final ZoneId zone;

    public Returns(SaleRepository sales, SaleReturnRepository returns, CreditNoteRepository creditNotes, Sales sell, Invoices invoices,
            GemPrints prints, Numbers numbers, Approvals approvals, ApprovalRepository approvalRepo, PdfDocuments pdfs,
            Delivery delivery, ActivityLog log, CurrentUser current, AppProperties app) {
        this.sales = sales;
        this.returns = returns;
        this.creditNotes = creditNotes;
        this.sell = sell;
        this.invoices = invoices;
        this.prints = prints;
        this.numbers = numbers;
        this.approvals = approvals;
        this.approvalRepo = approvalRepo;
        this.pdfs = pdfs;
        this.delivery = delivery;
        this.log = log;
        this.current = current;
        this.zone = ZoneId.of(app.timezone());
    }

    // ------------------------------------------------------------------ step 1 / 2: find the sale

    /** Latest completed sales matching the invoice number, customer name or a gem code / name (at most 8). */
    @Transactional(readOnly = true)
    public List<ReturnSale> find(String q) {
        String like = q == null || q.isBlank() ? "" : "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
        return sales.forReturn(current.scope().branchId(), like, PageRequest.of(0, 8)).stream().map(this::returnSale).toList();
    }

    @Transactional(readOnly = true)
    public ReturnSale sale(String number) {
        return returnSale(requireSale(number));
    }

    // ------------------------------------------------------------------ ask the owner

    @Transactional
    public ReturnView create(CustomerReturnForm f) {
        Who w = current.require();
        Sale s = requireSale(f.sale());
        String code = f.gem().trim().toUpperCase(Locale.ROOT);
        SaleItem item = s.getItems().stream().filter(i -> i.getGem().getCode().equals(code)).findFirst()
                .orElseThrow(() -> ApiException.notFound(code + " is not on invoice " + s.getNumber() + "."));
        if (item.isReleased()) {
            throw ApiException.conflict(NOT_RETURNABLE, code + " was already returned.");
        }
        SaleReturn waiting = waitingFor(s).get(item.getId());
        if (waiting != null) {
            throw ApiException.conflict(RETURN_WAITING, "A return for " + code + " is already waiting for the owner (" + waiting.getNumber() + ").");
        }
        SaleReturn r = new SaleReturn(numbers.next(Counter.RETURN), Instant.now(), s, item, f.kind(), f.reason().trim(), sell.user(w));
        returns.save(r);
        Gem g = item.getGem();
        boolean exchange = f.kind() == SaleReturn.Kind.EXCHANGE;
        Approval a = approvals.request(new Approvals.Request(ApprovalType.RETURN, s.getBranch().getId(),
                (exchange ? "Exchange: " : "Return: ") + g.getName() + " (" + g.getCode() + ")",
                "Invoice " + s.getNumber() + " · " + r.getReason(), r.getAmount(), r.getNumber()));
        r.asked(a.getNumber());
        log.add(exchange ? "Exchange requested" : "Return requested", r.getNumber() + " · " + g.getCode() + " · invoice " + s.getNumber()
                + " · " + Sales.lkr(r.getAmount()), s.getBranch().getId());
        return view(r);
    }

    // ------------------------------------------------------------------ lists and views

    @Transactional(readOnly = true)
    public List<ReturnView> list() {
        List<SaleReturn> list = returns.list(current.scope().branchId());
        Map<String, Approval> answers = answers(list);
        return list.stream().map(r -> view(r, answers.get(r.getApprovalNo()))).toList();
    }

    @Transactional(readOnly = true)
    public ReturnView get(String number) {
        SaleReturn r = returns.findByNumber(clean(number)).orElseThrow(() -> ApiException.notFound("No return " + number + "."));
        current.checkBranch(r.getBranch().getId());
        return view(r);
    }

    @Transactional(readOnly = true)
    public List<CreditNoteView> creditNotes() {
        return creditNotes.list(current.scope().branchId()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public CreditNoteView creditNote(String number) {
        return view(requireCredit(number));
    }

    // ------------------------------------------------------------------ credit note PDF and sending

    @Transactional(readOnly = true)
    public byte[] pdf(String number) {
        CreditNote c = requireCredit(number);
        CreditNoteView v = view(c);
        SaleReturn r = c.getSaleReturn();
        Sale s = r.getSale();
        Gem g = r.getGem();
        String footer = "This credit note is against the original invoice " + s.getNumber() + ". The original invoice is kept on "
                + "record. The amount is the price paid for the gem; a card fee is not part of it.";
        return pdfs.render(new PdfDocuments.Spec("CREDIT NOTE", List.of(
                new PdfDocuments.Line("No", c.getNumber()),
                new PdfDocuments.Line("Date", invoices.when(c.getIssuedAt())),
                new PdfDocuments.Line("Against invoice", s.getNumber() + " (" + DMY.format(s.getSoldAt().atZone(zone)) + ")")), footer,
                false, null), body -> {
            Font small = PdfBody.font(8.5f, Font.NORMAL, PdfDocuments.GREY);
            Font normal = PdfBody.font(9.5f, Font.NORMAL, Color.BLACK);
            Font bold = PdfBody.font(9.5f, Font.BOLD, Color.BLACK);
            Customer cu = s.getCustomer();
            Paragraph to = new Paragraph();
            to.add(new Chunk("Customer: ", bold));
            to.add(new Chunk(PdfBody.clean(cu.getName() + ", " + cu.getCountry()), normal));
            to.setSpacingAfter(10);
            body.document().add(to);

            PdfPTable t = new PdfPTable(new float[] {12, 40, 32, 16});
            t.setWidthPercentage(100);
            for (String h : new String[] {"Photo", "Returned gem", "Reason", "Credit"}) {
                PdfPCell cell = new PdfPCell(new Paragraph(h, PdfBody.font(8.5f, Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(PdfDocuments.NAVY);
                cell.setBorderColor(PdfDocuments.NAVY);
                cell.setPadding(5);
                if ("Credit".equals(h)) {
                    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                }
                t.addCell(cell);
            }
            Image img = Image.getInstance(prints.picture(g, 240));
            img.scaleToFit(44, 44);
            PdfPCell pic = new PdfPCell(img, false);
            pic.setBorderColor(PdfDocuments.BORDER);
            pic.setPadding(4);
            t.addCell(pic);
            Paragraph n = new Paragraph();
            n.add(new Chunk(PdfBody.clean(g.getName()), bold));
            n.add(Chunk.NEWLINE);
            n.add(new Chunk(g.getCode(), small));
            n.add(Chunk.NEWLINE);
            n.add(new Chunk(PdfBody.clean(g.getCarat().setScale(2, RoundingMode.HALF_UP) + " ct · " + g.getTreatment()), normal));
            t.addCell(cell(n, Element.ALIGN_LEFT));
            t.addCell(cell(new Paragraph(PdfBody.clean((r.getKind() == SaleReturn.Kind.EXCHANGE ? "Exchange" : "Refund") + ": "
                    + r.getReason()), normal), Element.ALIGN_LEFT));
            t.addCell(cell(new Paragraph(Sales.lkr(c.getAmount()), bold), Element.ALIGN_RIGHT));
            body.document().add(t);
            body.total("TOTAL CREDIT", Sales.lkr(c.getAmount()));
            Paragraph who = new Paragraph(PdfBody.clean("Approved by: " + c.getApprovedBy() + " (owner) · Stock: gem returned to stock · "
                    + "Commissions taken back: " + taken(v)), normal);
            who.setSpacingBefore(10);
            body.document().add(who);
            if (c.isUsed()) {
                Paragraph used = new Paragraph(PdfBody.clean("Used for the exchange on invoice " + c.getUsedSaleNo() + " ("
                        + Sales.lkr(c.getUsedAmount()) + ")."), normal);
                body.document().add(used);
            }
        });
    }

    @Transactional
    public SendResult send(String number, SendForm f) {
        CreditNote c = requireCredit(number);
        Sale s = c.getSaleReturn().getSale();
        Delivery.Sent sent = delivery.send(f, s.getCustomer(), s.getBranch().getDisplayName(), "credit note " + c.getNumber(),
                "Credit note " + c.getNumber() + ".pdf", pdf(number));
        c.sent(sent.email(), sent.whatsapp());
        log.add("Credit note sent", c.getNumber() + " to " + s.getCustomer().getName() + " by " + sent.by(), s.getBranch().getId());
        return sent.result();
    }

    // ------------------------------------------------------------------ helpers

    private ReturnSale returnSale(Sale s) {
        Map<Long, SaleReturn> waiting = waitingFor(s);
        long days = ChronoUnit.DAYS.between(s.getSoldAt().atZone(zone).toLocalDate(), LocalDate.now(zone));
        int policy = sell.docShop().returnDays();
        List<ReturnGem> items = new ArrayList<>();
        for (SaleItem i : s.getItems()) {
            SaleReturn w = waiting.get(i.getId());
            items.add(new ReturnGem(sell.line(i.getGem(), i.getListPrice(), i.getAgreed(), i.getApprovalNo(), i.isReleased()),
                    i.isReleased(), w == null ? null : w.getNumber()));
        }
        return new ReturnSale(s.getNumber(), s.getSoldAt(), days, days > policy, policy, s.getBranch().getCode(),
                Sales.pick(s.getCustomer(), 0L), s.getTotal(), items);
    }

    private Map<Long, SaleReturn> waitingFor(Sale s) {
        return returns.findBySaleId(s.getId()).stream().filter(SaleReturn::isPending)
                .collect(Collectors.toMap(r -> r.getItem().getId(), Function.identity(), (a, b) -> a));
    }

    private Sale requireSale(String number) {
        Sale s = sales.findByNumber(clean(number)).orElseThrow(() -> ApiException.notFound("No invoice " + number + "."));
        current.checkBranch(s.getBranch().getId());
        if (s.getStatus() != Sale.Status.COMPLETED) {
            throw ApiException.conflict(NOT_RETURNABLE, "Invoice " + s.getNumber() + " was cancelled – nothing to return.");
        }
        return s;
    }

    private CreditNote requireCredit(String number) {
        CreditNote c = creditNotes.findByNumber(clean(number)).orElseThrow(() -> ApiException.notFound("No credit note " + number + "."));
        current.checkBranch(c.getSaleReturn().getBranch().getId());
        return c;
    }

    private Map<String, Approval> answers(List<SaleReturn> list) {
        List<String> nos = list.stream().map(SaleReturn::getApprovalNo).filter(x -> x != null).toList();
        return nos.isEmpty() ? Map.of() : approvalRepo.findByNumberIn(nos).stream()
                .collect(Collectors.toMap(Approval::getNumber, Function.identity(), (a, b) -> a));
    }

    private ReturnView view(SaleReturn r) {
        return view(r, r.getApprovalNo() == null ? null : approvalRepo.findByNumber(r.getApprovalNo()).orElse(null));
    }

    private ReturnView view(SaleReturn r, Approval a) {
        Sale s = r.getSale();
        SaleItem i = r.getItem();
        boolean decided = a != null && !a.isPending();
        CreditNote cn = r.getCreditNote();
        return new ReturnView(r.getNumber(), r.getRequestedAt(), r.getBranch().getCode(), s.getNumber(), s.getSoldAt(),
                Sales.pick(s.getCustomer(), 0L), sell.line(r.getGem(), i.getListPrice(), i.getAgreed(), i.getApprovalNo(), i.isReleased()),
                r.getKind(), r.getReason(), r.getAmount(), r.getStatus(), Sales.name(r.getStaff()), r.getApprovalNo(),
                decided ? a.getNote() : null, decided ? Sales.name(a.getDecidedBy()) : null, decided ? a.getDecidedAt() : null,
                cn == null ? null : cn.getNumber(), cn == null ? null : cn.getUsedSaleNo());
    }

    private CreditNoteView view(CreditNote c) {
        SaleReturn r = c.getSaleReturn();
        Sale s = r.getSale();
        SaleItem i = r.getItem();
        return new CreditNoteView(c.getNumber(), c.getIssuedAt(), r.getBranch().getCode(), r.getNumber(), r.getKind(), r.getReason(),
                s.getNumber(), s.getSoldAt(), Sales.pick(s.getCustomer(), 0L),
                sell.line(r.getGem(), i.getListPrice(), i.getAgreed(), i.getApprovalNo(), i.isReleased()), c.getAmount(),
                c.getGuideComm(), c.getStaffComm(), s.getGuide() == null ? null : s.getGuide().getName(), Sales.name(s.getStaff()),
                c.getApprovedBy(), c.getUsedSaleNo(), c.getUsedAmount(), c.isSentEmail(), c.isSentWhatsapp(), sell.docShop());
    }

    /** "Sunil Kumara LKR 16,500.00, Nimal Silva LKR 0.00". */
    static String taken(CreditNoteView v) {
        List<String> parts = new ArrayList<>();
        if (v.guide() != null && v.guideComm().signum() > 0) {
            parts.add(v.guide() + " " + Sales.lkr(v.guideComm()));
        }
        if (v.staffComm().signum() > 0) {
            parts.add(v.staff() + " " + Sales.lkr(v.staffComm()));
        }
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    private static String clean(String number) {
        return number == null ? "" : number.trim().toUpperCase(Locale.ROOT);
    }

    private static PdfPCell cell(Paragraph p, int align) {
        PdfPCell c = new PdfPCell(p);
        c.setBorderColor(PdfDocuments.BORDER);
        c.setPadding(5);
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_TOP);
        return c;
    }
}
