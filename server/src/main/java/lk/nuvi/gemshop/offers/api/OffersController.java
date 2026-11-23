package lk.nuvi.gemshop.offers.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.offers.Offers;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferAudience;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferAudienceForm;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferDetail;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferForm;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferList;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferOptions;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferRow;
import lk.nuvi.gemshop.offers.api.OfferDtos.OfferTestResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Offers by email and WhatsApp (prototype email-campaign, whatsapp-campaign, campaign-report; Guide 46, 47, 48). Owner only. */
@RestController
@RequestMapping("/api/offers")
@Tag(name = "Offers")
@PreAuthorize(Roles.OWNER)
public class OffersController {

    private final Offers offers;

    public OffersController(Offers offers) {
        this.offers = offers;
    }

    @GetMapping
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "listOffers", summary = "Offer results: every offer with sent, delivered, opened, clicked, unsubscribed")
    public OfferList list() {
        return offers.list();
    }

    @GetMapping("/options")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "offerOptions", summary = "Countries, gem types, WhatsApp templates and whether email / WhatsApp can send")
    public OfferOptions options() {
        return offers.options();
    }

    @PostMapping("/audience")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "offerAudience", summary = "How many customers will receive it (permission only) and the cost")
    public OfferAudience audience(@Valid @RequestBody OfferAudienceForm form) {
        return offers.audience(form);
    }

    @GetMapping("/{code}")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "getOffer", summary = "One offer (to edit a draft)")
    public OfferDetail get(@PathVariable String code) {
        return offers.detail(code);
    }

    @PostMapping("/draft")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "saveOfferDraft", summary = "Save a draft (new, or the draft with this code)")
    public OfferDetail saveDraft(@Valid @RequestBody OfferForm form) {
        return offers.saveDraft(form);
    }

    @DeleteMapping("/{code}")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "deleteOfferDraft", summary = "Delete a draft")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        offers.deleteDraft(code);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/send")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "sendOffer", summary = "Save and send to every customer chosen who gave permission")
    public OfferRow send(@Valid @RequestBody OfferForm form) {
        return offers.send(form);
    }

    @PostMapping("/test")
    @PreAuthorize(Roles.OWNER)
    @Operation(operationId = "sendOfferTest", summary = "Send a test to my email / phone (not counted)")
    public OfferTestResult test(@Valid @RequestBody OfferForm form) {
        return offers.test(form);
    }
}
