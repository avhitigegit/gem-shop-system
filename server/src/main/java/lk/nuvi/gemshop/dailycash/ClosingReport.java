package lk.nuvi.gemshop.dailycash;

import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import java.awt.Color;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.dailycash.api.CashDtos.ClosingView;
import lk.nuvi.gemshop.files.pdf.PdfBody;
import lk.nuvi.gemshop.files.pdf.PdfDocuments;
import lk.nuvi.gemshop.sales.Sales;
import org.springframework.stereotype.Component;

/** The day-end closing report (BRD I4) – emailed to the owner, and downloadable. A difference is highlighted in red. */
@Component
public class ClosingReport {

    private static final Color RED = new Color(0xB4, 0x23, 0x18);
    private static final Color GREEN = new Color(0x1B, 0x7F, 0x3B);

    private final PdfDocuments pdfs;
    private final ZoneId zone;

    public ClosingReport(PdfDocuments pdfs, AppProperties app) {
        this.pdfs = pdfs;
        this.zone = ZoneId.of(app.timezone());
    }

    public byte[] pdf(ClosingView c) {
        boolean balanced = c.difference().abs().compareTo(DailyCash.TOLERANCE) < 0;
        return pdfs.render(new PdfDocuments.Spec("DAY CLOSING", List.of(
                new PdfDocuments.Line("No", c.number()),
                new PdfDocuments.Line("Date", DateTimeFormatter.ofPattern("dd/MM/yyyy").format(c.day())),
                new PdfDocuments.Line("Branch", c.branchName()),
                new PdfDocuments.Line("Closed", DateTimeFormatter.ofPattern("HH:mm").format(c.closedAt().atZone(zone)) + " by " + c.closedBy())),
                "Expected cash = opening + cash sales (after change given) − cash paid out of the drawer. The card machine total "
                        + "is the card sales; the bank fee is paid by the shop.", false, null), body -> {
                    var t = body.table(new float[] {28, 24, 24, 24}, "", ">Expected", ">Counted", ">Difference");
                    row(t, "LKR cash", c.expected().lkr(), c.counted().lkr());
                    row(t, "USD cash", c.expected().usd(), c.counted().usd());
                    row(t, "EUR cash", c.expected().eur(), c.counted().eur());
                    row(t, "Card machine (LKR)", c.cardExpected(), c.cardCounted());
                    t.end();
                    Paragraph p = new Paragraph(PdfBody.clean(balanced ? "Balanced – counted cash matches the expected amounts."
                            : "DIFFERENCE: " + Sales.lkr(c.difference()) + " (all currencies at the day's rates: USD "
                                    + c.rateUsd().stripTrailingZeros().toPlainString() + ", EUR " + c.rateEur().stripTrailingZeros().toPlainString() + ")"),
                            PdfBody.font(11, Font.BOLD, balanced ? GREEN : RED));
                    p.setSpacingBefore(10);
                    p.setSpacingAfter(8);
                    body.document().add(p);
                    if (c.note() != null) {
                        body.text("Note: " + c.note());
                    }
                    var n = body.table(new float[] {60, 40}, "Today in numbers", ">");
                    n.row("Bills", String.valueOf(c.bills()));
                    n.row("Sales total", Sales.lkr(c.salesTotal()));
                    n.row("Card machine", Sales.lkr(c.cardExpected()));
                    n.row("Card bank fee (shop pays)", Sales.lkr(c.cardFee()));
                    n.row("Paid out of the drawer", Sales.lkr(c.drawerOut()));
                    n.end();
                });
    }

    private static void row(PdfBody.Table t, String label, BigDecimal expected, BigDecimal counted) {
        BigDecimal d = counted.subtract(expected);
        t.row(label, expected, counted, (d.signum() > 0 ? "+" : "") + PdfBody.money(d));
    }
}
