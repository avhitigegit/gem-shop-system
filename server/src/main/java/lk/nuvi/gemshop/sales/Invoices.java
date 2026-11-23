package lk.nuvi.gemshop.sales;

import com.lowagie.text.Chunk;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalRepository;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.audit.approval.Approvals;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import lk.nuvi.gemshop.files.pdf.PdfBody;
import lk.nuvi.gemshop.files.pdf.PdfDocuments;
import lk.nuvi.gemshop.gems.CertType;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemPrints;
import lk.nuvi.gemshop.sales.api.SalesDtos.CancelForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.InvoiceItem;
import lk.nuvi.gemshop.sales.api.SalesDtos.InvoicePage;
import lk.nuvi.gemshop.sales.api.SalesDtos.InvoiceRow;
import lk.nuvi.gemshop.sales.api.SalesDtos.SaleView;
import lk.nuvi.gemshop.sales.api.SalesDtos.SendForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.SendResult;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invoices (BRD G3–G6; rules 4, 7): the list, the PDF (no VAT line, no card fee line – the customer pays the same price),
 * sending it by email / WhatsApp, and cancelling – only with the owner's approval. An invoice is never edited or deleted;
 * a cancelled one stays on record marked CANCELLED, its gems go back in stock and its commissions no longer count.
 */
@Service
public class Invoices {

    public static final String CANCELLED = "INVOICE_CANCELLED";
    public static final String CANCEL_WAITING = "CANCEL_WAITING";
    public static final String NOT_SENT = Delivery.NOT_SENT;
    public static final int MAX_PAGE = 100;
    public static final int EXPORT_MAX = 5_000;

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DMY_HM = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SaleRepository sales;
    private final Sales sell;
    private final Approvals approvals;
    private final ApprovalRepository approvalRepo;
    private final PdfDocuments pdfs;
    private final GemPrints prints;
    private final Delivery delivery;
    private final ShopSettingsRepository shops;
    private final ActivityLog log;
    private final CurrentUser current;
    private final ZoneId zone;

    public Invoices(SaleRepository sales, Sales sell, Approvals approvals, ApprovalRepository approvalRepo, PdfDocuments pdfs,
            GemPrints prints, Delivery delivery, ShopSettingsRepository shops, ActivityLog log,
            CurrentUser current, AppProperties app) {
        this.sales = sales;
        this.sell = sell;
        this.approvals = approvals;
        this.approvalRepo = approvalRepo;
        this.pdfs = pdfs;
        this.prints = prints;
        this.delivery = delivery;
        this.shops = shops;
        this.log = log;
        this.current = current;
        this.zone = ZoneId.of(app.timezone());
    }

    /** Invoices list filters. Dates are shop days (both included); {@code pay} is cash, card or split. */
    public record Filter(LocalDate from, LocalDate to, Sale.Status status, String pay, String q) {}

    // ------------------------------------------------------------------ list

