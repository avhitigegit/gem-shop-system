package lk.nuvi.gemshop.customers.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lk.nuvi.gemshop.auth.security.PublicApi;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.customers.CustomerBook;
import lk.nuvi.gemshop.customers.api.CustomerDtos.ConsentForm;
import lk.nuvi.gemshop.customers.api.CustomerDtos.CustomerDetailsForm;
import lk.nuvi.gemshop.customers.api.CustomerDtos.CustomerList;
import lk.nuvi.gemshop.customers.api.CustomerDtos.CustomerProfile;
import lk.nuvi.gemshop.customers.api.CustomerDtos.CustomerRow;
import lk.nuvi.gemshop.customers.api.CustomerDtos.UnsubscribeView;
import lk.nuvi.gemshop.files.Downloads;
import lk.nuvi.gemshop.files.excel.ExcelExport;
import lk.nuvi.gemshop.files.pdf.PdfDocuments;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Customers (P4-03, prototype j-customers-promotions/customers, customer-profile) and the public unsubscribe page. */
@RestController
@RequestMapping("/api")
@Tag(name = "Customers")
public class CustomersController {

    private final CustomerBook book;
    private final PdfDocuments pdfs;
    private final ExcelExport excel;

    public CustomersController(CustomerBook book, PdfDocuments pdfs, ExcelExport excel) {
        this.book = book;
        this.pdfs = pdfs;
        this.excel = excel;
    }

    @GetMapping("/customers")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "listCustomers", summary = "All customers with birthday, purchases in the branch and permission")
    public CustomerList list() {
        return book.list();
    }

    @GetMapping("/customers/export")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "exportCustomers", summary = "The customers list as PDF or Excel (owner)")
    public ResponseEntity<byte[]> export(@Parameter(description = "pdf or xlsx") @RequestParam String format) {
        List<CustomerRow> rows = book.list().customers();
        String name = Downloads.dated("Customers");
        if ("xlsx".equalsIgnoreCase(format)) {
            return Downloads.xlsx(excel.write("Customers", List.of(
                    ExcelExport.Column.<CustomerRow>text("Customer", 10, CustomerRow::code),
                    ExcelExport.Column.<CustomerRow>text("Name", 24, CustomerRow::name),
                    ExcelExport.Column.<CustomerRow>text("Country", 16, CustomerRow::country),
                    ExcelExport.Column.<CustomerRow>text("Email", 28, r -> nz(r.email())),
                    ExcelExport.Column.<CustomerRow>text("WhatsApp", 18, r -> nz(r.whatsapp())),
                    ExcelExport.Column.<CustomerRow>text("Birthday", 10, r -> birthday(r.birthday())),
                    ExcelExport.Column.<CustomerRow>number("Purchases", 10, CustomerRow::purchases),
                    ExcelExport.Column.<CustomerRow>money("Spent (LKR)", 16, CustomerRow::spent),
                    ExcelExport.Column.<CustomerRow>text("Promotions", 18, CustomersController::promotions)), rows), name);
        }
        if (!"pdf".equalsIgnoreCase(format)) {
            throw ApiException.badRequest("BAD_FORMAT", "Format must be pdf or xlsx.");
        }
        byte[] pdf = pdfs.render(new PdfDocuments.Spec("CUSTOMERS", List.of(new PdfDocuments.Line("Customers", String.valueOf(rows.size()))),
                "Private customer data (PDPA No. 9 of 2022) – do not share.", true, null), body -> {
                    var t = body.table(new float[] {8, 18, 12, 22, 14, 8, 6, 12}, "Customer", "Name", "Country", "Email", "WhatsApp",
                            "Birthday", "Bought", "Spent");
                    for (CustomerRow r : rows) {
                        t.row(r.code(), r.name(), r.country(), nz(r.email()), nz(r.whatsapp()), birthday(r.birthday()), r.purchases(),
                                r.spent());
                    }
                    t.end();
                });
        return Downloads.pdf(pdf, name, true);
    }

    @PostMapping("/customers")
    @PreAuthorize(Roles.OWNER_SALES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addCustomer", summary = "Add a customer (name and country required; field errors are customer.*)")
    public CustomerRow add(@Valid @RequestBody CustomerDetailsForm form) {
        return book.create(form);
    }

    @GetMapping("/customers/{code}")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "getCustomer", summary = "One customer: details, purchases, reservations / quotations, messages")
    public CustomerProfile get(@PathVariable String code) {
        return book.profile(code);
    }

    @PutMapping("/customers/{code}")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "editCustomer", summary = "Edit a customer (empty passport keeps the saved one)")
    public CustomerRow edit(@PathVariable String code, @Valid @RequestBody CustomerDetailsForm form) {
        return book.update(code, form);
    }

    @PutMapping("/customers/{code}/consent")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "setCustomerConsent", summary = "Permission for offers and birthday wishes on / off")
    public CustomerRow consent(@PathVariable String code, @RequestBody ConsentForm form) {
        return book.consent(code, form.consent());
    }

    // ------------------------------------------------------------------ public: the unsubscribe link in offers

    @GetMapping("/public/unsubscribe/{token}")
    @PublicApi
    @Operation(operationId = "unsubscribePage", summary = "Unsubscribe link: who it is for (first name only)")
    public UnsubscribeView unsubscribePage(@PathVariable String token) {
        return book.unsubscribePage(token);
    }

    @PostMapping("/public/unsubscribe/{token}")
    @PublicApi
    @Operation(operationId = "unsubscribe", summary = "Unsubscribe link: no more offers or birthday wishes")
    public UnsubscribeView unsubscribe(@PathVariable String token) {
        return book.unsubscribe(token);
    }

    private static String promotions(CustomerRow r) {
        return r.unsubscribed() ? "Unsubscribed" : r.consent() ? "Consent given" : "No consent";
    }

    private static String birthday(String b) {
        if (b == null || b.isBlank()) {
            return "";
        }
        String[] p = b.split("-");
        return Integer.parseInt(p[1]) + " " + java.time.Month.of(Integer.parseInt(p[0]))
                .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
