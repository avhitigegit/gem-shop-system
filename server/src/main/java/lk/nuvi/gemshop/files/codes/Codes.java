package lk.nuvi.gemshop.files.codes;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code39Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import lk.nuvi.gemshop.common.error.ApiException;
import org.springframework.stereotype.Component;

/**
 * QR codes and barcodes for printable gem tags (C1, ZXing). PNG pictures, also for PDFs.
 * <ul>
 *   <li>Barcode: Code 39 (as on the prototype tags) – read by the USB scanner at the till; letters, digits and "-".</li>
 *   <li>QR: any short text, e.g. the gem code or a link to the shop certificate.</li>
 * </ul>
 */
@Component
public class Codes {

    public static final int MAX_TEXT = 300;

    /** Square QR code, {@code size} pixels, with a small white border. */
    public byte[] qr(String text, int size) {
        check(text);
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1,
                            EncodeHintType.CHARACTER_SET, "UTF-8"));
            return png(m);
        } catch (WriterException e) {
            throw ApiException.badRequest("CODE_TEXT", "This text cannot be made into a QR code.");
        }
    }

    /** Code 39 barcode (e.g. GEM-00131), {@code width} × {@code height} pixels. */
    public byte[] barcode(String text, int width, int height) {
        check(text);
        String t = text.toUpperCase(Locale.ROOT);
        if (!t.matches("[0-9A-Z. $/+%-]{1,40}")) {
            throw ApiException.badRequest("CODE_TEXT", "A barcode can only have letters, numbers and '-' (at most 40).");
        }
        try {
            BitMatrix m = new Code39Writer().encode(t, BarcodeFormat.CODE_39, width, height, Map.of(EncodeHintType.MARGIN, 8));
            return png(m);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("CODE_TEXT", "This text cannot be made into a barcode.");
        }
    }

    private static void check(String text) {
        if (text == null || text.isBlank()) {
            throw ApiException.badRequest("CODE_TEXT", "Text is required.");
        }
        if (text.length() > MAX_TEXT) {
            throw ApiException.badRequest("CODE_TEXT", "Text is too long (at most " + MAX_TEXT + " characters).");
        }
    }

    private static byte[] png(BitMatrix m) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(m, "PNG", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