    @Transactional(readOnly = true)
    public InvoicePage list(Filter f, int page, int size) {
        List<Sale> all = find(f);
        int s = Math.max(1, Math.min(size, MAX_PAGE));
        int from = Math.min(Math.max(page, 0) * s, all.size());
        List<InvoiceRow> rows = all.subList(from, Math.min(from + s, all.size())).stream().map(this::row).toList();
        BigDecimal amount = all.stream().filter(x -> x.getStatus() == Sale.Status.COMPLETED).map(Sale::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new InvoicePage(rows, all.size(), Math.max(page, 0), s, amount);
    }

    /** The same search for the export buttons (at most 5,000 invoices). */
    @Transactional(readOnly = true)
    public List<InvoiceRow> export(Filter f) {
        return find(f).stream().limit(EXPORT_MAX).map(this::row).toList();
    }

    private List<Sale> find(Filter f) {
        LocalDate to = f.to() == null ? LocalDate.now(zone) : f.to();
        LocalDate from = f.from() == null ? to.minusDays(30) : f.from();
        if (from.isAfter(to)) {
            throw ApiException.badRequest("BAD_DATES", "'From' must be on or before 'To'.");
        }
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        String q = f.q() == null ? "" : f.q().trim().toLowerCase(Locale.ROOT);
        String pay = f.pay() == null ? "" : f.pay().trim().toLowerCase(Locale.ROOT);
        return sales.search(current.scope().branchId(), start, end, f.status()).stream()
                .filter(s -> pay.isEmpty() || payType(s).equals(pay))
                .filter(s -> q.isEmpty() || s.getNumber().toLowerCase(Locale.ROOT).contains(q)
                        || s.getCustomer().getName().toLowerCase(Locale.ROOT).contains(q)
                        || s.getItems().stream().anyMatch(i -> i.getGem().getCode().toLowerCase(Locale.ROOT).contains(q)
                                || i.getGem().getName().toLowerCase(Locale.ROOT).contains(q)))
                .toList();
    }

    private InvoiceRow row(Sale s) {
        return new InvoiceRow(s.getNumber(), s.getSoldAt(), s.getBranch().getCode(), s.getCustomer().getName(),
                s.getCustomer().getCountry(),
                s.getItems().stream().map(i -> new InvoiceItem(i.getGem().getCode(), i.getGem().getName(), i.getGem().getGemType(),
                        i.getGem().getProductType(), i.isReleased() && s.getStatus() == Sale.Status.COMPLETED)).toList(),
                payment(s), payType(s), s.getTotal(), s.isSentEmail(), s.isSentWhatsapp(), s.getStatus(), pending(s));
    }

    /** "Blue Sapphire Ring (GEM-00125), …" for exports. */
    public static String gems(InvoiceRow r) {
        return String.join(", ", r.items().stream().map(i -> i.name() + " (" + i.code() + ")" + (i.released() ? " returned" : "")).toList());
    }

    /** "Cash USD + Card". */
    static String payment(Sale s) {
        Set<String> parts = new LinkedHashSet<>();
        for (SalePayment p : s.getPayments()) {
            parts.add(method(p));
        }
        return parts.isEmpty() ? "–" : String.join(" + ", parts);
    }

    /** "Card", "Cash USD" or "Credit note". */
    static String method(SalePayment p) {
        return switch (p.getMethod()) {
            case CARD -> "Card";
            case CREDIT -> "Credit note";
            case CASH -> "Cash " + p.getCurrency();
        };
    }

    static String payType(Sale s) {
        if (s.getPayments().size() > 1) {
            return "split";
        }
        return s.getPayments().isEmpty() ? "" : s.getPayments().get(0).getMethod() == SalePayment.Method.CARD ? "card" : "cash";
    }

    /** The cancellation request waiting for the owner, if any. */
    private String pending(Sale s) {
        if (s.getCancelApprovalNo() == null || s.getStatus() == Sale.Status.CANCELLED) {
            return null;
        }
        return approvalRepo.findByNumber(s.getCancelApprovalNo()).filter(Approval::isPending).map(Approval::getNumber).orElse(null);
    }

    // ------------------------------------------------------------------ cancel (owner approval)

    /** Ask the owner to cancel an invoice (rule 7). Nothing changes until the owner approves. */
    @Transactional
    public SaleView requestCancel(String number, CancelForm f) {
        Sale s = require(number);
        if (s.getStatus() == Sale.Status.CANCELLED) {
            throw ApiException.conflict(CANCELLED, s.getNumber() + " is already cancelled.");
        }
        String waiting = pending(s);
        if (waiting != null) {
            throw ApiException.conflict(CANCEL_WAITING, "Cancellation of " + s.getNumber() + " is already waiting for the owner ("
                    + waiting + ").");
        }
        String reason = f.reason().trim();
        if (reason.isEmpty()) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Give the reason.", "reason", "Required");
        }
        Approval a = approvals.request(new Approvals.Request(ApprovalType.CANCEL_INVOICE, s.getBranch().getId(),
                "Cancel invoice " + s.getNumber(), reason, s.getTotal(), s.getNumber()));
        s.cancelAsked(a.getNumber(), reason);
        return sell.view(s);
    }

