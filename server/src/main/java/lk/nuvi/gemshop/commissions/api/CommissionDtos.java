package lk.nuvi.gemshop.commissions.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lk.nuvi.gemshop.commissions.CommissionLine;
import lk.nuvi.gemshop.commissions.PayMethod;
import lk.nuvi.gemshop.sales.api.SalesDtos.DocShop;

/** Guides & drivers, commission per sale, paying commission and the slip (P3-05). */
public final class CommissionDtos {

    private CommissionDtos() {}

    // ------------------------------------------------------------------ guides & drivers

    public record GuideForm(
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(min = 2, max = 120) String name,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(min = 5, max = 40) String phone,
            @Schema(requiredMode = REQUIRED, description = "Tuk-tuk, Van, Taxi, Car or Other")
            @NotBlank @Pattern(regexp = "Tuk-tuk|Van|Taxi|Car|Other") String vehicle,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 20) String vehicleNo,
            @Schema(description = "Empty = the default % for new guides")
            @DecimalMin("0") @DecimalMax("50") BigDecimal pct) {}

    /** One guide on the list. Amounts are empty for the salesperson. */
    public record GuideCard(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            String vehicle,
            String vehicleNo,
            @Schema(requiredMode = REQUIRED) String phone,
            @Schema(requiredMode = REQUIRED) BigDecimal pct,
            LocalDate since,
            @Schema(requiredMode = REQUIRED) int sales,
            @Schema(requiredMode = REQUIRED) BigDecimal salesValue,
            @Schema(description = "Commission earned (owner / accountant)") BigDecimal earned,
            @Schema(description = "Not yet paid (owner / accountant)") BigDecimal unpaid) {}

    public record GuideList(
            @Schema(requiredMode = REQUIRED) List<GuideCard> guides,
            @Schema(requiredMode = REQUIRED, description = "The % a new guide starts with") BigDecimal defaultPct) {}

    /** One guide with the commission lines in a period. Amounts are empty for the salesperson. */
    public record GuideProfile(
            @Schema(requiredMode = REQUIRED) GuideCard guide,
            @Schema(requiredMode = REQUIRED) LocalDate from,
            @Schema(requiredMode = REQUIRED) LocalDate to,
            @Schema(requiredMode = REQUIRED) int sales,
            @Schema(requiredMode = REQUIRED) BigDecimal salesValue,
            BigDecimal earned,
            BigDecimal unpaid,
            @Schema(requiredMode = REQUIRED) int unpaidLines,
            @Schema(requiredMode = REQUIRED) List<CommissionRow> lines) {}

    // ------------------------------------------------------------------ commission lines

    public record CommissionRow(
            @Schema(requiredMode = REQUIRED) long id,
            @Schema(requiredMode = REQUIRED) Instant earnedAt,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) String sale,
            @Schema(requiredMode = REQUIRED) String customer,
            @Schema(requiredMode = REQUIRED) BigDecimal saleTotal,
            @Schema(requiredMode = REQUIRED) CommissionLine.Payee payee,
            @Schema(requiredMode = REQUIRED, description = "\"G:G-01\" (guide) or \"S:12\" (staff)") String person,
            @Schema(requiredMode = REQUIRED) String name,
            BigDecimal pct,
            @Schema(description = "Empty for the salesperson") BigDecimal amount,
            @Schema(requiredMode = REQUIRED) CommissionLine.Status status,
            @Schema(requiredMode = REQUIRED, description = "Not paid yet (a negative line is taken off the next payment)") boolean owed,
            String note,
            Instant paidAt,
            String method,
            String payment) {}

    public record PayeeOption(
            @Schema(requiredMode = REQUIRED) String key,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) CommissionLine.Payee payee) {}

    // ------------------------------------------------------------------ commission per sale

    public record CommissionSaleRow(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) Instant soldAt,
            @Schema(requiredMode = REQUIRED) String customer,
            String guide,
            @Schema(requiredMode = REQUIRED) String staff,
            @Schema(requiredMode = REQUIRED) BigDecimal total,
            @Schema(requiredMode = REQUIRED) BigDecimal commission) {}

    public record CommissionSaleList(
            @Schema(requiredMode = REQUIRED) List<CommissionSaleRow> sales,
            @Schema(requiredMode = REQUIRED, description = "Guide filter") List<PayeeOption> guides,
            @Schema(requiredMode = REQUIRED, description = "Salesperson filter") List<PayeeOption> staff) {}

    public record StaffLevel(
            @Schema(description = "Empty = the last level (\"above …\")") BigDecimal upTo,
            @Schema(requiredMode = REQUIRED) BigDecimal amount) {}

    public record SaleCommission(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) Instant soldAt,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) String customer,
            @Schema(requiredMode = REQUIRED) String staff,
            @Schema(requiredMode = REQUIRED) BigDecimal total,
            String guide,
            String guideVehicle,
            @Schema(requiredMode = REQUIRED) BigDecimal guidePct,
            @Schema(requiredMode = REQUIRED) BigDecimal guideComm,
            @Schema(requiredMode = REQUIRED) BigDecimal staffComm,
            @Schema(requiredMode = REQUIRED, description = "0-based staff level of the sale total") int level,
            @Schema(requiredMode = REQUIRED) List<StaffLevel> levels,
            @Schema(requiredMode = REQUIRED) List<CommissionRow> lines) {}

    // ------------------------------------------------------------------ pay guides & drivers

    public enum PayableShow { OWED, PAID, REVERSED, ALL }

    public record OwedPerson(
            @Schema(requiredMode = REQUIRED) String person,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) CommissionLine.Payee payee,
            @Schema(requiredMode = REQUIRED) int lines,
            @Schema(requiredMode = REQUIRED) BigDecimal amount) {}

    public record Payable(
            @Schema(requiredMode = REQUIRED) BigDecimal owed,
            @Schema(requiredMode = REQUIRED) int owedLines,
            @Schema(requiredMode = REQUIRED) BigDecimal guidesOwed,
            @Schema(requiredMode = REQUIRED) int guidesPeople,
            @Schema(requiredMode = REQUIRED) BigDecimal staffOwed,
            @Schema(requiredMode = REQUIRED) List<OwedPerson> byPerson,
            @Schema(requiredMode = REQUIRED) List<CommissionRow> lines,
            @Schema(requiredMode = REQUIRED) List<PayeeOption> people) {}

    public record PayForm(
            @Schema(requiredMode = REQUIRED) @NotEmpty @Size(max = 500) List<@NotNull Long> lines,
            @Schema(requiredMode = REQUIRED) @NotNull LocalDate paidOn,
            @Schema(requiredMode = REQUIRED) @NotNull PayMethod method) {}

    public record PaidView(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) BigDecimal amount,
            @Schema(requiredMode = REQUIRED) String payee,
            @Schema(requiredMode = REQUIRED) int lines) {}

    // ------------------------------------------------------------------ commission slip

    public record CommissionSlipLine(
            @Schema(requiredMode = REQUIRED) Instant date,
            @Schema(requiredMode = REQUIRED) String sale,
            @Schema(requiredMode = REQUIRED) BigDecimal saleAmount,
            @Schema(requiredMode = REQUIRED) BigDecimal amount,
            @Schema(requiredMode = REQUIRED) CommissionLine.Status status,
            String note) {}

    public record CommissionSlip(
            @Schema(description = "CP-… – empty for a statement (last 30 days of one guide)") String number,
            @Schema(requiredMode = REQUIRED) Instant date,
            @Schema(description = "How it was paid") String method,
            @Schema(requiredMode = REQUIRED) String payee,
            String guideVehicle,
            String guideVehicleNo,
            String guidePhone,
            BigDecimal guidePct,
            @Schema(requiredMode = REQUIRED) String paidBy,
            @Schema(requiredMode = REQUIRED) List<CommissionSlipLine> lines,
            @Schema(requiredMode = REQUIRED) BigDecimal total,
            @Schema(requiredMode = REQUIRED) DocShop shop) {}

    // ------------------------------------------------------------------ settings → commission: each guide's own %

    public record GuidePct(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            String vehicle,
            @Schema(requiredMode = REQUIRED) BigDecimal pct) {}

    public record GuidePctForm(
            @Schema(requiredMode = REQUIRED) @NotBlank String code,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0") @DecimalMax("50") BigDecimal pct) {}
}
