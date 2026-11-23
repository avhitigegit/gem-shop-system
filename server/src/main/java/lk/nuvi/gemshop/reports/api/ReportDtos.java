package lk.nuvi.gemshop.reports.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lk.nuvi.gemshop.gems.GemStatus;

/** Answers of the reports (P5-02, prototype l-reports/*). Amounts in LKR, the branch at the top filters. */
public final class ReportDtos {

    private ReportDtos() {}

    /** A choice in a filter: value sent back, label shown. */
    public record ReportOption(
            @Schema(requiredMode = REQUIRED) String value,
            @Schema(requiredMode = REQUIRED) String label) {}

    /** Reports home: today's sales of the branch at the top. */
    public record ReportsSummary(
            @Schema(requiredMode = REQUIRED) BigDecimal salesToday,
            @Schema(requiredMode = REQUIRED) int billsToday) {}

    // ------------------------------------------------------------------ sales

    /** How the sales report is grouped. */
    public enum SalesBy { DAY, MONTH, BRANCH, GEM, STAFF, GUIDE, PAY, COUNTRY }

    public record SalesRow(
            @Schema(requiredMode = REQUIRED, description = "Sort key, e.g. 2026-11-24 for a day") String key,
            @Schema(requiredMode = REQUIRED, description = "What is shown, e.g. 24/11/2026, Blue Sapphire, Walk-in") String label,
            @Schema(requiredMode = REQUIRED) int bills,
            @Schema(requiredMode = REQUIRED) int items,
            @Schema(requiredMode = REQUIRED) BigDecimal total) {}

    public record SalesReport(
            @Schema(requiredMode = REQUIRED) LocalDate from,
            @Schema(requiredMode = REQUIRED) LocalDate to,
            @Schema(requiredMode = REQUIRED) SalesBy by,
            @Schema(requiredMode = REQUIRED, description = "Days and months newest first, the rest biggest first") List<SalesRow> rows,
            @Schema(requiredMode = REQUIRED) int bills,
            @Schema(requiredMode = REQUIRED) int items,
            @Schema(requiredMode = REQUIRED) BigDecimal total,
            @Schema(requiredMode = REQUIRED) BigDecimal averageBill,
            @Schema(requiredMode = REQUIRED) List<String> gemTypes,
            @Schema(requiredMode = REQUIRED, description = "Owner and salespeople (value = user id)") List<ReportOption> staff,
            @Schema(requiredMode = REQUIRED, description = "Guides (value = code); \"walkin\" = sales without a guide") List<ReportOption> guides,
            @Schema(requiredMode = REQUIRED, description = "Card, Cash LKR, Cash USD, Cash EUR, Credit note, Split") List<String> payTypes) {}

    // ------------------------------------------------------------------ profit

    public record ProfitRow(
            @Schema(requiredMode = REQUIRED) String gem,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String gemType,
            @Schema(requiredMode = REQUIRED) String productType,
            @Schema(description = "First photo (small), or null = draw the gem") String photo,
            @Schema(requiredMode = REQUIRED) Instant soldAt,
            @Schema(requiredMode = REQUIRED) String invoice,
            @Schema(requiredMode = REQUIRED, description = "Agreed selling price") BigDecimal price,
            @Schema(requiredMode = REQUIRED) BigDecimal cost,
            @Schema(requiredMode = REQUIRED, description = "The bill's guide + staff commission, shared by price") BigDecimal commission,
            @Schema(requiredMode = REQUIRED) BigDecimal profit,
            @Schema(requiredMode = REQUIRED, description = "Profit ÷ price × 100") BigDecimal margin,
            @Schema(requiredMode = REQUIRED, description = "Sold below the minimum price (owner approved)") boolean belowMin) {}

    public record ProfitReport(
            @Schema(requiredMode = REQUIRED) LocalDate from,
            @Schema(requiredMode = REQUIRED) LocalDate to,
            @Schema(requiredMode = REQUIRED, description = "Newest first") List<ProfitRow> rows,
            @Schema(requiredMode = REQUIRED) BigDecimal sales,
            @Schema(requiredMode = REQUIRED) BigDecimal cost,
            @Schema(requiredMode = REQUIRED) BigDecimal commission,
            @Schema(requiredMode = REQUIRED) BigDecimal profit,
            @Schema(requiredMode = REQUIRED) BigDecimal margin,
            @Schema(requiredMode = REQUIRED) List<String> gemTypes) {}

    // ------------------------------------------------------------------ daily cash

    /** Drawer cash per currency and the card machine total. */
    public record DrawerAmounts(
            @Schema(requiredMode = REQUIRED) BigDecimal lkr,
            @Schema(requiredMode = REQUIRED) BigDecimal usd,
            @Schema(requiredMode = REQUIRED) BigDecimal eur,
            @Schema(requiredMode = REQUIRED) BigDecimal card) {}

