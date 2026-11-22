package lk.nuvi.gemshop.gems;

import java.util.Locale;

/** Lab certificate (e.g. NGJA, GIA), the shop's own certificate (printed from the system), or none (BRD C7–C9). */
public enum CertType {
    LAB, SHOP, NONE;

    /** Rule 6 / C9: sapphires (and padparadscha) normally have a lab certificate; other gems the shop certificate. */
    public static CertType suggestedFor(String gemType) {
        String g = gemType == null ? "" : gemType.toLowerCase(Locale.ROOT);
        return g.contains("sapphire") || g.contains("padparadscha") ? LAB : SHOP;
    }
}
