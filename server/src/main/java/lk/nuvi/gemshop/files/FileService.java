package lk.nuvi.gemshop.files;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lk.nuvi.gemshop.auth.security.CurrentUser;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.config.AppProperties;
import lk.nuvi.gemshop.common.crypto.FieldCrypto;
import lk.nuvi.gemshop.common.error.ApiException;
import lk.nuvi.gemshop.files.api.FileDtos.FileView;
import lk.nuvi.gemshop.settings.branch.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Uploads (photos, bill photos, certificate scans, logo) and private download links.
 * <ol>
 *   <li>The screen uploads the file first ({@code POST /api/files}) and gets its id and links back.</li>
 *   <li>When the gem / expense / … is saved, the module calls {@link #attach} with the ids.</li>
 *   <li>Uploads that were never attached (form closed without saving) are removed after a day.</li>
 * </ol>
 * Links are private: {@code /api/files/{id}/content?exp=…&sig=…} only works with the right signature and only for
 * {@code app.files.link-minutes}. API answers include fresh links each time.
 */
@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    public static final String THUMB = "thumb";

    private final StoredFileRepository files;
    private final FileStorage storage;
    private final ImageProcessor images;
    private final FieldCrypto crypto;
    private final CurrentUser currentUser;
    private final EntityManager em;
    private final AppProperties.Files props;

    public FileService(StoredFileRepository files, FileStorage storage, ImageProcessor images, FieldCrypto crypto,
            CurrentUser currentUser, EntityManager em, AppProperties app) {
        this.files = files;
        this.storage = storage;
        this.images = images;
        this.crypto = crypto;
        this.currentUser = currentUser;
        this.em = em;
        this.props = app.files();
    }

    // ------------------------------------------------------------------ upload

    @Transactional
    public FileView upload(FileKind kind, String originalName, byte[] bytes) {
        var who = currentUser.require();
        if (bytes == null || bytes.length == 0) {
            throw ApiException.badRequest("FILE_EMPTY", "The file is empty.");
        }
        FileType type = FileType.detect(bytes);
        if (type == null || !kind.accepts().contains(type)) {
            if (FileType.isHeic(bytes)) {
                throw ApiException.badRequest("FILE_TYPE", "iPhone HEIC photos are not supported. Please use JPEG "
                        + "(iPhone: Settings → Camera → Formats → Most Compatible) or take a screenshot of the photo.");
            }
            throw ApiException.badRequest("FILE_TYPE", allowed(kind));
        }
        byte[] main = bytes;
        byte[] thumb = null;
        Integer width = null;
        Integer height = null;
        FileType stored = type;
        if (type.isImage()) {
            try {
                ImageProcessor.Result r = images.process(bytes, kind, type);
                main = r.bytes();
                thumb = r.thumb();
                width = r.width();
                height = r.height();
                stored = r.type();
            } catch (IOException | RuntimeException e) {
                log.info("Picture could not be read: {}", e.getMessage());
                throw ApiException.badRequest("FILE_BROKEN", "This picture cannot be opened. Please take or choose it again.");
            }
        }
        UUID id = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String base = "%d/%02d/%s".formatted(today.getYear(), today.getMonthValue(), id);
        String key = base + "." + stored.extension();
        String thumbKey = thumb == null ? null : base + "-thumb.jpg";
        storage.put(key, main);
        if (thumbKey != null) {
            storage.put(thumbKey, thumb);
        }
        afterRollback(() -> {
            storage.delete(key);
            storage.delete(thumbKey);
        });
        Long branchId = currentUser.scope().branchId();
        StoredFile f = files.save(new StoredFile(id, kind, stored.contentType(), main.length, cleanName(originalName, stored),
                key, thumbKey, width, height, branchId == null ? null : em.getReference(Branch.class, branchId),
                em.getReference(UserAccount.class, who.id())));
        return view(f);
    }

    private static String allowed(FileKind kind) {
        boolean pdf = kind.accepts().contains(FileType.PDF);
        return pdf ? "Please choose a photo (JPEG, PNG or WebP) or a PDF." : "Please choose a photo (JPEG, PNG or WebP).";
    }

    /** "C:\fakepath\IMG_0042.HEIC.jpg" → "IMG_0042.HEIC.jpg"; the extension follows what is stored. */
    static String cleanName(String name, FileType stored) {
        String n = name == null ? "" : name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        int dot = n.lastIndexOf('.');
        String stem = dot > 0 ? n.substring(0, dot) : n;
        if (stem.isBlank()) {
            stem = "file";
        }
        if (stem.length() > 200) {
            stem = stem.substring(0, 200);
        }
        return stem + "." + stored.extension();
    }

    // ------------------------------------------------------------------ for the modules

    /**
     * Links uploads to the record they belong to, e.g. {@code attach(ids, FileKind.PRODUCT_PHOTO, "GEM", "GEM-00131")}.
     * Call it in the transaction that saves the record. Refuses files of another kind, of another branch,
     * or already attached to something else.
     */
    @Transactional
    public List<StoredFile> attach(List<UUID> ids, FileKind kind, String ownerType, String ownerRef) {
        return ids.stream().map(id -> {
            StoredFile f = files.findByPublicId(id).orElseThrow(() -> ApiException.badRequest("FILE_NOT_FOUND",
                    "An uploaded file is missing. Please upload it again."));
            if (f.getKind() != kind) {
                throw ApiException.badRequest("FILE_KIND", "This file was uploaded for something else.");
            }
            if (f.isAttached() && !(ownerType.equals(f.getOwnerType()) && ownerRef.equals(f.getOwnerRef()))) {
                throw ApiException.conflict("FILE_IN_USE", "This file already belongs to " + f.getOwnerRef() + ".");
            }
            if (f.getBranch() != null) {
                currentUser.checkBranch(f.getBranch().getId());
            }
            f.attach(ownerType, ownerRef);
            return f;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<StoredFile> ofOwner(String ownerType, String ownerRef) {
        return files.findByOwnerTypeAndOwnerRefOrderByIdAsc(ownerType, ownerRef);
    }

    /** Files of many records at once (e.g. the first photo of each gem on a page). */
    @Transactional(readOnly = true)
    public List<StoredFile> ofOwners(String ownerType, java.util.Collection<String> ownerRefs) {
        return ownerRefs.isEmpty() ? List.of() : files.findByOwnerTypeAndOwnerRefInOrderByIdAsc(ownerType, ownerRefs);
    }

    /** Removes a file (row now, bytes once the transaction has been saved). */
    @Transactional
    public void remove(StoredFile f) {
        files.delete(f);
        String key = f.getStorageKey();
        String thumbKey = f.getThumbKey();
        afterCommit(() -> {
            storage.delete(key);
            storage.delete(thumbKey);
        });
    }

    // ------------------------------------------------------------------ screens

    @Transactional(readOnly = true)
    public FileView get(UUID id) {
        return view(visible(id));
    }

    /** Remove an upload before saving the form ("✕" on the photo). Attached files are removed by their module. */
    @Transactional
    public void discard(UUID id) {
        StoredFile f = visible(id);
        if (f.isAttached()) {
            throw ApiException.conflict("FILE_IN_USE", "This file belongs to " + f.getOwnerRef() + " – remove it there.");
        }
        var who = currentUser.require();
        if (f.getUploadedBy() == null || f.getUploadedBy().getId() != who.id()) {
            throw ApiException.forbidden("Only the person who uploaded this file can remove it.");
        }
        remove(f);
    }

    private StoredFile visible(UUID id) {
        StoredFile f = files.findByPublicId(id).orElseThrow(() -> ApiException.notFound("File not found."));
        if (f.getBranch() != null) {
            var who = currentUser.require();
            if (!who.role().seesAllBranches() && !f.getBranch().getId().equals(who.branchId())) {
                throw ApiException.notFound("File not found.");
            }
        }
        return f;
    }

    public FileView view(StoredFile f) {
        return new FileView(f.getPublicId(), f.getKind(), f.getContentType(), f.getSizeBytes(), f.getOriginalName(),
                f.getWidth(), f.getHeight(), link(f, false), f.getThumbKey() == null ? null : link(f, true), f.isAttached());
    }

    // ------------------------------------------------------------------ private links

    /** A link that works for {@code app.files.link-minutes}, e.g. for an {@code <img src>} or a PDF download. */
    public String link(StoredFile f, boolean thumb) {
        long exp = Instant.now().plus(props.linkMinutes(), ChronoUnit.MINUTES).getEpochSecond();
        String v = thumb ? THUMB : "";
        return "/api/files/" + f.getPublicId() + "/content?" + (thumb ? "v=thumb&" : "") + "exp=" + exp + "&sig="
                + crypto.sign(signed(f.getPublicId(), v, exp));
    }

    private static String signed(UUID id, String v, long exp) {
        return id + "|" + v + "|" + exp;
    }

    public record Content(InputStream in, String contentType, long size, String name, long secondsLeft) {}

    /** Opens a file for a signed link. Wrong signature, or too late: 403. */
    @Transactional(readOnly = true)
    public Content open(UUID id, String v, long exp, String sig) {
        String variant = THUMB.equals(v) ? THUMB : "";
        if (!crypto.verify(signed(id, variant, exp), sig)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LINK_INVALID", "This link is not valid.");
        }
        long left = exp - Instant.now().getEpochSecond();
        if (left <= 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "LINK_EXPIRED", "This link has expired. Please open the page again.");
        }
        StoredFile f = files.findByPublicId(id).orElseThrow(() -> ApiException.notFound("File not found."));
        boolean thumb = !variant.isEmpty() && f.getThumbKey() != null;
        String key = thumb ? f.getThumbKey() : f.getStorageKey();
        try {
            return new Content(storage.open(key), thumb ? "image/jpeg" : f.getContentType(),
                    thumb ? -1 : f.getSizeBytes(), f.getOriginalName(), left);
        } catch (IOException e) {
            log.warn("File {} is missing on disk ({})", f.getPublicId(), key);
            throw ApiException.notFound("File not found.");
        }
    }

    // ------------------------------------------------------------------ cleanup

    /** Every night: uploads never saved with a record (form closed without saving). */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Colombo")
    @Transactional
    public int removeUnattached() {
        List<StoredFile> old = files.findUnattachedBefore(Instant.now().minus(props.keepUnattachedHours(), ChronoUnit.HOURS));
        old.forEach(this::remove);
        if (!old.isEmpty()) {
            log.info("Removed {} uploads that were never saved with a record", old.size());
        }
        return old.size();
    }

    private static void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }

    private static void afterRollback(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        r.run();
                    }
                }
            });
        }
    }
}
