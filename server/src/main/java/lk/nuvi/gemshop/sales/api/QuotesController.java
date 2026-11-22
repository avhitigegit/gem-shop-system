package lk.nuvi.gemshop.sales.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Locale;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.files.Downloads;
import lk.nuvi.gemshop.sales.Quote;
import lk.nuvi.gemshop.sales.Quotes;
import lk.nuvi.gemshop.sales.api.SalesDtos.ExtendForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.QuoteForm;
import lk.nuvi.gemshop.sales.api.SalesDtos.QuoteList;
import lk.nuvi.gemshop.sales.api.SalesDtos.QuoteView;
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

/** Keep a gem / give a quote (P3-03): the owner and the salesperson. */
@RestController
@RequestMapping("/api/quotes")
@Tag(name = "Reserved & quotes")
public class QuotesController {

    private final Quotes quotes;

    public QuotesController(Quotes quotes) {
        this.quotes = quotes;
    }

    @GetMapping
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "listQuotes", summary = "Reservations or quotations (newest first) and the active counts")
    public QuoteList list(@RequestParam(defaultValue = "RESERVATION") Quote.Kind kind) {
        return quotes.list(kind);
    }

    @PostMapping
    @PreAuthorize(Roles.OWNER_SALES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createQuote", summary = "Keep the gems on the bill for the customer, or give a price quote")
    public QuoteView create(@Valid @RequestBody QuoteForm form) {
        return quotes.create(form);
    }

    @GetMapping("/{number}")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "getQuote", summary = "One reservation or quotation")
    public QuoteView get(@PathVariable String number) {
        return quotes.get(number);
    }

    @PostMapping("/{number}/extend")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "extendQuote", summary = "New expiry date")
    public QuoteView extend(@PathVariable String number, @Valid @RequestBody ExtendForm form) {
        return quotes.extend(number, form);
    }

    @PostMapping("/{number}/release")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "releaseQuote", summary = "Release a reservation (gems back in stock) or withdraw a quotation")
    public QuoteView release(@PathVariable String number) {
        return quotes.release(number);
    }

    @GetMapping("/{number}/pdf")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "quotePdf", summary = "The quotation / reservation as a PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable String number, @RequestParam(defaultValue = "true") boolean download) {
        String no = number.trim().toUpperCase(Locale.ROOT);
        return Downloads.pdf(quotes.pdf(no), (no.startsWith("RS") ? "Reservation " : "Quotation ") + no, download);
    }

    @PostMapping("/{number}/send")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "sendQuote", summary = "Send the PDF by email and / or WhatsApp")
    public SendResult send(@PathVariable String number, @Valid @RequestBody SendForm form) {
        return quotes.send(number, form);
    }
}
