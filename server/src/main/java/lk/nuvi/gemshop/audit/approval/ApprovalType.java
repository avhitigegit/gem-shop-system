package lk.nuvi.gemshop.audit.approval;

import lk.nuvi.gemshop.audit.alert.AlertType;

/** What needs the owner's approval (K3 + F3, E1, G6, D7, M6, N6). Labels as on the Approvals screen. */
public enum ApprovalType {
    BELOW_MIN("Below-minimum price", AlertType.BELOW_MIN),
    RETURN("Return / exchange", AlertType.RETURN),
    CANCEL_INVOICE("Invoice cancellation", AlertType.CANCEL_INVOICE),
    STOCK_CORRECTION("Stock correction", AlertType.STOCK_CORRECTION),
    EXPENSE("Large expense", AlertType.EXPENSE),
    /** A change in a closed month: the month is re-opened first (rule 11). */
    MONTH_REOPEN("Re-open closed month", AlertType.MONTH_REOPEN);

    private final String label;
    private final AlertType alert;

    ApprovalType(String label, AlertType alert) {
        this.label = label;
        this.alert = alert;
    }

    public String label() {
        return label;
    }

    /** The owner alert raised with a new request. */
    public AlertType alert() {
        return alert;
    }
}
