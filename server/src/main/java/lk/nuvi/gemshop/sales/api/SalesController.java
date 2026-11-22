package lk.nuvi.gemshop.sales.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lk.nuvi.gemshop.auth.security.Roles;
import io.swagger.v3.oas.annotations.Parameter;
import java.time.LocalDate;
import java.util.Locale;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.files.Downloads;
import lk.nuvi.gemshop.files.excel.ExcelExport;
import lk.nuvi.gemshop.files.pdf.PdfDocuments;
import lk.nuvi.gemshop.sales.Invoices;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.sales.Sales;
import lk.nuvi.gemshop.sales.api.SalesDtos.CancelForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.InvoicePage;
import lk.nuvi.gemshop.sales.api.SalesDtos.InvoiceRow;
import lk.nuvi.gemshop.sales.api.SalesDtos.SendForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.SendResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final Invoices invoices;
    private final PdfDocuments pdfs;
    private final ExcelExport excel;

    public SalesController(Sales sales, Invoices invoices, PdfDocuments pdfs, ExcelExport excel) {
        this.sales = sales;
        this.invoices = invoices;
        this.pdfs = pdfs;
        this.excel = excel;
    }

    // ------------------------------------------------------------------ invoices (P3-02)

    @GetMapping("/sales")
    @PreAuthorize(Roles.OWNER_SALES_ACCOUNTANT)
    @Operation(operationId = "listInvoices", summary = "Invoices list (newest first; default the last 30 days)")
    public InvoicePage invoices(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Sale.Status status,
            @Parameter(description = "cash, card or split") @RequestParam(required = false) String pay,
            @Parameter(description = "Invoice number, customer, gem code or name") @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return invoices.list(new Invoices.Filter(from, to, status, pay, q), page, size);
    }

    @GetMapping("/sales/export")
    @PreAuthorize(Roles.OWNER_SALES_ACCOUNTANT)
    @Operation(operationId = "exportInvoices", summary = "The invoices list as PDF or Excel (same filters)")
    public ResponseEntity<byte[]> export(
            @Parameter(description = "pdf or xlsx") @RequestParam String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Sale.Status status,
            @RequestParam(required = false) String pay,
            @RequestParam(required = false) String q) {
        List<InvoiceRow> rows = invoices.export(new Invoices.Filter(from, to, status, pay, q));
        String name = Downloads.dated("Invoices");
        if ("xlsx".equalsIgnoreCase(format)) {
            return Downloads.xlsx(excel.write("Invoices", List.of(
                    ExcelExport.Column.<InvoiceRow>text("Invoice", 14, InvoiceRow::number),
                    ExcelExport.Column.<InvoiceRow>dateTime("Date", 18, InvoiceRow::soldAt),
                    ExcelExport.Column.<InvoiceRow>text("Customer", 24, InvoiceRow::customer),
                    ExcelExport.Column.<InvoiceRow>text("Country", 14, InvoiceRow::country),
                    ExcelExport.Column.<InvoiceRow>text("Gems", 40, Invoices::gems),
                    ExcelExport.Column.<InvoiceRow>text("Payment", 18, InvoiceRow::payment),
                    ExcelExport.Column.<InvoiceRow>money("Total (LKR)", 16, InvoiceRow::total),
                    ExcelExport.Column.<InvoiceRow>text("Status", 12, r -> r.status().name())), rows), name);
        }
        if (!"pdf".equalsIgnoreCase(format)) {
            throw ApiException.badRequest("BAD_FORMAT", "Format must be pdf or xlsx.");
        }
        byte[] pdf = pdfs.render(new PdfDocuments.Spec("INVOICES", List.of(new PdfDocuments.Line("Invoices", String.valueOf(rows.size()))),
                "Invoices are never edited or deleted. Cancelled invoices stay on record.", true, null), body -> {
                    var t = body.table(new float[] {12, 14, 20, 30, 12, 12}, "Invoice", "Date", "Customer", "Gems", "Payment", "Total");
                    for (InvoiceRow r : rows) {
                        t.row(r.number() + (r.status() == Sale.Status.CANCELLED ? " (cancelled)" : ""), invoices.when(r.soldAt()),
                                r.customer() + ", " + r.country(), Invoices.gems(r), r.payment(), r.total());
                    }
                    t.end();
                });
        return Downloads.pdf(pdf, name, true);
    }

    @GetMapping("/sales/{number}/pdf")
    @PreAuthorize(Roles.OWNER_SALES_ACCOUNTANT)
    @Operation(operationId = "invoicePdf", summary = "The invoice as a PDF (no VAT line, no card fee line)")
    public ResponseEntity<byte[]> pdf(@PathVariable String number, @RequestParam(defaultValue = "true") boolean download) {
        return Downloads.pdf(invoices.pdf(number), "Invoice " + number.trim().toUpperCase(Locale.ROOT), download);
    }

    @PostMapping("/sales/{number}/send")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "sendInvoice", summary = "Send the invoice PDF by email and / or WhatsApp")
    public SendResult send(@PathVariable String number, @Valid @RequestBody SendForm form) {
        return invoices.send(number, form);
    }

    @PostMapping("/sales/{number}/cancel")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "requestInvoiceCancel", summary = "Ask the owner to cancel an invoice (never deleted)")
    public SaleView cancelInvoice(@PathVariable String number, @Valid @RequestBody CancelForm form) {
        return invoices.requestCancel(number, form);
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
