package lk.nuvi.gemshop.auth.security;

/**
 * Who may call an API – use on every controller method: {@code @PreAuthorize(Roles.OWNER)}.
 * A test fails if an API has no {@code @PreAuthorize} and is not marked {@link PublicApi}.
 */
public final class Roles {

    public static final String ANY = "isAuthenticated()";
    public static final String OWNER = "hasRole('OWNER')";
    public static final String OWNER_SALES = "hasAnyRole('OWNER','SALESPERSON')";
    public static final String OWNER_STAFF = "hasAnyRole('OWNER','STAFF')";
    public static final String OWNER_ACCOUNTANT = "hasAnyRole('OWNER','ACCOUNTANT')";
    public static final String OWNER_SALES_ACCOUNTANT = "hasAnyRole('OWNER','SALESPERSON','ACCOUNTANT')";
    public static final String OWNER_SALES_STAFF = "hasAnyRole('OWNER','SALESPERSON','STAFF')";

    private Roles() {}
}
