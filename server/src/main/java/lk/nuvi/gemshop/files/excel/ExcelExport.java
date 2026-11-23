package lk.nuvi.gemshop.files.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.stereotype.Component;

/**
 * Excel exports (Apache POI) for the accountant and the reports: a heading row in the shop's navy, frozen at the
 * top, with filters; amounts as real numbers (2 decimals) so the accountant can add them up; dates as Excel dates
 * in Sri Lanka time.
 * <pre>
 * excel.write("Activity log", List.of(
 *     ExcelExport.Column.dateTime("When", 18, LogLine::at),
 *     ExcelExport.Column.text("Who", 22, LogLine::userName),
 *     ExcelExport.Column.money("Amount (LKR)", 16, Row::amount)), rows);
 * </pre>
 */
@Component
public class ExcelExport {

    public enum Kind { TEXT, NUMBER, MONEY, DATE, DATE_TIME }

    /** One column: heading, width in characters, how to read the value from a row. */
    public record Column<T>(String heading, int width, Kind kind, Function<T, ?> value) {
        public static <T> Column<T> text(String h, int w, Function<T, ?> v) { return new Column<>(h, w, Kind.TEXT, v); }
        public static <T> Column<T> number(String h, int w, Function<T, ?> v) { return new Column<>(h, w, Kind.NUMBER, v); }
        public static <T> Column<T> money(String h, int w, Function<T, ?> v) { return new Column<>(h, w, Kind.MONEY, v); }
        public static <T> Column<T> date(String h, int w, Function<T, ?> v) { return new Column<>(h, w, Kind.DATE, v); }
        public static <T> Column<T> dateTime(String h, int w, Function<T, ?> v) { return new Column<>(h, w, Kind.DATE_TIME, v); }
    }

    private static final ZoneId SHOP = ZoneId.of("Asia/Colombo");
    private static final byte[] NAVY = {0x13, 0x21, 0x3f};

    public <T> byte[] write(String sheetName, List<Column<T>> columns, List<T> rows) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            SXSSFSheet sheet = wb.createSheet(safeName(sheetName));
            CellStyle head = wb.createCellStyle();
            Font hf = wb.createFont();
            hf.setBold(true);
            hf.setColor(IndexedColors.WHITE.getIndex());
            head.setFont(hf);
            ((XSSFCellStyle) head).setFillForegroundColor(new XSSFColor(NAVY, null));
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            head.setBorderBottom(BorderStyle.THIN);
            var fmt = wb.getCreationHelper().createDataFormat();
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(fmt.getFormat("#,##0.00"));
            CellStyle number = wb.createCellStyle();
            number.setDataFormat(fmt.getFormat("#,##0.##"));
            CellStyle date = wb.createCellStyle();
            date.setDataFormat(fmt.getFormat("dd/mm/yyyy"));
            CellStyle dateTime = wb.createCellStyle();
            dateTime.setDataFormat(fmt.getFormat("dd/mm/yyyy hh:mm"));

            Row hr = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell c = hr.createCell(i);
                c.setCellValue(columns.get(i).heading());
                c.setCellStyle(head);
                sheet.setColumnWidth(i, Math.min(255, columns.get(i).width()) * 256);
            }
            sheet.createFreezePane(0, 1);
            int r = 1;
            for (T row : rows) {
                Row xr = sheet.createRow(r++);
                for (int i = 0; i < columns.size(); i++) {
                    Column<T> col = columns.get(i);
                    Object v = col.value().apply(row);
                    if (v == null) {
                        continue;
                    }
                    Cell c = xr.createCell(i);
                    if (v instanceof String text && col.kind() != Kind.TEXT) {
                        c.setCellValue(text);        // e.g. "Total" in a date column of a totals row
                        continue;
                    }
                    switch (col.kind()) {
                        case MONEY -> {
                            c.setCellValue(num(v));
                            c.setCellStyle(money);
                        }
                        case NUMBER -> {
                            c.setCellValue(num(v));
                            c.setCellStyle(number);
                        }
                        case DATE -> {
                            c.setCellValue(toDate(v));
                            c.setCellStyle(date);
                        }
                        case DATE_TIME -> {
                            c.setCellValue(toDateTime(v));
                            c.setCellStyle(dateTime);
                        }
                        default -> c.setCellValue(v.toString());
                    }
                }
            }
            if (!columns.isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, r - 1), 0, columns.size() - 1));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static double num(Object v) {
        return v instanceof BigDecimal b ? b.doubleValue() : ((Number) v).doubleValue();
    }

    private static LocalDate toDate(Object v) {
        return v instanceof Instant i ? i.atZone(SHOP).toLocalDate() : (LocalDate) v;
    }

    private static LocalDateTime toDateTime(Object v) {
        return v instanceof Instant i ? LocalDateTime.ofInstant(i, SHOP) : (LocalDateTime) v;
    }

    /** Excel sheet names: at most 31 characters, none of : \ / ? * [ ]. */
    static String safeName(String name) {
        String n = name == null || name.isBlank() ? "Export" : name.replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
        return n.length() > 31 ? n.substring(0, 31) : n;
    }
}
