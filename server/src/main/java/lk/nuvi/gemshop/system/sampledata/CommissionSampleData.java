package lk.nuvi.gemshop.system.sampledata;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lk.nuvi.gemshop.settings.commission.CommissionLevel;
import lk.nuvi.gemshop.settings.commission.CommissionLevelRepository;
import lk.nuvi.gemshop.settings.commission.CommissionRules;
import lk.nuvi.gemshop.settings.commission.CommissionRulesRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Prototype commission rules: guides 10 %, staff levels 500 / 1,500 / 3,000 / 5,000 (BRD page 9). */
@Component
class CommissionSampleData implements SampleDataPart {

    private final CommissionRulesRepository rules;
    private final CommissionLevelRepository levels;

    CommissionSampleData(CommissionRulesRepository rules, CommissionLevelRepository levels) {
        this.rules = rules;
        this.levels = levels;
    }

    @Override
    public int order() {
        return 150;
    }

    @Override
    public String name() {
        return "commission rules";
    }

    @Override
    public int catchUp(SampleData data) {
        return rules.count() == 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        JsonNode r = data.node("commissionRules");
        rules.save(new CommissionRules(SampleData.decimal(r.path("defaultGuidePct")).setScale(2)));
        List<CommissionLevel> list = new ArrayList<>();
        List<JsonNode> slabs = new ArrayList<>(r.path("slabs").values());
        for (int i = 0; i < slabs.size(); i++) {
            JsonNode s = slabs.get(i);
            BigDecimal upTo = s.path("upTo").isNull() || s.path("upTo").isMissingNode() || i == slabs.size() - 1 ? null
                    : SampleData.decimal(s.path("upTo")).setScale(2);
            list.add(new CommissionLevel(i + 1, upTo, SampleData.decimal(s.path("amount")).setScale(2)));
        }
        levels.saveAll(list);
        return 1 + list.size();
    }
}
