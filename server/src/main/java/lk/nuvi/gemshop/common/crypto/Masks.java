package lk.nuvi.gemshop.common.crypto;

/** How sensitive values are shown on screen. */
public final class Masks {

    private Masks() {}

    /** Passport as on the customer profile: "GE4000000" → "•••• 000". Empty stays empty. */
    public static String passport(String passport) {
        if (passport == null || passport.isBlank()) {
            return null;
        }
        String p = passport.replaceAll("\\s", "");
        return p.length() <= 3 ? "••••" : "•••• " + p.substring(p.length() - 3); // too short to show any of it
    }
}
