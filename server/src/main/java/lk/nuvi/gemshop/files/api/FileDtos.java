package lk.nuvi.gemshop.files.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import lk.nuvi.gemshop.files.FileKind;

public final class FileDtos {

    private FileDtos() {}

    /**
     * An uploaded file. {@code url} / {@code thumbUrl} are private links that work for about an hour
     * (thumbUrl is empty for PDFs). {@code attached} = saved with a gem, expense, …
     */
    public record FileView(
            @Schema(requiredMode = REQUIRED) UUID id,
            @Schema(requiredMode = REQUIRED) FileKind kind,
            @Schema(requiredMode = REQUIRED) String contentType,
            @Schema(requiredMode = REQUIRED) long sizeBytes,
            @Schema(requiredMode = REQUIRED) String name,
            Integer width,
            Integer height,
            @Schema(requiredMode = REQUIRED) String url,
            String thumbUrl,
            @Schema(requiredMode = REQUIRED) boolean attached) {}
}
