package lk.nuvi.gemshop.files;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Answers that download a file: PDF or Excel, named e.g. "Activity log 2026-11-22.xlsx". */
public final class Downloads {

    public static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private Downloads() {}

    public static ResponseEntity<byte[]> pdf(byte[] bytes, String name, boolean download) {
        return file(bytes, MediaType.APPLICATION_PDF, name + ".pdf", download);
    }

    public static ResponseEntity<byte[]> xlsx(byte[] bytes, String name) {
        return file(bytes, XLSX, name + ".xlsx", true);
    }

    /** A ZIP folder (e.g. the tax pack). */
    public static ResponseEntity<byte[]> zip(byte[] bytes, String name) {
        return file(bytes, MediaType.parseMediaType("application/zip"), name + ".zip", true);
    }

    /** "Activity log" → "Activity log 2026-11-22". */
    public static String dated(String name) {
        return name + " " + LocalDate.now(ZoneId.of("Asia/Colombo"));
    }

    private static ResponseEntity<byte[]> file(byte[] bytes, MediaType type, String filename, boolean download) {
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore())
                .header("Content-Disposition", (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }
}
