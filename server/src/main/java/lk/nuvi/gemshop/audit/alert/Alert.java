package lk.nuvi.gemshop.audit.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.settings.branch.Branch;

/** One owner alert (K2): "Sale INV-000138 · LKR 352,000 · France". Read once the owner opens or ticks it. */
@Entity
@Table(name = "alert")
public class Alert extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private Instant at;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private AlertType type;

    @Column(nullable = false, updatable = false, length = 300)
    private String text;

    /** App page to open, e.g. {@code /sell/invoices/INV-000138}. */
    @Column(updatable = false, length = 300)
    private String link;

    /** Empty = whole shop (e.g. the NGJA licence reminder). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", updatable = false)
    private Branch branch;

    private Instant readAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "read_by")
    private UserAccount readBy;

    protected Alert() {}

    public Alert(Instant at, AlertType type, String text, String link, Branch branch) {
        this.at = at;
        this.type = type;
        this.text = text.length() <= 300 ? text : text.substring(0, 299) + "…";
        this.link = link;
        this.branch = branch;
    }

    public void markRead(UserAccount by, Instant at) {
        if (readAt == null) {
            this.readAt = at;
            this.readBy = by;
        }
    }

    public boolean isRead() { return readAt != null; }
    public Instant getAt() { return at; }
    public AlertType getType() { return type; }
    public String getText() { return text; }
    public String getLink() { return link; }
    public Branch getBranch() { return branch; }
    public Instant getReadAt() { return readAt; }
}
