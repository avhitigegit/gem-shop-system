package lk.nuvi.gemshop.system.sampledata;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import lk.nuvi.gemshop.common.money.Currency;
import lk.nuvi.gemshop.settings.rates.ExchangeRate;
import lk.nuvi.gemshop.settings.rates.ExchangeRateRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Prototype exchange rates: the history (one rate per day) and today's rate USD 300 / EUR 325. */
@Component
class RatesSampleData implements SampleDataPart {

    private final ExchangeRateRepository rates;

    RatesSampleData(ExchangeRateRepository rates) {
        this.rates = rates;
    }

    @Override
    public int order() {
        return 120;
    }

    @Override
    public String name() {
        return "exchange rates";
    }

    @Override
    public int catchUp(SampleData data) {
        return rates.count() == 0 ? load(data) : 0;
    }

    @Override
    public int load(SampleData data) {
        int n = 0;
        ZoneId colombo = ZoneId.of("Asia/Colombo");
        for (JsonNode h : data.node("rateHistory").values()) {
            Instant at = data.date(h.path("date")).atTime(LocalTime.of(9, 0)).atZone(colombo).toInstant();
            n += save(h, at);
        }
        JsonNode now = data.node("rates");
        Instant at = data.instant(now.path("updatedAt"));
        n += save(now, at == null ? Instant.now() : at.plusSeconds(1));
        return n;
    }

    private int save(JsonNode n, Instant at) {
        rates.save(new ExchangeRate(Currency.USD, SampleData.decimal(n.path("USD")), at));
        rates.save(new ExchangeRate(Currency.EUR, SampleData.decimal(n.path("EUR")), at));
        return 2;
    }
}
