package lk.nuvi.gemshop.messaging.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lk.nuvi.gemshop.auth.security.PublicApi;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.customers.CustomerBook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * WhatsApp Business webhook (Meta Cloud API): messages customers send to the shop's WhatsApp number. A customer who replies
 * STOP gets no more offers or birthday wishes (BRD J2, rule 8). Off until {@code app.whatsapp.*} is set (P6-03); every call
 * must carry Meta's signature, so nobody else can unsubscribe customers.
 */
@RestController
@RequestMapping("/api/public/whatsapp/webhook")
@Tag(name = "Messages")
public class WhatsappWebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsappWebhookController.class);

    private final CustomerBook customers;
    private final lk.nuvi.gemshop.offers.Offers offers;
    private final AppProperties.Whatsapp cfg;
    private final ObjectMapper json;

    public WhatsappWebhookController(CustomerBook customers, lk.nuvi.gemshop.offers.Offers offers, AppProperties app, ObjectMapper json) {
        this.customers = customers;
        this.offers = offers;
        this.cfg = app.whatsapp();
        this.json = json;
    }

    /** Meta checks the webhook once when it is set up: echo the challenge if the word matches. */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @PublicApi
    @Operation(operationId = "whatsappWebhookVerify", summary = "Meta webhook check (hub.challenge)")
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (blank(cfg.verifyToken()) || !"subscribe".equals(mode) || token == null
                || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), cfg.verifyToken().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(challenge == null ? "" : challenge);
    }

    /** Messages coming in. Only signed calls are read; STOP unsubscribes the customer with that number. */
    @PostMapping
    @PublicApi
    @Operation(operationId = "whatsappWebhook", summary = "Messages from WhatsApp (STOP = unsubscribe)")
    public ResponseEntity<Void> receive(@RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody(required = false) byte[] body) {
        if (blank(cfg.appSecret())) {
            return ResponseEntity.notFound().build();
        }
        byte[] raw = body == null ? new byte[0] : body;
        if (!signed(raw, signature)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        JsonNode root;
        try {
            root = json.readTree(raw);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
        int n = 0;
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                for (JsonNode st : change.path("value").path("statuses")) {   // delivered / read of an offer (P4-05)
                    String token = st.path("biz_opaque_callback_data").asString("");
                    if (!token.isEmpty()) {
                        offers.whatsappStatus(token, st.path("status").asString(""));
                    }
                }
                for (JsonNode m : change.path("value").path("messages")) {
                    String text = switch (m.path("type").asString("")) {
                        case "text" -> m.path("text").path("body").asString("");
                        case "button" -> m.path("button").path("text").asString("");
                        default -> "";
                    };
                    n += customers.whatsappMessage(m.path("from").asString(""), text);
                }
            }
        }
        if (n > 0) {
            LOG.info("WhatsApp STOP: {} customer(s) unsubscribed", n);
        }
        return ResponseEntity.ok().build(); // Meta resends anything that is not 200
    }

    private boolean signed(byte[] body, String header) {
        if (header == null || !header.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cfg.appSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(body);
            byte[] given = HexFormat.of().parseHex(header.substring(7).trim().toLowerCase(java.util.Locale.ROOT));
            return MessageDigest.isEqual(expected, given);
        } catch (IllegalArgumentException e) {
            return false;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
