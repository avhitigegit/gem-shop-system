package lk.nuvi.gemshop.gems;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/** One line in a gem's history: added, edited, price changed, ready to sell, sold, moved … */
@Entity
@Table(name = "gem_history")
public class GemHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gem_id")
    private Gem gem;

    @Column(nullable = false)
    private Instant at;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Column(nullable = false, length = 120)
    private String userName;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 500)
    private String detail;

    protected GemHistory() {}

    public GemHistory(Gem gem, Instant at, UserAccount user, String userName, String action, String detail) {
        this.gem = gem;
        this.at = at;
        this.user = user;
        this.userName = userName;
        this.action = action;
        this.detail = detail == null ? "" : detail.length() > 500 ? detail.substring(0, 500) : detail;
    }

    public Gem getGem() { return gem; }
    public Instant getAt() { return at; }
    public UserAccount getUser() { return user; }
    public String getUserName() { return userName; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
}
