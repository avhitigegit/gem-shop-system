package lk.nuvi.gemshop.notifications.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lk.nuvi.gemshop.notifications.PushKind;

/** Phone push alerts (P4-02). */
public final class PushDtos {

    private PushDtos() {}

    public record PushKey(@Schema(requiredMode = REQUIRED, description = "VAPID public key (base64url)") String publicKey) {}

    /** A browser push subscription (PushSubscription.toJSON()). */
    public record DeviceForm(
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 1000) String endpoint,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 200) String p256dh,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 100) String auth,
            @Schema(description = "e.g. \"iPhone · Safari\"") @Size(max = 200) String device) {}

    public record PushDeviceView(
            @Schema(requiredMode = REQUIRED) long id,
            String device,
            @Schema(requiredMode = REQUIRED) Instant addedAt,
            Instant lastSentAt) {}

    public record PushKindSetting(
            @Schema(requiredMode = REQUIRED) PushKind kind,
            @Schema(requiredMode = REQUIRED) String label,
            @Schema(requiredMode = REQUIRED) boolean on) {}

    public record PushSettings(
            @Schema(requiredMode = REQUIRED) List<PushKindSetting> kinds,
            @Schema(requiredMode = REQUIRED, description = "This person's phones / browsers") List<PushDeviceView> devices) {}

    public record PushSettingsForm(@Schema(requiredMode = REQUIRED) @NotNull Map<PushKind, Boolean> kinds) {}

    public record PushTestResult(
            @Schema(requiredMode = REQUIRED, description = "Phones / browsers it was sent to") int sent) {}
}
