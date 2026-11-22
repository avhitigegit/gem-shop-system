package lk.nuvi.gemshop.gems;

import lk.nuvi.gemshop.settings.lists.ListName;
import lk.nuvi.gemshop.settings.lists.ListUsage;
import org.springframework.stereotype.Component;

/** Settings → Drop-down lists: "Used by 12 gem(s)", and renaming a value renames it on the gems too. */
@Component
class GemListUsage implements ListUsage {

    private final GemRepository gems;

    GemListUsage(GemRepository gems) {
        this.gems = gems;
    }

    @Override
    public long count(ListName list, String value) {
        return switch (list) {
            case GEM_TYPE -> gems.countGemType(value);
            case PRODUCT_TYPE -> gems.countProductType(value);
            case COLOUR -> gems.countColour(value);
            case SHAPE -> gems.countShape(value);
            case TREATMENT -> gems.countTreatment(value);
            case LAB -> gems.countLab(value);
            case EXPENSE_TYPE -> 0;
        };
    }

    @Override
    public void rename(ListName list, String from, String to) {
        switch (list) {
            case GEM_TYPE -> gems.renameGemType(from, to);
            case PRODUCT_TYPE -> gems.renameProductType(from, to);
            case COLOUR -> gems.renameColour(from, to);
            case SHAPE -> gems.renameShape(from, to);
            case TREATMENT -> gems.renameTreatment(from, to);
            case LAB -> gems.renameLab(from, to);
            case EXPENSE_TYPE -> { }
        }
    }

    @Override
    public String unit(ListName list) {
        return list == ListName.EXPENSE_TYPE ? null : "gem(s)";
    }
}
