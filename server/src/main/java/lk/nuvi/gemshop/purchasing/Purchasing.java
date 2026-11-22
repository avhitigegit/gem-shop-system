package lk.nuvi.gemshop.purchasing;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.nuvi.gemshop.audit.log.ActivityLog;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.branch.BranchScope;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.error.ApiError;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.files.FileKind;
import lk.nuvi.gemshop.files.FileService;
import lk.nuvi.gemshop.files.api.FileDtos.FileView;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemRepository;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.gems.Gems;
import lk.nuvi.gemshop.gems.api.GemDtos.GemForm;
import lk.nuvi.gemshop.purchasing.SupplierReturn.RefundMethod;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.PurchaseForm;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.PurchaseGem;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.PurchaseLineForm;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.PurchaseList;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.PurchaseRow;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.PurchaseView;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.PurchasingSummary;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.ReturnForm;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.ReturnRow;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.ReturnableGem;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.SupplierForm;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.SupplierRow;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.SupplierView;
import lk.nuvi.gemshop.settings.branch.Branch;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suppliers, cash purchases and returns to suppliers (BRD B1–B4). Only the owner buys and returns; the accountant
 * may look. Buying adds every gem to stock with its own code in the same save (no second entry).
 */
@Service
public class Purchasing {

    public static final String OWNER_TYPE = "PURCHASE";
    public static final String FUTURE_DATE = "FUTURE_DATE";
    public static final String CANNOT_RETURN = "CANNOT_RETURN";

    /** Gems that can go back to the supplier: still in the shop and not on a sale. */
    static final Set<GemStatus> RETURNABLE = EnumSet.of(GemStatus.IN_STOCK, GemStatus.NOT_READY, GemStatus.RETURNED);

    private static final Pattern SUP = Pattern.compile("SUP-(\\d+)");

    private final SupplierRepository suppliers;
    private final PurchaseRepository purchases;
    private final SupplierReturnRepository returns;
    private final GemRepository gemRepo;
    private final Gems gems;
    private final BranchRepository branches;
    private final Numbers numbers;
    private final FileService files;
    private final CurrentUser current;
    private final ActivityLog log;
    private final EntityManager em;
    private final ZoneId zone;

    public Purchasing(SupplierRepository suppliers, PurchaseRepository purchases,
            SupplierReturnRepository returns, GemRepository gemRepo, Gems gems, BranchRepository branches, Numbers numbers,
            FileService files, CurrentUser current, ActivityLog log, EntityManager em, AppProperties app) {
        this.suppliers = suppliers;
        this.purchases = purchases;
        this.returns = returns;
        this.gemRepo = gemRepo;
        this.gems = gems;
        this.branches = branches;
        this.numbers = numbers;
        this.files = files;
        this.current = current;
        this.log = log;
        this.em = em;
        this.zone = ZoneId.of(app.timezone());
    }

    // ------------------------------------------------------------------ suppliers

    @Transactional(readOnly = true)
    public List<SupplierRow> suppliers() {
        Map<Long, Object[]> totals = new HashMap<>();
        for (Object[] t : purchases.totalsBySupplier()) {
            totals.put((Long) t[0], t);
        }
        return suppliers.findAllByOrderByNameAsc().stream().map(s -> row(s, totals.get(s.getId()))).toList();
    }

    @Transactional
    public SupplierRow addSupplier(SupplierForm f) {
        long last = suppliers.findAll().stream().map(s -> SUP.matcher(s.getCode()))
                .filter(Matcher::matches).mapToLong(m -> Long.parseLong(m.group(1))).max().orElse(0);
        Supplier s = suppliers.save(new Supplier("SUP-%02d".formatted(last + 1), details(f)));
        log.add("Supplier added", s.getCode() + " · " + s.getName(), null);
        return row(s, null);
    }

    @Transactional
    public SupplierRow editSupplier(String code, SupplierForm f) {
        Supplier s = supplier(code);
        s.change(details(f));
        log.add("Supplier edited", s.getCode() + " · " + s.getName(), null);
        return row(s, purchases.totalsBySupplier().stream().filter(t -> t[0].equals(s.getId())).findFirst().orElse(null));
    }

