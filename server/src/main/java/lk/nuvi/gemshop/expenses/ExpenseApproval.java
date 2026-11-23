package lk.nuvi.gemshop.expenses;

import lk.nuvi.gemshop.audit.approval.Approval;
import lk.nuvi.gemshop.audit.approval.ApprovalHandler;
import lk.nuvi.gemshop.audit.approval.ApprovalType;
import org.springframework.stereotype.Component;

/**
 * The owner decided a large expense (BRD M6): approved / rejected → the expense status. A rejected drawer expense no
 * longer comes off the expected cash (the money goes back into the drawer).
 */
@Component
public class ExpenseApproval implements ApprovalHandler {

    private final ExpenseRepository expenses;

    public ExpenseApproval(ExpenseRepository expenses) {
        this.expenses = expenses;
    }

    @Override
    public ApprovalType type() {
        return ApprovalType.EXPENSE;
    }

    @Override
    public void decided(Approval a, boolean approved) {
        expenses.findByNumber(a.getRef() == null ? "" : a.getRef())
                .filter(e -> e.getStatus() == Expense.Status.PENDING)
                .ifPresent(e -> e.decided(approved));
    }
}
