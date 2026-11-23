package lk.nuvi.gemshop.messaging.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Email &amp; WhatsApp settings, automatic messages and birthday wishes (P4-04, BRD J5, J7, J8). */
public final class MessageDtos {

    private MessageDtos() {}

    // ------------------------------------------------------------------ settings screen (owner)

    public record SmtpView(
            String host,
            Integer port,
            @Schema(requiredMode = REQUIRED, example = "STARTTLS") String security,
            String user,
            @Schema(requiredMode = REQUIRED, description = "A password is saved (never sent back)") boolean hasPassword,
            String from) {}

    public record SesView(
            @Schema(requiredMode = REQUIRED) boolean enabled,
            String domain,
            String region,
            String from,
            String accessKey,
            @Schema(requiredMode = REQUIRED) boolean hasSecret,
            @Schema(requiredMode = REQUIRED) boolean spf,
            @Schema(requiredMode = REQUIRED) boolean dkim) {}

    public record WhatsappView(
            @Schema(requiredMode = REQUIRED) boolean enabled,
            String phone,
            String accountId,
            @Schema(requiredMode = REQUIRED, description = "A token is saved (never sent back)") boolean hasToken,
            @Schema(requiredMode = REQUIRED) boolean verified,
            Instant verifiedAt) {}

    public record AutoView(
            @Schema(requiredMode = REQUIRED) boolean invoiceEmail,
            @Schema(requiredMode = REQUIRED) boolean invoiceWhatsapp,
            @Schema(requiredMode = REQUIRED) boolean thankYou,
            @Schema(requiredMode = REQUIRED) boolean ownerClosing) {}

    public record BirthdayView(
            @Schema(requiredMode = REQUIRED) boolean on,
            @Schema(requiredMode = REQUIRED) boolean email,
            @Schema(requiredMode = REQUIRED) boolean whatsapp,
            @Schema(requiredMode = REQUIRED, example = "09:00") String time,
            @Schema(requiredMode = REQUIRED) String subject,
            @Schema(requiredMode = REQUIRED) String text) {}

    public record MessageSettingsView(
            @Schema(requiredMode = REQUIRED) boolean emailOn,
            @Schema(requiredMode = REQUIRED, description = "Emails go to the test mail server (MailHog) until real email is connected")
            boolean emailTestMode,
            @Schema(requiredMode = REQUIRED, description = "WhatsApp messages are only written to the server log until P6-03")
            boolean whatsappTestMode,
            @Schema(requiredMode = REQUIRED) SmtpView smtp,
            @Schema(requiredMode = REQUIRED) SesView ses,
            @Schema(requiredMode = REQUIRED) WhatsappView whatsapp,
            @Schema(requiredMode = REQUIRED) AutoView auto,
            @Schema(requiredMode = REQUIRED) BirthdayView birthday) {}

    /** What the send buttons need (everyone who sends invoices and quotations). */
    public record MessageStatus(
            @Schema(requiredMode = REQUIRED) boolean emailOn,
            @Schema(requiredMode = REQUIRED) boolean whatsappReady,
            @Schema(requiredMode = REQUIRED) boolean whatsappTestMode,
            @Schema(requiredMode = REQUIRED, description = "The invoice is emailed by itself after a sale") boolean autoInvoiceEmail,
            @Schema(requiredMode = REQUIRED, description = "The invoice goes on WhatsApp by itself after a sale") boolean autoInvoiceWhatsapp) {}

    // ------------------------------------------------------------------ forms

    public record SmtpForm(
            @Schema(requiredMode = REQUIRED, example = "smtp.gmail.com") @NotBlank @Size(max = 120) String host,
            @Schema(requiredMode = REQUIRED, example = "587") @NotNull @Min(1) @Max(65535) Integer port,
            @Schema(requiredMode = REQUIRED, example = "STARTTLS") @NotBlank @Pattern(regexp = "STARTTLS|SSL|NONE") String security,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 160) String user,
            @Schema(description = "Empty = keep the saved one") @Size(max = 200) String password,
            @Schema(requiredMode = REQUIRED, example = "Ceylon Gem House <sales@ceylongemhouse.lk>") @NotBlank @Size(max = 200) String from) {}

    public record SesForm(
            @Schema(requiredMode = REQUIRED) boolean enabled,
            @Size(max = 120) String domain,
            @Size(max = 30) String region,
            @Size(max = 200) String from,
            @Size(max = 64) String accessKey,
            @Schema(description = "Empty = keep the saved one") @Size(max = 200) String secret) {}

    public record WhatsappForm(
            @Schema(requiredMode = REQUIRED, example = "+94 77 123 4567") @NotBlank @Size(max = 40) String phone,
            @Schema(requiredMode = REQUIRED, example = "102938475610293") @NotBlank @Size(max = 40) String accountId,
            @Schema(description = "Empty = keep the saved one") @Size(max = 600) String token) {}

    public record VerifyResult(
            @Schema(requiredMode = REQUIRED) boolean verified,
            @Schema(requiredMode = REQUIRED) String message,
            @Schema(requiredMode = REQUIRED) MessageSettingsView settings) {}

    /** Switches: only the ones sent are changed. */
    public record SwitchForm(Boolean emailOn, Boolean whatsappOn, Boolean invoiceEmail, Boolean invoiceWhatsapp, Boolean thankYou,
            Boolean ownerClosing, Boolean birthdays) {}

    public record BirthdayForm(
            @Schema(requiredMode = REQUIRED) boolean email,
            @Schema(requiredMode = REQUIRED) boolean whatsapp,
            @Schema(requiredMode = REQUIRED, example = "09:00") @NotBlank @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d") String time,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 200) String subject,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 1000) String text) {}

    public record TestEmailResult(
            @Schema(requiredMode = REQUIRED) String to,
            @Schema(requiredMode = REQUIRED) String via,
            @Schema(requiredMode = REQUIRED) boolean testMode) {}

    // ------------------------------------------------------------------ birthdays

    public record BirthdayRow(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String country,
            @Schema(requiredMode = REQUIRED, description = "MM-DD") String birthday,
            @Schema(requiredMode = REQUIRED, description = "Days until the birthday, 0 = today") int birthdayIn,
            @Schema(requiredMode = REQUIRED, description = "SENT, NO_PERMISSION, OFF or WILL_SEND") String status,
            @Schema(description = "When this year's wish went out") Instant sentAt) {}

    public record BirthdayList(
            @Schema(requiredMode = REQUIRED, description = "Birthdays in the next 30 days") List<BirthdayRow> next30,
            @Schema(requiredMode = REQUIRED) int sentThisYear,
            @Schema(requiredMode = REQUIRED, example = "Happy birthday from Ceylon Gem House!") String previewSubject,
            @Schema(requiredMode = REQUIRED, description = "The message for a customer called Anna") String previewText) {}

    public record SendNowResult(@Schema(requiredMode = REQUIRED) int sent) {}

    // ------------------------------------------------------------------ message log

    public record MessageLogRow(
            @Schema(requiredMode = REQUIRED) Instant at,
            @Schema(requiredMode = REQUIRED, example = "THANK_YOU") String kind,
            @Schema(requiredMode = REQUIRED, example = "EMAIL") String channel,
            @Schema(requiredMode = REQUIRED) String recipient,
            String customer,
            String ref,
            String subject,
            @Schema(requiredMode = REQUIRED, example = "SENT") String status,
            @Schema(requiredMode = REQUIRED) boolean automatic) {}

    public record MessageLogList(@Schema(requiredMode = REQUIRED) @Valid List<MessageLogRow> rows) {}
}
