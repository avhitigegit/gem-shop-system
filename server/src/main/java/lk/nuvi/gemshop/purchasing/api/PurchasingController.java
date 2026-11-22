package lk.nuvi.gemshop.purchasing.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.purchasing.Purchasing;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.PurchaseForm;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.PurchaseList;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.PurchaseView;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.PurchasingSummary;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.ReturnForm;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.ReturnRow;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.ReturnableGem;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.SupplierForm;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.SupplierRow;
import lk.nuvi.gemshop.purchasing.api.PurchasingDtos.SupplierView;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Suppliers, cash purchases, returns to supplier (P2-03). The owner buys and returns; the accountant may look. */
@RestController
@Tag(name = "Purchasing")
public class PurchasingController {

    private final Purchasing purchasing;

    public PurchasingController(Purchasing purchasing) {
        this.purchasing = purchasing;
    }

    // ------------------------------------------------------------------ suppliers

    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @GetMapping("/api/suppliers")
    @Operation(summary = "Suppliers, with the number of purchases and the total bought")
    public List<SupplierRow> suppliers() {
        return purchasing.suppliers();
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/api/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a supplier (gets the next SUP- code)")
    public SupplierRow addSupplier(@Valid @RequestBody SupplierForm form) {
        return purchasing.addSupplier(form);
    }

    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @GetMapping("/api/suppliers/{code}")
    @Operation(summary = "One supplier: details, purchase history and returns")
    public SupplierView supplier(@PathVariable String code) {
        return purchasing.supplierView(code);
    }

    @PreAuthorize(Roles.OWNER)
    @PutMapping("/api/suppliers/{code}")
    @Operation(summary = "Edit a supplier")
    public SupplierRow editSupplier(@PathVariable String code, @Valid @RequestBody SupplierForm form) {
        return purchasing.editSupplier(code, form);
    }

    // ------------------------------------------------------------------ purchases

    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @GetMapping("/api/purchases/summary")
    @Operation(summary = "Counts for the Gems & stock home")
    public PurchasingSummary summary() {
        return purchasing.summary();
    }

    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @GetMapping("/api/purchases")
    @Operation(summary = "Purchase history by supplier and date (default: last 180 days), following the branch in the top bar")
    public PurchaseList purchases(@RequestParam(required = false) String supplier,
            @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        return purchasing.search(supplier, from, to);
    }

    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @GetMapping("/api/purchases/{number}")
    @Operation(summary = "One purchase with its gems and the bill photo")
    public PurchaseView purchase(@PathVariable String number) {
        return purchasing.view(number);
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/api/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Buy gems from a supplier: saves the cash purchase and adds every gem to stock with its own code")
    public PurchaseView buy(@Valid @RequestBody PurchaseForm form) {
        return purchasing.buy(form);
    }

    // ------------------------------------------------------------------ returns to supplier

    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @GetMapping("/api/supplier-returns")
    @Operation(summary = "Gems given back to suppliers, newest first")
    public List<ReturnRow> returns() {
        return purchasing.returns();
    }

    @PreAuthorize(Roles.OWNER)
    @GetMapping("/api/supplier-returns/gems")
    @Operation(summary = "Gems that can go back to their supplier (optionally of one supplier)")
    public List<ReturnableGem> returnable(@RequestParam(required = false) String supplier) {
        return purchasing.returnable(supplier);
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/api/supplier-returns")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Return a gem to its supplier with a reason; the gem leaves stock")
    public ReturnRow giveBack(@Valid @RequestBody ReturnForm form) {
        return purchasing.giveBack(form);
    }
}
