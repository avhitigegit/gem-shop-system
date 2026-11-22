package lk.nuvi.gemshop.gems.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
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

/** Gems API (P2-01). Cost and minimum price are only filled for the owner (null for everyone else). */
public final class GemDtos {

    private GemDtos() {}

    /** One gem in All gems (photo grid / table). */
    public record GemCard(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String productType,
            @Schema(requiredMode = REQUIRED) String gemType,
            @Schema(requiredMode = REQUIRED) BigDecimal carat,
            @Schema(requiredMode = REQUIRED) String colour,
            @Schema(requiredMode = REQUIRED) String shape,
            @Schema(requiredMode = REQUIRED) String treatment,
            @Schema(requiredMode = REQUIRED) CertType certType,
            String certLab,
            @Schema(requiredMode = REQUIRED) BigDecimal price,
            @Schema(description = "Owner only") BigDecimal cost,
            @Schema(description = "Owner only") BigDecimal min,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) GemStatus status,
            @Schema(description = "First photo (small), or null = draw the gem") String photo) {}

    /** One page of All gems, with totals over every matching gem. */
    public record GemPage(
            @Schema(requiredMode = REQUIRED) List<GemCard> items,
            @Schema(requiredMode = REQUIRED) long total,
            @Schema(requiredMode = REQUIRED) int page,
            @Schema(requiredMode = REQUIRED) int size,
            @Schema(requiredMode = REQUIRED) BigDecimal totalPrice,
            @Schema(description = "Owner only") BigDecimal totalCost) {}

    public record HistoryLine(
            @Schema(requiredMode = REQUIRED) Instant at,
            @Schema(requiredMode = REQUIRED) String user,
            @Schema(requiredMode = REQUIRED) String action,
            @Schema(requiredMode = REQUIRED) String detail) {}

    /** One gem page. */
    public record GemView(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String productType,
            @Schema(requiredMode = REQUIRED) String gemType,
            @Schema(requiredMode = REQUIRED) BigDecimal carat,
            @Schema(requiredMode = REQUIRED) String colour,
            @Schema(requiredMode = REQUIRED) String shape,
            @Schema(requiredMode = REQUIRED) String treatment,
            String description,
            @Schema(requiredMode = REQUIRED) CertType certType,
            String certLab,
            @Schema(description = "Lab certificate number, or the shop certificate number") String certNo,
            LocalDate certDate,
            FileView certScan,
            @Schema(requiredMode = REQUIRED) List<FileView> photos,
            @Schema(requiredMode = REQUIRED) BigDecimal price,
            @Schema(description = "Owner only") BigDecimal cost,
            @Schema(description = "Owner only") BigDecimal min,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) String branchName,
            @Schema(requiredMode = REQUIRED) GemStatus status,
            @Schema(description = "Owner only") String supplierCode,
            @Schema(description = "Owner only") String purchaseNo,
            String saleNo,
            Instant soldAt,
            @Schema(requiredMode = REQUIRED) Instant addedAt,
            @Schema(requiredMode = REQUIRED) String addedBy,
            @Schema(requiredMode = REQUIRED) boolean canSell,
            @Schema(requiredMode = REQUIRED) List<HistoryLine> history) {}

    /** Add / edit a gem (owner). {@code photos} = every photo the gem should have, in order (old and new uploads). */
    public record GemForm(
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 120) String name,
            @Schema(requiredMode = REQUIRED) @NotBlank String productType,
            @Schema(requiredMode = REQUIRED) @NotBlank String gemType,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0.01") @DecimalMax("9999.99") BigDecimal carat,
            @Schema(requiredMode = REQUIRED) @NotBlank String colour,
            @Schema(requiredMode = REQUIRED) @NotBlank String shape,
            @Schema(requiredMode = REQUIRED) @NotBlank String treatment,
            @Size(max = 1000) String description,
            @Schema(requiredMode = REQUIRED) @NotNull CertType certType,
            String certLab,
            @Size(max = 60) String certNo,
            LocalDate certDate,
            UUID certScan,
            @Schema(requiredMode = REQUIRED) @NotNull @Size(max = 10) List<UUID> photos,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("0") @DecimalMax("999999999") BigDecimal cost,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("1") @DecimalMax("999999999") BigDecimal price,
            @Schema(requiredMode = REQUIRED) @NotNull @DecimalMin("1") @DecimalMax("999999999") BigDecimal min,
            @Schema(requiredMode = REQUIRED) @NotBlank String branch,
            @Schema(description = "Add only: not ready to sell yet (e.g. needs cutting)") Boolean notReady,
            @Schema(description = "Supplier code (SUP-01) or empty = not linked; a gem bought in a purchase keeps its supplier")
            String supplier) {}

    /** Gems & stock home: counts and the newest gems (in the chosen branch). */
    public record StockSummary(
            @Schema(requiredMode = REQUIRED) long inStock,
            @Schema(requiredMode = REQUIRED) long notReady,
            @Schema(requiredMode = REQUIRED) long returned,
            @Schema(requiredMode = REQUIRED) List<GemCard> newest) {}

    /** For the add form: the code the new gem will get. */
    public record NextCode(@Schema(requiredMode = REQUIRED) String code) {}

    // ------------------------------------------------------------------ P2-02 tags and shop certificate

    /** One gem tag (QR + barcode of the code, name, carat, treatment, price). */
    public record TagView(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String gemType,
            @Schema(requiredMode = REQUIRED) String productType,
            @Schema(requiredMode = REQUIRED) BigDecimal carat,
            @Schema(requiredMode = REQUIRED) String treatment,
            @Schema(requiredMode = REQUIRED) CertType certType,
            @Schema(requiredMode = REQUIRED) BigDecimal price,
            @Schema(requiredMode = REQUIRED) GemStatus status) {}

    public record TagSheet(
            @Schema(requiredMode = REQUIRED) String shopName,
            @Schema(requiredMode = REQUIRED) List<TagView> tags) {}

    /** Print tags: which gems, the label size and whether the selling price is on the tag. */
    public record TagPrintForm(
            @Schema(requiredMode = REQUIRED) @NotNull @Size(min = 1, max = 200) List<@NotBlank String> codes,
            @Schema(requiredMode = REQUIRED) @NotNull LabelSize size,
            @Schema(requiredMode = REQUIRED) @NotNull Boolean showPrice) {}

    /** Label sizes for the tag PDF: one label per page on a label printer, or a sheet of 60 × 30 mm labels on A4. */
    public enum LabelSize {
        L60X30("60 × 30 mm label", 60, 30),
        L50X25("50 × 25 mm label", 50, 25),
        A4_SHEET("A4 sheet (24 labels of 60 × 30 mm)", 60, 30);

        private final String label;
        private final float widthMm;
        private final float heightMm;

        LabelSize(String label, float widthMm, float heightMm) {
            this.label = label;
            this.widthMm = widthMm;
            this.heightMm = heightMm;
        }

        public String label() { return label; }
        public float widthMm() { return widthMm; }
        public float heightMm() { return heightMm; }
    }

    /** The shop certificate as shown on screen (the PDF has the same content). */
    public record CertificateView(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String gemType,
            @Schema(requiredMode = REQUIRED) String productType,
            @Schema(requiredMode = REQUIRED) BigDecimal carat,
            @Schema(requiredMode = REQUIRED) String colour,
            @Schema(requiredMode = REQUIRED) String shape,
            @Schema(requiredMode = REQUIRED) String treatment,
            String description,
            @Schema(requiredMode = REQUIRED) CertType certType,
            @Schema(requiredMode = REQUIRED, description = "Shop certificate number, e.g. CGH-SC-00131") String certNo,
            @Schema(description = "First photo, or null = draw the gem") String photoUrl,
            @Schema(requiredMode = REQUIRED) String shopName,
            @Schema(description = "The shop's uploaded logo, or null = the standard logo") String logoUrl,
            String shopAddress,
            String shopPhone,
            String licenceNo,
            LocalDate licenceExpiry,
            @Schema(requiredMode = REQUIRED) LocalDate issuedOn,
            @Schema(requiredMode = REQUIRED) String signedBy,
            @Schema(requiredMode = REQUIRED, description = "Text in the QR code: gem code | certificate number") String qrText) {}
}
