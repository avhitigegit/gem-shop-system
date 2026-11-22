package lk.nuvi.gemshop.stock.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lk.nuvi.gemshop.gems.GemStatus;
import lk.nuvi.gemshop.stock.StockCorrection;
import lk.nuvi.gemshop.stock.Transfer;

/** Transfers, stock check, stock value and corrections (P2-04). */
public final class StockDtos {

    private StockDtos() {}

    /** A gem in a list on these screens (picture, code, name, where, status; prices for the owner only). */
    public record StockGem(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String gemType,
            @Schema(requiredMode = REQUIRED) String productType,
            @Schema(requiredMode = REQUIRED) BigDecimal carat,
            @Schema(requiredMode = REQUIRED) String colour,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) GemStatus status,
            @Schema(description = "Owner only") BigDecimal price) {}

    // ------------------------------------------------------------------ transfers

    public record TransferView(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) String from,
            @Schema(requiredMode = REQUIRED) String to,
            @Schema(requiredMode = REQUIRED) Transfer.Status status,
            @Schema(requiredMode = REQUIRED) Instant sentAt,
            @Schema(requiredMode = REQUIRED) String sentBy,
            Instant receivedAt,
            String receivedBy,
            String notes,
            @Schema(requiredMode = REQUIRED, description = "Can the signed-in person confirm it (on the way to their branch)") boolean canReceive,
            @Schema(requiredMode = REQUIRED) List<StockGem> gems) {}

    public record TransferForm(
            @Schema(requiredMode = REQUIRED) @NotBlank String from,
            @Schema(requiredMode = REQUIRED) @NotBlank String to,
            @Schema(requiredMode = REQUIRED) @NotNull @Size(min = 1, max = 100) List<@NotBlank String> gems,
            @Size(max = 300) String notes) {}

    /** The codes ticked (or scanned) at the receiving branch: every gem of the transfer. */
    public record ReceiveForm(@Schema(requiredMode = REQUIRED) @NotNull @Size(min = 1, max = 100) List<@NotBlank String> gems) {}

    // ------------------------------------------------------------------ stock check

    /** What the check of one branch starts with: every gem that should be on the shelf. */
    public record CheckStart(
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) String branchName,
            @Schema(requiredMode = REQUIRED) List<StockGem> expected) {}

    public enum ScanResult { FOUND, WRONG_PLACE, UNKNOWN }

    /** One scanned tag: found here, belongs elsewhere (or sold / gone), or not a gem. */
    public record ScanView(
            @Schema(requiredMode = REQUIRED) String code,
            @Schema(requiredMode = REQUIRED) ScanResult result,
            StockGem gem,
            @Schema(requiredMode = REQUIRED, description = "e.g. \"should be at Branch 2 (In stock)\" or \"Sold\"") String note) {}

    public record CheckForm(
            @Schema(requiredMode = REQUIRED) @NotBlank String branch,
            @Schema(requiredMode = REQUIRED, description = "Everything scanned (codes or tag text)") @NotNull @Size(max = 2000) List<String> scanned,
            @Size(max = 500) String notes) {}

    public record CheckView(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) String branch,
            @Schema(requiredMode = REQUIRED) Instant checkedAt,
            @Schema(requiredMode = REQUIRED) String checkedBy,
            @Schema(requiredMode = REQUIRED) int expected,
            @Schema(requiredMode = REQUIRED) int found,
            @Schema(requiredMode = REQUIRED) List<String> missing,
            @Schema(requiredMode = REQUIRED) List<String> wrong,
            @Schema(requiredMode = REQUIRED) List<String> unknown,
            String notes) {}

    // ------------------------------------------------------------------ value (owner)

    public record ValueRow(
            @Schema(requiredMode = REQUIRED) String group,
            @Schema(requiredMode = REQUIRED) long items,
            @Schema(requiredMode = REQUIRED) BigDecimal carats,
            @Schema(requiredMode = REQUIRED) BigDecimal cost,
            @Schema(requiredMode = REQUIRED) BigDecimal price) {}

    public record StockValue(
            @Schema(requiredMode = REQUIRED) ValueRow total,
            @Schema(requiredMode = REQUIRED) List<ValueRow> byGemType,
            @Schema(requiredMode = REQUIRED) List<ValueRow> byProductType,
            @Schema(requiredMode = REQUIRED) List<ValueRow> byBranch) {}

    // ------------------------------------------------------------------ corrections

    public record CorrectionView(
            @Schema(requiredMode = REQUIRED) String number,
            @Schema(requiredMode = REQUIRED) Instant requestedAt,
            @Schema(requiredMode = REQUIRED) StockGem gem,
            @Schema(requiredMode = REQUIRED) StockCorrection.Kind kind,
            @Schema(requiredMode = REQUIRED) String from,
            @Schema(requiredMode = REQUIRED) String to,
            @Schema(requiredMode = REQUIRED, description = "e.g. \"In stock → Written off\"") String change,
            @Schema(requiredMode = REQUIRED) String reason,
            @Schema(requiredMode = REQUIRED) String requestedBy,
            @Schema(requiredMode = REQUIRED) StockCorrection.Status status,
            String approvalNo) {}

    public record CorrectionForm(
            @Schema(requiredMode = REQUIRED) @NotBlank String gem,
            @Schema(requiredMode = REQUIRED) @NotNull StockCorrection.Kind kind,
            @Schema(requiredMode = REQUIRED, description = "STATUS: WRITTEN_OFF or IN_STOCK · BRANCH: branch code · CARAT: 5.01") @NotBlank @Size(max = 40) String to,
            @Schema(requiredMode = REQUIRED) @NotBlank @Size(max = 500) String reason) {}

    /** Gems & stock home: what is waiting. */
    public record StockWaiting(
            @Schema(requiredMode = REQUIRED, description = "Transfers on the way to this branch (or any)") long transfersOnTheWay,
            @Schema(requiredMode = REQUIRED) long correctionsWaiting) {}
}
