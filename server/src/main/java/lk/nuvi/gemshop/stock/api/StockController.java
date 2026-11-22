package lk.nuvi.gemshop.stock.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.stock.StockChecks;
import lk.nuvi.gemshop.stock.StockCorrections;
import lk.nuvi.gemshop.stock.StockValues;
import lk.nuvi.gemshop.stock.Transfers;
import lk.nuvi.gemshop.stock.api.StockDtos.CheckForm;
import lk.nuvi.gemshop.stock.api.StockDtos.CheckStart;
import lk.nuvi.gemshop.stock.api.StockDtos.CheckView;
import lk.nuvi.gemshop.stock.api.StockDtos.CorrectionForm;
import lk.nuvi.gemshop.stock.api.StockDtos.CorrectionView;
import lk.nuvi.gemshop.stock.api.StockDtos.ReceiveForm;
import lk.nuvi.gemshop.stock.api.StockDtos.ScanView;
import lk.nuvi.gemshop.stock.api.StockDtos.StockGem;
import lk.nuvi.gemshop.stock.api.StockDtos.StockValue;
import lk.nuvi.gemshop.stock.api.StockDtos.StockWaiting;
import lk.nuvi.gemshop.stock.api.StockDtos.TransferForm;
import lk.nuvi.gemshop.stock.api.StockDtos.TransferView;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Stock (P2-04): transfers, stock check, stock value (owner), corrections. Owner and shop staff; follows the top bar. */
@RestController
@Tag(name = "Stock")
public class StockController {

    private final Transfers transfers;
    private final StockChecks checks;
    private final StockCorrections corrections;
    private final StockValues values;

    public StockController(Transfers transfers, StockChecks checks, StockCorrections corrections, StockValues values) {
        this.transfers = transfers;
        this.checks = checks;
        this.corrections = corrections;
        this.values = values;
    }

    @PreAuthorize(Roles.OWNER_STAFF)
    @GetMapping("/api/stock/waiting")
    @Operation(summary = "Gems & stock home: transfers on the way here and corrections waiting for the owner")
    public StockWaiting waiting() {
        return new StockWaiting(transfers.onTheWay(), corrections.waiting());
    }

    // ------------------------------------------------------------------ transfers

    @PreAuthorize(Roles.OWNER_STAFF)
    @GetMapping("/api/transfers")
    @Operation(summary = "Transfers from or to the branch, newest first")
    public List<TransferView> transfers() {
        return transfers.list();
    }

    @PreAuthorize(Roles.OWNER_STAFF)
    @GetMapping("/api/transfers/gems")
    @Operation(summary = "Gems that can be sent from a branch (in stock or back from a customer)")
    public List<StockGem> sendable(@RequestParam String branch) {
        return transfers.available(branch);
    }

    @PreAuthorize(Roles.OWNER_STAFF)
    @PostMapping("/api/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send gems to the other branch: they are On the way until received")
    public TransferView send(@Valid @RequestBody TransferForm form) {
        return transfers.send(form);
    }

    @PreAuthorize(Roles.OWNER_STAFF)
    @PostMapping("/api/transfers/{number}/receive")
    @Operation(summary = "The receiving branch confirms every gem (ticked or scanned)")
    public TransferView receive(@PathVariable String number, @Valid @RequestBody ReceiveForm form) {
        return transfers.receive(number, form);
    }

    // ------------------------------------------------------------------ stock check

    @PreAuthorize(Roles.OWNER_STAFF)
    @GetMapping("/api/stock-checks/start")
    @Operation(summary = "The gems that should be on the shelf of a branch")
    public CheckStart start(@RequestParam String branch) {
        return checks.start(branch);
    }

    @PreAuthorize(Roles.OWNER_STAFF)
    @GetMapping("/api/stock-checks/scan")
    @Operation(summary = "One scanned tag: found, wrong place or unknown")
    public ScanView scan(@RequestParam String branch, @RequestParam String code) {
        return checks.scan(branch, code);
    }

    @PreAuthorize(Roles.OWNER_STAFF)
    @PostMapping("/api/stock-checks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Finish the check: saves found / missing / wrong place / unknown and tells the owner")
    public CheckView finish(@Valid @RequestBody CheckForm form) {
        return checks.finish(form);
    }

    @PreAuthorize(Roles.OWNER_STAFF)
    @GetMapping("/api/stock-checks")
    @Operation(summary = "Previous checks, newest first")
    public List<CheckView> previous() {
        return checks.previous();
    }

    // ------------------------------------------------------------------ value

    @PreAuthorize(Roles.OWNER)
    @GetMapping("/api/stock/value")
    @Operation(summary = "Stock value at cost and selling price, by gem type, product type and branch (owner only)")
    public StockValue value() {
        return values.value();
    }

    // ------------------------------------------------------------------ corrections

    @PreAuthorize(Roles.OWNER_STAFF)
    @GetMapping("/api/stock-corrections")
    @Operation(summary = "Correction history, newest first")
    public List<CorrectionView> corrections() {
        return corrections.list();
    }

    @PreAuthorize(Roles.OWNER_STAFF)
    @PostMapping("/api/stock-corrections")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ask the owner to correct a gem's status, branch or carat (nothing changes until approved)")
    public CorrectionView requestCorrection(@Valid @RequestBody CorrectionForm form) {
        return corrections.request(form);
    }
}
