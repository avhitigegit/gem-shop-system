package lk.nuvi.gemshop.commissions;

import java.math.BigDecimal;
import java.util.Collection;
import lk.nuvi.gemshop.commissions.api.CommissionDtos.CommissionRow;

/** Commission line → table row. */
final class Rows {

    private Rows() {}

    static CommissionRow row(CommissionLine l, boolean amounts) {
        return new CommissionRow(l.getId(), l.getEarnedAt(), l.getBranch().getCode(), l.getSale().getNumber(),
                l.getSale().getCustomer().getName(), l.getSale().getTotal(), l.getPayee(), l.personKey(), l.personName(), l.getPct(),
                amounts ? l.getAmount() : null, l.getStatus(), l.isOwed(), l.getNote(), l.getPaidAt(),
                l.getMethod() == null ? null : l.getMethod().label(), l.getPayment() == null ? null : l.getPayment().getNumber());
    }

    static BigDecimal sum(Collection<CommissionLine> lines) {
        return lines.stream().map(CommissionLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
