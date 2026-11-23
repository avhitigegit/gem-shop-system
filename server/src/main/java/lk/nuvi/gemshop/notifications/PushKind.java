package lk.nuvi.gemshop.notifications;

import lk.nuvi.gemshop.audit.alert.AlertType;

/** What a push to the owner's phone is about. Each can be turned on or off per person (default on). */
public enum PushKind {
    SALE("Every sale", "New sale"),
    APPROVAL("Requests waiting for approval", "Approval needed"),
    CASH_DIFFERENCE("Cash difference at day end", "Cash difference"),
    DAY_CLOSED("Day closed (balanced)", "Day closed");

    private final String label;
    private final String title;

    PushKind(String label, String title) {
        this.label = label;
        this.title = title;
    }

    public String label() {
        return label;
    }

    /** The notification title. */
    public String title() {
        return title;
    }

    /** The push for an alert, or null when that alert does not go to the phone. */
    public static PushKind of(AlertType t) {
        return switch (t) {
            case SALE -> SALE;
            case BELOW_MIN, RETURN, CANCEL_INVOICE, STOCK_CORRECTION, EXPENSE, MONTH_REOPEN -> APPROVAL;
            case CASH_DIFFERENCE -> CASH_DIFFERENCE;
            case CLOSING -> DAY_CLOSED;
            case CANCEL, STOCK, LICENCE -> null;
        };
    }
}
