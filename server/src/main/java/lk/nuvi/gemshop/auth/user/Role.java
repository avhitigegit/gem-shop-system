package lk.nuvi.gemshop.auth.user;

/** The four roles (BRD A2). */
public enum Role {
    OWNER("Owner"),
    SALESPERSON("Salesperson"),
    STAFF("Shop Staff"),
    ACCOUNTANT("Accountant");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Owner and Accountant see all branches; the others only their own branch. */
    public boolean seesAllBranches() {
        return this == OWNER || this == ACCOUNTANT;
    }
}
