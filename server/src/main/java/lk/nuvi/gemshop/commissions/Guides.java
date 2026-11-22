package lk.nuvi.gemshop.commissions;

import java.util.List;
import java.util.Locale;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Guides and drivers for New sale (BRD F4). Adding and editing them comes with P3-05. */
@Service
public class Guides {

    private final GuideRepository guides;

    public Guides(GuideRepository guides) {
        this.guides = guides;
    }

    @Transactional(readOnly = true)
    public List<Guide> active() {
        return guides.findByActiveTrueOrderByNameAsc();
    }

    /** The guide on a sale, or null for walk-in (blank code). */
    @Transactional(readOnly = true)
    public Guide orWalkIn(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        Guide g = guides.findByCode(code.trim().toUpperCase(Locale.ROOT)).orElse(null);
        if (g == null || !g.isActive()) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Choose the guide or driver again.",
                    "guide", "Unknown guide.");
        }
        return g;
    }
}