    // ------------------------------------------------------------------ PDF and sending

    @Transactional(readOnly = true)
    public byte[] pdf(String number) {
        return pdf(require(number));
    }

    /** The invoice PDF without the branch check (automatic messages, no one signed in). */
    @Transactional(readOnly = true)
    public byte[] pdf(Sale s) {
        ShopSettings shop = shops.current().orElse(null);
        boolean cancelled = s.getStatus() == Sale.Status.CANCELLED;
        List<PdfDocuments.Line> head = new ArrayList<>(List.of(
                new PdfDocuments.Line("Invoice No", s.getNumber()),
                new PdfDocuments.Line("Date", DMY_HM.format(s.getSoldAt().atZone(zone))),
                new PdfDocuments.Line("Branch", s.getBranch().getDisplayName()),
                new PdfDocuments.Line("Served by", Sales.name(s.getStaff()))));
        return pdfs.render(new PdfDocuments.Spec("INVOICE", head, footer(shop, s), false, cancelled ? "CANCELLED" : null), body -> {
            Font small = PdfBody.font(8.5f, Font.NORMAL, PdfDocuments.GREY);
            Font normal = PdfBody.font(9.5f, Font.NORMAL, Color.BLACK);
            Font bold = PdfBody.font(9.5f, Font.BOLD, Color.BLACK);

            PdfPTable to = new PdfPTable(new float[] {55, 45});
            to.setWidthPercentage(100);
            to.setSpacingAfter(10);
            Paragraph bill = new Paragraph();
            bill.add(new Chunk("BILL TO", small));
            for (String line : new String[] {s.getCustomer().getName(), s.getCustomer().getCountry(), s.getCustomer().getEmail(),
                    s.getCustomer().getWhatsapp()}) {
                if (line != null && !line.isBlank()) {
                    bill.add(Chunk.NEWLINE);
                    bill.add(new Chunk(PdfBody.clean(line), line.equals(s.getCustomer().getName()) ? bold : normal));
                }
            }
            to.addCell(plain(bill, Element.ALIGN_LEFT));
            Paragraph cur = new Paragraph();
            cur.add(new Chunk("CURRENCY", small));
            cur.add(Chunk.NEWLINE);
            cur.add(new Chunk("Amounts in Sri Lankan Rupees (LKR)", normal));
            if (s.getRateUsd() != null || s.getRateEur() != null) {
                cur.add(Chunk.NEWLINE);
                cur.add(new Chunk(PdfBody.clean(rates(s)), small));
            }
            if (cancelled) {
                cur.add(Chunk.NEWLINE);
                cur.add(new Chunk(PdfBody.clean("CANCELLED " + DMY.format(s.getCancelledAt().atZone(zone))
                        + (s.getCancelReason() == null ? "" : ": " + s.getCancelReason())), PdfBody.font(9, Font.BOLD, new Color(0xb9, 0x1c, 0x1c))));
            }
            to.addCell(plain(cur, Element.ALIGN_RIGHT));
            body.document().add(to);

            PdfPTable t = new PdfPTable(new float[] {5, 13, 44, 22, 16});
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            for (String h : new String[] {"#", "Photo", "Gem details", "Certificate", "Amount"}) {
                PdfPCell c = new PdfPCell(new Paragraph(h, PdfBody.font(8.5f, Font.BOLD, Color.WHITE)));
                c.setBackgroundColor(PdfDocuments.NAVY);
                c.setPadding(5);
                c.setBorderColor(PdfDocuments.NAVY);
                if ("Amount".equals(h)) {
                    c.setHorizontalAlignment(Element.ALIGN_RIGHT);
                }
                t.addCell(c);
            }
            int n = 0;
            for (SaleItem i : s.getItems()) {
                Gem g = i.getGem();
                t.addCell(cell(new Paragraph(String.valueOf(++n), normal), Element.ALIGN_LEFT));
                Image img = Image.getInstance(prints.picture(g, 240));
                img.scaleToFit(44, 44);
                PdfPCell pic = new PdfPCell(img, false);
                pic.setBorderColor(PdfDocuments.BORDER);
                pic.setPadding(4);
                t.addCell(pic);
                Paragraph d = new Paragraph();
                d.add(new Chunk(PdfBody.clean(g.getName()), bold));
                if (i.isReleased() && !cancelled) {
                    d.add(new Chunk("  (returned – see credit note)", small));
                }
                d.add(Chunk.NEWLINE);
                d.add(new Chunk(g.getCode(), small));
                d.add(Chunk.NEWLINE);
                d.add(new Chunk(PdfBody.clean(g.getGemType() + " · " + g.getProductType() + " · " + carat(g.getCarat()) + " · "
                        + g.getColour() + " · " + g.getShape()), normal));
                d.add(Chunk.NEWLINE);
                d.add(new Chunk("Treatment: ", normal));
                d.add(new Chunk(PdfBody.clean(g.getTreatment()), bold));
                t.addCell(cell(d, Element.ALIGN_LEFT));
                Paragraph c = new Paragraph();
                if (g.getCertType() == CertType.LAB) {
                    c.add(new Chunk("Lab certificate", normal));
                    c.add(Chunk.NEWLINE);
                    c.add(new Chunk(PdfBody.clean(g.getCertLab() == null ? "" : g.getCertLab()), bold));
                    if (g.getCertNo() != null) {
                        c.add(Chunk.NEWLINE);
                        c.add(new Chunk("No. " + PdfBody.clean(g.getCertNo()), normal));
                    }
                } else if (g.getCertType() == CertType.SHOP) {
                    c.add(new Chunk("Shop certificate", normal));
                    c.add(Chunk.NEWLINE);
                    c.add(new Chunk("No. " + PdfBody.clean(prints.certNo(g)), normal));
                } else {
                    c.add(new Chunk("No certificate", normal));
                }
                t.addCell(cell(c, Element.ALIGN_LEFT));
                t.addCell(cell(new Paragraph(Sales.lkr(i.getAgreed()), bold), Element.ALIGN_RIGHT));
            }
            body.document().add(t);

            body.total("TOTAL", Sales.lkr(s.getTotal()));
            String approx = approx(s);
            if (approx != null) {
                Paragraph p = new Paragraph(PdfBody.clean(approx), small);
                p.setAlignment(Element.ALIGN_RIGHT);
                body.document().add(p);
            }

            PdfPTable bottom = new PdfPTable(new float[] {50, 50});
            bottom.setWidthPercentage(100);
            bottom.setSpacingBefore(14);
            Paragraph paid = new Paragraph();
            paid.add(new Chunk("Payment received", bold));
            for (SalePayment p : s.getPayments()) {
                paid.add(Chunk.NEWLINE);
                String what = p.getMethod() == SalePayment.Method.CREDIT ? "Credit note " + p.getCreditNoteNo() : method(p);
                String amount = p.getCurrency() == lk.nuvi.gemshop.common.money.Currency.LKR ? Sales.lkr(p.getLkr())
                        : p.getCurrency() + " " + num(p.getAmount()) + " @ " + num(p.getRate()) + " = " + Sales.lkr(p.getLkr());
                paid.add(new Chunk(PdfBody.clean(what + ": " + amount), normal));
            }
            if (s.getChangeDue() != null && s.getChangeDue().signum() > 0) {
                paid.add(Chunk.NEWLINE);
                paid.add(new Chunk("Change given: " + Sales.lkr(s.getChangeDue()), normal));
            }
            bottom.addCell(plain(paid, Element.ALIGN_LEFT));
            Paragraph more = new Paragraph();
            if (s.getGuide() != null) {
                more.add(new Chunk("Introduced by: ", bold));
                more.add(new Chunk(PdfBody.clean(s.getGuide().getName() + (s.getGuide().getVehicle() == null ? ""
                        : " (" + s.getGuide().getVehicle() + ")")), normal));
                more.add(Chunk.NEWLINE);
            }
            more.add(new Chunk("Goods sold are genuine natural gemstones as described. Treatment is disclosed above.", normal));
            bottom.addCell(plain(more, Element.ALIGN_LEFT));
            body.document().add(bottom);
        });
    }

