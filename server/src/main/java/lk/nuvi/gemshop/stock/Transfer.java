package lk.nuvi.gemshop.stock;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.gems.Gem;
import lk.nuvi.gemshop.settings.branch.Branch;

/**
 * Gems sent to the other branch (BRD D3). They are "On the way" until the receiving branch confirms each one; only then
 * do they count at the new branch (rule 9).
 */
@Entity
@Table(name = "stock_transfer")
public class Transfer extends BaseEntity {

    public enum Status { IN_TRANSIT, RECEIVED }

    @Column(nullable = false, length = 20, updatable = false)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_branch_id")
    private Branch from;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_branch_id")
    private Branch to;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Status status = Status.IN_TRANSIT;

    @Column(nullable = false)
    private Instant sentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by")
    private UserAccount sentBy;

    private Instant receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private UserAccount receivedBy;

    @Column(length = 300)
    private String notes;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<TransferItem> items = new ArrayList<>();

    protected Transfer() {}

    public Transfer(String number, Branch from, Branch to, Instant sentAt, UserAccount sentBy, String notes) {
        this.number = number;
        this.from = from;
        this.to = to;
        this.sentAt = sentAt;
        this.sentBy = sentBy;
        this.notes = notes;
    }

    public void add(Gem gem) {
        items.add(new TransferItem(this, items.size() + 1, gem));
    }

    public void received(Instant at, UserAccount by) {
        this.status = Status.RECEIVED;
        this.receivedAt = at;
        this.receivedBy = by;
    }

    public String getNumber() { return number; }
    public Branch getFrom() { return from; }
    public Branch getTo() { return to; }
    public Status getStatus() { return status; }
    public Instant getSentAt() { return sentAt; }
    public UserAccount getSentBy() { return sentBy; }
    public Instant getReceivedAt() { return receivedAt; }
    public UserAccount getReceivedBy() { return receivedBy; }
    public String getNotes() { return notes; }
    public List<TransferItem> getItems() { return items; }
}
