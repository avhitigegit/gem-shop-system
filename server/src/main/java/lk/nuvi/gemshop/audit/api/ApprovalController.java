package lk.nuvi.gemshop.audit.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.nuvi.gemshop.audit.api.AuditDtos.ApprovalPage;
import lk.nuvi.gemshop.audit.api.AuditDtos.ApprovalStatusView;
import lk.nuvi.gemshop.audit.api.AuditDtos.ApprovalView;
import lk.nuvi.gemshop.audit.api.AuditDtos.DecisionRequest;
import lk.nuvi.gemshop.audit.api.AuditDtos.Waiting;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import lk.nuvi.gemshop.audit.approval.Approvals;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.common.paging.PageResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Approvals inbox (prototype k-owner-control/approvals.html, K3) and the bell in the top bar.
 * Everybody can see the requests of their branch; only the owner approves or rejects.
 * Requests are sent by the modules themselves (sale below minimum, return, …), not through this API.
 */
@RestController
@RequestMapping("/api/approvals")
@Tag(name = "Approvals")
public class ApprovalController {

    private final Approvals approvals;
    private final CurrentUser current;

    public ApprovalController(Approvals approvals, CurrentUser current) {
        this.approvals = approvals;
        this.current = current;
    }

    @PreAuthorize(Roles.ANY)
    @GetMapping
    @Operation(operationId = "getApprovals", summary = "Pending requests or the history (approved and rejected), newest first")
    public ApprovalPage list(
            @Parameter(description = "pending (default) or done") @RequestParam(defaultValue = "pending") String tab,
            @RequestParam(required = false) ApprovalType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        return approvals.list(current.scope(), !"done".equalsIgnoreCase(tab), type, Math.max(0, page),
                PageResult.size(size, 50, 200));
    }

    @PreAuthorize(Roles.ANY)
    @GetMapping("/waiting")
    @Operation(operationId = "getApprovalsWaiting", summary = "For the bell: how many requests wait for the owner, and the newest few")
    public Waiting waiting() {
        return approvals.waiting(current.scope());
    }

    @PreAuthorize(Roles.ANY)
    @GetMapping("/{number}")
    @Operation(operationId = "getApproval", summary = "One request")
    public ApprovalView get(@PathVariable String number) {
        return approvals.get(number, current.scope());
    }

    @PreAuthorize(Roles.ANY)
    @GetMapping("/{number}/status")
    @Operation(operationId = "getApprovalStatus", summary = "For the waiting screen: still pending, approved or rejected (ask every few seconds)")
    public ApprovalStatusView status(@PathVariable String number) {
        return approvals.status(number, current.scope());
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/{number}/approve")
    @Operation(operationId = "approve", summary = "Approve – the waiting action then runs")
    public ApprovalView approve(@PathVariable String number, @Valid @RequestBody(required = false) DecisionRequest body) {
        return approvals.decide(number, true, body == null ? null : body.note());
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/{number}/reject")
    @Operation(operationId = "reject", summary = "Reject, with an optional note for the person who asked")
    public ApprovalView reject(@PathVariable String number, @Valid @RequestBody(required = false) DecisionRequest body) {
        return approvals.decide(number, false, body == null ? null : body.note());
    }
}
