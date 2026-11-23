package lk.nuvi.gemshop.expenses;

import lk.nuvi.gemshop.settings.lists.ListName;
import lk.nuvi.gemshop.settings.lists.ListUsage;
import org.springframework.stereotype.Component;

/** Settings → Drop-down lists: "Used by 12 expense(s)"; renaming an expense type renames it on expenses and monthly bills. */
@Component
class ExpenseListUsage implements ListUsage {

    private final ExpenseRepository expenses;
    private final MonthlyBillRepository bills;

    ExpenseListUsage(ExpenseRepository expenses, MonthlyBillRepository bills) {
        this.expenses = expenses;
        this.bills = bills;
    }

    @Override
    public long count(ListName list, String value) {
        return list == ListName.EXPENSE_TYPE ? expenses.countByExpenseType(value) + bills.countByExpenseType(value) : 0;
    }

    @Override
    public void rename(ListName list, String from, String to) {
        if (list == ListName.EXPENSE_TYPE) {
            expenses.renameType(from, to);
            bills.renameType(from, to);
        }
    }

    @Override
    public String unit(ListName list) {
        return list == ListName.EXPENSE_TYPE ? "expense(s)" : null;
    }
}
