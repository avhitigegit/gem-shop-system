package lk.nuvi.gemshop.common.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * The one error format for every API.
 *
 * @param status      HTTP status number, e.g. 400
 * @param error       HTTP status text, e.g. "Bad Request"
 * @param code        stable code the frontend can check, e.g. VALIDATION_FAILED, NOT_FOUND, BELOW_MIN_PRICE
 * @param message     short message that can be shown to the user
 * @param fieldErrors problems with single form fields (empty when none)
 */
@Schema(name = "ApiError")
public record ApiError(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant timestamp,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String error,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String path,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FieldError> fieldErrors) {

    @Schema(name = "ApiFieldError")
    public record FieldError(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String field,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {}
}
