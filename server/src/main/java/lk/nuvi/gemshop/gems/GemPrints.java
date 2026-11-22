package lk.nuvi.gemshop.gems;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.files.FileKind;
import lk.nuvi.gemshop.files.FileService;
import lk.nuvi.gemshop.files.FileStorage;
import lk.nuvi.gemshop.files.StoredFile;
import lk.nuvi.gemshop.files.codes.Codes;
import lk.nuvi.gemshop.files.pdf.PdfBody;
import lk.nuvi.gemshop.files.pdf.PdfDocuments;
import lk.nuvi.gemshop.gems.api.GemDtos.CertificateView;
import lk.nuvi.gemshop.gems.api.GemDtos.LabelSize;
import lk.nuvi.gemshop.gems.api.GemDtos.TagSheet;
import lk.nuvi.gemshop.gems.api.GemDtos.TagView;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gem tags (BRD C1) and the shop certificate (C10, rule 6).
 * <ul>
 *   <li>Tag: QR code + Code 39 barcode of the gem code (what the scanners read), shop name, gem name, carat,
 *       treatment, certificate and (if wanted) the selling price. Printed from a PDF the size of the label.</li>
 *   <li>Shop certificate: photo (or the gem drawing), gem details, code, certificate number, shop details,
 *       licence and a QR code to check it. It is not a laboratory report.</li>
 * </ul>
 */
@Service
public class GemPrints {

    private static final float MM = 72f / 25.4f;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color NAVY = PdfDocuments.NAVY;
    private static final Color GOLD = new Color(0xb8, 0x8a, 0x1e);

    private final Gems gems;
    private final ShopSettingsRepository shops;
    private final UserAccountRepository users;
    private final FileService files;
    private final FileStorage storage;
    private final Codes codes;
    private final PdfDocuments pdfs;
    private final ActivityLog log;
    private final ZoneId zone;

    public GemPrints(Gems gems, ShopSettingsRepository shops, UserAccountRepository users,
            FileService files, FileStorage storage, Codes codes, PdfDocuments pdfs, ActivityLog log, AppProperties app) {
        this.gems = gems;
        this.shops = shops;
        this.users = users;
        this.files = files;
        this.storage = storage;
        this.codes = codes;
        this.pdfs = pdfs;
        this.log = log;
        this.zone = ZoneId.of(app.timezone());
    }

    // ------------------------------------------------------------------ tags

    @Transactional(readOnly = true)
    public TagSheet tags(List<String> codeList) {
        return new TagSheet(shopName(), find(codeList).stream().map(GemPrints::tag).toList());
    }

