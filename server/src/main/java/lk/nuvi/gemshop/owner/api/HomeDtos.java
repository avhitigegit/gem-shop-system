package lk.nuvi.gemshop.owner.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lk.nuvi.gemshop.audit.api.AuditDtos.AlertView;
import lk.nuvi.gemshop.audit.api.AuditDtos.ApprovalView;

/** Home figures per role and the owner's phone view (P4-01, BRD K1). Amounts in LKR. */
public final class HomeDtos {

    private HomeDtos() {}

    /** One gem sold today ("Sold today" on Home and the phone view). */
    public record SoldGem(
            @Schema(requiredMode = REQUIRED) String invoice,
            @Schema(requiredMode = REQUIRED) Instant soldAt,
            @Schema(requiredMode = REQUIRED) String gem,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String gemType,
            @Schema(description = "First photo (small), or null = draw the gem") String photo,
            @Schema(requiredMode = REQUIRED) BigDecimal agreed,
            @Schema(requiredMode = REQUIRED) boolean belowMin,
            String country,
            @Schema(requiredMode = REQUIRED) String staff,
            @Schema(description = "Guide / driver, or null = walk-in") String guide) {}

    /** Cash in the drawer now (expected), per currency, and card payments today. */
    public record DrawerNow(
            @Schema(requiredMode = REQUIRED) BigDecimal lkr,
            @Schema(requiredMode = REQUIRED) BigDecimal usd,
            @Schema(requiredMode = REQUIRED) BigDecimal eur,
            @Schema(requiredMode = REQUIRED) BigDecimal card) {}

    /** Home page figures. Fields a role does not see are null. */
    public record HomeFigures(
            @Schema(requiredMode = REQUIRED) LocalDate day,
            @Schema(description = "Today's sales total (owner, salesperson)") BigDecimal salesTotal,
            @Schema(requiredMode = REQUIRED) int bills,
            @Schema(description = "Cash in the drawer and card today (owner, salesperson)") DrawerNow cash,
            @Schema(requiredMode = REQUIRED, description = "Approvals waiting (owner: for them; others: their own requests)")
            long approvalsWaiting,
            @Schema(requiredMode = REQUIRED) long gemsInStock,
            @Schema(requiredMode = REQUIRED, description = "Transfers on the way to this branch") long onTheWay,
            @Schema(requiredMode = REQUIRED) long customers,
            @Schema(description = "Opening cash entered today (owner, salesperson; null on All branches)") Boolean dayStarted,
            @Schema(description = "Day closed today (owner, salesperson; null on All branches)") Boolean dayClosed,
            @Schema(description = "Owner: days until the gem dealer licence expires, when inside the reminder time") Integer licenceDays,
            LocalDate licenceExpiry,
            @Schema(requiredMode = REQUIRED, description = "Gems sold today (owner, salesperson)") List<SoldGem> soldToday) {}

    /** Owner phone view (K1) for one branch or all. */
    public record PhoneView(
            @Schema(requiredMode = REQUIRED, description = "Branch code or \"all\"") String branch,
            @Schema(requiredMode = REQUIRED) LocalDate day,
            @Schema(requiredMode = REQUIRED) BigDecimal salesTotal,
            @Schema(requiredMode = REQUIRED) int bills,
            @Schema(requiredMode = REQUIRED) int items,
            @Schema(requiredMode = REQUIRED, description = "Sales − gem cost − commissions") BigDecimal profit,
            @Schema(requiredMode = REQUIRED) DrawerNow cash,
            @Schema(requiredMode = REQUIRED) long stockCount,
            @Schema(requiredMode = REQUIRED) BigDecimal stockValue,
            @Schema(requiredMode = REQUIRED) BigDecimal stockCost,
            @Schema(requiredMode = REQUIRED) long approvalsWaiting,
            @Schema(requiredMode = REQUIRED, description = "The 3 newest approvals waiting") List<ApprovalView> approvals,
            @Schema(requiredMode = REQUIRED) List<SoldGem> soldToday,
            @Schema(requiredMode = REQUIRED, description = "The 5 newest alerts") List<AlertView> alerts) {}
}
