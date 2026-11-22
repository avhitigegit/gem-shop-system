package lk.nuvi.gemshop.files.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** The part of a PDF under the header: text, tables like the prototype's (navy heading row), totals, pictures. */
public final class PdfBody {

    private static final BaseFont HELVETICA;
    private static final BaseFont HELVETICA_BOLD;

    static {
        try {
            HELVETICA = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            HELVETICA_BOLD = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private final Document doc;

    PdfBody(Document doc) {
        this.doc = doc;
    }

    /** The OpenPDF document, for anything not covered here. */
    public Document document() {
        return doc;
    }

    public static Font font(float size, int style, Color color) {
        return new Font((style & Font.BOLD) != 0 ? HELVETICA_BOLD : HELVETICA, size, style & ~Font.BOLD, color);
    }

    public PdfBody text(String text) throws DocumentException {
        return text(text, 9.5f, Font.NORMAL);
    }

    public PdfBody text(String text, float size, int style) throws DocumentException {
        Paragraph p = new Paragraph(clean(text), font(size, style, Color.BLACK));
        p.setSpacingAfter(4);
        doc.add(p);
        return this;
    }

    public PdfBody heading(String text) throws DocumentException {
        Paragraph p = new Paragraph(clean(text), font(11, Font.BOLD, PdfDocuments.NAVY));
        p.setSpacingBefore(8);
        p.setSpacingAfter(6);
        doc.add(p);
        return this;
    }

    public PdfBody picture(byte[] image, float maxWidth, float maxHeight) throws DocumentException, IOException {
        Image img = Image.getInstance(image);
        img.scaleToFit(maxWidth, maxHeight);
        doc.add(img);
        return this;
    }

    /** A table with a navy heading row. Columns marked with a leading ">" are right-aligned (amounts). */
    public Table table(float[] widths, String... headings) {
        return new Table(widths, headings);
    }

    /** "TOTAL  LKR 352,000.00" on the right, with a navy line above (prototype {@code .totals .grand}). */
    public PdfBody total(String label, String value) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[] {55, 45});
        t.setWidthPercentage(40);
        t.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.setSpacingBefore(8);
        String[] cells = {label, value};
        for (int i = 0; i < cells.length; i++) {
            PdfPCell c = new PdfPCell(new Phrase(clean(cells[i]), font(11, Font.BOLD, Color.BLACK)));
            c.setBorder(Rectangle.TOP);
            c.setBorderColorTop(PdfDocuments.NAVY);
            c.setBorderWidthTop(1.5f);
            c.setPadding(5);
            if (i == 1) {
                c.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            t.addCell(c);
        }
        doc.add(t);
        return this;
    }

    public final class Table {
        private final PdfPTable t;
        private final boolean[] right;

        private Table(float[] widths, String... headings) {
            t = new PdfPTable(widths);
            t.setWidthPercentage(100);
            t.setHeaderRows(1);
            t.setSpacingBefore(4);
            right = new boolean[headings.length];
            for (int i = 0; i < headings.length; i++) {
                String h = headings[i];
                right[i] = h.startsWith(">");
                PdfPCell c = new PdfPCell(new Phrase(clean(right[i] ? h.substring(1) : h), font(8.5f, Font.BOLD, Color.WHITE)));
                c.setBackgroundColor(PdfDocuments.NAVY);
                c.setBorder(Rectangle.NO_BORDER);
                c.setPadding(5);
                if (right[i]) {
                    c.setHorizontalAlignment(Element.ALIGN_RIGHT);
                }
                t.addCell(c);
            }
        }

        public Table row(Object... values) {
            for (int i = 0; i < values.length; i++) {
                Object v = values[i];
                String s = v == null ? "" : v instanceof BigDecimal b ? money(b) : v.toString();
                PdfPCell c = new PdfPCell(new Phrase(clean(s), font(8.5f, Font.NORMAL, Color.BLACK)));
                c.setBorder(Rectangle.BOTTOM);
                c.setBorderColorBottom(PdfDocuments.BORDER);
                c.setPadding(5);
                if (i < right.length && right[i]) {
                    c.setHorizontalAlignment(Element.ALIGN_RIGHT);
                }
                t.addCell(c);
            }
            return this;
        }

        public PdfBody end() throws DocumentException {
            doc.add(t);
            return PdfBody.this;
        }
    }

    private static final java.nio.charset.CharsetEncoder CP1252 = java.nio.charset.Charset.forName("windows-1252").newEncoder();

    /**
     * Text the PDF font can print (Western letters, "–", "·", "é", "ü" …). Arrows and other signs are written
     * the plain way ("→" becomes "->"); anything else unknown becomes "?".
     */
    public static synchronized String clean(String s) {
        if (s == null) {
            return "";
        }
        if (CP1252.canEncode(s)) {
            return s;
        }
        StringBuilder b = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            String c = new String(Character.toChars(cp));
            if (CP1252.canEncode(c)) {
                b.append(c);
            } else {
                b.append(switch (cp) {
                    case 0x2192 -> "->";
                    case 0x2190 -> "<-";
                    case 0x2248 -> "~";
                    case 0x2264 -> "<=";
                    case 0x2265 -> ">=";
                    case 0x2713, 0x2714 -> "OK";
                    case 0x2715, 0x2716 -> "x";
                    default -> "?";
                });
            }
        });
        return b.toString();
    }

    /** 352000 → "352,000.00". */
    public static String money(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return f.format(v.setScale(2, RoundingMode.HALF_UP));
    }
}
