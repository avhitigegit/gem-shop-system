package lk.nuvi.gemshop.stock;

import java.util.Locale;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemRepository;
import lk.nuvi.gemshop.gems.Gems;
import lk.nuvi.gemshop.stock.api.StockDtos.StockGem;
import org.springframework.stereotype.Component;

/** Gem lookups and the small gem view used on the stock screens. */
@Component
class StockGems {

    private final GemRepository gems;

    StockGems(GemRepository gems) {
        this.gems = gems;
    }

    /** By code or scanned tag text (GEM-00125, 125, GEM-00125|CGH-SC-00125); any branch. */
    Gem find(String code) {
        String c = Gems.normaliseCode(code);
        return (c == null ? null : gems.findByCode(c).orElse(null));
    }

    Gem require(String code) {
        Gem g = find(code);
        if (g == null) {
            throw ApiException.notFound("No gem with code " + (code == null ? "" : code.trim().toUpperCase(Locale.ROOT)) + ".");
        }
        return g;
    }

    /** Cost-based figures are for the owner only; the selling price too on these lists (as in the prototype). */
    static StockGem view(Gem g) {
        boolean owner = CurrentUser.role().map(r -> r == Role.OWNER).orElse(false);
        return new StockGem(g.getCode(), g.getName(), g.getGemType(), g.getProductType(), g.getCarat(), g.getColour(),
                g.getBranch().getCode(), g.getStatus(), owner ? g.getPrice() : null);
    }
}
