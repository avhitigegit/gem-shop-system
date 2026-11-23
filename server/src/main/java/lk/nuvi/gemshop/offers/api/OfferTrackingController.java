package lk.nuvi.gemshop.offers.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.Base64;
import lk.nuvi.gemshop.auth.security.PublicApi;
import lk.nuvi.gemshop.offers.Offers;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Offer results from the customer's email (no sign-in): the picture pixel counts "opened", the button counts "clicked" and
 * goes on to the shop's website. The token is random and says nothing about the customer.
 */
@RestController
@RequestMapping("/api/public/offers/{token}")
@Tag(name = "Offers")
public class OfferTrackingController {

    private static final byte[] PIXEL = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");

    private final Offers offers;

    public OfferTrackingController(Offers offers) {
        this.offers = offers;
    }

    @GetMapping("/open.gif")
    @PublicApi
    @Operation(operationId = "offerOpened", summary = "The offer email was opened (1×1 picture)")
    public ResponseEntity<byte[]> open(@PathVariable String token) {
        offers.opened(token);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_GIF).cacheControl(CacheControl.noStore()).body(PIXEL);
    }

    @GetMapping("/click")
    @PublicApi
    @Operation(operationId = "offerClicked", summary = "The link in the offer email was clicked: goes on to the shop's website")
    public ResponseEntity<Void> click(@PathVariable String token) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(offers.clicked(token))).build();
    }
}
