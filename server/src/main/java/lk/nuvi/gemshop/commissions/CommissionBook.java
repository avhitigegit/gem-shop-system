package lk.nuvi.gemshop.commissions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lk.nuvi.gemshop.sales.Sale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the commission lines (BRD H1–H3, E2, G6; rule 5). Called inside the sale / return / cancel transaction. Has only
 * repository dependencies so approval handlers can use it.
 * <pre>
 * book.earned(sale)                                  // guide % line + staff level line
 * book.takeBack(sale, guide, staff, at, "Taken back – return RT-0003")
 * book.cancelled(sale, at)                           // everything on the invoice taken back
 * </pre>
 */
@Service
public class CommissionBook {

    private final CommissionLineRepository lines;

    public CommissionBook(CommissionLineRepository lines) {
        this.lines = lines;
    }

    /** A completed sale: the commission worked out on it is kept (later rule changes do not touch it). */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CommissionLine> earned(Sale s) {
        List<CommissionLine> out = new java.util.ArrayList<>();
        if (s.getGuide() != null) {
            out.add(CommissionLine.guide(s, s.getGuide(), s.getGuideComm(), s.getGuidePct()));
        }
        if (s.getStaff() != null) {
            out.add(CommissionLine.staff(s, s.getStaff(), s.getStaffComm()));
        }
        return lines.saveAll(out);
    }

    /** A returned gem: the guide's and the salesperson's commission for it are taken back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void takeBack(Sale s, BigDecimal guide, BigDecimal staff, Instant at, String note) {
        List<CommissionLine> on = lines.findBySaleIdOrderByIdAsc(s.getId());
        first(on, CommissionLine.Payee.GUIDE).filter(x -> guide.signum() > 0)
                .ifPresent(l -> lines.save(CommissionLine.takenBack(l, at, guide, note)));
        first(on, CommissionLine.Payee.STAFF).filter(x -> staff.signum() > 0)
                .ifPresent(l -> lines.save(CommissionLine.takenBack(l, at, staff, note)));
    }

    /** A cancelled invoice: what is left of each person's commission on it is taken back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelled(Sale s, Instant at) {
        Map<String, CommissionLine> first = new LinkedHashMap<>();
        Map<String, BigDecimal> left = new LinkedHashMap<>();
        for (CommissionLine l : lines.findBySaleIdOrderByIdAsc(s.getId())) {
            first.putIfAbsent(l.personKey(), l);
            left.merge(l.personKey(), l.getAmount(), BigDecimal::add);
        }
        left.forEach((k, v) -> {
            if (v.signum() > 0) {
                lines.save(CommissionLine.takenBack(first.get(k), at, v, "Taken back – invoice " + s.getNumber() + " cancelled"));
            }
        });
    }

    private static java.util.Optional<CommissionLine> first(List<CommissionLine> on, CommissionLine.Payee p) {
        return on.stream().filter(l -> l.getPayee() == p && l.getStatus() != CommissionLine.Status.REVERSED).findFirst();
    }
}
