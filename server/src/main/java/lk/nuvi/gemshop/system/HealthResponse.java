package lk.nuvi.gemshop.system;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * @param status      UP when the API and the database work, DEGRADED when the database does not answer
 * @param environment local, dev, uat or prod
 * @param database    UP or DOWN
 */
public record HealthResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"UP", "DEGRADED"}) String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String app,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String environment,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"UP", "DOWN"}) String database,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant time) {}
