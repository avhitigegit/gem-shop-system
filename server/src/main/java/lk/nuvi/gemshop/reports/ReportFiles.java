package lk.nuvi.gemshop.reports;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.files.Downloads;
import lk.nuvi.gemshop.files.excel.ExcelExport;
import lk.nuvi.gemshop.files.pdf.PdfDocuments;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * "Export PDF" / "Export Excel" of every report (BRD L6): one table, the same rows and totals in both. The PDF has the
 * shop's header with the report name, period and key figures on the right; the Excel file has real numbers and dates.
 * <pre>
 * files.download("pdf", new ReportFiles.Sheet("SALES REPORT", "Sales report", lines, cols, rows, totals, note, false));
 * </pre>
 */
@Component
public class ReportFiles {

    public enum Kind { TEXT, NUMBER, MONEY, DATE, DATE_TIME }

    /** A column: heading, width (characters in Excel, share of the page in the PDF), kind of value. */
    public record Col(String heading, int width, Kind kind) {
        public static Col text(String h, int w) { return new Col(h, w, Kind.TEXT); }
        public static Col number(String h, int w) { return new Col(h, w, Kind.NUMBER); }
        public static Col money(String h, int w) { return new Col(h, w, Kind.MONEY); }
        public static Col date(String h, int w) { return new Col(h, w, Kind.DATE); }
        public static Col dateTime(String h, int w) { return new Col(h, w, Kind.DATE_TIME); }
    }

    /**
     * @param title     big title on the PDF (SALES REPORT)
     * @param name      file name and Excel sheet ("Sales report" → "Sales report 2026-11-24.xlsx")
     * @param lines     lines under the PDF title (Period, Branch, key figures)
     * @param totals    the totals row, or null
     * @param note      text at the bottom of every PDF page, or null
     */
    public record Sheet(String title, String name, List<PdfDocuments.Line> lines, List<Col> cols, List<Object[]> rows,
            Object[] totals, String note, boolean landscape) {}

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DMY_HM = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PdfDocuments pdfs;
    private final ExcelExport excel;

    public ReportFiles(PdfDocuments pdfs, ExcelExport excel) {
        this.pdfs = pdfs;
        this.excel = excel;
    }

    public ResponseEntity<byte[]> download(String format, Sheet s, ZoneId zone) {
        String file = Downloads.dated(s.name());
        String f = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        return switch (f) {
            case "xlsx" -> Downloads.xlsx(xlsx(s), file);
            case "pdf" -> Downloads.pdf(pdf(s, zone), file, true);
            default -> throw ApiException.badRequest("BAD_FORMAT", "Format must be pdf or xlsx.");
        };
    }

    byte[] xlsx(Sheet s) {
        List<ExcelExport.Column<Object[]>> cols = new ArrayList<>();
        for (int i = 0; i < s.cols().size(); i++) {
            Col c = s.cols().get(i);
            int at = i;
            ExcelExport.Kind k = ExcelExport.Kind.valueOf(c.kind().name());
            cols.add(new ExcelExport.Column<Object[]>(c.heading(), c.width(), k, r -> at < r.length ? r[at] : null));
        }
        List<Object[]> rows = new ArrayList<>(s.rows());
        if (s.totals() != null) {
            rows.add(s.totals());
        }
        return excel.write(s.name(), cols, rows);
    }

    byte[] pdf(Sheet s, ZoneId zone) {
        List<PdfDocuments.Line> lines = new ArrayList<>(s.lines());
        lines.add(new PdfDocuments.Line("Printed", DMY_HM.format(LocalDateTime.now(zone))));
        float[] widths = new float[s.cols().size()];
        String[] heads = new String[s.cols().size()];
        for (int i = 0; i < widths.length; i++) {
            Col c = s.cols().get(i);
            widths[i] = c.width();
            boolean right = c.kind() == Kind.MONEY || c.kind() == Kind.NUMBER;
            heads[i] = (right ? ">" : "") + c.heading();
        }
        return pdfs.render(new PdfDocuments.Spec(s.title(), lines, s.note(), s.landscape(), null), body -> {
            var t = body.table(widths, heads);
            if (s.rows().isEmpty()) {
                Object[] none = new Object[widths.length];
                none[0] = "Nothing in this period.";
                t.row(cells(s, none, zone));
            }
            for (Object[] r : s.rows()) {
                t.row(cells(s, r, zone));
            }
            if (s.totals() != null) {
                t.row(cells(s, s.totals(), zone));
            }
            t.end();
        });
    }

    private static Object[] cells(Sheet s, Object[] r, ZoneId zone) {
        Object[] out = new Object[s.cols().size()];
        for (int i = 0; i < out.length; i++) {
            Object v = i < r.length ? r[i] : null;
            Kind k = s.cols().get(i).kind();
            out[i] = v == null ? "" : switch (k) {
                case NUMBER -> v instanceof BigDecimal b ? b.stripTrailingZeros().toPlainString() : v.toString();
                case DATE -> v instanceof Instant at ? DMY.format(at.atZone(zone)) : v instanceof LocalDate d ? DMY.format(d) : v.toString();
                case DATE_TIME -> v instanceof Instant at ? DMY_HM.format(at.atZone(zone)) : v.toString();
                default -> v;
            };
        }
        return out;
    }
}
