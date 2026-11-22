package lk.nuvi.gemshop.settings.lists;

import java.util.List;
import java.util.Map;
import lk.nuvi.gemshop.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Production starts without sample data: empty drop-down lists get the standard values (BRD C2, C3, C8, M1), so
 * the owner can add gems and expenses on day one and edit the lists later. Local / dev / UAT get the prototype's
 * lists from the sample data instead (the same values).
 */
@Component
class ListDefaults {

    private static final Logger LOG = LoggerFactory.getLogger(ListDefaults.class);

    static final Map<ListName, List<String>> STANDARD = Map.of(
            ListName.GEM_TYPE, List.of("Blue Sapphire", "Yellow Sapphire", "Pink Sapphire", "Padparadscha", "Star Sapphire",
                    "Ruby", "Cat's Eye", "Alexandrite", "Moonstone", "Spinel", "Garnet", "Topaz", "Other"),
            ListName.PRODUCT_TYPE, List.of("Loose gem", "Ring", "Pendant", "Earrings", "Bracelet", "Necklace", "Other"),
            ListName.COLOUR, List.of("Royal Blue", "Cornflower Blue", "Ceylon Blue", "Golden Yellow", "Pink", "Pinkish Orange",
                    "Red", "Green", "White", "Other"),
            ListName.SHAPE, List.of("Oval", "Cushion", "Round", "Pear", "Emerald", "Cabochon", "Heart", "Marquise", "Princess"),
            ListName.LAB, List.of("NGJA Gem Testing Laboratory", "GIA", "GRS (GemResearch Swisslab)", "SSEF", "Gübelin",
                    "Lotus Gemology"),
            ListName.TREATMENT, List.of("Natural", "Heated", "Untreated"),
            ListName.EXPENSE_TYPE, List.of("Rent", "Electricity", "Water", "Phone & internet", "Staff salaries & allowances",
                    "Transport", "Packaging & gift boxes", "Lab & certificate fees", "Marketing & messages", "Repairs", "Other"));

    private final LookupValueRepository values;
    private final AppProperties app;

    ListDefaults(LookupValueRepository values, AppProperties app) {
        this.values = values;
        this.app = app;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void fillEmptyLists() {
        if (app.sampleData().enabled()) {
            return;
        }
        int n = 0;
        for (ListName l : ListName.values()) {
            if (values.countByList(l) == 0) {
                int order = 0;
                for (String v : STANDARD.get(l)) {
                    values.save(new LookupValue(l, v, ++order));
                    n++;
                }
            }
        }
        if (n > 0) {
            LOG.info("Drop-down lists: added {} standard values to empty lists", n);
        }
    }
}
