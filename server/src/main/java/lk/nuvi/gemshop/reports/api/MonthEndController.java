package lk.nuvi.gemshop.reports.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.files.Downloads;
import lk.nuvi.gemshop.files.pdf.PdfBody;
import lk.nuvi.gemshop.files.pdf.PdfDocuments.Line;
import lk.nuvi.gemshop.reports.Finance;
import lk.nuvi.gemshop.reports.MonthEnd;
import lk.nuvi.gemshop.reports.ReportFiles;
import lk.nuvi.gemshop.reports.ReportFiles.Col;
import lk.nuvi.gemshop.reports.ReportFiles.Sheet;
import lk.nuvi.gemshop.reports.TaxPack;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.AccountantHome;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.MonthList;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.MonthRow;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.PlExpenseType;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.PlMonth;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.ProfitLoss;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.ReopenForm;
import lk.nuvi.gemshop.reports.api.MonthEndDtos.TaxPackPreview;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Month end with the accountant (prototype n-accountant/*, Guide 72–75; BRD N1–N6, rule 11): accountant home, Profit &amp;
 * Loss, tax pack and closing the month. Owner and accountant.
 */
@RestController
@Tag(name = "Month end")
public class MonthEndController {

    private final MonthEnd monthEnd;
    private final TaxPack taxPack;
    private final ReportFiles files;
    private final Finance finance;

    public MonthEndController(MonthEnd monthEnd, TaxPack taxPack, ReportFiles files, Finance finance) {
        this.monthEnd = monthEnd;
        this.taxPack = taxPack;
        this.files = files;
        this.finance = finance;
    }

    @GetMapping("/api/accountant/home")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "accountantHome", summary = "Accountant home: this month, months and their status, things to do")
    public AccountantHome home() {
        return monthEnd.home();
    }

    // ------------------------------------------------------------------ profit & loss

    @GetMapping("/api/reports/profit-loss")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "profitLoss", summary = "Profit & Loss of a month (empty = last month), every month, per branch")
    public ProfitLoss profitLoss(@Parameter(description = "e.g. 2026-10") @RequestParam(required = false) String month) {
        return monthEnd.profitLoss(month);
    }

    @GetMapping("/api/reports/profit-loss/export")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "exportProfitLoss", summary = "The month's Profit & Loss as PDF or Excel")
    public ResponseEntity<byte[]> profitLossExport(@Parameter(description = "pdf or xlsx") @RequestParam String format,
            @RequestParam(required = false) String month) {
        ProfitLoss pl = monthEnd.profitLoss(month);
        PlMonth m = pl.figures();
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] {"Sales", m.sales()});
        rows.add(new Object[] {"Less: returns (credit notes)", m.returns().negate()});
        rows.add(new Object[] {"Net sales", m.netSales()});
        rows.add(new Object[] {"Less: cost of products sold", m.cost().negate()});
        rows.add(new Object[] {"Gross profit", m.grossProfit()});
        rows.add(new Object[] {"Less: commissions (guides & staff)", m.commissions().negate()});
        rows.add(new Object[] {"Less: card bank fees (" + pl.cardFeePct().stripTrailingZeros().toPlainString() + "%, paid by the shop)",
                m.cardFees().negate()});
        rows.add(new Object[] {"Less: shop expenses", m.expenses().negate()});
        for (PlExpenseType t : pl.expenseTypes()) {
            rows.add(new Object[] {"      " + t.expenseType(), t.total()});
        }
        String label = lk.nuvi.gemshop.accountant.MonthLock.label(java.time.YearMonth.parse(m.month()));
        Sheet s = new Sheet("PROFIT AND LOSS", "Profit and Loss " + m.month(), List.of(new Line("Month", label),
                new Line("Status", m.closed() ? "Closed (locked)" : pl.monthToDate() ? "Open – month to date" : "Open"),
                new Line("Net profit", "LKR " + PdfBody.money(m.netProfit()))),
                List.of(Col.text("", 50), Col.money("LKR", 20)), rows, new Object[] {"Net profit for the month", m.netProfit()},
                "Net profit = sales − returns − cost − commissions − card bank fees − expenses.", false);
        return files.download(format, s, finance.zone());
    }

    // ------------------------------------------------------------------ month closing

    @GetMapping("/api/months")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "listMonths", summary = "Every month with its figures, checks and closed / open status")
    public MonthList months() {
        return monthEnd.months();
    }

    @PostMapping("/api/months/{month}/close")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "closeMonth", summary = "Close an ended month – nothing dated in it can change afterwards")
    public MonthRow close(@PathVariable String month) {
        return monthEnd.close(month);
    }

    @PostMapping("/api/months/{month}/reopen")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "reopenMonth", summary = "Owner: re-open a closed month. Accountant: ask the owner to re-open it")
    public MonthRow reopen(@PathVariable String month, @Valid @RequestBody ReopenForm form) {
        return monthEnd.reopen(month, form.reason());
    }

    // ------------------------------------------------------------------ tax pack

    @GetMapping("/api/tax-pack/preview")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "taxPackPreview", summary = "What the tax pack for these months holds")
    public TaxPackPreview preview(@Parameter(description = "e.g. 2026-08") @RequestParam(required = false) String from,
            @Parameter(description = "e.g. 2026-10") @RequestParam(required = false) String to) {
        return taxPack.preview(taxPack.months(from, to));
    }

    @GetMapping("/api/tax-pack")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "downloadTaxPack", summary = "The tax pack as one ZIP: the reports (Excel / PDF) and the bill photos")
    public ResponseEntity<byte[]> download(@RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @Parameter(description = "SALES, PURCHASES, EXPENSES, PROFIT_LOSS, COMMISSIONS, CLOSINGS, CREDIT_NOTES (empty = all)")
            @RequestParam(required = false) List<String> parts,
            @RequestParam(defaultValue = "BOTH") TaxPack.Format format,
            @Parameter(description = "Include the bill photos") @RequestParam(defaultValue = "true") boolean photos) {
        TaxPack.Zip zip = taxPack.build(taxPack.months(from, to), TaxPack.parts(parts), format, photos);
        return Downloads.zip(zip.bytes(), zip.name());
    }
}
