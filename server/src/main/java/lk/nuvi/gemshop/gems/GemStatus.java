package lk.nuvi.gemshop.gems;

import java.util.EnumSet;
import java.util.Set;

/** Where a gem is in its life. "Available stock" = the shop still has it (everything but sold / gone). */
public enum GemStatus {
    IN_STOCK("In stock"),
    NOT_READY("Not ready to sell"),
    RESERVED("Reserved"),
    ON_HOLD("On hold (approval)"),
    SOLD("Sold"),
    RETURNED("Returned"),
    IN_TRANSIT("On the way"),
    WRITTEN_OFF("Written off"),
    RETURNED_SUPPLIER("Returned to supplier");

    /** Still in the shop's stock (prototype Metrics.STOCK_STATUSES). */
    public static final Set<GemStatus> STOCK = EnumSet.of(IN_STOCK, NOT_READY, RESERVED, ON_HOLD, RETURNED, IN_TRANSIT);

    private final String label;

    GemStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Can be put on a sale now: in stock, or returned by a customer and back on the shelf. */
    public boolean sellable() {
        return this == IN_STOCK || this == RETURNED;
    }
}
