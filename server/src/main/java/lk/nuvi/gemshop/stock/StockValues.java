package lk.nuvi.gemshop.stock;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.gems.GemRepository;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.stock.api.StockDtos.StockValue;
import lk.nuvi.gemshop.stock.api.StockDtos.ValueRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stock value at cost and at selling price (BRD D6) – owner only. Stock = every gem the shop still has. */
@Service
public class StockValues {

    private final GemRepository gems;
    private final CurrentUser current;

    public StockValues(GemRepository gems, CurrentUser current) {
        this.gems = gems;
        this.current = current;
    }

    @Transactional(readOnly = true)
    public StockValue value() {
        Long branch = current.scope().branchId();
        List<Gem> stock = branch == null ? gems.findByStatusInOrderByCodeAsc(GemStatus.STOCK)
                : gems.findByBranch_IdAndStatusInOrderByCodeAsc(branch, GemStatus.STOCK);
        return new StockValue(row("Total", stock), by(stock, Gem::getGemType), by(stock, Gem::getProductType),
                by(stock, g -> g.getBranch().getCode()));
    }

    /** Groups, biggest selling value first. */
    private static List<ValueRow> by(List<Gem> stock, Function<Gem, String> key) {
        Map<String, List<Gem>> groups = new LinkedHashMap<>();
        for (Gem g : stock) {
            groups.computeIfAbsent(key.apply(g), k -> new java.util.ArrayList<>()).add(g);
        }
        return groups.entrySet().stream().map(e -> row(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ValueRow::price).reversed()).toList();
    }

    private static ValueRow row(String group, List<Gem> gs) {
        return new ValueRow(group, gs.size(), sum(gs, Gem::getCarat), sum(gs, Gem::getCost), sum(gs, Gem::getPrice));
    }

    private static BigDecimal sum(List<Gem> gs, Function<Gem, BigDecimal> f) {
        return gs.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
