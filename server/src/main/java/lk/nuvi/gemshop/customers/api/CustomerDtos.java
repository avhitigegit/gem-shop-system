package lk.nuvi.gemshop.customers.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Customers screens (P4-03, BRD J1, J2, J8, F5). */
public final class CustomerDtos {

    private CustomerDtos() {}

    /** Add / edit a customer. On edit an empty passport keeps the one saved; consent is ignored (it has its own switch). */
    public record CustomerDetailsForm(
            @Schema(requiredMode = REQUIRED, example = "Anna Müller") @NotBlank @Size(max = 120) String name,
            @Schema(requiredMode = REQUIRED, example = "Germany") @NotBlank @Size(max = 60) String country,
            @Size(max = 160) String email,
            @Size(max = 40) String whatsapp,
            @Schema(description = "MM-DD, day and month only (or empty)", example = "04-17") @Size(max = 5) String birthday,
            @Schema(description = "Stored encrypted") @Size(max = 20) String passport,
            @Schema(description = "Agrees to receive offers and birthday wishes (add only)") Boolean consent) {}

    public record ConsentForm(@Schema(requiredMode = REQUIRED) boolean consent) {}

    /** One row of the customers list. Purchases and spent count the branch at the top (completed invoices). */
    public record CustomerRow(
            @Schema(requiredMode = REQUIRED, example = "C-001") String code,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String country,
            String email,
            String whatsapp,
            @Schema(description = "MM-DD") String birthday,
            @Schema(description = "Days until the next birthday (0 = today), shop time") Integer birthdayIn,
            @Schema(requiredMode = REQUIRED) long purchases,
            @Schema(requiredMode = REQUIRED) BigDecimal spent,
            @Schema(requiredMode = REQUIRED) boolean consent,
            Instant consentAt,
            @Schema(requiredMode = REQUIRED) boolean unsubscribed,
            Instant unsubscribedAt,
            @Schema(requiredMode = REQUIRED, description = "May get offers and birthday wishes (consent and not unsubscribed)")
            boolean canMessage,
            @Schema(requiredMode = REQUIRED) Instant joinedAt) {}

    public record CustomerList(@Schema(requiredMode = REQUIRED) List<CustomerRow> customers) {}

    public record CustomerGem(@Schema(requiredMode = REQUIRED) String code, @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String gemType, @Schema(requiredMode = REQUIRED) String productType) {}

    public record CustomerPurchase(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) Instant soldAt,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) List<CustomerGem> gems,
            @Schema(requiredMode = REQUIRED) BigDecimal total,
            @Schema(requiredMode = REQUIRED, example = "COMPLETED") String status) {}

    public record CustomerQuote(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED, example = "RESERVATION") String kind,
            @Schema(requiredMode = REQUIRED) Instant createdOn,
            @Schema(requiredMode = REQUIRED) List<String> gems,
            @Schema(requiredMode = REQUIRED) LocalDate expires,
            @Schema(requiredMode = REQUIRED, example = "ACTIVE") String status) {}

    /** A message sent to the customer: an invoice or quote by email / WhatsApp (birthday wishes and offers come with P4-04 / P4-05). */
    public record CustomerMessage(
            @Schema(requiredMode = REQUIRED) Instant at,
            @Schema(requiredMode = REQUIRED, example = "INVOICE") String kind,
            @Schema(requiredMode = REQUIRED, example = "Invoice INV-000140") String text,
            @Schema(requiredMode = REQUIRED) boolean email,
            @Schema(requiredMode = REQUIRED) boolean whatsapp) {}

    public record CustomerProfile(
            @Schema(requiredMode = REQUIRED) CustomerRow customer,
            @Schema(description = "Owner only: \"•••• 000\" (stored encrypted)") String passport,
            @Schema(requiredMode = REQUIRED, description = "A passport is saved (shown to the owner only)") boolean hasPassport,
            @Schema(description = "LINK or WHATSAPP") String unsubVia,
            String notes,
            @Schema(requiredMode = REQUIRED) List<CustomerPurchase> purchases,
            @Schema(requiredMode = REQUIRED) List<CustomerQuote> quotes,
            @Schema(requiredMode = REQUIRED) List<CustomerMessage> messages) {}

    /** The public unsubscribe page: first name only, nothing else about the customer. */
    public record UnsubscribeView(
            @Schema(requiredMode = REQUIRED) String firstName,
            @Schema(requiredMode = REQUIRED) String shop,
            @Schema(requiredMode = REQUIRED) boolean unsubscribed) {}
}
