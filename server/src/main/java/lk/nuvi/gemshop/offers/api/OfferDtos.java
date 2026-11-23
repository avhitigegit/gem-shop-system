package lk.nuvi.gemshop.offers.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Offers by email and WhatsApp and their results (P4-05, BRD J2, J3, J4, J6). */
public final class OfferDtos {

    private OfferDtos() {}

    /** Who gets the offer: none ticked = everyone with permission. */
    public record OfferAudienceForm(
            @Schema(requiredMode = REQUIRED, example = "EMAIL") @NotNull @Pattern(regexp = "EMAIL|WHATSAPP") String channel,
            @Schema(description = "Countries; empty = all") @Size(max = 100) List<@Size(max = 60) String> countries,
            @Schema(description = "Gem types bought before; empty = any") @Size(max = 100) List<@Size(max = 80) String> gems,
            @Schema(description = "WhatsApp template (for the cost)") @Size(max = 30) String template) {}

    public record OfferAudience(
            @Schema(requiredMode = REQUIRED, description = "Customers who match the countries and gems") int matching,
            @Schema(requiredMode = REQUIRED, description = "Who will receive it: permission, not unsubscribed, email / number") int recipients,
            @Schema(requiredMode = REQUIRED, description = "No permission, unsubscribed or no email / number") int excluded,
            @Schema(requiredMode = REQUIRED, description = "First name of the first recipient, for the preview", example = "Anna")
            String firstName,
            @Schema(requiredMode = REQUIRED, description = "Estimated cost in US$ (Amazon SES or Meta)") BigDecimal costUsd) {}

    public record OfferForm(
            @Schema(description = "A draft being edited; empty = new") @Size(max = 12) String code,
            @Schema(requiredMode = REQUIRED, example = "EMAIL") @NotNull @Pattern(regexp = "EMAIL|WHATSAPP") String channel,
            @Schema(requiredMode = REQUIRED, example = "New Padparadscha arrivals") @NotBlank @Size(max = 120) String name,
            @Schema(description = "Email only") @Size(max = 200) String subject,
            @Schema(description = "WhatsApp only: new_arrivals, festive_offer, thank_you") @Size(max = 30) String template,
            @Schema(requiredMode = REQUIRED, description = "Email text, or WhatsApp \"your text\"; {name} = first name") @NotBlank
            @Size(max = 5000) String body,
            @Schema(description = "Gem picture chosen (e.g. Padparadscha), \"upload\", or empty = no picture") @Size(max = 40) String picture,
            @Schema(description = "The picture as a data URL (PNG / JPEG, up to 2 MB); empty = keep the saved one") @Size(max = 2_900_000)
            String photo,
            @Size(max = 100) List<@Size(max = 60) String> countries,
            @Size(max = 100) List<@Size(max = 80) String> gems) {}

    public record OfferTemplate(
            @Schema(requiredMode = REQUIRED, example = "new_arrivals") String key,
            @Schema(requiredMode = REQUIRED, example = "new_arrivals (Marketing)") String label,
            @Schema(requiredMode = REQUIRED, description = "With {name}, {shop} and {text}") String text,
            @Schema(requiredMode = REQUIRED) boolean marketing) {}

    public record OfferOptions(
            @Schema(requiredMode = REQUIRED) List<String> countries,
            @Schema(requiredMode = REQUIRED) List<String> gemTypes,
            @Schema(requiredMode = REQUIRED) List<OfferTemplate> templates,
            @Schema(requiredMode = REQUIRED, description = "Email on and Amazon SES on") boolean emailReady,
            @Schema(requiredMode = REQUIRED) boolean emailOn,
            @Schema(requiredMode = REQUIRED, description = "WhatsApp on and verified") boolean whatsappReady,
            @Schema(requiredMode = REQUIRED) boolean emailTestMode,
            @Schema(requiredMode = REQUIRED) boolean whatsappTestMode,
            @Schema(requiredMode = REQUIRED) String shopName,
            String shopAddress,
            String website,
            @Schema(requiredMode = REQUIRED, description = "Where \"Send test to me\" goes") String myEmail,
            String myPhone) {}

    public record OfferRow(
            @Schema(requiredMode = REQUIRED, example = "OF-0001") String code,
            @Schema(requiredMode = REQUIRED, example = "EMAIL") String channel,
            @Schema(requiredMode = REQUIRED) String name,
            String subject,
            @Schema(requiredMode = REQUIRED, example = "Germany, Netherlands · Moonstone") String audience,
            Instant sentAt,
            String sentBy,
            @Schema(requiredMode = REQUIRED, example = "SENT") String status,
            @Schema(requiredMode = REQUIRED) int sent,
            @Schema(requiredMode = REQUIRED) int delivered,
            @Schema(requiredMode = REQUIRED) int opened,
            @Schema(requiredMode = REQUIRED) int clicked,
            @Schema(requiredMode = REQUIRED) int unsubscribed) {}

    public record OfferTotals(
            @Schema(requiredMode = REQUIRED, description = "Offers sent") int offers,
            @Schema(requiredMode = REQUIRED) int sent,
            @Schema(requiredMode = REQUIRED) int delivered,
            @Schema(requiredMode = REQUIRED) int opened,
            @Schema(requiredMode = REQUIRED) int clicked,
            @Schema(requiredMode = REQUIRED) int unsubscribed) {}

    public record OfferList(
            @Schema(requiredMode = REQUIRED) List<OfferRow> offers,
            @Schema(requiredMode = REQUIRED) OfferTotals totals) {}

    /** One offer, for editing a draft. */
    public record OfferDetail(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String channel,
            @Schema(requiredMode = REQUIRED) String name,
            String subject,
            String template,
            @Schema(requiredMode = REQUIRED) String body,
            String picture,
            @Schema(description = "data URL") String photo,
            @Schema(requiredMode = REQUIRED) List<String> countries,
            @Schema(requiredMode = REQUIRED) List<String> gems,
            @Schema(requiredMode = REQUIRED) String status) {}

    public record OfferTestResult(
            @Schema(requiredMode = REQUIRED) String to,
            @Schema(requiredMode = REQUIRED) boolean testMode) {}
}