    public record CashReportRow(
            @Schema(requiredMode = REQUIRED, example = "DC-20261124-COL") String number,
            @Schema(requiredMode = REQUIRED) LocalDate day,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) String closedBy,
            @Schema(requiredMode = REQUIRED) Instant closedAt,
            @Schema(requiredMode = REQUIRED) DrawerAmounts expected,
            @Schema(requiredMode = REQUIRED) DrawerAmounts counted,
            @Schema(requiredMode = REQUIRED, description = "Counted − expected, all currencies in LKR") BigDecimal difference,
            @Schema(requiredMode = REQUIRED, description = "Difference under LKR 0.50") boolean balanced,
            String note,
            @Schema(requiredMode = REQUIRED) boolean sentToOwner) {}

    public record CashReport(
            @Schema(requiredMode = REQUIRED) LocalDate from,
            @Schema(requiredMode = REQUIRED) LocalDate to,
            @Schema(requiredMode = REQUIRED, description = "Newest first") List<CashReportRow> rows,
            @Schema(requiredMode = REQUIRED) int days,
            @Schema(requiredMode = REQUIRED) int daysWithDifference,
            @Schema(requiredMode = REQUIRED, description = "In LKR") BigDecimal netDifference) {}

    // ------------------------------------------------------------------ stock

    public record StockReportRow(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String gemType,
            @Schema(requiredMode = REQUIRED) String productType,
            @Schema(requiredMode = REQUIRED) BigDecimal carat,
            @Schema(requiredMode = REQUIRED, description = "Branch code") String branch,
            @Schema(requiredMode = REQUIRED) GemStatus status,
            @Schema(requiredMode = REQUIRED) Instant addedAt,
            @Schema(requiredMode = REQUIRED, description = "Days in stock") long days,
            @Schema(requiredMode = REQUIRED) BigDecimal cost,
            @Schema(requiredMode = REQUIRED) BigDecimal price,
            @Schema(description = "First photo (small), or null = draw the gem") String photo) {}

    public record StockReport(
            @Schema(requiredMode = REQUIRED, description = "Only gems not sold for more than slowDays") boolean slow,
            @Schema(requiredMode = REQUIRED) int slowDays,
            @Schema(requiredMode = REQUIRED, description = "Oldest first") List<StockReportRow> rows,
            @Schema(requiredMode = REQUIRED) int items,
            @Schema(requiredMode = REQUIRED) BigDecimal cost,
            @Schema(requiredMode = REQUIRED) BigDecimal price,
            @Schema(requiredMode = REQUIRED) long averageDays,
            @Schema(requiredMode = REQUIRED) List<String> gemTypes) {}

    // ------------------------------------------------------------------ commissions

    public record CommissionPersonRow(
            @Schema(requiredMode = REQUIRED, description = "GUIDE or STAFF") String payee,
            @Schema(requiredMode = REQUIRED, description = "Guide code (for the slip) or staff user id") String id,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED, description = "e.g. \"Guide / driver · Van · 10%\" or \"Staff · fixed by price level\"") String detail,
            @Schema(requiredMode = REQUIRED) int sales,
            @Schema(requiredMode = REQUIRED) BigDecimal salesAmount,
            @Schema(requiredMode = REQUIRED) BigDecimal earned,
            @Schema(requiredMode = REQUIRED, description = "Taken back after returns / cancelled invoices (negative)") BigDecimal reversed,
            @Schema(requiredMode = REQUIRED) BigDecimal paid,
            @Schema(requiredMode = REQUIRED) BigDecimal unpaid) {}

    public record CommissionReport(
            @Schema(requiredMode = REQUIRED) LocalDate from,
            @Schema(requiredMode = REQUIRED) LocalDate to,
            @Schema(requiredMode = REQUIRED, description = "Biggest earned first") List<CommissionPersonRow> rows,
            @Schema(requiredMode = REQUIRED) int sales,
            @Schema(requiredMode = REQUIRED) BigDecimal salesAmount,
            @Schema(requiredMode = REQUIRED) BigDecimal earned,
            @Schema(requiredMode = REQUIRED) BigDecimal reversed,
            @Schema(requiredMode = REQUIRED) BigDecimal paid,
            @Schema(requiredMode = REQUIRED) BigDecimal unpaid) {}

    // ------------------------------------------------------------------ purchases

    public record SupplierReportRow(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            String address,
            @Schema(requiredMode = REQUIRED) int purchases,
            @Schema(requiredMode = REQUIRED) int products,
            @Schema(requiredMode = REQUIRED, description = "Of these products, how many are sold") int sold,
            @Schema(requiredMode = REQUIRED) BigDecimal cost,
            @Schema(requiredMode = REQUIRED, description = "Refunds for gems returned to the supplier") BigDecimal returned,
            LocalDate lastPurchase) {}

    public record PurchaseReport(
            @Schema(requiredMode = REQUIRED) LocalDate from,
            @Schema(requiredMode = REQUIRED) LocalDate to,
            @Schema(requiredMode = REQUIRED, description = "Biggest total first") List<SupplierReportRow> rows,
            @Schema(requiredMode = REQUIRED) int purchases,
            @Schema(requiredMode = REQUIRED) int products,
            @Schema(requiredMode = REQUIRED) int sold,
            @Schema(requiredMode = REQUIRED) BigDecimal cost,
            @Schema(requiredMode = REQUIRED) BigDecimal returned) {}
}
