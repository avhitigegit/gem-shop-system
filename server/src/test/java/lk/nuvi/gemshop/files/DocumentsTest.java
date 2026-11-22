package lk.nuvi.gemshop.files;

import static lk.nuvi.gemshop.SignIn.OWNER;
import static lk.nuvi.gemshop.SignIn.SALES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.oned.Code39Reader;
import com.google.zxing.qrcode.QRCodeReader;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import javax.imageio.ImageIO;
import lk.nuvi.gemshop.SignIn;
import lk.nuvi.gemshop.TestcontainersConfiguration;
import lk.nuvi.gemshop.files.codes.Codes;
import lk.nuvi.gemshop.files.excel.ExcelExport;
import lk.nuvi.gemshop.files.pdf.PdfBody;
import lk.nuvi.gemshop.files.pdf.PdfDocuments;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** P1-05: PDFs with the shop header and footer, QR / barcodes, Excel exports. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DocumentsTest {

    @Autowired PdfDocuments pdfs;
    @Autowired ExcelExport excel;
    @Autowired Codes codes;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;

    // ---------------------------------------------------------------- PDF

    @Test
    void pdfHasTheShopHeaderAndAFooterOnEveryPage() throws Exception {
        byte[] pdf = pdfs.render(new PdfDocuments.Spec("INVOICE",
                        List.of(new PdfDocuments.Line("Invoice No", "INV-000141"), new PdfDocuments.Line("Date", "22/11/2026")),
                        "Returns accepted within 14 days with this invoice. Thank you for shopping at Ceylon Gem House, Ella!",
                        false, "CANCELLED"),
                body -> {
                    var t = body.table(new float[] {10, 60, 30}, "#", "Gem details", ">Amount");
                    for (int i = 1; i <= 80; i++) {
                        t.row(i, "Blue Sapphire Ring · 2.10 ct → Müller", new BigDecimal("352000"));
                    }
                    t.end();
                    body.total("TOTAL", "LKR 28,160,000.00");
                });
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        PdfReader reader = new PdfReader(pdf);
        int pages = reader.getNumberOfPages();
        assertThat(pages).isGreaterThan(1);
        PdfTextExtractor text = new PdfTextExtractor(reader);
        String first = text.getTextFromPage(1);
        assertThat(first).contains("Ceylon Gem House").contains("INVOICE").contains("INV-000141")
                .contains("NGJA Licence").contains("NGJA/DL/2024/0457").contains("42 Main Street")
                .contains("Page 1 of " + pages).contains("Returns accepted within 14 days")
                .contains("352,000.00").contains("-> Müller");
        String last = text.getTextFromPage(pages);
        assertThat(last).contains("Page " + pages + " of " + pages).contains("TOTAL").contains("Returns accepted");
        reader.close();
    }

    @Test
    void textThePdfFontCannotPrintIsWrittenPlainly() {
        assertThat(PdfBody.clean("GEM-00111 carat 5.10 → 5.01")).isEqualTo("GEM-00111 carat 5.10 -> 5.01");
        assertThat(PdfBody.clean("≈ USD 1,173 · EUR 1,083 – Müller •")).isEqualTo("~ USD 1,173 · EUR 1,083 – Müller •");
        assertThat(PdfBody.clean("日本")).isEqualTo("??");
        assertThat(PdfBody.clean(null)).isEmpty();
        assertThat(PdfBody.money(new BigDecimal("1152000"))).isEqualTo("1,152,000.00");
    }

    // ---------------------------------------------------------------- QR / barcode

    @Test
    void qrCodeAndBarcodeCanBeScanned() throws Exception {
        var qr = ImageIO.read(new ByteArrayInputStream(codes.qr("https://ceylongemhouse.lk/c/GEM-00131", 240)));
        assertThat(new QRCodeReader().decode(bitmap(qr)).getText()).isEqualTo("https://ceylongemhouse.lk/c/GEM-00131");
        var bar = ImageIO.read(new ByteArrayInputStream(codes.barcode("gem-00131", 360, 80)));
        assertThat(new Code39Reader().decode(bitmap(bar)).getText()).isEqualTo("GEM-00131");
    }

    @Test
    void codePicturesFromTheApi() throws Exception {
        String sales = SignIn.login(mvc, SALES).token();
        mvc.perform(get("/api/codes/qr").param("text", "GEM-00131").header("Authorization", "Bearer " + sales))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"));
        mvc.perform(get("/api/codes/barcode").param("text", "GEM-00131").header("Authorization", "Bearer " + sales))
                .andExpect(status().isOk());
        mvc.perform(get("/api/codes/barcode").param("text", "Ruby ♥").header("Authorization", "Bearer " + sales))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_TEXT"));
        mvc.perform(get("/api/codes/qr").param("text", "x".repeat(400)).header("Authorization", "Bearer " + sales))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/codes/qr").param("text", "GEM-00131")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- Excel

    @Test
    void excelHasRealNumbersAndDates() throws Exception {
        record Line(String code, BigDecimal amount, java.time.Instant at) {}
        byte[] xlsx = excel.write("Sales: Nov/2026", List.of(
                ExcelExport.Column.<Line>text("Invoice", 14, Line::code),
                ExcelExport.Column.<Line>money("Amount (LKR)", 16, Line::amount),
                ExcelExport.Column.<Line>dateTime("When", 18, Line::at)),
                List.of(new Line("INV-000138", new BigDecimal("352000.00"), java.time.Instant.parse("2026-11-22T07:53:00Z")),
                        new Line("INV-000139", new BigDecimal("363000.50"), null)));
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("Sales  Nov 2026");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Amount (LKR)");
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(352000.0);
            assertThat(sheet.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(363000.5);
            // 07:53 UTC = 13:23 in Sri Lanka
            assertThat(sheet.getRow(1).getCell(2).getLocalDateTimeCellValue()).isEqualTo("2026-11-22T13:23");
            assertThat(sheet.getRow(2).getCell(2)).isNull();
        }
    }

    // ---------------------------------------------------------------- activity log export (first real use)

    @Test
    void activityLogExports() throws Exception {
        jdbc.update("update app_user set failed_logins = 0, locked_until = null, active = true, password_hash = ?",
                encoder.encode(SignIn.PASSWORD));
        String owner = SignIn.login(mvc, OWNER).token();
        byte[] pdf = mvc.perform(get("/api/activity-log/export").param("format", "pdf").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Activity log")))
                .andReturn().getResponse().getContentAsByteArray();
        PdfReader reader = new PdfReader(pdf);
        assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("ACTIVITY LOG").contains("Sale completed");
        reader.close();
        byte[] xlsx = mvc.perform(get("/api/activity-log/export").param("format", "xlsx").param("q", "day closed")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Action");
            assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(10);
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("Day closed");
        }
        mvc.perform(get("/api/activity-log/export").param("format", "doc").header("Authorization", "Bearer " + owner))
                .andExpect(status().isBadRequest());
        String sales = SignIn.login(mvc, SALES).token();
        mvc.perform(get("/api/activity-log/export").param("format", "pdf").header("Authorization", "Bearer " + sales))
                .andExpect(status().isForbidden());
    }

    private static BinaryBitmap bitmap(java.awt.image.BufferedImage img) {
        return new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img)));
    }
}
