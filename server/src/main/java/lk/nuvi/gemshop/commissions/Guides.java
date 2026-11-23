package lk.nuvi.gemshop.commissions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.GuideCard;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.GuideForm;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.GuideList;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.GuidePct;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.GuidePctForm;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.GuideProfile;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.settings.commission.Commission;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guides and drivers (BRD H1, F4, A6): non-staff who bring tourists and earn their own % of every sale they bring. The owner
 * adds and edits them; new guides start with the default % (Settings → Commission). The salesperson sees them without
 * amounts.
 */
@Service
public class Guides {

    public static final String DUPLICATE_GUIDE = "DUPLICATE_GUIDE";
    private static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile("G-(\\d+)");

    private final GuideRepository guides;
    private final CommissionLineRepository lines;
    private final Commission commission;
    private final ActivityLog log;
    private final CurrentUser current;
    private final ZoneId zone;

    public Guides(GuideRepository guides, CommissionLineRepository lines, Commission commission, ActivityLog log, CurrentUser current,
            AppProperties app) {
        this.guides = guides;
        this.lines = lines;
        this.commission = commission;
        this.log = log;
        this.current = current;
        this.zone = ZoneId.of(app.timezone());
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

    // ------------------------------------------------------------------ list and profile

    @Transactional(readOnly = true)
    public GuideList list() {
        Map<Long, List<CommissionLine>> by = new HashMap<>();
        for (CommissionLine l : lines.guideLines(current.scope().branchId())) {
            by.computeIfAbsent(l.getGuide().getId(), k -> new ArrayList<>()).add(l);
        }
        boolean amounts = amounts();
        List<GuideCard> cards = active().stream().map(g -> card(g, by.getOrDefault(g.getId(), List.of()), amounts)).toList();
        return new GuideList(cards, commission.current().defaultGuidePct());
    }

    @Transactional(readOnly = true)
    public GuideProfile profile(String code, LocalDate from, LocalDate to) {
        Guide g = require(code);
        LocalDate t = to == null ? LocalDate.now(zone) : to;
        LocalDate f = from == null ? t.minusDays(30) : from;
        boolean amounts = amounts();
        List<CommissionLine> all = lines.ofGuide(g.getId(), current.scope().branchId());
        List<CommissionLine> in = all.stream().filter(l -> {
            LocalDate d = l.getEarnedAt().atZone(zone).toLocalDate();
            return !d.isBefore(f) && !d.isAfter(t);
        }).toList();
        List<CommissionLine> brought = in.stream().filter(l -> l.getStatus() != CommissionLine.Status.REVERSED).toList();
        List<CommissionLine> owed = in.stream().filter(CommissionLine::isOwed).toList();
        return new GuideProfile(card(g, all, amounts), f, t, brought.size(),
                brought.stream().map(l -> l.getSale().getTotal()).reduce(BigDecimal.ZERO, BigDecimal::add),
                amounts ? Rows.sum(in) : null, amounts ? Rows.sum(owed) : null, owed.size(),
                in.stream().map(l -> Rows.row(l, amounts)).toList());
    }

    // ------------------------------------------------------------------ add / edit (owner)

    @Transactional
    public GuideCard create(GuideForm f) {
        Guide.Details d = details(f, f.pct() == null ? commission.current().defaultGuidePct() : f.pct(), LocalDate.now(zone));
        checkUnique(d, null);
        Guide g = guides.save(new Guide(nextCode(), d));
        log.add("Guide added", g.getCode() + " · " + g.getName() + " · " + pct(g.getPct()), null);
        return card(g, List.of(), true);
    }

    @Transactional
    public GuideCard update(String code, GuideForm f) {
        Guide g = require(code);
        BigDecimal before = g.getPct();
        Guide.Details d = details(f, f.pct() == null ? g.getPct() : f.pct(), g.getSince());
        checkUnique(d, g);
        g.change(d);
        log.add("Guide edited", g.getCode() + " · " + g.getName() + " · " + pct(g.getPct())
                + (before.compareTo(g.getPct()) == 0 ? "" : " (was " + pct(before) + ", new sales only)"), null);
        return card(g, lines.ofGuide(g.getId(), current.scope().branchId()), true);
    }

    // ------------------------------------------------------------------ settings → commission: each guide's own %

    @Transactional(readOnly = true)
    public List<GuidePct> pcts() {
        return active().stream().map(g -> new GuidePct(g.getCode(), g.getName(), g.getVehicle(), g.getPct())).toList();
    }

    /** Saves the % of the guides sent (others stay as they are). Applies to new sales. */
    @Transactional
    public List<GuidePct> savePcts(List<GuidePctForm> list) {
        Map<String, BigDecimal> changed = new LinkedHashMap<>();
        for (GuidePctForm p : list == null ? List.<GuidePctForm>of() : list) {
            Guide g = require(p.code());
            BigDecimal next = p.pct().setScale(2, RoundingMode.HALF_UP);
            if (g.getPct().compareTo(next) != 0) {
                g.change(new Guide.Details(g.getName(), g.getVehicle(), g.getVehicleNo(), g.getPhone(), next, g.getSince()));
                changed.put(g.getName(), next);
            }
        }
        if (!changed.isEmpty()) {
            List<String> parts = new ArrayList<>();
            changed.forEach((n, v) -> parts.add(n + " " + pct(v)));
            log.add("Guide % changed", String.join(" · ", parts), null);
        }
        return pcts();
    }

    // ------------------------------------------------------------------ helpers

    Guide require(String code) {
        return guides.findByCode(code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("No guide or driver " + code + "."));
    }

    private boolean amounts() {
        Role r = current.require().role();
        return r == Role.OWNER || r == Role.ACCOUNTANT;
    }

    private static GuideCard card(Guide g, List<CommissionLine> ls, boolean amounts) {
        List<CommissionLine> brought = ls.stream()
                .filter(l -> l.getStatus() != CommissionLine.Status.REVERSED && l.getSale().getStatus() == Sale.Status.COMPLETED).toList();
        return new GuideCard(g.getCode(), g.getName(), g.getVehicle(), g.getVehicleNo(), g.getPhone(), g.getPct(), g.getSince(),
                brought.size(), brought.stream().map(l -> l.getSale().getTotal()).reduce(BigDecimal.ZERO, BigDecimal::add),
                amounts ? Rows.sum(ls) : null, amounts ? Rows.sum(ls.stream().filter(CommissionLine::isOwed).toList()) : null);
    }

    private static Guide.Details details(GuideForm f, BigDecimal pct, LocalDate since) {
        return new Guide.Details(f.name().trim(), f.vehicle(), f.vehicleNo().trim().toUpperCase(Locale.ROOT), f.phone().trim(),
                pct.setScale(2, RoundingMode.HALF_UP), since);
    }

    /** Two active guides may not share a phone number (the same person added twice). */
    private void checkUnique(Guide.Details d, Guide self) {
        String phone = digits(d.phone());
        for (Guide o : active()) {
            if (o != self && digits(o.getPhone()).equals(phone)) {
                throw ApiException.field(HttpStatus.CONFLICT, DUPLICATE_GUIDE, o.getName() + " already has this phone number.",
                        "phone", "Already used by " + o.getName() + ".");
            }
        }
    }

    private String nextCode() {
        int max = 0;
        for (Guide g : guides.findAll()) {
            Matcher m = CODE.matcher(g.getCode());
            if (m.matches()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        return String.format(Locale.ROOT, "G-%02d", max + 1);
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    static String pct(BigDecimal p) {
        return p.stripTrailingZeros().toPlainString() + "%";
    }
}
