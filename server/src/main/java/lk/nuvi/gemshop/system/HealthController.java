package lk.nuvi.gemshop.system;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lk.nuvi.gemshop.common.config.AppProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "System")
@lk.nuvi.gemshop.auth.security.PublicApi
@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbc;
    private final AppProperties app;
    private final String version;

    public HealthController(JdbcTemplate jdbc, AppProperties app, ObjectProvider<BuildProperties> build) {
        this.jdbc = jdbc;
        this.app = app;
        BuildProperties b = build.getIfAvailable();
        this.version = b != null ? b.getVersion() : "dev";
    }

    @Operation(operationId = "getHealth", summary = "Is the API running and can it reach the database?")
    @GetMapping("/health")
    public HealthResponse health() {
        String db;
        try {
            Integer one = jdbc.queryForObject("select 1", Integer.class);
            db = Integer.valueOf(1).equals(one) ? "UP" : "DOWN";
        } catch (RuntimeException e) {
            db = "DOWN";
        }
        return new HealthResponse("UP".equals(db) ? "UP" : "DEGRADED", app.name(), version, app.environment(), db, Instant.now());
    }
}
