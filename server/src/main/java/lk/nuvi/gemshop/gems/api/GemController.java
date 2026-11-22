package lk.nuvi.gemshop.gems.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.common.paging.PageResult;
import lk.nuvi.gemshop.gems.CertType;
import java.util.List;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.common.error.ErrorCode;
import lk.nuvi.gemshop.files.Downloads;
import lk.nuvi.gemshop.gems.GemPrints;
import lk.nuvi.gemshop.gems.Gems;
import lk.nuvi.gemshop.gems.api.GemDtos.CertificateView;
import lk.nuvi.gemshop.gems.api.GemDtos.TagPrintForm;
import lk.nuvi.gemshop.gems.api.GemDtos.TagSheet;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import lk.nuvi.gemshop.gems.api.GemDtos.GemForm;
import lk.nuvi.gemshop.gems.api.GemDtos.GemPage;
import lk.nuvi.gemshop.gems.api.GemDtos.GemView;
import lk.nuvi.gemshop.gems.api.GemDtos.NextCode;
import lk.nuvi.gemshop.gems.api.GemDtos.StockSummary;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gems (P2-01): All gems, one gem, add / edit (owner), mark ready to sell. Lists follow the branch in the top bar. */
@RestController
@Tag(name = "Gems")
public class GemController {

    private final Gems gems;
    private final GemPrints prints;

    public GemController(Gems gems, GemPrints prints) {
        this.gems = gems;
        this.prints = prints;
    }

    // ------------------------------------------------------------------ P2-02 tags and shop certificate

    @PreAuthorize(Roles.OWNER_STAFF)
    @GetMapping("/api/gems/tags")
    @Operation(summary = "Tag details for the Print tags screen (codes = GEM-00131,GEM-00132)")
    public TagSheet tags(@RequestParam List<String> codes) {
        return prints.tags(limit(codes));
    }

    @PreAuthorize(Roles.OWNER_STAFF)
    @PostMapping(value = "/api/gems/tags/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Tags as a PDF the size of the label (or an A4 sheet); adds \"Tag printed\" to each gem")
    public ResponseEntity<byte[]> tagsPdf(@Valid @RequestBody TagPrintForm form) {
        return Downloads.pdf(prints.tagsPdf(limit(form.codes()), form.size(), form.showPrice()), Downloads.dated("Gem tags"),
                false);
    }

    @PreAuthorize(Roles.OWNER_SALES)
    @GetMapping("/api/gems/{code}/shop-certificate")
    @Operation(summary = "Shop certificate of a gem (screen)")
    public CertificateView certificate(@PathVariable String code) {
        return prints.certificate(code);
    }

    @PreAuthorize(Roles.OWNER_SALES)
    @GetMapping(value = "/api/gems/{code}/shop-certificate/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Shop certificate PDF (download=true saves it, else opens it)")
    public ResponseEntity<byte[]> certificatePdf(@PathVariable String code, @RequestParam(defaultValue = "false") boolean download) {
        CertificateView c = prints.certificate(code);
        return Downloads.pdf(prints.certificatePdf(code), "Shop certificate " + c.code(), download);
    }

    private static List<String> limit(List<String> codes) {
        if (codes.size() > 200) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Print at most 200 tags at a time.");
        }
        return codes;
    }

    @PreAuthorize(Roles.OWNER_SALES_STAFF_ACCOUNTANT)
    @GetMapping("/api/gems/summary")
    @Operation(summary = "Gems & stock home: counts and the newest gems")
    public StockSummary summary() {
        return gems.summary();
    }

    @PreAuthorize(Roles.OWNER_SALES_STAFF)
    @GetMapping("/api/gems")
    @Operation(summary = "All gems. status: stock (default) | all | IN_STOCK | NOT_READY | …; q: code, name, colour, gem type")
    public GemPage search(@RequestParam(required = false) String gemType, @RequestParam(required = false) String productType,
            @RequestParam(required = false) String status, @RequestParam(required = false) CertType cert,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        return gems.search(new Gems.Filter(gemType, productType, status, cert, q), Math.max(0, page),
                PageResult.size(size, 48, 200));
    }

    @PreAuthorize(Roles.OWNER)
    @GetMapping("/api/gems/next-code")
    @Operation(summary = "The code the next new gem gets (add form)")
    public NextCode nextCode() {
        return new NextCode(gems.nextCode());
    }

    @PreAuthorize(Roles.OWNER_SALES_STAFF)
    @GetMapping("/api/gems/{code}")
    @Operation(summary = "One gem (also a scanned tag: GEM-00125, 125)")
    public GemView view(@PathVariable String code) {
        return gems.view(code);
    }

    @PreAuthorize(Roles.OWNER)
    @PostMapping("/api/gems")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a gem not bought from a supplier (owner)")
    public GemView add(@Valid @RequestBody GemForm form) {
        return gems.add(form);
    }

    @PreAuthorize(Roles.OWNER)
    @PutMapping("/api/gems/{code}")
    @Operation(summary = "Edit a gem and its prices (owner)")
    public GemView edit(@PathVariable String code, @Valid @RequestBody GemForm form) {
        return gems.edit(code, form);
    }

    @PreAuthorize(Roles.OWNER_STAFF)
    @PostMapping("/api/gems/{code}/ready")
    @Operation(summary = "Mark ready to sell (gem was Not ready to sell)")
    public GemView markReady(@PathVariable String code) {
        return gems.markReady(code);
    }
}
