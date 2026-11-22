package lk.nuvi.gemshop.system.sampledata;

import lk.nuvi.gemshop.settings.lists.ListName;
import lk.nuvi.gemshop.settings.lists.LookupValue;
import lk.nuvi.gemshop.settings.lists.LookupValueRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Prototype drop-down lists: 13 gem types, 7 product types, 23 colours, 9 shapes, 6 labs, 3 treatments, 11 expense types. */
@Component
class ListsSampleData implements SampleDataPart {

    private final LookupValueRepository values;

    ListsSampleData(LookupValueRepository values) {
        this.values = values;
    }

    @Override
    public int order() {
        return 140;
    }

    @Override
    public String name() {
        return "drop-down lists";
    }

    /** Lists added after the database was filled (P1-06): only lists that are still empty. */
    @Override
    public int catchUp(SampleData data) {
        return load(data);
    }

    @Override
    public int load(SampleData data) {
        int n = 0;
        JsonNode lists = data.node("lists");
        for (ListName l : ListName.values()) {
            if (values.countByList(l) > 0) {
                continue;
            }
            int order = 0;
            for (JsonNode v : lists.path(l.prototypeKey()).values()) {
                values.save(new LookupValue(l, v.asString().trim(), ++order));
                n++;
            }
        }
        return n;
    }
}
