package lk.nuvi.gemshop.commissions;

/** How a commission was paid (prototype: Cash (drawer) / Cash (owner) / Bank transfer / With salary). */
public enum PayMethod {
    /** From the cash drawer – part of the day's cash closing (BRD I2). */
    DRAWER("Cash (drawer)"),
    OWNER_CASH("Cash (owner)"),
    BANK("Bank transfer"),
    SALARY("With salary");

    private final String label;

    PayMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
