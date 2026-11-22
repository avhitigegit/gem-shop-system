package lk.nuvi.gemshop.gems;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.security.CurrentUser.Who;
import lk.nuvi.gemshop.auth.user.Role;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.branch.BranchScope;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.files.FileKind;
import lk.nuvi.gemshop.files.FileService;
import lk.nuvi.gemshop.files.StoredFile;
import lk.nuvi.gemshop.gems.api.GemDtos.GemCard;
import lk.nuvi.gemshop.gems.api.GemDtos.GemForm;
import lk.nuvi.gemshop.gems.api.GemDtos.GemPage;
import lk.nuvi.gemshop.gems.api.GemDtos.GemView;
import lk.nuvi.gemshop.gems.api.GemDtos.HistoryLine;
import lk.nuvi.gemshop.gems.api.GemDtos.StockSummary;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.settings.branch.BranchStatus;
import lk.nuvi.gemshop.settings.lists.ListName;
import lk.nuvi.gemshop.settings.lists.Lists;
import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gems (BRD C1–C9, D1, D2, rule 1, 2, 6). Only the owner adds gems and changes prices; cost and minimum price are
 * shown to the owner only. Every change goes in the gem's history and the activity log.
 */
@Service
public class Gems {

    public static final String OWNER_TYPE = "GEM";
    public static final String PRICES = "PRICES";
    public static final String CERT_NEEDED = "CERT_NEEDED";
    public static final String NOT_NOT_READY = "NOT_NOT_READY";
    public static final String BRANCH_CLOSED = "BRANCH_CLOSED";

    private static final Pattern DIGITS = Pattern.compile("\\d{1,5}");
    private static final Pattern CODE = Pattern.compile("GEM-?(\\d{1,5})");

    private final GemRepository gems;
    private final GemHistoryRepository history;
    private final BranchRepository branches;
    private final Lists lists;
    private final Numbers numbers;
    private final FileService files;
    private final CurrentUser current;
    private final ActivityLog log;
    private final ShopSettingsRepository shops;
    private final EntityManager em;

    public Gems(GemRepository gems, GemHistoryRepository history, BranchRepository branches, Lists lists, Numbers numbers,
            FileService files, CurrentUser current, ActivityLog log, ShopSettingsRepository shops, EntityManager em) {
        this.gems = gems;
        this.history = history;
        this.branches = branches;
        this.lists = lists;
        this.numbers = numbers;
        this.files = files;
        this.current = current;
        this.log = log;
        this.shops = shops;
        this.em = em;
    }

    // ------------------------------------------------------------------ search

    /**
     * All gems filter. {@code status}: "stock" (default – still in the shop), "all", or one {@link GemStatus};
     * {@code q}: code, name, colour or gem type ("125" and "gem-125" find GEM-00125).
     */
    public record Filter(String gemType, String productType, String status, CertType cert, String q) {}

    @Transactional(readOnly = true)
    public GemPage search(Filter f, int page, int size) {
        BranchScope scope = current.scope();
        boolean owner = isOwner();
        Specification<Gem> spec = (root, query, cb) -> where(root, cb, scope, f);
        Page<Gem> found = gems.findAll(spec, PageRequest.of(page, size, Sort.by(Sort.Order.desc("code"))));
        Map<String, String> photos = firstPhotos(found.getContent().stream().map(Gem::getCode).toList());
        List<GemCard> cards = found.getContent().stream().map(g -> card(g, owner, photos.get(g.getCode()))).toList();

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Object[]> sums = cb.createQuery(Object[].class);
        Root<Gem> r = sums.from(Gem.class);
        sums.multiselect(cb.coalesce(cb.sum(r.get("price")), BigDecimal.ZERO), cb.coalesce(cb.sum(r.get("cost")), BigDecimal.ZERO))
                .where(where(r, cb, scope, f));
        Object[] t = em.createQuery(sums).getSingleResult();
        return new GemPage(cards, found.getTotalElements(), found.getNumber(), found.getSize(), (BigDecimal) t[0],
                owner ? (BigDecimal) t[1] : null);
    }

    /** Gems & stock home: gems still in the shop, not ready, back from customers, and the 6 newest. */
    @Transactional(readOnly = true)
    public StockSummary summary() {
        BranchScope scope = current.scope();
        long stock = gems.count(statusIn(scope, GemStatus.STOCK));
        long notReady = gems.count(statusIn(scope, java.util.Set.of(GemStatus.NOT_READY)));
        long returned = gems.count(statusIn(scope, java.util.Set.of(GemStatus.RETURNED)));
        List<GemCard> newest = search(new Filter(null, null, "stock", null, null), 0, 6).items();
        return new StockSummary(stock, notReady, returned, newest);
    }

