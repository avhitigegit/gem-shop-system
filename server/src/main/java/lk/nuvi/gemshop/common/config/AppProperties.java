package lk.nuvi.gemshop.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings under {@code app.*} in application.yml. */
@ConfigurationProperties("app")
public record AppProperties(
        @DefaultValue("Gem Shop System") String name,
        @DefaultValue("local") String environment,
        @DefaultValue("Asia/Colombo") String timezone,
        @DefaultValue Cors cors,
        @DefaultValue SampleData sampleData) {

    public record Cors(@DefaultValue("http://localhost:3000") List<String> allowedOrigins) {}

    /** Prototype demo data on an empty database: local, dev and uat only – never prod. */
    public record SampleData(@DefaultValue("false") boolean enabled) {}
}
