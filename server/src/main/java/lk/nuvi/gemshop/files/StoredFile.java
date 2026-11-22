package lk.nuvi.gemshop.files;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.settings.branch.Branch;

/** An uploaded file. The bytes are in {@link FileStorage}; links use {@link #getPublicId()}. */
@Entity
@Table(name = "stored_file")
public class StoredFile extends BaseEntity {

    @Column(nullable = false, updatable = false, unique = true)
    private UUID publicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private FileKind kind;

    @Column(nullable = false, updatable = false, length = 60)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false, length = 255)
    private String originalName;

    @Column(nullable = false, updatable = false, length = 255)
    private String storageKey;

    @Column(updatable = false, length = 255)
    private String thumbKey;

    @Column(updatable = false)
    private Integer width;

    @Column(updatable = false)
    private Integer height;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", updatable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", updatable = false)
    private UserAccount uploadedBy;

    /** E.g. GEM, EXPENSE. Empty until the record it belongs to is saved. */
    @Column(length = 40)
    private String ownerType;

    /** E.g. GEM-00131. */
    @Column(length = 40)
    private String ownerRef;

    protected StoredFile() {}

    StoredFile(UUID publicId, FileKind kind, String contentType, long sizeBytes, String originalName, String storageKey,
            String thumbKey, Integer width, Integer height, Branch branch, UserAccount uploadedBy) {
        this.publicId = publicId;
        this.kind = kind;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.originalName = originalName;
        this.storageKey = storageKey;
        this.thumbKey = thumbKey;
        this.width = width;
        this.height = height;
        this.branch = branch;
        this.uploadedBy = uploadedBy;
    }

    void attach(String ownerType, String ownerRef) {
        this.ownerType = ownerType;
        this.ownerRef = ownerRef;
    }

    public boolean isAttached() { return ownerType != null; }
    public UUID getPublicId() { return publicId; }
    public FileKind getKind() { return kind; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getOriginalName() { return originalName; }
    public String getStorageKey() { return storageKey; }
    public String getThumbKey() { return thumbKey; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public Branch getBranch() { return branch; }
    public UserAccount getUploadedBy() { return uploadedBy; }
    public String getOwnerType() { return ownerType; }
    public String getOwnerRef() { return ownerRef; }
}
