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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.CurrentUser.Who;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.customers.Customer;
import lk.nuvi.gemshop.files.pdf.PdfBody;
import lk.nuvi.gemshop.files.pdf.PdfDocuments;
import lk.nuvi.gemshop.gems.CertType;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemPrints;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.gems.Gems;
import lk.nuvi.gemshop.sales.Sales.Line;
import lk.nuvi.gemshop.sales.api.SalesDtos.ExtendForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.QuoteForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.QuoteList;
import lk.nuvi.gemshop.sales.api.SalesDtos.QuoteView;
import lk.nuvi.gemshop.sales.api.SalesDtos.SendForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.SendResult;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.rates.Rates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keep a gem for a customer, or give a price quote (BRD F7, rule 3). Made from the bill on New sale. A reservation makes
 * its gems "Reserved" until it is sold, released or expires; a quotation is a priced offer (PDF, email / WhatsApp). Every
 * night the ones past their date expire and reserved gems go back in stock. "Sell now" opens New sale with the
 * reservation or quotation; completing the sale marks it converted.
 */
@Service
public class Quotes {

    public static final String NOT_ACTIVE = "QUOTE_NOT_ACTIVE";
    public static final String BELOW_MIN = Sales.BELOW_MIN;
    public static final int MAX_DAYS = 90;

    private static final Logger LOG = LoggerFactory.getLogger(Quotes.class);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final QuoteRepository quotes;
    private final Sales sell;
    private final Gems gems;
    private final GemPrints prints;
    private final Numbers numbers;
    private final Rates rates;
    private final PdfDocuments pdfs;
    private final Delivery delivery;
    private final ActivityLog log;
    private final CurrentUser current;
    private final ZoneId zone;

    public Quotes(QuoteRepository quotes, Sales sell, Gems gems, GemPrints prints, Numbers numbers, Rates rates,
            PdfDocuments pdfs, Delivery delivery, ActivityLog log, CurrentUser current, AppProperties app) {
        this.quotes = quotes;
        this.sell = sell;
        this.gems = gems;
        this.prints = prints;
        this.numbers = numbers;
        this.rates = rates;
        this.pdfs = pdfs;
        this.delivery = delivery;
        this.log = log;
        this.current = current;
        this.zone = ZoneId.of(app.timezone());
    }

    // ------------------------------------------------------------------ make one from the bill

