package lk.nuvi.gemshop.reports.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.expenses.Expenses;
import lk.nuvi.gemshop.reports.ReportFiles;
import lk.nuvi.gemshop.reports.ReportSheets;
import lk.nuvi.gemshop.reports.Reports;
import lk.nuvi.gemshop.reports.Reports.SalesFilter;
import lk.nuvi.gemshop.reports.api.ReportDtos.CashReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.CommissionReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.ProfitReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.PurchaseReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.ReportsSummary;
import lk.nuvi.gemshop.reports.api.ReportDtos.SalesBy;
import lk.nuvi.gemshop.reports.api.ReportDtos.SalesReport;
import lk.nuvi.gemshop.reports.api.ReportDtos.StockReport;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports (prototype hub/reports.html, l-reports/*; Guide 63–66, 68, 70, 71; BRD L1–L6). Owner and accountant; the stock
 * report shows cost prices, so it is the owner's only (rule 1). Every report has an {@code /export?format=pdf|xlsx}
 * with the same filters. The branch at the top filters.
 */
@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports")
public class ReportsController {

    private final Reports reports;
    private final ReportSheets sheets;
    private final ReportFiles files;
    private final Expenses expenses;

    public ReportsController(Reports reports, ReportSheets sheets, ReportFiles files, Expenses expenses) {
        this.reports = reports;
        this.sheets = sheets;
        this.files = files;
        this.expenses = expenses;
    }

    @GetMapping("/summary")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "reportsSummary", summary = "Reports home: today's sales")
    public ReportsSummary summary() {
        return reports.summary();
    }

    // ------------------------------------------------------------------ sales

    @GetMapping("/sales")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "salesReport", summary = "Sales by day, month, branch, gem type, salesperson, guide, payment or country")
    public SalesReport sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String gemType,
            @Parameter(description = "A salesperson's user id") @RequestParam(required = false) Long staff,
            @Parameter(description = "A guide code, or \"walkin\" for sales without a guide") @RequestParam(required = false) String guide,
            @Parameter(description = "Card, Cash LKR, Cash USD, Cash EUR, Credit note or Split") @RequestParam(required = false) String pay,
            @RequestParam(defaultValue = "DAY") SalesBy by) {
        return reports.sales(reports.period(from, to, 29), new SalesFilter(gemType, staff, guide, pay, by));
    }

    @GetMapping("/sales/export")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "exportSalesReport", summary = "The sales report as PDF or Excel")
    public ResponseEntity<byte[]> salesExport(@Parameter(description = "pdf or xlsx") @RequestParam String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String gemType, @RequestParam(required = false) Long staff,
            @RequestParam(required = false) String guide, @RequestParam(required = false) String pay,
            @RequestParam(defaultValue = "DAY") SalesBy by) {
        return files.download(format, sheets.sales(sales(from, to, gemType, staff, guide, pay, by)), reports.zone());
    }

    // ------------------------------------------------------------------ profit

    @GetMapping("/profit")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "profitReport", summary = "Per gem sold: selling price − cost − commission share")
    public ProfitReport profit(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String gemType) {
        return reports.profit(reports.period(from, to, 29), gemType);
    }

    @GetMapping("/profit/export")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "exportProfitReport", summary = "The profit report as PDF or Excel")
    public ResponseEntity<byte[]> profitExport(@Parameter(description = "pdf or xlsx") @RequestParam String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String gemType) {
        return files.download(format, sheets.profit(profit(from, to, gemType)), reports.zone());
    }

    // ------------------------------------------------------------------ daily cash

    @GetMapping("/daily-cash")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "dailyCashReport", summary = "Every day closing with differences highlighted")
    public CashReport dailyCash(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Only days that did not match") @RequestParam(defaultValue = "false") boolean onlyDifferences) {
        return reports.dailyCash(reports.period(from, to, 30), onlyDifferences);
    }

    @GetMapping("/daily-cash/export")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "exportDailyCashReport", summary = "The daily cash report as PDF or Excel")
    public ResponseEntity<byte[]> dailyCashExport(@Parameter(description = "pdf or xlsx") @RequestParam String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean onlyDifferences) {
        return files.download(format, sheets.dailyCash(dailyCash(from, to, onlyDifferences)), reports.zone());
    }

    // ------------------------------------------------------------------ stock

    @GetMapping("/stock")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "stockReport", summary = "Stock with photos, days in stock and value; slow-moving gems")
    public StockReport stock(@RequestParam(required = false) String gemType,
            @Parameter(description = "Only gems not sold for more than slowDays") @RequestParam(defaultValue = "false") boolean slow,
            @RequestParam(defaultValue = "60") int slowDays) {
        return reports.stock(gemType, slow, slowDays);
    }

    @GetMapping("/stock/export")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "exportStockReport", summary = "The stock report as PDF or Excel")
    public ResponseEntity<byte[]> stockExport(@Parameter(description = "pdf or xlsx") @RequestParam String format,
            @RequestParam(required = false) String gemType, @RequestParam(defaultValue = "false") boolean slow,
            @RequestParam(defaultValue = "60") int slowDays) {
        return files.download(format, sheets.stock(stock(gemType, slow, slowDays)), reports.zone());
    }

    // ------------------------------------------------------------------ commissions

    @GetMapping("/commissions")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "commissionReport", summary = "Commission by guide / driver and by staff member")
    public CommissionReport commissions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "GUIDE, STAFF or empty for both") @RequestParam(required = false) String payee) {
        return reports.commissions(reports.period(from, to, 29), payee);
    }

    @GetMapping("/commissions/export")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "exportCommissionReport", summary = "The commission report as PDF or Excel")
    public ResponseEntity<byte[]> commissionsExport(@Parameter(description = "pdf or xlsx") @RequestParam String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String payee) {
        return files.download(format, sheets.commissions(commissions(from, to, payee)), reports.zone());
    }

    // ------------------------------------------------------------------ purchases

    @GetMapping("/purchases")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "purchaseReport", summary = "Purchases by supplier: bought, sold, returned")
    public PurchaseReport purchases(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.purchases(reports.period(from, to, 180));
    }

    @GetMapping("/purchases/export")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "exportPurchaseReport", summary = "Purchases by supplier as PDF or Excel")
    public ResponseEntity<byte[]> purchasesExport(@Parameter(description = "pdf or xlsx") @RequestParam String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return files.download(format, sheets.purchases(purchases(from, to)), reports.zone());
    }

    // ------------------------------------------------------------------ expenses (report from P5-01)

    @GetMapping("/expenses/export")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "exportExpenseReport", summary = "The expense report as PDF or Excel")
    public ResponseEntity<byte[]> expensesExport(@Parameter(description = "pdf or xlsx") @RequestParam String format) {
        return files.download(format, sheets.expenses(expenses.report()), reports.zone());
    }
}