    private static Specification<Gem> statusIn(BranchScope scope, java.util.Collection<GemStatus> st) {
        return (g, q, cb) -> {
            Predicate p = g.get("status").in(st);
            return scope.branch().map(b -> cb.and(p, cb.equal(g.get("branch").get("id"), b))).orElse(p);
        };
    }

    private static Predicate where(Root<Gem> g, CriteriaBuilder cb, BranchScope scope, Filter f) {
        List<Predicate> p = new ArrayList<>();
        scope.branch().ifPresent(b -> p.add(cb.equal(g.get("branch").get("id"), b)));
        if (notBlank(f.gemType())) {
            p.add(cb.equal(g.get("gemType"), f.gemType().trim()));
        }
        if (notBlank(f.productType())) {
            p.add(cb.equal(g.get("productType"), f.productType().trim()));
        }
        if (f.cert() != null) {
            p.add(cb.equal(g.get("certType"), f.cert()));
        }
        String st = f.status() == null || f.status().isBlank() ? "stock" : f.status().trim();
        if ("stock".equalsIgnoreCase(st)) {
            p.add(g.get("status").in(GemStatus.STOCK));
        } else if (!"all".equalsIgnoreCase(st)) {
            p.add(cb.equal(g.get("status"), status(st)));
        }
        if (notBlank(f.q())) {
            String q = f.q().trim();
            String code = normaliseCode(q);
            String like = "%" + q.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            List<Predicate> any = new ArrayList<>(List.of(
                    cb.like(cb.lower(g.get("code")), like, '\\'),
                    cb.like(cb.lower(g.get("name")), like, '\\'),
                    cb.like(cb.lower(g.get("colour")), like, '\\'),
                    cb.like(cb.lower(g.get("gemType")), like, '\\')));
            if (code != null) {
                any.add(cb.equal(g.get("code"), code));
            }
            p.add(cb.or(any.toArray(Predicate[]::new)));
        }
        return cb.and(p.toArray(Predicate[]::new));
    }

    private static GemStatus status(String s) {
        try {
            return GemStatus.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Unknown status: " + s);
        }
    }

    /** "GEM-00125", "gem-125", "GEM125", "125", "GEM-00125|CGH-SC-00125" → "GEM-00125" (what a tag scanner or a person types); else null. */
    public static String normaliseCode(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim().toUpperCase(Locale.ROOT);
        int bar = t.indexOf('|'); // shop certificate QR: "GEM-00131|CGH-SC-00131"
        if (bar > 0) {
            t = t.substring(0, bar).trim();
        }
        var m = CODE.matcher(t);
        if (m.matches()) {
            return "GEM-%05d".formatted(Integer.parseInt(m.group(1)));
        }
        return DIGITS.matcher(t).matches() ? "GEM-%05d".formatted(Integer.parseInt(t)) : null;
    }

    // ------------------------------------------------------------------ one gem

    @Transactional(readOnly = true)
    public GemView view(String code) {
        return view(visible(code));
    }

    /** The code the next new gem gets (shown on the add form; not reserved). */
    @Transactional(readOnly = true)
    public String nextCode() {
        return numbers.peek(Counter.GEM);
    }

    @Transactional
    public GemView add(GemForm f) {
        Branch branch = branch(f.branch());
        Gem.Details d = details(f);
        Gem.Prices p = prices(f);
        String code = numbers.next(Counter.GEM);
        Gem.Certificate c = certificate(f, code);
        Who who = current.require();
        GemStatus status = Boolean.TRUE.equals(f.notReady()) ? GemStatus.NOT_READY : GemStatus.IN_STOCK;
        Gem g = gems.save(new Gem(code, d, c, p, branch, status, Instant.now(), user(who)));
        g.boughtIn(supplier(f), null);
        attachFiles(g, f);
        String note = status == GemStatus.NOT_READY ? " · not ready to sell yet" : "";
        addHistory(g, "Added", "Added by " + who.name() + note);
        log.add("Gem added", code + " · " + d.name() + " · LKR " + lkr(p.price()) + note, branch.getId());
        return view(g);
    }

