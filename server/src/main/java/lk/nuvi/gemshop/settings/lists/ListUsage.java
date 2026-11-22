package lk.nuvi.gemshop.settings.lists;

/**
 * A module whose records use drop-down values. Gems (P2-01: gem type, product type, colour, shape, treatment,
 * lab) and expenses (P5-01: expense type) add a bean, so the Drop-down lists screen can say "Used by 12 gems",
 * refuse removing a used value, and rename the value in the records too.
 * <pre>
 * &#64;Component
 * class GemListUsage implements ListUsage {
 *     public long count(ListName list, String value) { ... }
 *     public void rename(ListName list, String from, String to) { ... }
 * }
 * </pre>
 */
public interface ListUsage {

    /** How many records use this value (0 if this module does not use the list). */
    long count(ListName list, String value);

    /** The owner renamed a value: change it in this module's records (same transaction). */
    void rename(ListName list, String from, String to);

    /** "gem(s)", "expense(s)" – for "Used by 3 gem(s)". */
    String unit(ListName list);
}
