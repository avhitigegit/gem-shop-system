package lk.nuvi.gemshop.audit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lk.nuvi.gemshop.audit.api.AuditDtos.LogLine;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.paging.PageResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Activity log (prototype k-owner-control/activity-log.html, K4). Read only: there is no API to change or delete a line.
 */
@RestController
@RequestMapping("/api/activity-log")
@Tag(name = "Activity log")
public class ActivityLogController {

    private final ActivityLog log;
    private final CurrentUser current;

    public ActivityLogController(ActivityLog log, CurrentUser current) {
        this.log = log;
        this.current = current;
    }

    @PreAuthorize(Roles.OWNER)
    @GetMapping
    @Operation(operationId = "getActivityLog", summary = "Who did what and when, newest first (last 30 days unless dates are given)")
    public PageResult<LogLine> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "A person's id, or \"system\" for automatic entries")
            @RequestParam(required = false) String user,
            @Parameter(description = "Text in the action or detail") @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw ApiException.badRequest("BAD_DATES", "'From' must be on or before 'To'.");
        }
        boolean system = "system".equalsIgnoreCase(user);
        Long userId = null;
        if (!system && user != null && !user.isBlank()) {
            try {
                userId = Long.parseLong(user);
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("UNKNOWN_USER", "Unknown user: " + user);
            }
        }
        return log.search(current.scope(), new ActivityLog.Filter(from, to, userId, system, q), Math.max(0, page),
                PageResult.size(size, 50, 300));
    }
}