    @Transactional(readOnly = true)
    public SupplierView supplierView(String code) {
        Supplier s = supplier(code);
        BranchScope scope = current.scope();
        List<Purchase> list = purchases.findBySupplierOrderByDateDescIdDesc(s).stream()
                .filter(p -> scope.allows(p.getBranch().getId())).toList();
        long bought = list.stream().mapToLong(p -> p.getLines().size()).sum();
        long sold = list.stream().flatMap(p -> p.getLines().stream()).filter(l -> l.getGem().getStatus() == GemStatus.SOLD).count();
        List<SupplierReturn> rets = returns.findBySupplierOrderByReturnedAtDescIdDesc(s);
        BigDecimal paid = list.stream().map(Purchase::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SupplierView(row(s, new Object[] {s.getId(), (long) list.size(), paid}), bought, sold, rets.size(),
                rets.stream().map(SupplierReturn::getRefund).reduce(BigDecimal.ZERO, BigDecimal::add),
                list.stream().map(this::row).toList(), rets.stream().map(Purchasing::row).toList());
    }

    private static Supplier.Details details(SupplierForm f) {
        return new Supplier.Details(f.name().trim(), blank(f.contact()), f.phone().trim(), f.address().trim(), f.idType(),
                f.idNo().trim().toUpperCase(Locale.ROOT), blank(f.notes()));
    }

    private Supplier supplier(String code) {
        return suppliers.findByCode(code == null ? "" : code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("Supplier not found."));
    }

    // ------------------------------------------------------------------ purchases

    @Transactional(readOnly = true)
    public PurchaseList search(String supplierCode, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(zone);
        LocalDate t = to == null ? today : to;
        LocalDate f = from == null ? t.minusDays(180) : from;
        Long sup = supplierCode == null || supplierCode.isBlank() ? null : supplier(supplierCode).getId();
        List<PurchaseRow> rows = purchases.search(sup, current.scope().branchId(), f, t).stream().map(this::row).toList();
        return new PurchaseList(rows, rows.stream().mapToLong(r -> r.gems().size()).sum(),
                rows.stream().map(PurchaseRow::total).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Transactional(readOnly = true)
    public PurchaseView view(String number) {
        Purchase p = purchases.findByNumber(number == null ? "" : number.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("Purchase not found."));
        current.checkBranch(p.getBranch().getId());
        return view(p);
    }

    @Transactional(readOnly = true)
    public PurchasingSummary summary() {
        LocalDate today = LocalDate.now(zone);
        return new PurchasingSummary(suppliers.count(), purchases.countByDateBetween(today.withDayOfMonth(1), today));
    }

    /**
     * Saves the purchase and adds each gem to stock at the branch, with its own code, linked to the supplier and the
     * purchase. All or nothing: a mistake on any gem stops the whole purchase and points at that gem.
     */
    @Transactional
    public PurchaseView buy(PurchaseForm f) {
        LocalDate today = LocalDate.now(zone);
        if (f.date().isAfter(today)) {
            throw ApiException.field(HttpStatus.BAD_REQUEST, FUTURE_DATE, "The purchase date cannot be in the future.", "date",
                    "Not after today.");
        }
        Supplier s = supplier(f.supplier());
        Branch branch = branches.findByCode(f.branch().trim()).orElseThrow(() -> ApiException.field(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED, "Choose a branch.", "branch", "Choose a branch."));
        String number = numbers.next(Counter.PURCHASE);
        UserAccount who = em.getReference(UserAccount.class, current.require().id());
        Purchase p = new Purchase(number, f.date(), s, branch, f.billPhoto(), blank(f.notes()), who);
        for (int i = 0; i < f.lines().size(); i++) {
            PurchaseLineForm l = f.lines().get(i);
            Gem g;
            try {
                g = gems.addBought(gemForm(l, branch.getCode()), s.getCode(), number, s.getName());
            } catch (ApiException e) {
                throw onLine(e, i);
            }
            p.add(g, l.cost());
        }
        purchases.save(p);
        files.attach(List.of(f.billPhoto()), FileKind.BILL_PHOTO, OWNER_TYPE, number);
        log.add("Purchase recorded", number + " · " + s.getName() + " · " + p.getLines().size() + " gem(s) · LKR "
                + money(p.getTotal()), branch.getId());
        return view(p);
    }

    private static GemForm gemForm(PurchaseLineForm l, String branch) {
        return new GemForm(l.name(), l.productType(), l.gemType(), l.carat(), l.colour(), l.shape(), l.treatment(), null,
                l.certType(), l.certLab(), l.certNo(), l.certDate(), null, List.of(), l.cost(), l.price(), l.min(), branch,
                l.notReady());
    }

    /** "Gem 3: …" and the field as lines[2].min, so the screen marks the right box. */
    private static ApiException onLine(ApiException e, int i) {
        List<ApiError.FieldError> fields = e.getFieldErrors().stream()
                .map(x -> new ApiError.FieldError("lines[" + i + "]." + x.field(), x.message())).toList();
        return new ApiException(e.getStatus(), e.getCode(), "Gem " + (i + 1) + ": " + e.getMessage(), fields);
    }

    // ------------------------------------------------------------------ returns to supplier

    @Transactional(readOnly = true)
    public List<ReturnRow> returns() {
        return returns.findAllByOrderByReturnedAtDescIdDesc().stream().map(Purchasing::row).toList();
    }

    @Transactional(readOnly = true)
    public List<ReturnableGem> returnable(String supplierCode) {
        Map<String, Supplier> byCode = new HashMap<>();
        suppliers.findAll().forEach(s -> byCode.put(s.getCode(), s));
        BranchScope scope = current.scope();
        return gemRepo.findBySupplierCodeIsNotNullAndStatusInOrderByCodeDesc(RETURNABLE).stream()
                .filter(g -> scope.allows(g.getBranch().getId()))
                .filter(g -> supplierCode == null || supplierCode.isBlank() || g.getSupplierCode().equals(supplierCode))
                .filter(g -> byCode.containsKey(g.getSupplierCode()))
                .map(g -> new ReturnableGem(g.getCode(), g.getName(), g.getSupplierCode(), byCode.get(g.getSupplierCode()).getName(),
                        g.getCost(), g.getPurchaseNo(), g.getAddedAt()))
                .toList();
    }

    /** Gives a gem back to its supplier: it leaves stock ("Returned to supplier"), with the reason and the refund. */
    @Transactional
    public ReturnRow giveBack(ReturnForm f) {
        Gem g = gems.visible(f.gem());
        if (!RETURNABLE.contains(g.getStatus())) {
            throw ApiException.conflict(CANNOT_RETURN, g.getCode() + " is " + g.getStatus().label().toLowerCase(Locale.ROOT)
                    + " – only a gem in the shop can go back to the supplier.");
        }
        if (g.getSupplierCode() == null) {
            throw ApiException.conflict(CANNOT_RETURN, g.getCode() + " was not bought from a supplier.");
        }
        Supplier s = supplier(g.getSupplierCode());
        String number = numbers.next(Counter.SUPPLIER_RETURN);
        String reason = f.reasonType().trim() + ": " + f.details().trim();
        SupplierReturn r = returns.save(new SupplierReturn(number, Instant.now(), s, g, reason, f.refund(), f.refundMethod(),
                em.getReference(UserAccount.class, current.require().id())));
        g.setStatus(GemStatus.RETURNED_SUPPLIER);
        gems.addHistory(g, "Returned to supplier", number + " · " + reason);
        log.add("Returned to supplier", number + " · " + g.getCode() + " to " + s.getName() + " · refund LKR " + money(f.refund())
                + " (" + label(f.refundMethod()) + ")", g.getBranch().getId());
        return row(r);
    }

    // ------------------------------------------------------------------ views

    private static SupplierRow row(Supplier s, Object[] totals) {
        return new SupplierRow(s.getCode(), s.getName(), s.getContact(), s.getPhone(), s.getAddress(), s.getIdType(),
                s.getIdNo(), s.getNotes(), totals == null ? 0 : (Long) totals[1],
                totals == null || totals[2] == null ? BigDecimal.ZERO : (BigDecimal) totals[2]);
    }

    private PurchaseRow row(Purchase p) {
        return new PurchaseRow(p.getNumber(), p.getDate(), p.getSupplier().getCode(), p.getSupplier().getName(),
                p.getBranch().getCode(), p.getLines().stream().map(Purchasing::gem).toList(), p.getBillPhoto() != null, p.getTotal());
    }

    private PurchaseView view(Purchase p) {
        FileView bill = files.ofOwner(OWNER_TYPE, p.getNumber()).stream().filter(x -> x.getKind() == FileKind.BILL_PHOTO)
                .findFirst().map(files::view).orElse(null);
        return new PurchaseView(p.getNumber(), p.getDate(), p.getSupplier().getCode(), p.getSupplier().getName(),
                p.getBranch().getCode(), p.getBranch().getDisplayName(), p.getPaidBy(), p.getTotal(), bill, p.getNotes(),
                p.getRecordedBy() == null ? "System" : p.getRecordedBy().getName(),
                p.getLines().stream().map(Purchasing::gem).toList());
    }

    private static PurchaseGem gem(PurchaseLine l) {
        Gem g = l.getGem();
        return new PurchaseGem(g.getCode(), g.getName(), g.getGemType(), g.getProductType(), g.getCarat(), l.getCost(),
                g.getStatus());
    }

    private static ReturnRow row(SupplierReturn r) {
        Gem g = r.getGem();
        return new ReturnRow(r.getNumber(), r.getReturnedAt(), r.getSupplier().getCode(), r.getSupplier().getName(), g.getCode(),
                g.getName(), g.getGemType(), g.getProductType(), r.getReason(), r.getRefund(), r.getRefundMethod());
    }

    static String label(RefundMethod m) {
        return switch (m) {
            case CASH -> "cash";
            case REPLACEMENT -> "replacement gem";
            case CREDIT -> "credit with supplier";
        };
    }

    private static String money(BigDecimal v) {
        return String.format(Locale.ROOT, "%,.0f", v);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
