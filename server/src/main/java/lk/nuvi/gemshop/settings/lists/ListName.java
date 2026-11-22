package lk.nuvi.gemshop.settings.lists;

/** The drop-down lists the owner can edit (BRD A5). Prototype key in brackets. */
public enum ListName {
    GEM_TYPE("Gem types", "gem type", "gemTypes"),
    PRODUCT_TYPE("Product types", "product type", "productTypes"),
    COLOUR("Colours", "colour", "colours"),
    SHAPE("Shapes / cuts", "shape", "shapes"),
    LAB("Certificate labs", "lab", "labs"),
    TREATMENT("Treatments", "treatment", "treatments"),
    EXPENSE_TYPE("Expense types", "expense type", "expenseCategories");

    private final String label;
    private final String one;
    private final String prototypeKey;

    ListName(String label, String one, String prototypeKey) {
        this.label = label;
        this.one = one;
        this.prototypeKey = prototypeKey;
    }

    /** "Gem types". */
    public String label() {
        return label;
    }

    /** "gem type" – for messages. */
    public String one() {
        return one;
    }

    public String prototypeKey() {
        return prototypeKey;
    }
}
