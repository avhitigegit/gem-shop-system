package lk.nuvi.gemshop.audit.approval;

/**
 * Finishes the waiting action once the owner decides. Each module adds one Spring bean per type it asks for:
 * <ul>
 *   <li>BELOW_MIN (P3-01): approved → the held sale can be paid; rejected → the gem goes back in stock.</li>
 *   <li>RETURN (P3-04): approved → gem back in stock, credit note, commissions reversed.</li>
 *   <li>CANCEL_INVOICE (P3-02): approved → invoice CANCELLED (kept), gems back in stock, commissions reversed.</li>
 *   <li>STOCK_CORRECTION (P2-04): approved → the change is made on the gem.</li>
 *   <li>EXPENSE (P5-01): approved / rejected → the expense status.</li>
 *   <li>MONTH_REOPEN (P5-03): approved → the month is open again.</li>
 * </ul>
 * Runs in the same transaction as the decision: if the handler fails (e.g. the gem was sold meanwhile),
 * nothing is saved and the owner sees the error.
 */
public interface ApprovalHandler {

    ApprovalType type();

    /** Called once, right after the owner approved ({@code approved = true}) or rejected the request. */
    void decided(Approval approval, boolean approved);
}
