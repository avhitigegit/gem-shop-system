package lk.nuvi.gemshop.gems.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.common.paging.PageResult;
import lk.nuvi.gemshop.gems.CertType;
import lk.nuvi.gemshop.gems.Gems;
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

    public GemController(Gems gems) {
        this.gems = gems;
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
