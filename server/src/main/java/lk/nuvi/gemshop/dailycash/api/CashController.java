package lk.nuvi.gemshop.dailycash.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Locale;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.dailycash.DailyCash;
import lk.nuvi.gemshop.dailycash.api.CashDtos.CashDay;
import lk.nuvi.gemshop.dailycash.api.CashDtos.CashSummary;
import lk.nuvi.gemshop.dailycash.api.CashDtos.CloseDayForm;
import lk.nuvi.gemshop.dailycash.api.CashDtos.ClosingView;
import lk.nuvi.gemshop.dailycash.api.CashDtos.DrawerExpenseForm;
import lk.nuvi.gemshop.dailycash.api.CashDtos.DrawerExpenseRow;
import lk.nuvi.gemshop.dailycash.api.CashDtos.OpeningForm;
import lk.nuvi.gemshop.files.Downloads;
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

/** The cash drawer: start the day, pay out cash, close the day (P3-06). Works on the branch chosen at the top. */
@RestController
@RequestMapping("/api/cash")
@Tag(name = "Daily cash")
public class CashController {

    private final DailyCash cash;

    public CashController(DailyCash cash) {
        this.cash = cash;
    }

    @GetMapping("/summary")
    @PreAuthorize(Roles.OWNER_SALES_ACCOUNTANT)
    @Operation(operationId = "cashSummary", summary = "Cash & expenses home: day started / closed, waiting expenses, days not matching")
    public CashSummary summary() {
        return cash.summary();
    }

    @GetMapping("/day")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "cashDay", summary = "Today's drawer: opening, expected cash, payments, pay-outs and the closing")
    public CashDay day() {
        return cash.day();
    }

    @PostMapping("/opening")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "saveOpeningCash", summary = "Start the day: cash in the drawer per currency (changeable until closed)")
    public CashDay opening(@Valid @RequestBody OpeningForm form) {
        return cash.open(form);
    }

    @PostMapping("/expenses")
    @PreAuthorize(Roles.OWNER_SALES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "payDrawerExpense", summary = "Pay a small expense from the drawer (bill photo required; above the limit → owner)")
    public DrawerExpenseRow expense(@Valid @RequestBody DrawerExpenseForm form) {
        return cash.payExpense(form);
    }

    @PostMapping("/refunds/{creditNote}")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "payDrawerRefund", summary = "Pay a refund credit note back in cash from the drawer")
    public CashDay refund(@PathVariable String creditNote) {
        return cash.payRefund(creditNote);
    }

    @PostMapping("/close")
    @PreAuthorize(Roles.OWNER_SALES)
    @Operation(operationId = "closeDay", summary = "Close the day: counted vs expected; the report goes to the owner; the day is locked")
    public ClosingView close(@Valid @RequestBody CloseDayForm form) {
        return cash.close(form);
    }

    @GetMapping("/closings/{number}")
    @PreAuthorize(Roles.OWNER_SALES_ACCOUNTANT)
    @Operation(operationId = "getClosing", summary = "One day closing")
    public ClosingView closing(@PathVariable String number) {
        return cash.closing(number);
    }

    @GetMapping("/closings/{number}/pdf")
    @PreAuthorize(Roles.OWNER_SALES_ACCOUNTANT)
    @Operation(operationId = "closingPdf", summary = "The closing report as a PDF")
    public ResponseEntity<byte[]> pdf(@PathVariable String number, @RequestParam(defaultValue = "true") boolean download) {
        return Downloads.pdf(cash.closingPdf(number), "Day closing " + number.trim().toUpperCase(Locale.ROOT), download);
    }
}
