package lk.nuvi.gemshop.system.sampledata;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lk.nuvi.gemshop.commissions.CommissionLine;
import lk.nuvi.gemshop.commissions.CommissionLineRepository;
import lk.nuvi.gemshop.commissions.PayMethod;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import lk.nuvi.gemshop.sales.CreditNote;
import lk.nuvi.gemshop.sales.CreditNoteRepository;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.sales.SaleRepository;
import org.springframework.stereotype.Component;

/**
 * The prototype's commissions (P3-05): one line per guide and salesperson on every sample sale. As in the prototype, a
 * guide line older than 7 days was paid "Cash (owner)" and a staff line older than 14 days "With salary"; the rest is not
 * yet paid. Credit notes (CN-0001) add the "taken back" lines; a cancelled invoice takes everything back.
 */
@Component
class CommissionLinesSampleData implements SampleDataPart {

    private final CommissionLineRepository lines;
    private final SaleRepository sales;
    private final CreditNoteRepository creditNotes;
    private final Numbers numbers;

    CommissionLinesSampleData(CommissionLineRepository lines, SaleRepository sales, CreditNoteRepository creditNotes, Numbers numbers) {
        this.lines = lines;
        this.sales = sales;
        this.creditNotes = creditNotes;
        this.numbers = numbers;
    }

    @Override
    public int order() {
        return 345;
    }

    @Override
    public String name() {
        return "commissions";
    }

    @Override
    public int catchUp(SampleData data) {
        return lines.count() == 0 && sales.count() > 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        Instant now = Instant.now();
        List<CommissionLine> out = new ArrayList<>();
        List<Sale> all = sales.findAll().stream().sorted(Comparator.comparing(Sale::getSoldAt)).toList();
        for (Sale s : all) {
            long days = Duration.between(s.getSoldAt(), now).toDays();
            if (s.getGuide() != null) {
                CommissionLine g = CommissionLine.guide(s, s.getGuide(), s.getGuideComm(), s.getGuidePct());
                if (days > 7) {
                    g.settled(s.getSoldAt(), PayMethod.OWNER_CASH);
                }
                out.add(g);
            }
            if (s.getStaff() != null) {
                CommissionLine st = CommissionLine.staff(s, s.getStaff(), s.getStaffComm());
                if (days > 14) {
                    st.settled(s.getSoldAt(), PayMethod.SALARY);
                }
                out.add(st);
            }
            if (s.getStatus() == Sale.Status.CANCELLED && s.getCancelledAt() != null) {
                List<CommissionLine> mine = new ArrayList<>(out.subList(out.size() - (s.getGuide() != null ? 2 : 1), out.size()));
                for (CommissionLine l : mine) {
                    out.add(CommissionLine.takenBack(l, s.getCancelledAt(), l.getAmount(), "Taken back – invoice " + s.getNumber() + " cancelled"));
                }
            }
        }
        lines.saveAll(out);
        int n = out.size();
        for (CreditNote c : creditNotes.findAll()) {
            List<CommissionLine> on = lines.findBySaleIdOrderByIdAsc(c.getSaleReturn().getSale().getId());
            String note = "Taken back – return " + c.getSaleReturn().getNumber();
            for (CommissionLine l : on) {
                java.math.BigDecimal back = l.getPayee() == CommissionLine.Payee.GUIDE ? c.getGuideComm() : c.getStaffComm();
                if (l.getStatus() != CommissionLine.Status.REVERSED && back.signum() > 0) {
                    lines.save(CommissionLine.takenBack(l, c.getIssuedAt(), back, note));
                    n++;
                }
            }
        }
        numbers.continueAfter(Counter.COMMISSION_PAYMENT, data.node("seq").path("payment").asLong(0));
        return n;
    }
}