    /**
     * A gem bought in a purchase (P2-03): the same checks as the gem form, a new code, linked to the supplier and the
     * purchase, "Added" in its history. Runs inside the purchase's transaction (all gems are saved, or none).
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Gem addBought(GemForm f, String supplierCode, String purchaseNo, String supplierName) {
        Branch branch = branch(f.branch());
        Gem.Details d = details(f);
        Gem.Prices p = prices(f);
        String code = numbers.next(Counter.GEM);
        Gem.Certificate c = certificate(f, code);
        boolean notReady = Boolean.TRUE.equals(f.notReady());
        Gem g = gems.save(new Gem(code, d, c, p, branch, notReady ? GemStatus.NOT_READY : GemStatus.IN_STOCK, Instant.now(),
                user(current.require())));
        g.boughtIn(supplierCode, purchaseNo);
        attachFiles(g, f);
        addHistory(g, "Added", "Bought from " + supplierName + " (" + purchaseNo + ")" + (notReady ? " – not ready to sell yet" : ""));
        return g;
    }

    /** The supplier chosen on the gem form, checked; null = not linked. */
    private String supplier(GemForm f) {
        if (f.supplier() == null || f.supplier().isBlank()) {
            return null;
        }
        String code = f.supplier().trim().toUpperCase(java.util.Locale.ROOT);
        if (!gems.supplierExists(code)) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Choose a supplier from the list.",
                    "supplier", "Not in the supplier list.");
        }
        return code;
    }

    @Transactional
    public GemView edit(String code, GemForm f) {
        Gem g = visible(code);
        Branch branch = branch(f.branch());
        Gem.Details d = details(f);
        Gem.Prices p = prices(f);
        Gem.Certificate c = certificate(f, code);
        List<String> changes = new ArrayList<>();
        Gem.Prices was = g.getPrices();
        money(changes, "Selling price", was.price(), p.price());
        money(changes, "Minimum price", was.min(), p.min());
        money(changes, "Cost price", was.cost(), p.cost());
        if (!g.getBranch().getId().equals(branch.getId())) {
            changes.add("Branch " + g.getBranch().getDisplayName() + " → " + branch.getDisplayName());
        }
        if (c.type() != g.getCertType()) {
            changes.add("Certificate " + label(g.getCertType()) + " → " + label(c.type()));
        }
        if (g.getPurchaseNo() == null) { // a gem bought in a purchase keeps its supplier
            String sup = supplier(f);
            if (!Objects.equals(sup, g.getSupplierCode())) {
                changes.add("Supplier " + (g.getSupplierCode() == null ? "not linked" : g.getSupplierCode()) + " → "
                        + (sup == null ? "not linked" : sup));
                g.boughtIn(sup, null);
            }
        }
        boolean detailsChanged = !d.equals(g.getDetails()) || !Objects.equals(c.lab(), g.getCertLab())
                || !Objects.equals(c.number(), g.getCertNo()) || !Objects.equals(c.date(), g.getCertDate())
                || !Objects.equals(c.scan(), g.getCertScan());
        g.details(d);
        g.certificate(c);
        g.prices(p);
        g.moveTo(branch);
        int photoChanges = attachFiles(g, f);
        if (detailsChanged && changes.stream().noneMatch(x -> x.startsWith("Certificate"))) {
            changes.add("Details updated");
        }
        if (photoChanges > 0) {
            changes.add("Photos updated");
        }
        if (changes.isEmpty()) {
            return view(g);
        }
        String detail = String.join(", ", changes);
        addHistory(g, changes.stream().anyMatch(x -> x.contains("price")) ? "Price changed" : "Edited", detail);
        log.add(changes.stream().anyMatch(x -> x.contains("price")) ? "Gem price changed" : "Gem edited",
                code + " · " + detail, branch.getId());
        return view(g);
    }

    /** "✓ Mark ready to sell" (owner or shop staff): a gem that needed work can now be sold. */
    @Transactional
    public GemView markReady(String code) {
        Gem g = visible(code);
        if (g.getStatus() != GemStatus.NOT_READY) {
            throw ApiException.conflict(NOT_NOT_READY, code + " is already " + g.getStatus().label().toLowerCase(Locale.ROOT) + ".");
        }
        g.setStatus(GemStatus.IN_STOCK);
        addHistory(g, "Ready to sell", "Marked ready to sell");
        log.add("Gem ready to sell", code + " · " + g.getName(), g.getBranch().getId());
        return view(g);
    }

    /** A gem the person may see (salesperson / staff: their branch only). For sales, transfers … (P2-04, P3). */
    @Transactional(readOnly = true)
    public Gem visible(String code) {
        String c = normaliseCode(code);
        Gem g = (c == null ? null : gems.findByCode(c).orElse(null));
        if (g == null) {
            throw ApiException.notFound("No gem with code " + code + ".");
        }
        Who w = current.require();
        if (!w.role().seesAllBranches() && !g.getBranch().getId().equals(w.branchId())) {
            throw ApiException.notFound("No gem with code " + code + " in your branch.");
        }
        return g;
    }

    /** A line in the gem's history (used by sales, transfers, returns …). */
    @Transactional
    public void addHistory(Gem g, String action, String detail) {
        Who w = CurrentUser.get().orElse(null);
        history.save(new GemHistory(g, Instant.now(), w == null ? null : user(w), w == null ? "System" : w.name(), action, detail));
    }

    // ------------------------------------------------------------------ form checks

    private Gem.Details details(GemForm f) {
        return new Gem.Details(f.name().trim(), lists.check(ListName.PRODUCT_TYPE, f.productType(), "productType"),
                lists.check(ListName.GEM_TYPE, f.gemType(), "gemType"), f.carat().setScale(2, java.math.RoundingMode.HALF_UP),
                lists.check(ListName.COLOUR, f.colour(), "colour"), lists.check(ListName.SHAPE, f.shape(), "shape"),
                lists.check(ListName.TREATMENT, f.treatment(), "treatment"), blank(f.description()));
    }

    /** Rule 2: the minimum price is at or above cost and not above the selling price. */
    private static Gem.Prices prices(GemForm f) {
        if (f.min().compareTo(f.price()) > 0) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, PRICES, "The minimum price cannot be above the selling price.",
                    "min", "Not above the selling price.");
        }
        if (f.min().compareTo(f.cost()) < 0) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, PRICES, "The minimum price cannot be below the cost price.",
                    "min", "Not below the cost price.");
        }
        return new Gem.Prices(f.cost(), f.price(), f.min());
    }

    private Gem.Certificate certificate(GemForm f, String code) {
        return switch (f.certType()) {
            case LAB -> {
                if (!notBlank(f.certNo())) {
                    throw ApiException.field(HttpStatus.BAD_REQUEST, CERT_NEEDED, "Enter the certificate number.", "certNo",
                            "Enter the certificate number.");
                }
                if (f.certDate() == null) {
                    throw ApiException.field(HttpStatus.BAD_REQUEST, CERT_NEEDED, "Enter the certificate date.", "certDate",
                            "Enter the certificate date.");
                }
                yield new Gem.Certificate(CertType.LAB, lists.check(ListName.LAB, f.certLab(), "certLab"), f.certNo().trim(),
                        f.certDate(), f.certScan());
            }
            case SHOP -> new Gem.Certificate(CertType.SHOP, null, shopCertNo(code), null, null);
            case NONE -> new Gem.Certificate(CertType.NONE, null, null, null, null);
        };
    }

    /** Shop certificate number: shop initials + SC + gem number, e.g. CGH-SC-00125 (prototype). */
    public String shopCertNo(String code) {
        String name = shops.current().map(ShopSettings::getName).orElse("Shop");
        StringBuilder initials = new StringBuilder();
        for (String w : name.split("[^\\p{L}\\p{N}]+")) {
            if (!w.isEmpty()) {
                initials.append(Character.toUpperCase(w.charAt(0)));
            }
        }
        String pre = initials.isEmpty() ? "SC" : initials.substring(0, Math.min(4, initials.length())) + "-SC";
        return pre + "-" + code.substring(code.indexOf('-') + 1);
    }

    private Branch branch(String code) {
        Branch b = branches.findByCode(code == null ? "" : code.trim()).orElseThrow(() -> ApiException.field(
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Choose a branch.", "branch", "Choose a branch."));
        if (b.getStatus() == BranchStatus.INACTIVE) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, BRANCH_CLOSED, b.getDisplayName() + " is closed.", "branch",
                    "This branch is closed.");
        }
        return b;
    }

    /** Photos = the list on the form (removed ones are deleted); the certificate scan likewise. Returns changes. */
    private int attachFiles(Gem g, GemForm f) {
        List<StoredFile> have = files.ofOwner(OWNER_TYPE, g.getCode());
        List<UUID> wantPhotos = f.photos().stream().filter(Objects::nonNull).distinct().toList();
        UUID wantScan = f.certType() == CertType.LAB ? f.certScan() : null;
        int changes = 0;
        for (StoredFile s : have) {
            boolean keep = s.getKind() == FileKind.PRODUCT_PHOTO ? wantPhotos.contains(s.getPublicId())
                    : s.getKind() == FileKind.CERTIFICATE && s.getPublicId().equals(wantScan);
            if (!keep) {
                files.remove(s);
                changes++;
            }
        }
        List<UUID> had = have.stream().map(StoredFile::getPublicId).toList();
        List<UUID> newPhotos = wantPhotos.stream().filter(id -> !had.contains(id)).toList();
        if (!newPhotos.isEmpty()) {
            files.attach(newPhotos, FileKind.PRODUCT_PHOTO, OWNER_TYPE, g.getCode());
            changes += newPhotos.size();
        }
        if (wantScan != null && !had.contains(wantScan)) {
            files.attach(List.of(wantScan), FileKind.CERTIFICATE, OWNER_TYPE, g.getCode());
            changes++;
        }
        return changes;
    }

    // ------------------------------------------------------------------ views

    private Map<String, String> firstPhotos(List<String> codes) {
        Map<String, String> m = new HashMap<>();
        for (StoredFile f : files.ofOwners(OWNER_TYPE, codes)) {
            if (f.getKind() == FileKind.PRODUCT_PHOTO) {
                m.putIfAbsent(f.getOwnerRef(), files.link(f, f.getThumbKey() != null));
            }
        }
        return m;
    }

    private GemCard card(Gem g, boolean owner, String photo) {
        return new GemCard(g.getCode(), g.getName(), g.getProductType(), g.getGemType(), g.getCarat(), g.getColour(),
                g.getShape(), g.getTreatment(), g.getCertType(), g.getCertLab(), g.getPrice(), owner ? g.getCost() : null,
                owner ? g.getMinPrice() : null, g.getBranch().getCode(), g.getStatus(), photo);
    }

    private GemView view(Gem g) {
        boolean owner = isOwner();
        Map<UUID, StoredFile> mine = new LinkedHashMap<>();
        files.ofOwner(OWNER_TYPE, g.getCode()).forEach(f -> mine.put(f.getPublicId(), f));
        var photos = mine.values().stream().filter(f -> f.getKind() == FileKind.PRODUCT_PHOTO).map(files::view).toList();
        var scan = g.getCertScan() == null || !mine.containsKey(g.getCertScan()) ? null : files.view(mine.get(g.getCertScan()));
        var lines = history.findByGemOrderByAtAscIdAsc(g).stream()
                .map(h -> new HistoryLine(h.getAt(), h.getUserName(), h.getAction(), h.getDetail())).toList();
        return new GemView(g.getCode(), g.getName(), g.getProductType(), g.getGemType(), g.getCarat(), g.getColour(),
                g.getShape(), g.getTreatment(), g.getDescription(), g.getCertType(), g.getCertLab(), g.getCertNo(),
                g.getCertDate(), scan, photos, g.getPrice(), owner ? g.getCost() : null, owner ? g.getMinPrice() : null,
                g.getBranch().getCode(), g.getBranch().getDisplayName(), g.getStatus(), owner ? g.getSupplierCode() : null,
                owner ? g.getPurchaseNo() : null, g.getSaleNo(), g.getSoldAt(), g.getAddedAt(),
                g.getAddedBy() == null ? "System" : g.getAddedBy().getName(), g.getStatus().sellable(), lines);
    }

    // ------------------------------------------------------------------ small helpers

    private boolean isOwner() {
        return current.require().role() == Role.OWNER;
    }

    private UserAccount user(Who w) {
        return em.getReference(UserAccount.class, w.id());
    }

    private static void money(List<String> changes, String what, BigDecimal from, BigDecimal to) {
        if (from.compareTo(to) != 0) {
            changes.add(what + " LKR " + lkr(from) + " → " + lkr(to));
        }
    }

    static String lkr(BigDecimal v) {
        return String.format(Locale.ROOT, "%,.0f", v);
    }

    private static String label(CertType t) {
        return switch (t) {
            case LAB -> "lab";
            case SHOP -> "shop";
            case NONE -> "none";
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
