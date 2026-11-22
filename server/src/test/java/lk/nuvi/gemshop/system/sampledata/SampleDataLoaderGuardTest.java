package lk.nuvi.gemshop.system.sampledata;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import lk.nuvi.gemshop.common.config.AppProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Sample data must never reach production, even if it is switched on by mistake. */
class SampleDataLoaderGuardTest {

    @Test
    void refusesInProduction() {
        AppProperties prod = new AppProperties("Gem Shop System", "prod", "Asia/Colombo",
                new AppProperties.Cors(List.of()), new AppProperties.SampleData(true, null));
        SampleDataLoader loader = new SampleDataLoader(List.of(), null, prod, JsonMapper.builder().build());
        assertThatThrownBy(loader::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("never loaded in production");
    }
}
