package lk.nuvi.gemshop.files.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import lk.nuvi.gemshop.files.FileStorage;
import lk.nuvi.gemshop.files.StoredFileRepository;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Printable documents (invoice, credit note, quotation, purchase, reports, exports) with the shop's header and
 * footer, as on the prototype's documents ({@code UI.docHead}):
 * <ul>
 *   <li>Header: logo, shop name, tagline, address, phone · email, NGJA licence (if "print licence" is on);
 *       on the right the document title (e.g. INVOICE) and its lines (number, date, …); a navy line below.</li>
 *   <li>Footer on every page: the document's footer text, the website and "Page 1 of 2".</li>
 *   <li>Optional watermark, e.g. CANCELLED.</li>
 * </ul>
 * Use: {@code pdf.render(new PdfDocuments.Spec("INVOICE", List.of(new Line("Invoice No", "INV-000141"))), body -> { … })}.
 */
@Component
public class PdfDocuments {

    public static final Color NAVY = new Color(0x13, 0x21, 0x3f);
    public static final Color SAPPHIRE = new Color(0x1f, 0x4f, 0xa3);
    public static final Color GREY = new Color(0x55, 0x55, 0x55);
    public static final Color BORDER = new Color(0xe5, 0xe7, 0xeb);

    private final ShopSettingsRepository shops;
    private final StoredFileRepository files;
    private final FileStorage storage;
    private final byte[] defaultLogo;

