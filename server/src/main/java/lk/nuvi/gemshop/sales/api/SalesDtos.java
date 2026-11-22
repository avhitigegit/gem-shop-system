package lk.nuvi.gemshop.sales.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lk.nuvi.gemshop.common.money.Currency;
import lk.nuvi.gemshop.gems.api.GemDtos.GemCard;
import lk.nuvi.gemshop.sales.HeldSale;
import lk.nuvi.gemshop.sales.Sale;
import lk.nuvi.gemshop.sales.SalePayment;

/** New sale (P3-01). Prices are LKR; USD / EUR are shown at today's rate. */
public final class SalesDtos {

    private SalesDtos() {}

    // ------------------------------------------------------------------ forms

    /** One gem on the bill with the price the customer agreed. */
    public record LineForm(
            @Schema(requiredMode = REQUIRED, example = "GEM-00125") @NotNull @Size(max = 40) String gem,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0.01") @DecimalMax("999999999") BigDecimal agreed) {}

    /** A customer who came before ({@code code}), or a new one (name + country required). */
    public record CustomerForm(
            @Schema(description = "C-001 = came before; empty = new customer") @Size(max = 12) String code,
            @Size(max = 120) String name,
            @Size(max = 60) String country,
            @Size(max = 160) String email,
            @Size(max = 40) String whatsapp,
            @Schema(description = "MM-DD, no year", example = "04-17") @Size(max = 5) String birthday,
            @Schema(description = "Promotions by email / WhatsApp allowed") Boolean consent) {}

    /** Prices and commission while the bill is being made (no customer needed). */
    public record CheckForm(
            @Schema(description = "Branch code; empty = the salesperson's branch / the first gem's branch") String branch,
            @Schema(requiredMode = REQUIRED) @NotNull @Size(max = 50) List<@Valid @NotNull LineForm> items,
            @Schema(description = "Guide code; empty = walk-in") String guide) {}

    /** Send the bill to the owner because a price is below the minimum. */
    public record HoldForm(
            String branch,
            @Schema(requiredMode = REQUIRED) @NotEmpty @Size(max = 50) List<@Valid @NotNull LineForm> items,
            @Schema(requiredMode = REQUIRED) @NotNull @Valid CustomerForm customer,
            String guide) {}

    public record PaymentForm(
            @Schema(requiredMode = REQUIRED) @NotNull SalePayment.Method method,
            @Schema(requiredMode = REQUIRED) @NotNull Currency currency,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0.01") @DecimalMax("999999999") BigDecimal amount) {}

    /** Complete the sale and take payment. With {@code held}: the owner-approved held sale. */
    public record SaleForm(
            String branch,
            @Schema(requiredMode = REQUIRED) @NotEmpty @Size(max = 50) List<@Valid @NotNull LineForm> items,
            @Schema(requiredMode = REQUIRED) @NotNull @Valid CustomerForm customer,
            String guide,
            @Schema(description = "HS-0001 when finishing an approved held sale") String held,
            @Schema(requiredMode = REQUIRED) @NotEmpty @Size(max = 10) List<@Valid @NotNull PaymentForm> payments,
            @Size(max = 500) String notes) {}

    // ------------------------------------------------------------------ views

    /** A scanned gem: can it go on the bill, and if not, why. */
    public record PosGem(
            @Schema(requiredMode = REQUIRED) GemCard gem,
            @Schema(requiredMode = REQUIRED) boolean sellable,
            @Schema(description = "Why it cannot be sold, e.g. already sold on 24/10/2026 (INV-000101)") String note) {}

    public record CheckLine(
            @Schema(requiredMode = REQUIRED) String gem,
            @Schema(requiredMode = REQUIRED) boolean belowMin) {}

    public record CheckView(
            @Schema(requiredMode = REQUIRED) List<CheckLine> lines,
            @Schema(requiredMode = REQUIRED) boolean belowMin,
            @Schema(requiredMode = REQUIRED) BigDecimal total,
            @Schema(requiredMode = REQUIRED) BigDecimal guidePct,
            @Schema(requiredMode = REQUIRED) BigDecimal guideComm,
            @Schema(requiredMode = REQUIRED) BigDecimal staffComm,
            @Schema(requiredMode = REQUIRED, description = "Staff commission price level, 1 = lowest") int level) {}

    public record CustomerPick(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String country,
            String email,
            String whatsapp,
            String birthday,
            @Schema(requiredMode = REQUIRED) boolean consent,
            @Schema(requiredMode = REQUIRED) long purchases) {}

    public record GuidePick(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            String vehicle,
            String vehicleNo,
            @Schema(requiredMode = REQUIRED) String phone,
            @Schema(requiredMode = REQUIRED) BigDecimal pct) {}

    public record SaleLine(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String gemType,
            @Schema(requiredMode = REQUIRED) String productType,
            @Schema(requiredMode = REQUIRED) BigDecimal carat,
            @Schema(requiredMode = REQUIRED) BigDecimal listPrice,
            @Schema(requiredMode = REQUIRED) BigDecimal agreed,
            String approvalNo) {}

    public record PaymentLine(
            @Schema(requiredMode = REQUIRED) SalePayment.Method method,
            @Schema(requiredMode = REQUIRED) Currency currency,
            @Schema(requiredMode = REQUIRED) BigDecimal amount,
            @Schema(requiredMode = REQUIRED) BigDecimal rate,
            @Schema(requiredMode = REQUIRED) BigDecimal lkr) {}

    public record SaleView(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) Instant soldAt,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) Sale.Status status,
            @Schema(requiredMode = REQUIRED) CustomerPick customer,
            GuidePick guide,
            @Schema(requiredMode = REQUIRED) String staff,
            @Schema(requiredMode = REQUIRED) List<SaleLine> items,
            @Schema(requiredMode = REQUIRED) List<PaymentLine> payments,
            @Schema(requiredMode = REQUIRED) BigDecimal total,
            @Schema(requiredMode = REQUIRED) BigDecimal paid,
            @Schema(requiredMode = REQUIRED) BigDecimal change,
            @Schema(requiredMode = REQUIRED) BigDecimal guidePct,
            @Schema(requiredMode = REQUIRED) BigDecimal guideComm,
            @Schema(requiredMode = REQUIRED) BigDecimal staffComm,
            @Schema(description = "Bank fee on cards, paid by the shop – owner and accountant only") BigDecimal cardFee,
            String heldNo,
            @Schema(requiredMode = REQUIRED) boolean sentEmail,
            @Schema(requiredMode = REQUIRED) boolean sentWhatsapp,
            String notes) {}

