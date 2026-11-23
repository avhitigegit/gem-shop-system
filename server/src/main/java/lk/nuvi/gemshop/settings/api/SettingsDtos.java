package lk.nuvi.gemshop.settings.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.settings.branch.BranchStatus;
import lk.nuvi.gemshop.settings.lists.ListName;

/** Data for the Settings screens (owner). Fields without {@code REQUIRED} can be empty. */
public final class SettingsDtos {

    private SettingsDtos() {}

    // ------------------------------------------------------------------ Settings home

    /** Counts and "needs attention" for the Settings home. */
    public record SettingsSummary(
            @Schema(requiredMode = REQUIRED) int staffLogins,
            @Schema(requiredMode = REQUIRED) int branches,
            BigDecimal usd,
            BigDecimal eur,
            Instant ratesUpdatedAt,
            @Schema(requiredMode = REQUIRED) boolean ratesUpdatedToday,
            LocalDate licenceExpiry,
            @Schema(description = "Days until the NGJA licence expires (below 0 = expired)") Integer licenceDaysLeft,
            @Schema(requiredMode = REQUIRED) int licenceRemindDays,
            @Schema(requiredMode = REQUIRED) BigDecimal defaultGuidePct,
            @Schema(requiredMode = REQUIRED) int listValues) {}

    // ------------------------------------------------------------------ Shop details

    public record ShopView(
            @Schema(requiredMode = REQUIRED) String name,
            String tagline,
            String address,
            String phone,
            String email,
            String website,
            @Schema(description = "Uploaded logo (empty = the default logo)") UUID logoFileId,
            @Schema(description = "Private link to the uploaded logo (works about an hour)") String logoUrl,
            String ngjaLicenceNo,
            LocalDate ngjaLicenceExpiry,
            @Schema(requiredMode = REQUIRED) int ngjaRemindDays,
            @Schema(requiredMode = REQUIRED) boolean printLicenceOnInvoice,
            @Schema(description = "Days until the licence expires (below 0 = expired)") Integer licenceDaysLeft,
            @Schema(requiredMode = REQUIRED) BigDecimal largeExpenseLimit,
            @Schema(requiredMode = REQUIRED) BigDecimal cardFeePct,
            @Schema(requiredMode = REQUIRED) int returnPolicyDays) {}