    @Transactional
    public QuoteView create(QuoteForm f) {
        Who w = current.require();
        Branch branch = sell.branchFor(f.branch(), f.items());
        List<Line> lines = sell.lines(f.items(), branch, Set.of());
        if (w.role() != Role.OWNER && lines.stream().anyMatch(Line::belowMin)) {
            throw ApiException.conflict(BELOW_MIN, "A price is below the minimum. Reservations and quotes must be at or above the "
                    + "minimum price – or make a sale and send it to the owner for approval.");
        }
        boolean reserve = f.kind() == Quote.Kind.RESERVATION;
        checkExpiry(f.expires());
        BigDecimal total = Sales.total(lines);
        BigDecimal deposit = reserve && f.deposit() != null ? f.deposit().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (deposit.compareTo(total) > 0) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "The deposit is more than the price.",
                    "deposit", "At most " + Sales.lkr(total));
        }
        Instant now = Instant.now();
        Customer customer = sell.customer(f.customer(), now);
        Quote q = new Quote(numbers.next(reserve ? Counter.RESERVATION : Counter.QUOTE), f.kind(), now, branch, sell.user(w),
                customer, f.expires(), deposit, Sales.blank(f.notes()));
        lines.forEach(l -> q.add(l.gem(), l.agreed()));
        quotes.save(q);
        if (reserve) {
            for (Line l : lines) {
                l.gem().setStatus(GemStatus.RESERVED);
                gems.addHistory(l.gem(), "Reserved", q.getNumber() + " for " + customer.getName() + " until "
                        + DMY.format(q.getExpires()));
            }
        }
        log.add(reserve ? "Reservation created" : "Quotation created", q.getNumber() + " · " + customer.getName() + " · "
                + Sales.lkr(total), branch.getId());
        return view(q);
    }

    // ------------------------------------------------------------------ list, one, extend, release

    @Transactional(readOnly = true)
    public QuoteList list(Quote.Kind kind) {
        Long branch = current.scope().branchId();
        return new QuoteList(quotes.list(kind, branch).stream().map(this::view).toList(),
                quotes.active(Quote.Kind.RESERVATION, branch), quotes.active(Quote.Kind.QUOTATION, branch));
    }

    @Transactional(readOnly = true)
    public QuoteView get(String number) {
        return view(require(number));
    }

    @Transactional
    public QuoteView extend(String number, ExtendForm f) {
        Quote q = requireActive(number);
        checkExpiry(f.expires());
        q.extend(f.expires());
        if (q.isReservation()) {
            q.getItems().forEach(i -> gems.addHistory(i.getGem(), "Reservation extended", q.getNumber() + " until "
                    + DMY.format(f.expires())));
        }
        log.add("Expiry extended", q.getNumber() + " → " + DMY.format(f.expires()), q.getBranch().getId());
        return view(q);
    }

    /** Release a reservation (gems back in stock) or withdraw a quotation. */
    @Transactional
    public QuoteView release(String number) {
        Quote q = requireActive(number);
        q.setStatus(Quote.Status.RELEASED);
        freeGems(q, "Reservation released", q.getNumber() + " released");
        log.add(q.isReservation() ? "Reservation released" : "Quotation withdrawn", q.getNumber() + " · " + q.getCustomer().getName(),
                q.getBranch().getId());
        return view(q);
    }

    // ------------------------------------------------------------------ the daily job

    /** Every night just after midnight (and when the server starts): past their date → expired, gems back in stock. */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Colombo")
    @Transactional
    public int expireDue() {
        List<Quote> due = quotes.due(LocalDate.now(zone));
        for (Quote q : due) {
            q.setStatus(Quote.Status.EXPIRED);
            freeGems(q, "Reservation expired", q.getNumber() + " passed its date " + DMY.format(q.getExpires()));
            log.addSystem(q.isReservation() ? "Reservation expired" : "Quotation expired", q.getNumber() + " · "
                    + q.getCustomer().getName(), q.getBranch().getId());
        }
        if (!due.isEmpty()) {
            LOG.info("{} reservation(s) / quotation(s) expired", due.size());
        }
        return due.size();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void expireOnStart() {
        try {
            expireDue();
        } catch (RuntimeException e) {
            LOG.warn("Could not expire old reservations at start: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ PDF and sending

    @Transactional(readOnly = true)
    public byte[] pdf(String number) {
        Quote q = require(number);
        QuoteView v = view(q);
        boolean reserve = q.isReservation();
        String wm = switch (q.getStatus()) {
            case ACTIVE -> null;
            case CONVERTED -> "SOLD";
            case RELEASED -> reserve ? "RELEASED" : "WITHDRAWN";
            case EXPIRED -> "EXPIRED";
        };
        String footer = "This " + (reserve ? "reservation" : "quotation") + " is valid until " + DMY.format(q.getExpires())
                + ". Prices are in LKR; USD / EUR amounts are for information and follow the rate on the day of payment. "
                + "Same price for cash or card. " + (reserve ? "The gems are held for you until the date above."
                        : "Gems are unique and are not held unless reserved.");
        return pdfs.render(new PdfDocuments.Spec(reserve ? "RESERVATION" : "QUOTATION", List.of(
                new PdfDocuments.Line("No", q.getNumber()),
                new PdfDocuments.Line("Date", DMY.format(q.getCreatedOn().atZone(zone))),
                new PdfDocuments.Line("Valid until", DMY.format(q.getExpires())),
                new PdfDocuments.Line("Prepared by", Sales.name(q.getStaff()))), footer, false, wm), body -> {
            Font small = PdfBody.font(8.5f, Font.NORMAL, PdfDocuments.GREY);
            Font normal = PdfBody.font(9.5f, Font.NORMAL, Color.BLACK);
            Font bold = PdfBody.font(9.5f, Font.BOLD, Color.BLACK);
            Customer c = q.getCustomer();
            Paragraph to = new Paragraph();
            to.add(new Chunk("For: ", bold));
            to.add(new Chunk(PdfBody.clean(c.getName() + ", " + c.getCountry() + (c.getEmail() == null ? "" : " · " + c.getEmail())), normal));
            to.setSpacingAfter(10);
            body.document().add(to);

            PdfPTable t = new PdfPTable(new float[] {12, 26, 32, 12, 9, 9});
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            for (String h : new String[] {"Photo", "Gem", "Gem details", "Price (LKR)", "USD", "EUR"}) {
                PdfPCell cell = new PdfPCell(new Paragraph(h, PdfBody.font(8.5f, Font.BOLD, Color.WHITE)));
                cell.setBackgroundColor(PdfDocuments.NAVY);
                cell.setBorderColor(PdfDocuments.NAVY);
                cell.setPadding(5);
                if (!"Photo".equals(h) && !"Gem".equals(h) && !"Gem details".equals(h)) {
                    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                }
                t.addCell(cell);
            }
            for (QuoteItem i : q.getItems()) {
                Gem g = i.getGem();
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
                t.addCell(cell(n, Element.ALIGN_LEFT));
                Paragraph d = new Paragraph();
                d.add(new Chunk(PdfBody.clean(g.getGemType() + " · " + g.getCarat().setScale(2, RoundingMode.HALF_UP) + " ct · "
                        + g.getColour() + " · " + g.getShape()), normal));
                d.add(Chunk.NEWLINE);
                d.add(new Chunk(PdfBody.clean("Treatment: " + g.getTreatment() + " · " + cert(g)), normal));
                t.addCell(cell(d, Element.ALIGN_LEFT));
                t.addCell(cell(new Paragraph(Invoices.num(i.getPrice()), bold), Element.ALIGN_RIGHT));
                t.addCell(cell(new Paragraph(fx(i.getPrice(), v.rateUsd()), normal), Element.ALIGN_RIGHT));
                t.addCell(cell(new Paragraph(fx(i.getPrice(), v.rateEur()), normal), Element.ALIGN_RIGHT));
            }
            body.document().add(t);
            body.total("TOTAL", Sales.lkr(v.total()));
            String approx = "≈ USD " + fx(v.total(), v.rateUsd()) + " · EUR " + fx(v.total(), v.rateEur()) + " at today's rates";
            Paragraph a = new Paragraph(PdfBody.clean(approx), small);
            a.setAlignment(Element.ALIGN_RIGHT);
            body.document().add(a);
            if (reserve && q.getDeposit().signum() > 0) {
                Paragraph dep = new Paragraph(PdfBody.clean("Deposit received: " + Sales.lkr(q.getDeposit())), bold);
                dep.setAlignment(Element.ALIGN_RIGHT);
                body.document().add(dep);
            }
            if (q.getNotes() != null) {
                Paragraph note = new Paragraph(PdfBody.clean("Note: " + q.getNotes()), normal);
                note.setSpacingBefore(10);
                body.document().add(note);
            }
        });
    }

    @Transactional
    public SendResult send(String number, SendForm f) {
        Quote q = requireActive(number);
        String doc = (q.isReservation() ? "reservation " : "quotation ") + q.getNumber();
        Delivery.Sent sent = delivery.send(f, q.getCustomer(), q.getBranch().getDisplayName(), doc,
                (q.isReservation() ? "Reservation " : "Quotation ") + q.getNumber() + ".pdf", pdf(number));
        q.sent(sent.email(), sent.whatsapp());
        log.add(q.isReservation() ? "Reservation sent" : "Quotation sent", q.getNumber() + " to " + q.getCustomer().getName()
                + " by " + sent.by(), q.getBranch().getId());
        return sent.result();
    }

    // ------------------------------------------------------------------ helpers

    private void freeGems(Quote q, String action, String detail) {
        if (!q.isReservation()) {
            return;
        }
        for (QuoteItem i : q.getItems()) {
            Gem g = i.getGem();
            if (g.getStatus() == GemStatus.RESERVED) {
                g.setStatus(GemStatus.IN_STOCK);
                gems.addHistory(g, action, detail);
            }
        }
    }

    private void checkExpiry(LocalDate d) {
        LocalDate today = LocalDate.now(zone);
        if (d.isBefore(today) || d.isAfter(today.plusDays(MAX_DAYS))) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "The expiry date must be from today up to " + MAX_DAYS + " days ahead.", "expires",
                    "Today to " + DMY.format(today.plusDays(MAX_DAYS)));
        }
    }

    private Quote require(String number) {
        Quote q = quotes.findByNumber(number == null ? "" : number.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("No reservation or quotation " + number + "."));
        current.checkBranch(q.getBranch().getId());
        return q;
    }

    private Quote requireActive(String number) {
        Quote q = require(number);
        if (!q.isActive()) {
            throw ApiException.conflict(NOT_ACTIVE, q.getNumber() + " is " + q.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
        return q;
    }

    QuoteView view(Quote q) {
        BigDecimal usd = null;
        BigDecimal eur = null;
        try {
            Rates.Today t = rates.today();
            usd = t.usd();
            eur = t.eur();
        } catch (RuntimeException ignored) {
            // no rate yet – the USD / EUR columns stay empty
        }
        return new QuoteView(q.getNumber(), q.getKind(), q.getStatus(), q.getCreatedOn(), q.getBranch().getCode(),
                Sales.name(q.getStaff()), Sales.pick(q.getCustomer(), 0L), q.getExpires(),
                ChronoUnit.DAYS.between(LocalDate.now(zone), q.getExpires()), q.getDeposit(), q.getNotes(),
                q.getItems().stream().map(i -> sell.line(i.getGem(), i.getGem().getPrice(), i.getPrice(), null, false)).toList(),
                q.total(), q.getSaleNo(), q.isSentEmail(), q.isSentWhatsapp(), usd, eur, sell.docShop());
    }

    private String cert(Gem g) {
        if (g.getCertType() == CertType.LAB) {
            return "Lab cert. " + (g.getCertLab() == null ? "" : g.getCertLab());
        }
        return g.getCertType() == CertType.SHOP ? "Shop certificate" : "No certificate";
    }

    private static String fx(BigDecimal lkr, BigDecimal rate) {
        return rate == null || rate.signum() == 0 ? "–" : Invoices.num(lkr.divide(rate, 2, RoundingMode.HALF_UP));
    }

    private static PdfPCell cell(Paragraph p, int align) {
        PdfPCell c = new PdfPCell(p);
        c.setBorderColor(PdfDocuments.BORDER);
        c.setPadding(5);
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_TOP);
        if (align == Element.ALIGN_RIGHT) {
            c.setBorder(Rectangle.BOX);
        }
        return c;
    }
}
