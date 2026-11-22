package lk.nuvi.gemshop.system.sampledata;

import java.util.LinkedHashMap;
import java.util.Map;
import lk.nuvi.gemshop.common.numbers.Counter;
import lk.nuvi.gemshop.common.numbers.Numbers;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Running numbers continue after the prototype's documents: next gem GEM-00131, next invoice INV-000141,
 * next expense EXP-0030, …
 */
@Component
class NumbersSampleData implements SampleDataPart {

    /** Prototype {@code seq} key → counter. Quotes and reservations share "quote" there; here each has its own. */
    private static final Map<String, Counter> SEQ = new LinkedHashMap<>();

    static {
        SEQ.put("product", Counter.GEM);
        SEQ.put("invoice", Counter.INVOICE);
        SEQ.put("credit", Counter.CREDIT_NOTE);
        SEQ.put("purchase", Counter.PURCHASE);
        SEQ.put("held", Counter.HELD_SALE);
        SEQ.put("expense", Counter.EXPENSE);
        SEQ.put("transfer", Counter.TRANSFER);
        SEQ.put("ret", Counter.RETURN);
        SEQ.put("correction", Counter.STOCK_CORRECTION);
        SEQ.put("supplierReturn", Counter.SUPPLIER_RETURN);
        SEQ.put("stockCheck", Counter.STOCK_CHECK);
    }

    private final Numbers numbers;

    NumbersSampleData(Numbers numbers) {
        this.numbers = numbers;
    }

    @Override
    public int order() {
        return 130;
    }

    @Override
    public String name() {
        return "running numbers";
    }

    @Override
    public int catchUp(SampleData data) {
        return load(data);
    }

    /** Returns how many counters were moved forward. */
    @Override
    public int load(SampleData data) {
        int n = 0;
        JsonNode seq = data.node("seq");
        for (var e : SEQ.entrySet()) {
            n += raise(e.getValue(), seq.path(e.getKey()).asLong(0));
        }
        long qt = 0;
        long rs = 0;
        for (JsonNode q : data.node("quotes").values()) {
            String id = q.path("id").asString("");
            long no = id.contains("-") ? Long.parseLong(id.substring(id.indexOf('-') + 1)) : 0;
            if (id.startsWith("QT-")) {
                qt = Math.max(qt, no);
            } else if (id.startsWith("RS-")) {
                rs = Math.max(rs, no);
            }
        }
        n += raise(Counter.QUOTE, qt);
        n += raise(Counter.RESERVATION, rs);
        return n;
    }

    private int raise(Counter c, long last) {
        if (last <= numbers.lastUsed(c)) {
            return 0;
        }
        numbers.continueAfter(c, last);
        return 1;
    }
}