    public record ShopForm(
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 120) String name,
            @Size(max = 160) String tagline,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 255) String address,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 40) String phone,
            @Schema(requiredMode = REQUIRED) @NotBlank @Email @Size(max = 160) String email,
            @Size(max = 120) String website,
            @Schema(description = "A LOGO upload (POST /api/files). Empty = keep the logo as it is.") UUID logoFileId,
            @Schema(description = "true = go back to the default logo") Boolean removeLogo,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 60) String ngjaLicenceNo,
            @Schema(requiredMode = REQUIRED) @NotNull LocalDate ngjaLicenceExpiry,
            @Schema(requiredMode = REQUIRED) @Min(1) @Max(365) int ngjaRemindDays,
            @Schema(requiredMode = REQUIRED) boolean printLicenceOnInvoice,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0") @DecimalMax("100000000") BigDecimal largeExpenseLimit,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0") @DecimalMax("10") BigDecimal cardFeePct,
            @Schema(requiredMode = REQUIRED) @Min(0) @Max(365) int returnPolicyDays) {}

    // ------------------------------------------------------------------ Branches

    public record BranchStaff(
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) Role role,
            @Schema(requiredMode = REQUIRED) boolean active) {}

    public record BranchView(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            String shortName,
            String address,
            String phone,
            @Schema(requiredMode = REQUIRED) BranchStatus status,
            LocalDate openedOn,
            @Schema(requiredMode = REQUIRED) List<BranchStaff> staff) {}

    public record BranchForm(
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 120) String name,
            @Schema(description = "Short name for the top bar, e.g. \"Branch 2\"") @Size(max = 60) String shortName,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 255) String address,
            @Size(max = 40) String phone,
            @Schema(requiredMode = REQUIRED) @NotNull LocalDate openedOn) {}

    public record BranchStatusForm(@Schema(requiredMode = REQUIRED) @NotNull BranchStatus status) {}

    // ------------------------------------------------------------------ Staff logins

    public record StaffView(
            @Schema(requiredMode = REQUIRED) long id,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String email,
            String phone,
            @Schema(requiredMode = REQUIRED) Role role,
            @Schema(description = "Branch code; empty for Owner and Accountant (all branches)") String branch,
            @Schema(requiredMode = REQUIRED) boolean active,
            @Schema(requiredMode = REQUIRED, description = "Locked after too many wrong passwords") boolean locked,
            @Schema(requiredMode = REQUIRED, description = "false = invited, has not chosen a password yet") boolean hasPassword,
            Instant lastLoginAt,
            @Schema(requiredMode = REQUIRED, description = "This is the signed-in owner") boolean me) {}

    public record StaffForm(
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 120) String name,
            @Schema(requiredMode = REQUIRED) @NotBlank @Email @Size(max = 160) String email,
            @Size(max = 40) String phone,
            @Schema(requiredMode = REQUIRED) @NotNull Role role,
            @Schema(description = "Branch code – needed for Salesperson and Shop Staff") String branch,
            @Schema(requiredMode = REQUIRED) boolean active) {}

    // ------------------------------------------------------------------ Exchange rates

    public record RateDay(
            @Schema(requiredMode = REQUIRED) LocalDate date,
            BigDecimal usd,
            BigDecimal eur,
            String by) {}

    public record RatesView(
            BigDecimal usd,
            BigDecimal eur,
            Instant updatedAt,
            String updatedBy,
            @Schema(requiredMode = REQUIRED) List<RateDay> history) {}

    public record RatesForm(
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("1") @DecimalMax("100000") BigDecimal usd,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("1") @DecimalMax("100000") BigDecimal eur) {}

    // ------------------------------------------------------------------ Drop-down lists

    public record ListItem(
            @Schema(requiredMode = REQUIRED) long id,
            @Schema(requiredMode = REQUIRED) String value,
            @Schema(requiredMode = REQUIRED) long usedBy,
            @Schema(requiredMode = REQUIRED) String unit) {}

    public record ListView(
            @Schema(requiredMode = REQUIRED) ListName name,
            @Schema(requiredMode = REQUIRED) String label,
            @Schema(requiredMode = REQUIRED) List<ListItem> items) {}

    /** The values of one list, for drop-downs in forms. */
    public record ListValues(
            @Schema(requiredMode = REQUIRED) ListName name,
            @Schema(requiredMode = REQUIRED) List<String> values) {}

    public record ValueForm(@Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 80) String value) {}

    // ------------------------------------------------------------------ Commission rules

    public record LevelDto(
            @Schema(description = "Sale total up to (LKR); empty on the last level = \"above …\"") BigDecimal upTo,
            @Schema(requiredMode = REQUIRED) @NotNull BigDecimal amount) {}

    public record CommissionView(
            @Schema(requiredMode = REQUIRED) BigDecimal defaultGuidePct,
            @Schema(requiredMode = REQUIRED) List<LevelDto> levels,
            @Schema(requiredMode = REQUIRED, description = "The BRD example levels (button \"Use BRD example levels\")")
            List<LevelDto> exampleLevels,
            @Schema(requiredMode = REQUIRED, description = "Each guide / driver with their own %")
            List<lk.nuvi.gemshop.commissions.api.CommissionDtos.GuidePct> guides) {}

    public record CommissionForm(
            @Schema(requiredMode = REQUIRED) @NotNull BigDecimal defaultGuidePct,
            @Schema(requiredMode = REQUIRED) @NotNull @Size(min = 2, max = 10) List<@Valid LevelDto> levels,
            @Schema(description = "Each guide's own % (only the ones sent are changed)")
            @Size(max = 500) List<lk.nuvi.gemshop.commissions.api.CommissionDtos.@Valid GuidePctForm> guides) {}
}
