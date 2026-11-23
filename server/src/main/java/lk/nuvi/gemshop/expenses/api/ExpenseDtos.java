package lk.nuvi.gemshop.expenses.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.nuvi.gemshop.files.api.FileDtos.FileView;

/** Shop expenses, monthly bills and the expense report (P5-01, BRD M1–M7). Owner and accountant. */
public final class ExpenseDtos {

    private ExpenseDtos() {}

    public record ExpenseForm(
            @Schema(requiredMode = REQUIRED, description = "The day on the bill (not in the future)") @NotNull LocalDate spentOn,
            @Schema(requiredMode = REQUIRED, example = "ella") @NotBlank @Size(max = 20) String branch,
            @Schema(requiredMode = REQUIRED, example = "Electricity") @NotBlank @Size(max = 80) String expenseType,
            @Schema(requiredMode = REQUIRED, description = "LKR, the total on the bill") @NotNull @DecimalMin("1") @DecimalMax("100000000")
            BigDecimal amount,
            @Schema(requiredMode = REQUIRED, example = "Ceylon Electricity Board") @NotBlank @Size(max = 120) String paidTo,
            @Schema(requiredMode = REQUIRED, description = "BANK, CARD or OWNER_CASH (cash from the drawer: Pay out cash)",
                    example = "BANK") @NotBlank @Pattern(regexp = "BANK|CARD|OWNER_CASH|DRAWER") String method,
            @Schema(description = "Uploaded bill photo (rule 11); a correction may keep the saved one") UUID bill,
            @Size(max = 500) String note) {}

    public record ExpenseBillForm(@Schema(requiredMode = REQUIRED) @NotNull UUID bill) {}

    public record ExpenseRow(
            @Schema(requiredMode = REQUIRED, example = "EXP-0030") String number,
            @Schema(requiredMode = REQUIRED) Instant spentAt,
            @Schema(requiredMode = REQUIRED, example = "ella") String branch,
            @Schema(requiredMode = REQUIRED) String expenseType,
            @Schema(requiredMode = REQUIRED) String paidTo,
            @Schema(requiredMode = REQUIRED, example = "BANK") String method,
            @Schema(requiredMode = REQUIRED) BigDecimal amount,
            @Schema(requiredMode = REQUIRED, example = "APPROVED") String status,
            String approvalNo,
            String note,
            @Schema(description = "Added by this monthly bill", example = "RC-01") String monthlyBill,
            @Schema(requiredMode = REQUIRED, description = "A bill photo or sample bill is there") boolean hasBill,
            String by) {}

    public record ExpenseMonth(
            @Schema(requiredMode = REQUIRED, example = "2026-11") String month,
            @Schema(requiredMode = REQUIRED) int count) {}

    public record ExpenseList(
            @Schema(requiredMode = REQUIRED) List<ExpenseRow> expenses,
            @Schema(requiredMode = REQUIRED, description = "Approved and waiting (not rejected)") BigDecimal total,
            @Schema(requiredMode = REQUIRED, description = "Months that have expenses, newest first") List<ExpenseMonth> months,
            @Schema(requiredMode = REQUIRED) List<String> expenseTypes,
            @Schema(requiredMode = REQUIRED) BigDecimal largeExpenseLimit) {}

    public record ExpenseDetail(
            @Schema(requiredMode = REQUIRED) ExpenseRow expense,
            FileView bill,
            @Schema(description = "Sample data: the prototype's bill name (no real photo)") String sampleBill,
            @Schema(requiredMode = REQUIRED) boolean canCorrect,
            @Schema(description = "Why it cannot be corrected") String locked) {}

    // ------------------------------------------------------------------ monthly bills

    public record MonthlyBillForm(
            @Schema(requiredMode = REQUIRED, example = "ella") @NotBlank @Size(max = 20) String branch,
            @Schema(requiredMode = REQUIRED, example = "Rent") @NotBlank @Size(max = 80) String expenseType,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("1") @DecimalMax("100000000") BigDecimal amount,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 120) String paidTo,
            @Schema(requiredMode = REQUIRED, example = "BANK") @NotBlank @Pattern(regexp = "BANK|CARD|OWNER_CASH") String method,
            @Schema(requiredMode = REQUIRED, description = "Day of the month, 1–28") @NotNull @Min(1) @Max(28) Integer day,
            @Schema(description = "On / off; empty = keep") Boolean active) {}

    public record MonthlyBillRow(
            @Schema(requiredMode = REQUIRED, example = "RC-01") String code,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) String expenseType,
            @Schema(requiredMode = REQUIRED) BigDecimal amount,
            @Schema(requiredMode = REQUIRED) String paidTo,
            @Schema(requiredMode = REQUIRED) String method,
            @Schema(requiredMode = REQUIRED) int day,
            @Schema(requiredMode = REQUIRED) boolean active,
            LocalDate lastAdded,
            @Schema(description = "The next day it is added (empty when off)") LocalDate next,
            @Schema(requiredMode = REQUIRED, description = "Already added this month") boolean addedThisMonth) {}

    public record MonthlyBillList(
            @Schema(requiredMode = REQUIRED) List<MonthlyBillRow> bills,
            @Schema(requiredMode = REQUIRED) List<String> expenseTypes,
            @Schema(requiredMode = REQUIRED, description = "Every month, total of the active bills") BigDecimal monthlyTotal) {}

    public record MonthlyRunResult(@Schema(requiredMode = REQUIRED) int added) {}

    // ------------------------------------------------------------------ report

    public record ReportMonth(
            @Schema(requiredMode = REQUIRED, example = "2026-11") String month,
            @Schema(requiredMode = REQUIRED) BigDecimal total) {}

    public record ReportTypeRow(
            @Schema(requiredMode = REQUIRED) String expenseType,
            @Schema(requiredMode = REQUIRED, description = "One amount per month, in the order of months") List<BigDecimal> amounts,
            @Schema(requiredMode = REQUIRED) BigDecimal total) {}

    public record ReportMethodRow(
            @Schema(requiredMode = REQUIRED) String method,
            @Schema(requiredMode = REQUIRED) int count,
            @Schema(requiredMode = REQUIRED) BigDecimal total) {}

    public record ReportBranchRow(
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) BigDecimal total) {}

    public record ExpenseReport(
            @Schema(requiredMode = REQUIRED, description = "Oldest first") List<ReportMonth> months,
            @Schema(requiredMode = REQUIRED) List<ReportTypeRow> types,
            @Schema(requiredMode = REQUIRED) List<ReportMethodRow> methods,
            @Schema(requiredMode = REQUIRED) List<ReportBranchRow> branches,
            @Schema(requiredMode = REQUIRED, description = "Approved expenses only") BigDecimal total) {}
}
