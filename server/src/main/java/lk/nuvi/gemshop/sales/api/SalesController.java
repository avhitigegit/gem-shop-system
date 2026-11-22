package lk.nuvi.gemshop.sales.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.sales.Sales;
import lk.nuvi.gemshop.sales.api.SalesDtos.BillForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.BillView;
import lk.nuvi.gemshop.sales.api.SalesDtos.CustomerPick;
import lk.nuvi.gemshop.sales.api.SalesDtos.GuidePick;
import lk.nuvi.gemshop.sales.api.SalesDtos.HeldView;
import lk.nuvi.gemshop.sales.api.SalesDtos.HoldForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.PosGem;
import lk.nuvi.gemshop.sales.api.SalesDtos.SaleForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.SaleView;
import lk.nuvi.gemshop.sales.api.SalesDtos.SellSummary;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** New sale (P3-01): the owner and the salesperson sell; the accountant can see the Sell home and invoices. */
@RestController
@RequestMapping("/api")
@Tag(name = "Sales")
public class SalesController {

    private final Sales sales;

    public SalesController(Sales sales) {
        this.sales = sales;
    }

    @GetMapping("/sales/summary")
    @PreAuthorize(Roles.OWNER_SALES_ACCOUNTANT)
    @Operation(summary = "Sell home: sales today, held sales, latest invoices")
    public SellSummary summary() {
        return sales.summary();
    }

    @GetMapping("/sales/gems/{code}")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(summary = "A scanned tag: the gem and whether it can be sold")
    public PosGem scan(@PathVariable String code) {
        return sales.scan(code);
    }

    @GetMapping("/sales/customers")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(summary = "Customers who came before (New sale)")
    public List<CustomerPick> customers() {
        return sales.customerList();
    }

    @GetMapping("/sales/guides")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(summary = "Guides and drivers (New sale)")
    public List<GuidePick> guides() {
        return sales.guideList();
    }

    @PostMapping("/sales/check")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(summary = "Totals, commissions and prices below the minimum while the bill is made")
    public BillView check(@Valid @RequestBody BillForm form) {
        return sales.check(form);
    }

    @PostMapping("/sales")
    @PreAuthorize(Roles.OWNER_SALES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Complete the sale and take payment")
    public SaleView complete(@Valid @RequestBody SaleForm form) {
        return sales.complete(form);
    }

    @GetMapping("/sales/{number}")
    @PreAuthorize(Roles.OWNER_SALES_ACCOUNTANT)
    @Operation(summary = "One sale (invoice)")
    public SaleView sale(@PathVariable String number) {
        return sales.sale(number);
    }

    @GetMapping("/held-sales")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(summary = "Held sales: waiting for the owner or approved")
    public List<HeldView> held() {
        return sales.heldList();
    }

    @PostMapping("/held-sales")
    @PreAuthorize(Roles.OWNER_SALES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Price below the minimum: hold the sale and ask the owner")
    public HeldView hold(@Valid @RequestBody HoldForm form) {
        return sales.hold(form);
    }

    @GetMapping("/held-sales/{number}")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(summary = "One held sale (the waiting screen asks every few seconds)")
    public HeldView heldSale(@PathVariable String number) {
        return sales.held(number);
    }

    @PostMapping("/held-sales/{number}/cancel")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(summary = "Start a new bill instead: the gems go back in stock")
    public HeldView cancel(@PathVariable String number) {
        return sales.cancelHeld(number);
    }
}
