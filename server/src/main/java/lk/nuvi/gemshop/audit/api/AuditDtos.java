package lk.nuvi.gemshop.audit.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lk.nuvi.gemshop.audit.alert.AlertType;
import lk.nuvi.gemshop.audit.approval.ApprovalStatus;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.auth.user.Role;

/**
 * Data for the Activity log, Approvals and Alerts screens. Branch = branch code, or "all" for the whole shop.
 * Fields without {@code REQUIRED} can be empty.
 */
public final class AuditDtos {

    private AuditDtos() {}

    /** One activity log line. {@code system} = done automatically ("System (automatic)"), then userId is empty. */
    public record LogLine(
            @Schema(requiredMode = REQUIRED) Instant at,
            Long userId,
            @Schema(requiredMode = REQUIRED) String userName,
            @Schema(requiredMode = REQUIRED) boolean system,
            @Schema(requiredMode = REQUIRED) String action,
            @Schema(requiredMode = REQUIRED) String detail,
            @Schema(requiredMode = REQUIRED) String branch) {}

    /** One alert. {@code group} = the filter button; {@code link} = app page to open. */
    public record AlertView(
            @Schema(requiredMode = REQUIRED) long id,
            @Schema(requiredMode = REQUIRED) Instant at,
            @Schema(requiredMode = REQUIRED) AlertType type,
            @Schema(requiredMode = REQUIRED) String group,
            @Schema(requiredMode = REQUIRED) String text,
            String link,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) boolean read) {}

    public record UnreadCount(@Schema(requiredMode = REQUIRED) long unread) {}

    public record ReadAll(@Schema(requiredMode = REQUIRED) int marked) {}

    public record Person(
            @Schema(requiredMode = REQUIRED) long id,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) Role role) {}

    /** One request. {@code amount} in LKR; {@code ref} = the waiting record (held sale, return, invoice, …). */
    public record ApprovalView(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) ApprovalType type,
            @Schema(requiredMode = REQUIRED) String typeLabel,
            @Schema(requiredMode = REQUIRED) ApprovalStatus status,
            @Schema(requiredMode = REQUIRED) Instant requestedAt,
            @Schema(requiredMode = REQUIRED) Person requestedBy,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) String title,
            @Schema(requiredMode = REQUIRED) String detail,
            BigDecimal amount,
            String ref,
            Instant decidedAt,
            String decidedBy,
            @Schema(requiredMode = REQUIRED) String note) {}

    /** One page of approvals plus how many are waiting (for the "Pending (n)" tab). */
    public record ApprovalPage(
            @Schema(requiredMode = REQUIRED) List<ApprovalView> items,
            @Schema(requiredMode = REQUIRED) long total,
            @Schema(requiredMode = REQUIRED) int page,
            @Schema(requiredMode = REQUIRED) int size,
            @Schema(requiredMode = REQUIRED) long pending) {}

    /** The bell and the menu count: how many requests wait for the owner, and the newest few. */
    public record Waiting(
            @Schema(requiredMode = REQUIRED) long count,
            @Schema(requiredMode = REQUIRED) List<ApprovalView> latest) {}

    /** Small answer for the "waiting for the owner" screen, asked every few seconds. */
    public record ApprovalStatusView(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) ApprovalStatus status,
            Instant decidedAt,
            String decidedBy,
            @Schema(requiredMode = REQUIRED) String note) {}

    public record DecisionRequest(@Size(max = 500) String note) {}
}
