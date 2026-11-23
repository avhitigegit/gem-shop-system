package lk.nuvi.gemshop.reports.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Profit & Loss, month closing, accountant home and tax pack (P5-03, prototype n-accountant/*). Amounts in LKR. */
public final class MonthEndDtos {

    private MonthEndDtos() {}

    /** One month's result (BRD N4): sales − returns − cost − commissions − card bank fees − expenses. */
    public record PlMonth(
            @Schema(requiredMode = REQUIRED, example = "2026-10") String month,
            @Schema(requiredMode = REQUIRED, description = "Invoices not cancelled") int invoices,
            @Schema(requiredMode = REQUIRED, description = "Invoice value of the month's sales") BigDecimal sales,
            @Schema(requiredMode = REQUIRED, description = "Credit notes given in the month (returns)") BigDecimal returns,
            @Schema(requiredMode = REQUIRED, description = "Sales − returns") BigDecimal netSales,
            @Schema(requiredMode = REQUIRED, description = "Cost of gems sold, less the cost of gems returned") BigDecimal cost,
            @Schema(requiredMode = REQUIRED) BigDecimal grossProfit,
            @Schema(requiredMode = REQUIRED, description = "Guide + staff commission, after take-backs") BigDecimal commissions,
            @Schema(requiredMode = REQUIRED, description = "Bank fee on card payments – paid by the shop") BigDecimal cardFees,
            @Schema(requiredMode = REQUIRED, description = "Approved shop expenses") BigDecimal expenses,
            @Schema(requiredMode = REQUIRED) BigDecimal netProfit,
            @Schema(requiredMode = REQUIRED) boolean closed) {}

    public record PlExpenseType(
            @Schema(requiredMode = REQUIRED) String expenseType,
            @Schema(requiredMode = REQUIRED) BigDecimal total) {}

    public record PlBranch(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) BigDecimal netSales,
            @Schema(requiredMode = REQUIRED) BigDecimal netProfit) {}

    public record ProfitLoss(
            @Schema(requiredMode = REQUIRED) PlMonth figures,
            @Schema(requiredMode = REQUIRED, description = "The month's expenses by type, biggest first") List<PlExpenseType> expenseTypes,
            @Schema(requiredMode = REQUIRED, description = "Every month, newest first") List<PlMonth> months,
            @Schema(requiredMode = REQUIRED, description = "The month per branch (whole shop)") List<PlBranch> branches,
            @Schema(requiredMode = REQUIRED) BigDecimal cardFeePct,
            @Schema(requiredMode = REQUIRED, description = "The chosen month is the current month (month to date)") boolean monthToDate) {}

    // ------------------------------------------------------------------ month closing

    public record MonthRow(
            @Schema(requiredMode = REQUIRED, example = "2026-10") String month,
            @Schema(requiredMode = REQUIRED) int invoices,
            @Schema(requiredMode = REQUIRED) BigDecimal netSales,
            @Schema(requiredMode = REQUIRED) BigDecimal netProfit,
            @Schema(requiredMode = REQUIRED) boolean closed,
            Instant closedAt,
            String closedBy,
            Instant reopenedAt,
            String reopenedBy,
            String reopenReason,
            @Schema(description = "A re-open request waiting for the owner (AP-…)") String reopenWaiting,
            @Schema(requiredMode = REQUIRED, description = "The month is over (only ended months can be closed)") boolean ended,
            @Schema(requiredMode = REQUIRED, description = "Expenses without a bill photo") int billsMissing,
            @Schema(requiredMode = REQUIRED) int expensesWaiting,
            @Schema(requiredMode = REQUIRED, description = "Invoice cancellations waiting for the owner") int cancellationsWaiting) {}

    public record MonthList(
            @Schema(requiredMode = REQUIRED, description = "Newest first") List<MonthRow> months) {}

    public record ReopenForm(
            @Schema(requiredMode = REQUIRED, description = "Why the month must change (kept in the activity log)")
            @NotBlank @Size(max = 500) String reason) {}

    // ------------------------------------------------------------------ accountant home

    public record AccountantHome(
            @Schema(requiredMode = REQUIRED, description = "This month so far") PlMonth thisMonth,
            @Schema(description = "Last month") PlMonth lastMonth,
            @Schema(requiredMode = REQUIRED, description = "Newest first, at most 6") List<MonthRow> months,
            @Schema(requiredMode = REQUIRED) int billsMissing,
            @Schema(requiredMode = REQUIRED) int expensesWaiting,
            @Schema(requiredMode = REQUIRED, description = "Last month – the one to close next") MonthRow toClose) {}

    // ------------------------------------------------------------------ tax pack

    public record TaxPackPreview(
            @Schema(requiredMode = REQUIRED, example = "2026-08") String from,
            @Schema(requiredMode = REQUIRED, example = "2026-10") String to,
            @Schema(requiredMode = REQUIRED) int invoices,
            @Schema(requiredMode = REQUIRED) BigDecimal netSales,
            @Schema(requiredMode = REQUIRED) BigDecimal netProfit,
            @Schema(requiredMode = REQUIRED, description = "Expense and purchase bill photos in the period") int billPhotos,
            @Schema(requiredMode = REQUIRED, description = "Months to choose from, newest first") List<String> months) {}
}
