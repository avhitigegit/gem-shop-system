package lk.nuvi.gemshop.audit.alert;

/**
 * What an owner alert is about (K2). {@link #group()} is the filter button on the Alerts screen.
 */
public enum AlertType {
    SALE("sale"),
    BELOW_MIN("below_min"),
    RETURN("return"),
    /** A request to cancel an invoice. */
    CANCEL_INVOICE("cancel"),
    /** An invoice was cancelled (after approval). */
    CANCEL("cancel"),
    STOCK_CORRECTION("stock"),
    /** Other stock changes: transfer sent / received, stock check difference. */
    STOCK("stock"),
    EXPENSE("other"),
    CLOSING("closing"),
    /** Day closing with a cash difference. */
    CASH_DIFFERENCE("closing"),
    LICENCE("other"),
    MONTH_REOPEN("other");

    /** Filter groups, in the order of the buttons: All, Sales, Below-minimum, Returns, Cancellations, Stock changes, Day closing, Other. */
    public static final String[] GROUPS = {"sale", "below_min", "return", "cancel", "stock", "closing", "other"};

    private final String group;

    AlertType(String group) {
        this.group = group;
    }

    public String group() {
        return group;
    }
}
