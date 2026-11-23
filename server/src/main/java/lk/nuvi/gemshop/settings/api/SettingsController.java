package lk.nuvi.gemshop.settings.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.auth.user.UserAccountRepository;
import lk.nuvi.gemshop.settings.api.SettingsDtos.BranchForm;
import lk.nuvi.gemshop.settings.api.SettingsDtos.BranchStatusForm;
import lk.nuvi.gemshop.settings.api.SettingsDtos.BranchView;
import lk.nuvi.gemshop.settings.api.SettingsDtos.CommissionForm;
import lk.nuvi.gemshop.settings.api.SettingsDtos.CommissionView;
import lk.nuvi.gemshop.settings.api.SettingsDtos.LevelDto;
import lk.nuvi.gemshop.settings.api.SettingsDtos.ListItem;
import lk.nuvi.gemshop.settings.api.SettingsDtos.ListValues;
import lk.nuvi.gemshop.settings.api.SettingsDtos.ListView;
import lk.nuvi.gemshop.settings.api.SettingsDtos.RatesForm;
import lk.nuvi.gemshop.settings.api.SettingsDtos.RatesView;
import lk.nuvi.gemshop.settings.api.SettingsDtos.SettingsSummary;
import lk.nuvi.gemshop.settings.api.SettingsDtos.ShopForm;
import lk.nuvi.gemshop.settings.api.SettingsDtos.ShopView;
import lk.nuvi.gemshop.settings.api.SettingsDtos.StaffForm;
import lk.nuvi.gemshop.settings.api.SettingsDtos.StaffView;
import lk.nuvi.gemshop.settings.api.SettingsDtos.ValueForm;
import lk.nuvi.gemshop.settings.branch.BranchRepository;
import lk.nuvi.gemshop.settings.branch.BranchStatus;
import lk.nuvi.gemshop.settings.branch.Branches;
import lk.nuvi.gemshop.commissions.Guides;
import lk.nuvi.gemshop.settings.commission.Commission;
import lk.nuvi.gemshop.settings.lists.ListName;
import lk.nuvi.gemshop.settings.lists.Lists;
import lk.nuvi.gemshop.settings.rates.Rates;
import lk.nuvi.gemshop.settings.shop.ShopService;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import lk.nuvi.gemshop.settings.staff.Staff;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Settings (owner): shop details, staff logins, exchange rates, commission rules, branches, drop-down lists. */
@RestController
@Tag(name = "Settings")
public class SettingsController {

    private final ShopService shop;
    private final ShopSettingsRepository shops;
    private final Staff staff;
    private final UserAccountRepository users;
    private final Branches branches;
    private final BranchRepository branchRepo;
    private final Rates rates;
    private final Lists lists;
    private final Commission commission;
    private final Guides guides;

    public SettingsController(ShopService shop, ShopSettingsRepository shops, Staff staff, UserAccountRepository users,
            Branches branches, BranchRepository branchRepo, Rates rates, Lists lists, Commission commission, Guides guides) {
        this.shop = shop;
        this.shops = shops;
        this.staff = staff;
        this.users = users;
        this.branches = branches;
        this.branchRepo = branchRepo;
        this.rates = rates;
        this.lists = lists;
        this.commission = commission;
        this.guides = guides;
    }

    // ------------------------------------------------------------------ Settings home

    @PreAuthorize(Roles.OWNER)
    @GetMapping("/api/settings/summary")
    @Transactional(readOnly = true)
    @Operation(operationId = "getSettingsSummary", summary = "Counts and 'needs attention' for the Settings home")
    public SettingsSummary summary() {
        var s = shops.current().orElse(null);
        Rates.Today t = rates.today();
        int values = lists.all().values().stream().mapToInt(List::size).sum();
        return new SettingsSummary((int) users.findAll().stream().filter(u -> u.isActive()).count(),
                (int) branchRepo.findAll().stream().filter(b -> b.getStatus() != BranchStatus.INACTIVE).count(),
                t.usd(), t.eur(), t.updatedAt(), rates.updatedToday(t),
                s == null ? null : s.getNgjaLicenceExpiry(), s == null ? null : shop.licenceDaysLeft(s),
                s == null ? 60 : s.getNgjaRemindDays(), commission.current().defaultGuidePct(), values);
    }

    // ------------------------------------------------------------------ Shop details

    @PreAuthorize(Roles.OWNER)
    @GetMapping("/api/settings/shop")
    @Operation(operationId = "getShopSettings", summary = "Shop and NGJA licence details")
    public ShopView getShop() {
        return shop.view();
    }

    @PreAuthorize(Roles.OWNER)
    @PutMapping("/api/settings/shop")
    @Operation(operationId = "saveShopSettings", summary = "Save shop details, licence and control limits")
    public ShopView saveShop(@Valid @RequestBody ShopForm form) {
        return shop.save(form);
    }

    // ------------------------------------------------------------------ Staff logins