    public record HeldView(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) Instant heldAt,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) HeldSale.Status status,
            String approvalNo,
            @Schema(description = "Owner's note when deciding") String note,
            String decidedBy,
            Instant decidedAt,
            @Schema(requiredMode = REQUIRED) String staff,
            @Schema(requiredMode = REQUIRED) CustomerPick customer,
            GuidePick guide,
            @Schema(requiredMode = REQUIRED) List<SaleLine> items,
            @Schema(requiredMode = REQUIRED) BigDecimal total,
            String saleNo) {}

    public record SaleRow(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) Instant soldAt,
            @Schema(requiredMode = REQUIRED) String customer,
            @Schema(requiredMode = REQUIRED) String country,
            @Schema(requiredMode = REQUIRED) int items,
            @Schema(requiredMode = REQUIRED) String gemType,
            @Schema(requiredMode = REQUIRED) String productType,
            @Schema(requiredMode = REQUIRED) BigDecimal total,
            @Schema(requiredMode = REQUIRED) Sale.Status status) {}

    /** Sell home: today's sales, held sales, latest invoices. */
    public record SellSummary(
            @Schema(requiredMode = REQUIRED) long salesToday,
            @Schema(requiredMode = REQUIRED) BigDecimal totalToday,
            @Schema(requiredMode = REQUIRED) List<HeldView> held,
            @Schema(requiredMode = REQUIRED) List<SaleRow> latest) {}
}
