package lk.nuvi.gemshop.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lk.nuvi.gemshop.common.persistence.BaseEntity;
import lk.nuvi.gemshop.gems.Gem;

/** One gem on a transfer. */
@Entity
@Table(name = "stock_transfer_item")
public class TransferItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id")
    private Transfer transfer;

    @Column(nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gem_id")
    private Gem gem;

    protected TransferItem() {}

    TransferItem(Transfer transfer, int lineNo, Gem gem) {
        this.transfer = transfer;
        this.lineNo = lineNo;
        this.gem = gem;
    }

    public Transfer getTransfer() { return transfer; }
    public int getLineNo() { return lineNo; }
    public Gem getGem() { return gem; }
}
