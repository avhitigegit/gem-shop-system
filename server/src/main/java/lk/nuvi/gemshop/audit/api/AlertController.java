package lk.nuvi.gemshop.audit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.nuvi.gemshop.audit.alert.Alerts;
import lk.nuvi.gemshop.audit.api.AuditDtos.AlertView;
import lk.nuvi.gemshop.audit.api.AuditDtos.ReadAll;
import lk.nuvi.gemshop.audit.api.AuditDtos.UnreadCount;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.common.paging.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Owner alerts (prototype k-owner-control/alerts.html, K2), per branch in the top bar. */
@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alerts")
public class AlertController {

    private final Alerts alerts;
    private final CurrentUser current;

    public AlertController(Alerts alerts, CurrentUser current) {
        this.alerts = alerts;
        this.current = current;
    }

    @PreAuthorize(Roles.OWNER)
    @GetMapping
    @Operation(operationId = "getAlerts", summary = "Alerts, newest first")
    public PageResult<AlertView> list(
            @Parameter(description = "sale, below_min, return, cancel, stock, closing or other; empty = all")
            @RequestParam(required = false) String group,
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        return alerts.list(current.scope(), group, unread, Math.max(0, page), PageResult.size(size, 80, 200));
    }

    @PreAuthorize(Roles.OWNER)
    @GetMapping("/unread-count")
    @Operation(operationId = "getUnreadAlertCount", summary = "How many alerts are not read yet")
    public UnreadCount unread() {
        return new UnreadCount(alerts.unread(current.scope()));
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/{id}/read")
    @Operation(operationId = "markAlertRead", summary = "Mark one alert as read")
    public AlertView read(@PathVariable long id) {
        return alerts.markRead(id, current.scope());
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/read-all")
    @Operation(operationId = "markAllAlertsRead", summary = "Mark all alerts of the branch in the top bar as read")
    public ReadAll readAll() {
        return new ReadAll(alerts.markAllRead(current.scope()));
    }
}