    public PdfDocuments(ShopSettingsRepository shops, StoredFileRepository files, FileStorage storage) {
        this.shops = shops;
        this.files = files;
        this.storage = storage;
        try (InputStream in = new ClassPathResource("pdf/logo.png").getInputStream()) {
            this.defaultLogo = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One line on the right of the header, e.g. "Invoice No" / "INV-000141". */
    public record Line(String label, String value) {}

    /**
     * @param title     big title on the right (INVOICE, CREDIT NOTE, ACTIVITY LOG, …)
     * @param lines     lines under the title
     * @param footer    text at the bottom of every page (return policy, licence, thank you …), or null
     * @param landscape wide pages for long tables
     * @param watermark big light text across each page (e.g. CANCELLED), or null
     */
    public record Spec(String title, List<Line> lines, String footer, boolean landscape, String watermark) {
        public Spec(String title, List<Line> lines) {
            this(title, lines, null, false, null);
        }
    }

    @FunctionalInterface
    public interface Body {
        void write(PdfBody body) throws DocumentException, IOException;
    }

    @Transactional(readOnly = true)
    public byte[] render(Spec spec, Body body) {
        ShopSettings shop = shops.current().orElse(null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Rectangle size = spec.landscape() ? PageSize.A4.rotate() : PageSize.A4;
        Document doc = new Document(size, 36, 36, 36, 54);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Footer(spec.footer(), shop == null ? null : shop.getWebsite(), spec.watermark()));
            doc.addTitle(spec.title());
            doc.addCreator(shop == null ? "Gem Shop System" : shop.getName());
            doc.open();
            doc.add(header(shop, spec));
            body.write(new PdfBody(doc));
            doc.close();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Could not make the PDF", e);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------ header

    private PdfPTable header(ShopSettings shop, Spec spec) throws IOException {
        PdfPTable t = new PdfPTable(new float[] {62, 38});
        t.setWidthPercentage(100);
        t.setSpacingAfter(14);

        PdfPTable left = new PdfPTable(new float[] {15, 85});
        left.setWidthPercentage(100);
        Image logo = Image.getInstance(logo(shop));
        logo.scaleToFit(48, 48);
        PdfPCell logoCell = new PdfPCell(logo, false);
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setPaddingRight(8);
        left.addCell(logoCell);
        Paragraph shopText = new Paragraph();
        shopText.add(new Chunk(PdfBody.clean(shop == null ? "Gem Shop System" : shop.getName()), PdfBody.font(17, Font.BOLD, NAVY)));
        Font small = PdfBody.font(8.5f, Font.NORMAL, GREY);
        if (shop != null) {
            for (String s : new String[] {shop.getTagline(), shop.getAddress(), join(shop.getPhone(), shop.getEmail())}) {
                if (s != null && !s.isBlank()) {
                    shopText.add(Chunk.NEWLINE);
                    shopText.add(new Chunk(PdfBody.clean(s), small));
                }
            }
            if (shop.isPrintLicenceOnInvoice() && shop.getNgjaLicenceNo() != null) {
                shopText.add(Chunk.NEWLINE);
                shopText.add(new Chunk("NGJA Licence: ", PdfBody.font(8.5f, Font.BOLD, GREY)));
                shopText.add(new Chunk(PdfBody.clean(shop.getNgjaLicenceNo()), small));
            }
        }
        PdfPCell textCell = new PdfPCell(shopText);
        textCell.setBorder(Rectangle.NO_BORDER);
        left.addCell(textCell);
        PdfPCell leftCell = new PdfPCell(left);
        style(leftCell);
        t.addCell(leftCell);

        Paragraph right = new Paragraph();
        right.setAlignment(Element.ALIGN_RIGHT);
        Chunk title = new Chunk(PdfBody.clean(spec.title()), PdfBody.font(16, Font.BOLD, SAPPHIRE));
        title.setCharacterSpacing(0.6f);
        right.add(title);
        for (Line l : spec.lines()) {
            right.add(Chunk.NEWLINE);
            right.add(new Chunk(PdfBody.clean(l.label()) + ": ", PdfBody.font(8.5f, Font.BOLD, Color.BLACK)));
            right.add(new Chunk(PdfBody.clean(l.value()), PdfBody.font(8.5f, Font.NORMAL, Color.BLACK)));
        }
        PdfPCell rightCell = new PdfPCell(right);
        style(rightCell);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(rightCell);
        return t;
    }

    private static void style(PdfPCell c) {
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColorBottom(NAVY);
        c.setBorderWidthBottom(2.2f);
        c.setPaddingBottom(10);
        c.setVerticalAlignment(Element.ALIGN_TOP);
    }

    /** The shop's own logo (Settings → Shop details: an uploaded LOGO file), or the Gem Shop logo. */
    private byte[] logo(ShopSettings shop) {
        if (shop != null && shop.getLogoFile() != null) {
            try {
                UUID id = UUID.fromString(shop.getLogoFile());
                var f = files.findByPublicId(id);
                if (f.isPresent() && storage.exists(f.get().getStorageKey())) {
                    return storage.read(f.get().getStorageKey());
                }
            } catch (IllegalArgumentException ignored) {
                // not an uploaded file id – use the default logo
            }
        }
        return defaultLogo;
    }

    private static String join(String a, String b) {
        boolean ha = a != null && !a.isBlank();
        boolean hb = b != null && !b.isBlank();
        return ha && hb ? a + " · " + b : ha ? a : hb ? b : null;
    }

    // ------------------------------------------------------------------ footer + watermark

    private static final class Footer extends PdfPageEventHelper {
        private final String text;
        private final String website;
        private final String watermark;
        private PdfTemplate total;
        private BaseFont base;

        Footer(String text, String website, String watermark) {
            this.text = text;
            this.website = website;
            this.watermark = watermark;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(30, 12);
            try {
                base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Rectangle page = document.getPageSize();
            float left = document.leftMargin();
            float right = page.getWidth() - document.rightMargin();
            if (watermark != null) {
                PdfContentByte under = writer.getDirectContentUnder();
                under.saveState();
                PdfGState gs = new PdfGState();
                gs.setFillOpacity(0.12f);
                under.setGState(gs);
                ColumnText.showTextAligned(under, Element.ALIGN_CENTER,
                        new Phrase(watermark, PdfBody.font(90, Font.BOLD, new Color(0xb9, 0x1c, 0x1c))),
                        page.getWidth() / 2, page.getHeight() / 2, 35);
                under.restoreState();
            }
            cb.saveState();
            cb.setColorStroke(BORDER);
            cb.setLineWidth(0.6f);
            cb.moveTo(left, 46);
            cb.lineTo(right, 46);
            cb.stroke();
            cb.restoreState();
            if (text != null && !text.isBlank()) {
                ColumnText ct = new ColumnText(cb);
                ct.setSimpleColumn(new Phrase(PdfBody.clean(text), PdfBody.font(7.5f, Font.NORMAL, GREY)), left, 16, right - 70, 44, 9,
                        Element.ALIGN_LEFT);
                try {
                    ct.go();
                } catch (DocumentException e) {
                    throw new IllegalStateException(e);
                }
            } else if (website != null && !website.isBlank()) {
                ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(PdfBody.clean(website), PdfBody.font(7.5f, Font.NORMAL, GREY)),
                        left, 34, 0);
            }
            String label = "Page " + writer.getPageNumber() + " of ";
            float w = base.getWidthPoint(label, 7.5f);
            cb.beginText();
            cb.setFontAndSize(base, 7.5f);
            cb.setColorFill(GREY);
            cb.setTextMatrix(right - 30 - w, 34);
            cb.showText(label);
            cb.endText();
            cb.addTemplate(total, right - 30, 34);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(base, 7.5f);
            total.setColorFill(GREY);
            total.setTextMatrix(0, 0);
            total.showText(String.valueOf(writer.getPageNumber() - 1)); // the counter has already moved past the last page
            total.endText();
        }
    }
}