    /** Send the invoice PDF by email (shop SMTP) and / or WhatsApp (test mode until P6-03). */
    @Transactional
    public SendResult send(String number, SendForm f) {
        Sale s = require(number);
        if (s.getStatus() == Sale.Status.CANCELLED) {
            throw ApiException.conflict(CANCELLED, s.getNumber() + " is cancelled – it cannot be sent.");
        }
        Delivery.Sent sent = delivery.send(f, s.getCustomer(), s.getBranch().getDisplayName(), "invoice " + s.getNumber(),
                "Invoice " + s.getNumber() + ".pdf", pdf(number));
        s.sent(sent.email(), sent.whatsapp());
        log.add("Invoice sent", s.getNumber() + " to " + s.getCustomer().getName() + " by " + sent.by(), s.getBranch().getId());
        return sent.result();
    }

    // ------------------------------------------------------------------ helpers

    /** "22/11/2026 13:05" in shop time. */
    public String when(Instant at) {
        return DMY_HM.format(at.atZone(zone));
    }

    private Sale require(String number) {
        Sale s = sales.findByNumber(number == null ? "" : number.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("No invoice " + number + "."));
        current.checkBranch(s.getBranch().getId());
        return s;
    }

    private String footer(ShopSettings shop, Sale s) {
        if (shop == null) {
            return "This is a computer-generated invoice.";
        }
        StringBuilder b = new StringBuilder("This is a computer-generated invoice. Returns accepted within ")
                .append(shop.getReturnPolicyDays()).append(" days with this invoice, subject to owner approval.");
        if (shop.getNgjaLicenceNo() != null && !shop.getNgjaLicenceNo().isBlank()) {
            b.append(" Licensed gem dealer – National Gem & Jewellery Authority, licence ").append(shop.getNgjaLicenceNo());
            if (shop.getNgjaLicenceExpiry() != null) {
                b.append(" (valid until ").append(DMY.format(shop.getNgjaLicenceExpiry())).append(')');
            }
            b.append('.');
        }
        b.append(" Thank you for shopping at ").append(shop.getName()).append(", ").append(s.getBranch().getDisplayName()).append('!');
        return b.toString();
    }

