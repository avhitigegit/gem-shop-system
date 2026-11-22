package lk.nuvi.gemshop.files.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lk.nuvi.gemshop.auth.security.PublicApi;
import lk.nuvi.gemshop.auth.security.Roles;
import lk.nuvi.gemshop.files.FileKind;
import lk.nuvi.gemshop.files.FileService;
import lk.nuvi.gemshop.files.FileService.Content;
import lk.nuvi.gemshop.files.api.FileDtos.FileView;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Photo and document uploads (photo / scan boxes on the gem, purchase, expense and certificate screens)
 * and the private links to open them.
 */
@RestController
@RequestMapping("/api/files")
@Tag(name = "Files")
public class FileController {

    private final FileService files;

    public FileController(FileService files) {
        this.files = files;
    }

    @PreAuthorize(Roles.ANY)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "uploadFile", summary = "Upload a photo or PDF (max 10 MB). Photos are made smaller (about 300 KB) and get a thumbnail")
    public FileView upload(@RequestParam FileKind kind, @RequestPart("file") MultipartFile file) throws IOException {
        return files.upload(kind, file.getOriginalFilename(), file.getBytes());
    }

    @PreAuthorize(Roles.ANY)
    @GetMapping("/{id}")
    @Operation(operationId = "getFile", summary = "One file with fresh links")
    public FileView get(@PathVariable UUID id) {
        return files.get(id);
    }

    @PreAuthorize(Roles.ANY)
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "discardFile", summary = "Remove my upload before the form is saved")
    public void discard(@PathVariable UUID id) {
        files.discard(id);
    }

    /** The private link itself: works without login, but only with the right signature and before it expires. */
    @PublicApi
    @GetMapping("/{id}/content")
    @Operation(operationId = "openFile", summary = "Open a file with a private link (from url / thumbUrl)")
    public ResponseEntity<InputStreamResource> content(@PathVariable UUID id,
            @Parameter(description = "thumb = the small picture") @RequestParam(required = false) String v,
            @RequestParam long exp, @RequestParam String sig,
            @Parameter(description = "true = save instead of open") @RequestParam(defaultValue = "false") boolean download) {
        Content c = files.open(id, v, exp, sig);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.parseMediaType(c.contentType()));
        if (c.size() >= 0) {
            h.setContentLength(c.size());
        }
        h.setContentDisposition((download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(c.name(), StandardCharsets.UTF_8).build());
        h.setCacheControl("private, max-age=" + c.secondsLeft());
        h.set("X-Content-Type-Options", "nosniff");
        return new ResponseEntity<>(new InputStreamResource(c.in()), h, HttpStatus.OK);
    }
}
