package lk.nuvi.gemshop.purchasing.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lk.nuvi.gemshop.files.api.FileDtos.FileView;
import lk.nuvi.gemshop.gems.CertType;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.purchasing.Supplier.IdType;
import lk.nuvi.gemshop.purchasing.SupplierReturn.RefundMethod;

/** Suppliers, cash purchases and returns to suppliers (P2-03). Amounts are in LKR. */
public final class PurchasingDtos {

    private PurchasingDtos() {}

    // ------------------------------------------------------------------ suppliers

    public record SupplierRow(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            String contact,
            @Schema(requiredMode = REQUIRED) String phone,
            @Schema(requiredMode = REQUIRED) String address,
            @Schema(requiredMode = REQUIRED) IdType idType,
            @Schema(requiredMode = REQUIRED) String idNo,
            String notes,
            @Schema(requiredMode = REQUIRED) long purchases,
            @Schema(requiredMode = REQUIRED) BigDecimal totalBought) {}

    public record SupplierForm(
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 120) String name,
            @Size(max = 120) String contact,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 40) String phone,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 300) String address,
            @Schema(requiredMode = REQUIRED) @NotNull IdType idType,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 30) String idNo,
            @Size(max = 500) String notes) {}

    /** One supplier page: details, totals, purchases and returns. */
    public record SupplierView(
            @Schema(requiredMode = REQUIRED) SupplierRow supplier,
            @Schema(requiredMode = REQUIRED) long gemsBought,
            @Schema(requiredMode = REQUIRED) long gemsSold,
            @Schema(requiredMode = REQUIRED) long returns,
            @Schema(requiredMode = REQUIRED) BigDecimal refunded,
            @Schema(requiredMode = REQUIRED) List<PurchaseRow> purchases,
            @Schema(requiredMode = REQUIRED) List<ReturnRow> returnList) {}

    // ------------------------------------------------------------------ purchases

    public record PurchaseGem(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String gemType,
            @Schema(requiredMode = REQUIRED) String productType,
            @Schema(requiredMode = REQUIRED) BigDecimal carat,
            @Schema(requiredMode = REQUIRED) BigDecimal cost,
            @Schema(requiredMode = REQUIRED) GemStatus status) {}

    public record PurchaseRow(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) LocalDate date,
            @Schema(requiredMode = REQUIRED) String supplierCode,
            @Schema(requiredMode = REQUIRED) String supplierName,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) List<PurchaseGem> gems,
            @Schema(requiredMode = REQUIRED) boolean hasBill,
            @Schema(requiredMode = REQUIRED) BigDecimal total,
            @Schema(requiredMode = REQUIRED, description = "Dated in a closed month (locked, rule 11)") boolean monthClosed) {}

    public record PurchaseList(
            @Schema(requiredMode = REQUIRED) List<PurchaseRow> items,
            @Schema(requiredMode = REQUIRED) long gems,
            @Schema(requiredMode = REQUIRED) BigDecimal total) {}

    public record PurchaseView(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) LocalDate date,
            @Schema(requiredMode = REQUIRED) String supplierCode,
            @Schema(requiredMode = REQUIRED) String supplierName,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) String branchName,
            @Schema(requiredMode = REQUIRED) String paidBy,
            @Schema(requiredMode = REQUIRED) BigDecimal total,
            FileView bill,
            String notes,
            @Schema(requiredMode = REQUIRED) String recordedBy,
            @Schema(requiredMode = REQUIRED) List<PurchaseGem> gems) {}

    /** One gem bought (same details and checks as Add a gem). */
    public record PurchaseLineForm(
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 120) String name,
            @Schema(requiredMode = REQUIRED) @NotBlank String productType,
            @Schema(requiredMode = REQUIRED) @NotBlank String gemType,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0.01") @DecimalMax("9999.99") BigDecimal carat,
            @Schema(requiredMode = REQUIRED) @NotBlank String colour,
            @Schema(requiredMode = REQUIRED) @NotBlank String shape,
            @Schema(requiredMode = REQUIRED) @NotBlank String treatment,
            @Schema(requiredMode = REQUIRED) @NotNull CertType certType,
            String certLab,
            @Size(max = 60) String certNo,
            LocalDate certDate,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0") @DecimalMax("999999999") BigDecimal cost,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("1") @DecimalMax("999999999") BigDecimal price,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("1") @DecimalMax("999999999") BigDecimal min,
            @Schema(description = "Not ready to sell yet (e.g. needs cutting)") Boolean notReady) {}

    /** Buy gems from a supplier: one cash purchase, every gem added to stock with its own code. */
    public record PurchaseForm(
            @Schema(requiredMode = REQUIRED) @NotNull LocalDate date,
            @Schema(requiredMode = REQUIRED) @NotBlank String supplier,
            @Schema(requiredMode = REQUIRED) @NotBlank String branch,
            @Schema(requiredMode = REQUIRED, description = "BILL_PHOTO upload id") @NotNull UUID billPhoto,
            @Size(max = 500) String notes,
            @Schema(requiredMode = REQUIRED) @NotNull @Size(min = 1, max = 50) List<@Valid PurchaseLineForm> lines) {}

    /** Gems & stock home: counts on the Suppliers and Purchases cards. */
    public record PurchasingSummary(
            @Schema(requiredMode = REQUIRED) long suppliers,
            @Schema(requiredMode = REQUIRED) long purchasesThisMonth) {}

    // ------------------------------------------------------------------ returns to supplier

    public record ReturnRow(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) Instant returnedAt,
            @Schema(requiredMode = REQUIRED) String supplierCode,
            @Schema(requiredMode = REQUIRED) String supplierName,
            @Schema(requiredMode = REQUIRED) String gemCode,
            @Schema(requiredMode = REQUIRED) String gemName,
            @Schema(requiredMode = REQUIRED) String gemType,
            @Schema(requiredMode = REQUIRED) String productType,
            @Schema(requiredMode = REQUIRED) String reason,
            @Schema(requiredMode = REQUIRED) BigDecimal refund,
            @Schema(requiredMode = REQUIRED) RefundMethod refundMethod) {}

    /** A gem that can go back to its supplier (in stock, not ready, or back from a customer; bought from a supplier). */
    public record ReturnableGem(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String supplierCode,
            @Schema(requiredMode = REQUIRED) String supplierName,
            @Schema(requiredMode = REQUIRED) BigDecimal cost,
            String purchaseNo,
            @Schema(requiredMode = REQUIRED) Instant addedAt) {}

    public record ReturnForm(
            @Schema(requiredMode = REQUIRED) @NotBlank String gem,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 60) String reasonType,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 400) String details,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0") @DecimalMax("999999999") BigDecimal refund,
            @Schema(requiredMode = REQUIRED) @NotNull RefundMethod refundMethod) {}
}