    /**
     * The tags as a PDF the size of the label (one label per page), or a sheet of labels on A4. Each gem gets a
     * "Tag printed" line in its history.
     */
    @Transactional
    public byte[] tagsPdf(List<String> codeList, LabelSize size, boolean showPrice) {
        List<Gem> list = find(codeList);
        String shop = shopName();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        float w = size.widthMm() * MM, h = size.heightMm() * MM;
        boolean sheet = size == LabelSize.A4_SHEET;
        Rectangle page = sheet ? com.lowagie.text.PageSize.A4 : new Rectangle(w, h);
        Document doc = new Document(page, 0, 0, 0, 0);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.addTitle("Gem tags");
            doc.open();
            PdfContentByte cb = writer.getDirectContent();
            int cols = 3, rows = 8;
            float gapX = 5 * MM, gapY = 3 * MM;
            float left = (page.getWidth() - (cols * w + (cols - 1) * gapX)) / 2;
            float top = page.getHeight() - (page.getHeight() - (rows * h + (rows - 1) * gapY)) / 2;
            for (int i = 0; i < list.size(); i++) {
                if (sheet) {
                    int n = i % (cols * rows);
                    if (i > 0 && n == 0) {
                        doc.newPage();
                    }
                    float x = left + (n % cols) * (w + gapX);
                    float y = top - (n / cols + 1) * h - (n / cols) * gapY;
                    cutLines(cb, x, y, w, h);
                    label(cb, list.get(i), shop, showPrice, x, y, w, h);
                } else {
                    if (i > 0) {
                        doc.newPage();
                    }
                    label(cb, list.get(i), shop, showPrice, 0, 0, w, h);
                }
            }
            doc.close();
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Could not make the tags", e);
        }
        for (Gem g : list) {
            gems.addHistory(g, "Tag printed", "QR / barcode tag printed (" + size.label() + ")");
        }
        log.add("Tags printed", list.size() + " tag(s): " + String.join(", ", list.stream().map(Gem::getCode).limit(10).toList())
                + (list.size() > 10 ? " …" : ""), list.size() == 1 ? list.get(0).getBranch().getId() : null);
        return out.toByteArray();
    }

    /** One label: QR on the left, text on the right, barcode along the bottom (prototype tag-print.html). */
    private void label(PdfContentByte cb, Gem g, String shop, boolean showPrice, float x, float y, float w, float h)
            throws DocumentException, IOException {
        float k = h / (30 * MM); // scale: fonts and gaps follow the label height
        float pad = 1.5f * MM * k;
        float barH = h * 0.24f;
        float qr = h - barH - 2 * pad;
        Image q = Image.getInstance(codes.qr(g.getCode(), 300));
        q.scaleAbsolute(qr, qr);
        q.setAbsolutePosition(x + pad, y + barH + pad);
        cb.addImage(q);
        Image b = Image.getInstance(codes.barcode(g.getCode(), 700, 110));
        b.scaleAbsolute(w - 2 * pad, barH - pad * 0.5f);
        b.setAbsolutePosition(x + pad, y + pad * 0.5f);
        cb.addImage(b);

        float tx = x + pad + qr + pad, tw = x + w - pad - tx;
        float ty = y + h - pad;
        List<Object[]> lines = new ArrayList<>();
        lines.add(new Object[] {shop, PdfBody.font(5.6f * k, Font.BOLD, NAVY)});
        lines.add(new Object[] {g.getCode(), new Font(BaseFont.createFont(BaseFont.COURIER_BOLD, BaseFont.CP1252, false), 8.2f * k)});
        lines.add(new Object[] {g.getName(), PdfBody.font(6.2f * k, Font.NORMAL, Color.BLACK)});
        lines.add(new Object[] {carat(g) + " · " + g.getTreatment() + (g.getCertType() == CertType.LAB ? " · Lab cert" : ""),
                PdfBody.font(5.8f * k, Font.NORMAL, Color.BLACK)});
        if (showPrice) {
            lines.add(new Object[] {"LKR " + Gems.lkr(g.getPrice()), PdfBody.font(7.2f * k, Font.BOLD, Color.BLACK)});
        }
        for (Object[] l : lines) {
            Font f = (Font) l[1];
            ty -= f.getSize() * 1.22f;
            String text = fit(PdfBody.clean((String) l[0]), f, tw);
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(text, f), tx, ty, 0);
        }
    }

    private static void cutLines(PdfContentByte cb, float x, float y, float w, float h) {
        cb.saveState();
        cb.setColorStroke(new Color(0xdd, 0xdd, 0xdd));
        cb.setLineWidth(0.4f);
        cb.setLineDash(2, 2, 0);
        cb.rectangle(x, y, w, h);
        cb.stroke();
        cb.restoreState();
    }

    /** Shortens a text with "…" so it fits the width. */
    private static String fit(String s, Font f, float width) {
        BaseFont bf = f.getCalculatedBaseFont(false);
        float size = f.getSize();
        if (bf.getWidthPoint(s, size) <= width) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && bf.getWidthPoint(t + "…", size) > width) {
            t = t.substring(0, t.length() - 1);
        }
        return t.trim() + "…";
    }

    // ------------------------------------------------------------------ shop certificate

    @Transactional(readOnly = true)
    public CertificateView certificate(String code) {
        Gem g = gems.visible(code);
        ShopSettings shop = shops.current().orElse(null);
        String certNo = shopCertNo(g);
        String photo = photo(g) == null ? null : files.link(photo(g), false);
        return new CertificateView(g.getCode(), g.getName(), g.getGemType(), g.getProductType(), g.getCarat(), g.getColour(),
                g.getShape(), g.getTreatment(), g.getDescription(), g.getCertType(), certNo, photo, shopName(), logoUrl(shop),
                shop == null ? null : shop.getAddress(), shop == null ? null : shop.getPhone(),
                shop == null ? null : shop.getNgjaLicenceNo(), shop == null ? null : shop.getNgjaLicenceExpiry(),
                LocalDate.now(zone), signedBy(), g.getCode() + "|" + certNo);
    }

    @Transactional(readOnly = true)
    public byte[] certificatePdf(String code) {
        CertificateView c = certificate(code);
        Gem g = gems.visible(code);
        StoredFile photo = photo(g);
        byte[] picture = photo != null && storage.exists(photo.getStorageKey()) ? storage.read(photo.getStorageKey())
                : GemDrawing.png(g.getGemType(), 600);
        String footer = "Scan the QR code" + (c.shopPhone() == null ? "" : " or call " + c.shopPhone())
                + " to check this certificate. This shop certificate is not a laboratory report.";
        return pdfs.render(new PdfDocuments.Spec("GEMSTONE CERTIFICATE",
                List.of(new PdfDocuments.Line("Certificate No", c.certNo()), new PdfDocuments.Line("Gem code", c.code()),
                        new PdfDocuments.Line("Issued", DATE.format(c.issuedOn()))), footer, false, null), body -> {
            Document doc = body.document();
            PdfPTable t = new PdfPTable(new float[] {36, 64});
            t.setWidthPercentage(100);
            t.setSpacingBefore(6);
            Image img = Image.getInstance(picture);
            img.scaleToFit(190, 190);
            PdfPCell pic = new PdfPCell(img, false);
            pic.setBorderColor(PdfDocuments.BORDER);
            pic.setPadding(6);
            pic.setHorizontalAlignment(Element.ALIGN_CENTER);
            pic.setVerticalAlignment(Element.ALIGN_MIDDLE);
            t.addCell(pic);
            PdfPTable d = new PdfPTable(new float[] {34, 66});
            d.setWidthPercentage(100);
            for (String[] r : details(c)) {
                d.addCell(cell(r[0], PdfBody.font(10, Font.NORMAL, PdfDocuments.GREY)));
                d.addCell(cell(r[1], PdfBody.font(10, Font.BOLD, Color.BLACK)));
            }
            PdfPCell dc = new PdfPCell(d);
            dc.setBorder(Rectangle.NO_BORDER);
            dc.setPaddingLeft(14);
            dc.setVerticalAlignment(Element.ALIGN_MIDDLE);
            t.addCell(dc);
            doc.add(t);

            Paragraph p = new Paragraph(PdfBody.clean("We certify that the gemstone described above is a genuine natural "
                    + c.gemType().toLowerCase(Locale.ROOT) + ", examined by our gemmologist. Treatment status is disclosed "
                    + "as above."), PdfBody.font(10, Font.NORMAL, Color.BLACK));
            p.setSpacingBefore(14);
            p.setSpacingAfter(18);
            doc.add(p);

            PdfPTable foot = new PdfPTable(new float[] {40, 20, 40});
            foot.setWidthPercentage(100);
            Paragraph lic = new Paragraph();
            Font small = PdfBody.font(8.5f, Font.NORMAL, PdfDocuments.GREY);
            lic.add(new Chunk("Issued " + DATE.format(c.issuedOn()), small));
            if (c.licenceNo() != null) {
                lic.add(Chunk.NEWLINE);
                lic.add(new Chunk(PdfBody.clean("NGJA dealer licence " + c.licenceNo()), small));
            }
            if (c.licenceExpiry() != null) {
                lic.add(Chunk.NEWLINE);
                lic.add(new Chunk("Valid until " + DATE.format(c.licenceExpiry()), small));
            }
            PdfPCell lc = new PdfPCell(lic);
            lc.setBorder(Rectangle.NO_BORDER);
            lc.setVerticalAlignment(Element.ALIGN_BOTTOM);
            foot.addCell(lc);
            Image qr = Image.getInstance(codes.qr(c.qrText(), 300));
            qr.scaleAbsolute(72, 72);
            PdfPCell qc = new PdfPCell(qr, false);
            qc.setBorder(Rectangle.NO_BORDER);
            qc.setHorizontalAlignment(Element.ALIGN_CENTER);
            foot.addCell(qc);
            Paragraph sign = new Paragraph();
            sign.setAlignment(Element.ALIGN_CENTER);
            sign.add(new Chunk(PdfBody.clean(c.signedBy()), PdfBody.font(9, Font.BOLD, Color.BLACK)));
            sign.add(Chunk.NEWLINE);
            sign.add(new Chunk("Owner & gemmologist", small));
            PdfPCell sc = new PdfPCell(sign);
            sc.setBorder(Rectangle.TOP);
            sc.setBorderColorTop(Color.DARK_GRAY);
            sc.setPaddingTop(4);
            sc.setHorizontalAlignment(Element.ALIGN_CENTER);
            sc.setVerticalAlignment(Element.ALIGN_BOTTOM);
            PdfPTable signBox = new PdfPTable(1);
            signBox.setWidthPercentage(80);
            PdfPCell space = new PdfPCell(new Phrase(" "));
            space.setBorder(Rectangle.NO_BORDER);
            space.setFixedHeight(46);
            signBox.addCell(space);
            signBox.addCell(sc);
            PdfPCell sbc = new PdfPCell(signBox);
            sbc.setBorder(Rectangle.NO_BORDER);
            sbc.setVerticalAlignment(Element.ALIGN_BOTTOM);
            foot.addCell(sbc);
            doc.add(foot);

            Paragraph seal = new Paragraph(PdfBody.clean(c.shopName().toUpperCase(Locale.ROOT) + " · SHOP CERTIFICATE"),
                    PdfBody.font(8, Font.BOLD, GOLD));
            seal.setAlignment(Element.ALIGN_CENTER);
            seal.setSpacingBefore(16);
            doc.add(seal);
        });
    }

    private static List<String[]> details(CertificateView c) {
        List<String[]> r = new ArrayList<>();
        r.add(new String[] {"Gem type", c.gemType()});
        r.add(new String[] {"Product", c.productType() + " - " + c.name()});
        r.add(new String[] {"Weight", c.carat().toPlainString() + " ct (" + c.carat().multiply(new java.math.BigDecimal("0.2"))
                .setScale(3, java.math.RoundingMode.HALF_UP).toPlainString() + " g)"});
        r.add(new String[] {"Colour", c.colour()});
        r.add(new String[] {"Shape / cut", c.shape()});
        r.add(new String[] {"Treatment", c.treatment()});
        if (c.description() != null && !c.description().isBlank()) {
            r.add(new String[] {"Description", c.description()});
        }
        return r;
    }

    private static PdfPCell cell(String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(PdfBody.clean(text), f));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(PdfDocuments.BORDER);
        c.setPaddingTop(5);
        c.setPaddingBottom(5);
        return c;
    }

    // ------------------------------------------------------------------ helpers

    /** Gems in the order asked, each once; unknown codes or another branch's gems stop the call. */
    private List<Gem> find(List<String> codeList) {
        Set<String> seen = new LinkedHashSet<>();
        List<Gem> out = new ArrayList<>();
        for (String c : codeList) {
            if (c == null || c.isBlank()) {
                continue;
            }
            Gem g = gems.visible(c.trim());
            if (seen.add(g.getCode())) {
                out.add(g);
            }
        }
        return out;
    }

    /** The shop certificate number (gems with a lab or no certificate get one made the same way). */
    /** The gem's first photo, or a drawing of the gem – for documents such as the invoice PDF (P3-02). */
    public byte[] picture(Gem g, int size) {
        StoredFile photo = photo(g);
        return photo != null && storage.exists(photo.getStorageKey()) ? storage.read(photo.getStorageKey())
                : GemDrawing.png(g.getGemType(), size);
    }

    /** Certificate number shown on documents: the lab number, or the shop certificate number. */
    public String certNo(Gem g) {
        return g.getCertType() == CertType.LAB ? g.getCertNo() : g.getCertType() == CertType.SHOP ? shopCertNo(g) : null;
    }

    private String shopCertNo(Gem g) {
        return g.getCertType() == CertType.SHOP && g.getCertNo() != null ? g.getCertNo() : gems.shopCertNo(g.getCode());
    }

    private StoredFile photo(Gem g) {
        return files.ofOwner(Gems.OWNER_TYPE, g.getCode()).stream().filter(f -> f.getKind() == FileKind.PRODUCT_PHOTO)
                .findFirst().orElse(null);
    }

    /** Link to the shop's uploaded logo, or null = the standard logo. */
    public String logoUrl(ShopSettings shop) {
        if (shop == null || shop.getLogoFile() == null) {
            return null;
        }
        try {
            return files.get(java.util.UUID.fromString(shop.getLogoFile())).url();
        } catch (RuntimeException e) {
            return null; // not an upload any more – the standard logo is shown
        }
    }

    private String shopName() {
        return shops.current().map(ShopSettings::getName).orElse("Gem Shop");
    }

    /** The owner who signs: the first active owner login. */
    private String signedBy() {
        return users.findAll().stream().filter(u -> u.isActive() && u.getRole() == Role.OWNER)
                .min(Comparator.comparing(UserAccount::getId)).map(UserAccount::getName).orElse("Owner");
    }

    private static TagView tag(Gem g) {
        return new TagView(g.getCode(), g.getName(), g.getGemType(), g.getProductType(), g.getCarat(), g.getTreatment(),
                g.getCertType(), g.getPrice(), g.getStatus());
    }

    private static String carat(Gem g) {
        return g.getCarat().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " ct";
    }
}
