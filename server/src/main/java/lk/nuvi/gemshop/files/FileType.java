package lk.nuvi.gemshop.files;

import java.util.Arrays;

/**
 * File types the shop accepts, recognised from the first bytes of the file – not from the name or what the
 * browser says, so a renamed program or web page cannot be uploaded as a "photo".
 */
public enum FileType {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    PDF("application/pdf", "pdf");

    private final String contentType;
    private final String extension;

    FileType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public boolean isImage() {
        return this != PDF;
    }

    /** The type of these bytes, or null when it is none of the accepted types (e.g. HEIC, Word, a program). */
    public static FileType detect(byte[] b) {
        if (starts(b, 0xFF, 0xD8, 0xFF)) {
            return JPEG;
        }
        if (starts(b, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return PNG;
        }
        if (b.length >= 12 && starts(b, 'R', 'I', 'F', 'F') && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return WEBP;
        }
        if (starts(b, '%', 'P', 'D', 'F', '-')) {
            return PDF;
        }
        return null;
    }

    /** True for iPhone photos in HEIC format (so the message can say what to do). */
    public static boolean isHeic(byte[] b) {
        if (b.length < 12 || !(b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p')) {
            return false;
        }
        String brand = new String(Arrays.copyOfRange(b, 8, 12), java.nio.charset.StandardCharsets.US_ASCII);
        return brand.startsWith("hei") || brand.startsWith("hev") || brand.equals("mif1") || brand.equals("msf1");
    }

    private static boolean starts(byte[] b, int... sig) {
        if (b.length < sig.length) {
            return false;
        }
        for (int i = 0; i < sig.length; i++) {
            if ((b[i] & 0xFF) != sig[i]) {
                return false;
            }
        }
        return true;
    }
}
