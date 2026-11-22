package lk.nuvi.gemshop.system.sampledata;

import lk.nuvi.gemshop.settings.shop.ShopSettings;
import lk.nuvi.gemshop.settings.shop.ShopSettingsRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Prototype shop details: Ceylon Gem House, NGJA licence, card bank fee 3 %, return policy 14 days. */
@Component
class ShopSampleData implements SampleDataPart {

    private final ShopSettingsRepository shops;

    ShopSampleData(ShopSettingsRepository shops) {
        this.shops = shops;
    }

    @Override
    public int order() {
        return 110;
    }

    @Override
    public String name() {
        return "shop details";
    }

    @Override
    public int load(SampleData data) {
        JsonNode s = data.node("shop");
        shops.save(new ShopSettings(s.path("name").asString())
                .contact(SampleData.text(s.path("tagline")), SampleData.text(s.path("address")),
                        SampleData.text(s.path("phone")), SampleData.text(s.path("email")), SampleData.text(s.path("website")))
                .licence(SampleData.text(s.path("ngjaNo")), data.date(s.path("ngjaExpiry")),
                        s.path("ngjaRemindDays").asInt(60), s.path("printLicenceOnInvoice").asBoolean(true))
                .rules(SampleData.decimal(s.path("largeExpenseLimit")), SampleData.decimal(s.path("cardFeePct")),
                        s.path("invoicePrefix").asString("INV-"), s.path("returnPolicyDays").asInt(14)));
        return 1;
    }
}