    @PreAuthorize(Roles.OWNER)
    @GetMapping("/api/settings/staff")
    @Operation(operationId = "getStaff", summary = "All logins (active first)")
    public List<StaffView> getStaff() {
        return staff.list();
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/api/settings/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addStaff", summary = "Add a login; a welcome email with a 'choose your password' link is sent")
    public StaffView addStaff(@Valid @RequestBody StaffForm form) {
        return staff.add(form);
    }

    @PreAuthorize(Roles.OWNER)
    @PutMapping("/api/settings/staff/{id}")
    @Operation(operationId = "editStaff", summary = "Edit a login (name, email, role, branch, on / off)")
    public StaffView editStaff(@PathVariable long id, @Valid @RequestBody StaffForm form) {
        return staff.edit(id, form);
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/api/settings/staff/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "resetStaffPassword", summary = "Email the person a link to choose a new password")
    public void resetStaffPassword(@PathVariable long id) {
        staff.sendPasswordLink(id);
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/api/settings/staff/{id}/unlock")
    @Operation(operationId = "unlockStaff", summary = "Unlock a login locked after too many wrong passwords")
    public StaffView unlockStaff(@PathVariable long id) {
        return staff.unlock(id);
    }

    // ------------------------------------------------------------------ Exchange rates

    @PreAuthorize(Roles.OWNER)
    @GetMapping("/api/settings/rates")
    @Operation(operationId = "getRates", summary = "Today's USD / EUR rates and the history (one line per day)")
    public RatesView getRates() {
        return rates.view();
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/api/settings/rates")
    @Operation(operationId = "saveRates", summary = "Save today's rates (used for all new sales from now on)")
    public RatesView saveRates(@Valid @RequestBody RatesForm form) {
        return rates.save(form.usd(), form.eur());
    }

    // ------------------------------------------------------------------ Commission rules

    @PreAuthorize(Roles.OWNER)
    @GetMapping("/api/settings/commission")
    @Operation(operationId = "getCommissionRules", summary = "Default guide % and the staff price levels")
    public CommissionView getCommission() {
        return view(commission.current());
    }

    @PreAuthorize(Roles.OWNER)
    @PutMapping("/api/settings/commission")
    @Operation(operationId = "saveCommissionRules", summary = "Save commission rules (applies to new sales)")
    public CommissionView saveCommission(@Valid @RequestBody CommissionForm form) {
        Commission.Rules saved = commission.save(form.defaultGuidePct(),
                form.levels().stream().map(l -> new Commission.Level(l.upTo(), l.amount())).toList());
        guides.savePcts(form.guides());
        return view(saved);
    }

    private CommissionView view(Commission.Rules r) {
        return new CommissionView(r.defaultGuidePct(), levels(r.levels()), levels(Commission.BRD_EXAMPLE), guides.pcts());
    }

    private static List<LevelDto> levels(List<Commission.Level> l) {
        return l.stream().map(x -> new LevelDto(x.upTo(), x.amount())).toList();
    }

    // ------------------------------------------------------------------ Branches

    @PreAuthorize(Roles.OWNER)
    @GetMapping("/api/settings/branches")
    @Operation(operationId = "getBranches", summary = "All branches with their staff")
    public List<BranchView> getBranches() {
        return branches.list();
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/api/settings/branches")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addBranch", summary = "Add a branch (Opening until its opening date)")
    public BranchView addBranch(@Valid @RequestBody BranchForm form) {
        return branches.add(form);
    }

    @PreAuthorize(Roles.OWNER)
    @PutMapping("/api/settings/branches/{code}")
    @Operation(operationId = "editBranch", summary = "Edit a branch")
    public BranchView editBranch(@PathVariable String code, @Valid @RequestBody BranchForm form) {
        return branches.edit(code, form);
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/api/settings/branches/{code}/status")
    @Operation(operationId = "setBranchStatus", summary = "Mark a branch as open, opening or closed (inactive)")
    public BranchView setBranchStatus(@PathVariable String code, @Valid @RequestBody BranchStatusForm form) {
        return branches.setStatus(code, form.status());
    }

    // ------------------------------------------------------------------ Drop-down lists

    @PreAuthorize(Roles.ANY)
    @GetMapping("/api/lists")
    @Operation(operationId = "getListValues", summary = "Values of every drop-down list, in order (for forms)")
    public List<ListValues> getListValues() {
        return lists.all().entrySet().stream().map(e -> new ListValues(e.getKey(), e.getValue())).toList();
    }

    @PreAuthorize(Roles.OWNER)
    @GetMapping("/api/settings/lists")
    @Operation(operationId = "getLists", summary = "Every drop-down list with how many records use each value")
    public List<ListView> getLists() {
        return listViews(lists.withUsage());
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/api/settings/lists/{list}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addListValue", summary = "Add a value at the end of a list")
    public List<ListView> addListValue(@PathVariable ListName list, @Valid @RequestBody ValueForm form) {
        lists.add(list, form.value());
        return getLists();
    }

    @PreAuthorize(Roles.OWNER)
    @PutMapping("/api/settings/lists/values/{id}")
    @Operation(operationId = "renameListValue", summary = "Rename a value (records using it are updated too)")
    public List<ListView> renameListValue(@PathVariable long id, @Valid @RequestBody ValueForm form) {
        lists.rename(id, form.value());
        return getLists();
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/api/settings/lists/values/{id}/up")
    @Operation(operationId = "moveListValueUp", summary = "Move a value one place up")
    public List<ListView> moveListValueUp(@PathVariable long id) {
        lists.moveUp(id);
        return getLists();
    }

    @PreAuthorize(Roles.OWNER)
    @DeleteMapping("/api/settings/lists/values/{id}")
    @Operation(operationId = "removeListValue", summary = "Remove a value nobody uses")
    public List<ListView> removeListValue(@PathVariable long id) {
        lists.remove(id);
        return getLists();
    }

    private static List<ListView> listViews(Map<ListName, List<Lists.Item>> m) {
        return m.entrySet().stream().map(e -> new ListView(e.getKey(), e.getKey().label(),
                e.getValue().stream().map(i -> new ListItem(i.id(), i.value(), i.usedBy(), i.unit())).toList())).toList();
    }
}
