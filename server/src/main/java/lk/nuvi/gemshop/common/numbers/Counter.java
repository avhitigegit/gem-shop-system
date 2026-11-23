package lk.nuvi.gemshop.common.numbers;

/**
 * The running numbers of the shop's documents (BRD rule 4). Prefix and length are in the {@code number_counter}
 * table: GEM-00001, INV-000001, CN-0001, PUR-0001, QT-0001, RS-0001, HS-0001, EXP-0001, TR-0001, RT-0001,
 * SC-0001, SR-0001, SCK-0001, CP-0001, OF-0001.
 */
public enum Counter {
    GEM,
    INVOICE,
    CREDIT_NOTE,
    PURCHASE,
    /** Price quotation (F7). */
    QUOTE,
    /** Gem kept for a customer (F7). */
    RESERVATION,
    /** Sale waiting for the owner (below-minimum price). */
    HELD_SALE,
    EXPENSE,
    TRANSFER,
    RETURN,
    STOCK_CORRECTION,
    SUPPLIER_RETURN,
    STOCK_CHECK,
    /** Commission paid to a guide / driver or staff (CP-…, P3-05). */
    COMMISSION_PAYMENT,
    /** Offer by email / WhatsApp (OF-…, P4-05). */
    OFFER
}
