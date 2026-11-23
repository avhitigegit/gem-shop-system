package lk.nuvi.gemshop.sales.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.files.Downloads;
import lk.nuvi.gemshop.files.excel.ExcelExport;
import lk.nuvi.gemshop.files.pdf.PdfDocuments;
import lk.nuvi.gemshop.sales.Invoices;
import lk.nuvi.gemshop.sales.Returns;
import lk.nuvi.gemshop.sales.SaleReturn;
import lk.nuvi.gemshop.sales.api.SalesDtos.CreditNoteView;
import lk.nuvi.gemshop.sales.api.SalesDtos.CustomerReturnForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.ReturnSale;
import lk.nuvi.gemshop.sales.api.SalesDtos.ReturnView;
import lk.nuvi.gemshop.sales.api.SalesDtos.SendForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.SendResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Returns and credit notes (P3-04). The owner approves returns in Approvals. */
@RestController
@RequestMapping("/api")
@Tag(name = "Returns")
public class ReturnsController {

    private final Returns returns;
    private final Invoices invoices;
    private final PdfDocuments pdfs;
    private final ExcelExport excel;

    public ReturnsController(Returns returns, Invoices invoices, PdfDocuments pdfs, ExcelExport excel) {
        this.returns = returns;
        this.invoices = invoices;
        this.pdfs = pdfs;
        this.excel = excel;
    }

    @GetMapping("/returns")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "listReturns", summary = "Returns and exchanges, newest first")
    public List<ReturnView> list() {
        return returns.list();
    }

    @GetMapping("/returns/sales")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "findReturnSales", summary = "Find the sale: invoice number, customer name or gem (8 latest)")
    public List<ReturnSale> find(@RequestParam(required = false) String q) {
        return returns.find(q);
    }

    @GetMapping("/returns/sales/{number}")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "getReturnSale", summary = "A completed sale and which of its gems may come back")
    public ReturnSale sale(@PathVariable String number) {
        return returns.sale(number);
    }

    @PostMapping("/returns")
    @PreAuthorize(Roles.OWNER_SALES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createReturn", summary = "Send a return / exchange to the owner for approval")
    public ReturnView create(@Valid @RequestBody CustomerReturnForm form) {
        return returns.create(form);
    }

    @GetMapping("/returns/{number}")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "getReturn", summary = "One return and the owner's answer")
    public ReturnView get(@PathVariable String number) {
        return returns.get(number);
    }

    @GetMapping("/credit-notes")
    @PreAuthorize(Roles.OWNER_SALES_ACCOUNTANT)
    @Operation(operationId = "listCreditNotes", summary = "Credit notes, newest first")
    public List<CreditNoteView> creditNotes() {
        return returns.creditNotes();
    }

    @GetMapping("/credit-notes/export")
    @PreAuthorize(Roles.OWNER_SALES_ACCOUNTANT)
    @Operation(operationId = "exportCreditNotes", summary = "The credit notes list as PDF or Excel")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "pdf") String format) {
        List<CreditNoteView> rows = returns.creditNotes();
        String name = Downloads.dated("Credit notes");
        if ("xlsx".equalsIgnoreCase(format)) {
            return Downloads.xlsx(excel.write("Credit notes", List.of(
                    ExcelExport.Column.<CreditNoteView>text("Credit note", 14, CreditNoteView::number),
                    ExcelExport.Column.<CreditNoteView>dateTime("Date", 18, CreditNoteView::issuedAt),
                    ExcelExport.Column.<CreditNoteView>text("Gem", 32, c -> c.gem().name() + " (" + c.gem().code() + ")"),
                    ExcelExport.Column.<CreditNoteView>text("Invoice", 14, CreditNoteView::sale),
                    ExcelExport.Column.<CreditNoteView>text("Customer", 24, c -> c.customer().name()),
                    ExcelExport.Column.<CreditNoteView>text("Type", 10, c -> kind(c.kind())),
                    ExcelExport.Column.<CreditNoteView>money("Amount (LKR)", 16, CreditNoteView::amount),
                    ExcelExport.Column.<CreditNoteView>money("Commission taken back", 18,
                            c -> c.guideComm().add(c.staffComm()))), rows), name);
        }
        if (!"pdf".equalsIgnoreCase(format)) {
            throw ApiException.badRequest("BAD_FORMAT", "Format must be pdf or xlsx.");
        }
        byte[] pdf = pdfs.render(new PdfDocuments.Spec("CREDIT NOTES", List.of(new PdfDocuments.Line("Credit notes",
                String.valueOf(rows.size()))), "Credit notes are made when the owner approves a return. They are never deleted.", true,
                null), body -> {
                    var t = body.table(new float[] {12, 14, 30, 13, 19, 12}, "Credit note", "Date", "Gem", "Invoice", "Customer", "Amount");
                    for (CreditNoteView c : rows) {
                        t.row(c.number(), invoices.when(c.issuedAt()), c.gem().name() + " (" + c.gem().code() + ")", c.sale(),
                                c.customer().name(), c.amount());
                    }
                    t.end();
                });
        return Downloads.pdf(pdf, name, true);
    }

    @GetMapping("/credit-notes/{number}")
    @PreAuthorize(Roles.OWNER_SALES_ACCOUNTANT)
    @Operation(operationId = "getCreditNote", summary = "One credit note")
    public CreditNoteView creditNote(@PathVariable String number) {
        return returns.creditNote(number);
    }

    @GetMapping("/credit-notes/{number}/pdf")
    @PreAuthorize(Roles.OWNER_SALES_ACCOUNTANT)
    @Operation(operationId = "creditNotePdf", summary = "The credit note as a PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable String number, @RequestParam(defaultValue = "true") boolean download) {
        return Downloads.pdf(returns.pdf(number), "Credit note " + number.trim().toUpperCase(Locale.ROOT), download);
    }

    @PostMapping("/credit-notes/{number}/send")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "sendCreditNote", summary = "Send the credit note by email and / or WhatsApp")
    public SendResult send(@PathVariable String number, @Valid @RequestBody SendForm form) {
        return returns.send(number, form);
    }

    private static String kind(SaleReturn.Kind k) {
        return k == SaleReturn.Kind.EXCHANGE ? "Exchange" : "Refund";
    }
}
