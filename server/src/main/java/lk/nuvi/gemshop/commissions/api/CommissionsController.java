package lk.nuvi.gemshop.commissions.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.commissions.Commissions;
import lk.nuvi.gemshop.commissions.Guides;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.CommissionRow;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.CommissionSaleList;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.CommissionSlip;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.GuideCard;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.GuideForm;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.GuideList;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.GuideProfile;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.PaidView;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.PayForm;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.Payable;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.PayableShow;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.SaleCommission;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.files.Downloads;
import lk.nuvi.gemshop.files.excel.ExcelExport;
import lk.nuvi.gemshop.files.pdf.PdfDocuments;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Guides & drivers, commission per sale, paying commission and the commission slip (P3-05). */
@RestController
@RequestMapping("/api")
@Tag(name = "Commissions")
public class CommissionsController {

    private final Guides guides;
    private final Commissions commissions;
    private final PdfDocuments pdfs;
    private final ExcelExport excel;

    public CommissionsController(Guides guides, Commissions commissions, PdfDocuments pdfs, ExcelExport excel) {
        this.guides = guides;
        this.commissions = commissions;
        this.pdfs = pdfs;
        this.excel = excel;
    }

    // ------------------------------------------------------------------ guides & drivers

    @GetMapping("/guides")
    @PreAuthorize(Roles.OWNER_SALES_ACCOUNTANT)
    @Operation(operationId = "listGuides", summary = "Guides & drivers with sales brought (amounts for owner / accountant)")
    public GuideList listGuides() {
        return guides.list();
    }

    @PostMapping("/guides")
    @PreAuthorize(Roles.OWNER)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addGuide", summary = "Add a guide / driver (empty % = the default %)")
    public GuideCard addGuide(@Valid @RequestBody GuideForm form) {
        return guides.create(form);
    }

    @GetMapping("/guides/{code}")
    @PreAuthorize(Roles.OWNER_SALES_ACCOUNTANT)
    @Operation(operationId = "getGuide", summary = "One guide / driver with commission in a period (default last 30 days)")
    public GuideProfile guide(@PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return guides.profile(code, from, to);
    }

    @PutMapping("/guides/{code}")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "editGuide", summary = "Edit a guide / driver (a new % applies to new sales)")
    public GuideCard editGuide(@PathVariable String code, @Valid @RequestBody GuideForm form) {
        return guides.update(code, form);
    }

    // ------------------------------------------------------------------ commission per sale

    @GetMapping("/commissions/sales")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "commissionSales", summary = "Sales with their commission (filters: from, to, guide code or \"walkin\", staff id)")
    public CommissionSaleList commissionSales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String guide, @RequestParam(required = false) Long staff) {
        return commissions.sales(from, to, guide, staff);
    }

    @GetMapping("/commissions/sales/{number}")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "saleCommission", summary = "How the commission of one sale was worked out")
    public SaleCommission saleCommission(@PathVariable String number) {
        return commissions.sale(number);
    }

    // ------------------------------------------------------------------ pay

    @GetMapping("/commissions")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "commissionsPayable", summary = "Commission owed by person, and the lines (show: OWED, PAID, REVERSED, ALL)")
    public Payable payable(@RequestParam(defaultValue = "OWED") PayableShow show, @RequestParam(required = false) String person) {
        return commissions.payable(show, person);
    }

    @GetMapping("/commissions/export")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "exportCommissions", summary = "The commission lines shown as PDF or Excel")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "pdf") String format,
            @RequestParam(defaultValue = "OWED") PayableShow show, @RequestParam(required = false) String person) {
        List<CommissionRow> rows = commissions.payable(show, person).lines();
        String name = Downloads.dated("Commissions payable");
        if ("xlsx".equalsIgnoreCase(format)) {
            return Downloads.xlsx(excel.write("Commissions", List.of(
                    ExcelExport.Column.<CommissionRow>dateTime("Date", 18, CommissionRow::earnedAt),
                    ExcelExport.Column.<CommissionRow>text("Invoice", 14, CommissionRow::sale),
                    ExcelExport.Column.<CommissionRow>text("Payee", 26, CommissionRow::name),
                    ExcelExport.Column.<CommissionRow>text("Type", 14, CommissionsController::type),
                    ExcelExport.Column.<CommissionRow>money("Amount (LKR)", 16, CommissionRow::amount),
                    ExcelExport.Column.<CommissionRow>text("Status", 16, r -> Commissions.status(r.status())),
                    ExcelExport.Column.<CommissionRow>text("Paid", 26, CommissionsController::paid)), rows), name);
        }
        if (!"pdf".equalsIgnoreCase(format)) {
            throw ApiException.badRequest("BAD_FORMAT", "Format must be pdf or xlsx.");
        }
        byte[] pdf = pdfs.render(new PdfDocuments.Spec("COMMISSIONS PAYABLE", List.of(new PdfDocuments.Line("Lines",
                String.valueOf(rows.size()))), "Commission is calculated per sale (rule 5).", true, null), body -> {
                    var t = body.table(new float[] {14, 13, 24, 13, 14, 22}, "Date", "Invoice", "Payee", "Type", ">Amount", "Status");
                    for (CommissionRow r : rows) {
                        t.row(commissions.when(r.earnedAt()), r.sale(), r.name(), type(r), r.amount(),
                                Commissions.status(r.status()) + (r.paidAt() == null ? "" : " · " + paid(r)));
                    }
                    t.end();
                });
        return Downloads.pdf(pdf, name, true);
    }

    @PostMapping("/commissions/pay")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "payCommissions", summary = "Record a commission payment for the chosen lines (CP-…)")
    public PaidView pay(@Valid @RequestBody PayForm form) {
        return commissions.pay(form);
    }

    // ------------------------------------------------------------------ slip

    @GetMapping("/commissions/slips/{id}")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "commissionSlip", summary = "A payment slip (CP-…) or a guide's statement of the last 30 days (G-…)")
    public CommissionSlip slip(@PathVariable String id) {
        return commissions.slip(id);
    }

    @GetMapping("/commissions/slips/{id}/pdf")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "commissionSlipPdf", summary = "The commission slip as a PDF")
    public ResponseEntity<byte[]> slipPdf(@PathVariable String id, @RequestParam(defaultValue = "true") boolean download) {
        return Downloads.pdf(commissions.slipPdf(id), "Commission slip " + id.trim().toUpperCase(Locale.ROOT), download);
    }

    private static String type(CommissionRow r) {
        return r.payee() == lk.nuvi.gemshop.commissions.CommissionLine.Payee.GUIDE ? "Guide/driver" : "Staff";
    }

    private static String paid(CommissionRow r) {
        return r.paidAt() == null ? "" : (r.method() == null ? "" : r.method()) + (r.payment() == null ? "" : " · " + r.payment());
    }
}
