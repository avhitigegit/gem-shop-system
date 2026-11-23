package lk.nuvi.gemshop.expenses.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.expenses.Expenses;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ExpenseBillForm;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ExpenseDetail;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ExpenseForm;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ExpenseList;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.ExpenseReport;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.MonthlyBillForm;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.MonthlyBillList;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.MonthlyBillRow;
import lk.nuvi.gemshop.expenses.api.ExpenseDtos.MonthlyRunResult;
import org.springframework.http.HttpStatus;
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

/** Shop expenses, monthly bills and the expense report (prototype m-expenses/*, Guide 50, 51, 52, 67). Owner and accountant. */
@RestController
@RequestMapping("/api/expenses")
@Tag(name = "Expenses")
public class ExpensesController {

    private final Expenses expenses;

    public ExpensesController(Expenses expenses) {
        this.expenses = expenses;
    }

    @GetMapping
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "listExpenses", summary = "Expenses of the branch at the top (filters: month, type, method, status)")
    public ExpenseList list(@RequestParam(required = false) String month, @RequestParam(required = false) String type,
            @RequestParam(required = false) String method, @RequestParam(required = false) String status) {
        return expenses.list(month, type, method, status);
    }

    @PostMapping
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addExpense", summary = "Add a bank / card expense with its bill photo; above the limit it waits for the owner")
    public ExpenseDetail add(@Valid @RequestBody ExpenseForm form) {
        return expenses.create(form);
    }

    @GetMapping("/report")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "expenseReport", summary = "Approved expenses by month, type, payment method and branch")
    public ExpenseReport report() {
        return expenses.report();
    }

    @GetMapping("/monthly-bills")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "listMonthlyBills", summary = "Monthly bills (rent, phone, salaries)")
    public MonthlyBillList monthlyBills() {
        return expenses.monthlyBills();
    }

    @PostMapping("/monthly-bills")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addMonthlyBill", summary = "Add a monthly bill")
    public MonthlyBillRow addBill(@Valid @RequestBody MonthlyBillForm form) {
        return expenses.saveBill(null, form);
    }

    @PutMapping("/monthly-bills/{code}")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "changeMonthlyBill", summary = "Change a monthly bill, or turn it on / off")
    public MonthlyBillRow changeBill(@PathVariable String code, @Valid @RequestBody MonthlyBillForm form) {
        return expenses.saveBill(code, form);
    }

    @PostMapping("/monthly-bills/run")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "runMonthlyBills", summary = "Add this month's bills now (those not added yet)")
    public MonthlyRunResult run() {
        return new MonthlyRunResult(expenses.runNow());
    }

    @GetMapping("/{number}")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "getExpense", summary = "One expense with its bill photo")
    public ExpenseDetail get(@PathVariable String number) {
        return expenses.detail(number);
    }

    @PutMapping("/{number}")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "correctExpense", summary = "Correct an expense (kept in the activity log)")
    public ExpenseDetail correct(@PathVariable String number, @Valid @RequestBody ExpenseForm form) {
        return expenses.correct(number, form);
    }

    @PostMapping("/{number}/bill")
    @PreAuthorize(Roles.OWNER_ACCOUNTANT)
    @Operation(operationId = "attachExpenseBill", summary = "Attach the bill photo (e.g. to a monthly bill added by itself)")
    public ExpenseDetail bill(@PathVariable String number, @Valid @RequestBody ExpenseBillForm form) {
        return expenses.attachBill(number, form.bill());
    }
}
