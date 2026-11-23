package lk.nuvi.gemshop.dailycash.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.nuvi.gemshop.expenses.Expense;

/** Start the day, pay out cash from the drawer, close the day (P3-06). */
public final class CashDtos {

    private CashDtos() {}

    public record DrawerAmounts(
            @Schema(requiredMode = REQUIRED) BigDecimal lkr,
            @Schema(requiredMode = REQUIRED) BigDecimal usd,
            @Schema(requiredMode = REQUIRED) BigDecimal eur) {}

    public record OpeningView(
            @Schema(requiredMode = REQUIRED) DrawerAmounts cash,
            @Schema(requiredMode = REQUIRED) Instant enteredAt,
            @Schema(requiredMode = REQUIRED) String enteredBy) {}

    public record OpeningRow(
            @Schema(requiredMode = REQUIRED) LocalDate day,
            @Schema(requiredMode = REQUIRED) DrawerAmounts cash) {}

    /** What should be in the drawer and on the card machine (I3). */
    public record ExpectedCash(
            @Schema(requiredMode = REQUIRED) DrawerAmounts opening,
            @Schema(requiredMode = REQUIRED, description = "Cash taken per currency, LKR after change given") DrawerAmounts cashIn,
            @Schema(requiredMode = REQUIRED, description = "Paid out of the drawer (LKR)") BigDecimal drawerOut,
            @Schema(requiredMode = REQUIRED) DrawerAmounts expected,
            @Schema(requiredMode = REQUIRED, description = "Card machine total = card sales (no fee added)") BigDecimal card,
            @Schema(requiredMode = REQUIRED, description = "Bank fee on card sales – the shop pays it") BigDecimal cardFee,
            @Schema(requiredMode = REQUIRED, description = "Cash sales in LKR after change") BigDecimal cashSalesLkr,
            @Schema(requiredMode = REQUIRED) BigDecimal changeGiven,
            @Schema(requiredMode = REQUIRED) BigDecimal expensesOut,
            @Schema(requiredMode = REQUIRED) BigDecimal commissionsOut,
            @Schema(requiredMode = REQUIRED) BigDecimal refundsOut,
            @Schema(requiredMode = REQUIRED) int bills,
            @Schema(requiredMode = REQUIRED) BigDecimal salesTotal) {}

    public record DayPayment(
            @Schema(requiredMode = REQUIRED) String invoice,
            @Schema(requiredMode = REQUIRED) Instant soldAt,
            @Schema(requiredMode = REQUIRED, description = "\"USD 400 + Card\"") String paid,
            @Schema(requiredMode = REQUIRED) BigDecimal change) {}

    public record DrawerExpenseRow(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) Instant at,
            @Schema(requiredMode = REQUIRED) String expenseType,
            @Schema(requiredMode = REQUIRED) String paidTo,
            @Schema(requiredMode = REQUIRED) BigDecimal amount,
            @Schema(requiredMode = REQUIRED) Expense.Status status,
            String approvalNo,
            @Schema(description = "Bill photo (thumbnail link)") String bill) {}

    public record DrawerCommissionRow(
            @Schema(requiredMode = REQUIRED) String payment,
            @Schema(requiredMode = REQUIRED) Instant at,
            @Schema(requiredMode = REQUIRED) String payee,
            @Schema(requiredMode = REQUIRED) BigDecimal amount) {}

    public record DrawerRefundRow(
            @Schema(requiredMode = REQUIRED) String creditNote,
            @Schema(requiredMode = REQUIRED) Instant at,
            @Schema(requiredMode = REQUIRED) String customer,
            @Schema(requiredMode = REQUIRED) String invoice,
            @Schema(requiredMode = REQUIRED) BigDecimal amount) {}

    public record ClosingView(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) LocalDate day,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) String branchName,
            @Schema(requiredMode = REQUIRED) DrawerAmounts expected,
            @Schema(requiredMode = REQUIRED) DrawerAmounts counted,
            @Schema(requiredMode = REQUIRED) BigDecimal cardExpected,
            @Schema(requiredMode = REQUIRED) BigDecimal cardCounted,
            @Schema(requiredMode = REQUIRED) BigDecimal cardFee,
            @Schema(requiredMode = REQUIRED) BigDecimal salesTotal,
            @Schema(requiredMode = REQUIRED) int bills,
            @Schema(requiredMode = REQUIRED) BigDecimal drawerOut,
            @Schema(requiredMode = REQUIRED) BigDecimal rateUsd,
            @Schema(requiredMode = REQUIRED) BigDecimal rateEur,
            @Schema(requiredMode = REQUIRED, description = "Counted − expected, all in LKR at the day's rates (card included)")
            BigDecimal difference,
            String note,
            @Schema(requiredMode = REQUIRED) Instant closedAt,
            @Schema(requiredMode = REQUIRED) String closedBy,
            @Schema(requiredMode = REQUIRED) boolean sentToOwner) {}

    /** Everything the Start the day / Pay out / Close the day screens show for one branch and day. */
    public record CashDay(
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) String branchName,
            @Schema(requiredMode = REQUIRED) LocalDate day,
            OpeningView opening,
            @Schema(description = "Counted cash of the last closing (suggested opening)") DrawerAmounts lastCounted,
            @Schema(requiredMode = REQUIRED) List<OpeningRow> recentOpenings,
            @Schema(requiredMode = REQUIRED) ExpectedCash expected,
            @Schema(requiredMode = REQUIRED) List<DayPayment> payments,
            @Schema(requiredMode = REQUIRED) List<DrawerExpenseRow> expenses,
            @Schema(requiredMode = REQUIRED) List<DrawerCommissionRow> commissions,
            @Schema(requiredMode = REQUIRED) List<DrawerRefundRow> refunds,
            @Schema(requiredMode = REQUIRED, description = "Refund credit notes not paid back yet") List<DrawerRefundRow> refundsToPay,
            ClosingView closing,
            @Schema(requiredMode = REQUIRED) BigDecimal rateUsd,
            @Schema(requiredMode = REQUIRED) BigDecimal rateEur,
            @Schema(requiredMode = REQUIRED) List<String> expenseTypes,
            @Schema(requiredMode = REQUIRED, description = "Expenses above this need the owner's approval") BigDecimal largeExpenseLimit) {}

    /** Cash & expenses home. */
    public record CashSummary(
            @Schema(description = "The branch the drawer screens work on (empty = choose a branch)") String branch,
            @Schema(requiredMode = REQUIRED) boolean opened,
            @Schema(requiredMode = REQUIRED) boolean closed,
            @Schema(requiredMode = REQUIRED) long expensesWaiting,
            @Schema(requiredMode = REQUIRED, description = "Closings of the last 7 days that did not match") int daysNotMatching) {}

    // ------------------------------------------------------------------ forms

    public record OpeningForm(
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0") @DecimalMax("100000000") BigDecimal lkr,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal usd,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal eur) {}

    public record DrawerExpenseForm(
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 80) String expenseType,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("1") @DecimalMax("100000000") BigDecimal amount,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 120) String paidTo,
            @Schema(requiredMode = REQUIRED, description = "Uploaded bill photo (rule 11)") @NotNull UUID bill,
            @Size(max = 500) String note) {}

    public record CloseDayForm(
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0") @DecimalMax("100000000") BigDecimal lkr,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal usd,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0") @DecimalMax("1000000") BigDecimal eur,
            @Schema(requiredMode = REQUIRED, description = "Card machine total") @NotNull @DecimalMin("0") @DecimalMax("100000000")
            BigDecimal card,
            @Schema(description = "Needed when there is a difference") @Size(max = 500) String note) {}
}
