package lk.nuvi.gemshop.files.codes;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.nuvi.gemshop.auth.security.Roles;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** QR code and barcode pictures (PNG) for tags and labels printed from the browser. */
@RestController
@RequestMapping("/api/codes")
@Tag(name = "Codes")
public class CodeController {

    private final Codes codes;

    public CodeController(Codes codes) {
        this.codes = codes;
    }

    @PreAuthorize(Roles.ANY)
    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(operationId = "getQrCode", summary = "QR code picture for a text (PNG)")
    public ResponseEntity<byte[]> qr(@RequestParam String text, @RequestParam(defaultValue = "240") int size) {
        return png(codes.qr(text, Math.max(80, Math.min(size, 1000))));
    }

    @PreAuthorize(Roles.ANY)
    @GetMapping(value = "/barcode", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(operationId = "getBarcode", summary = "Code 39 barcode picture (PNG), e.g. for GEM-00131")
    public ResponseEntity<byte[]> barcode(@RequestParam String text, @RequestParam(defaultValue = "360") int width,
            @RequestParam(defaultValue = "80") int height) {
        return png(codes.barcode(text, Math.max(120, Math.min(width, 1600)), Math.max(30, Math.min(height, 400))));
    }

    private static ResponseEntity<byte[]> png(byte[] b) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noCache().cachePrivate()).body(b);
    }
}