    private static String rates(Sale s) {
        List<String> r = new ArrayList<>();
        if (s.getRateUsd() != null) {
            r.add("USD " + num(s.getRateUsd()));
        }
        if (s.getRateEur() != null) {
            r.add("EUR " + num(s.getRateEur()));
        }
        return String.join(" · ", r) + " rate of the day";
    }

    private static String approx(Sale s) {
        List<String> r = new ArrayList<>();
        if (s.getRateUsd() != null && s.getRateUsd().signum() > 0) {
            r.add("USD " + num(s.getTotal().divide(s.getRateUsd(), 2, RoundingMode.HALF_UP)));
        }
        if (s.getRateEur() != null && s.getRateEur().signum() > 0) {
            r.add("EUR " + num(s.getTotal().divide(s.getRateEur(), 2, RoundingMode.HALF_UP)));
        }
        return r.isEmpty() ? null : "≈ " + String.join(" · ", r);
    }

    static String num(BigDecimal v) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(v == null ? BigDecimal.ZERO : v);
    }

    private static String carat(BigDecimal v) {
        return new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(v) + " ct";
    }

    private static PdfPCell plain(Paragraph p, int align) {
        p.setAlignment(align);
        PdfPCell c = new PdfPCell(p);
        c.setBorder(Rectangle.NO_BORDER);
        c.setHorizontalAlignment(align);
        return c;
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
