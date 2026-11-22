package lk.nuvi.gemshop.auth.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lk.nuvi.gemshop.auth.user.UserAccount;
import lk.nuvi.gemshop.common.persistence.BaseEntity;

/** A signed-in device. The browser keeps the token in a cookie; only its hash is stored here. */
@Entity
@Table(name = "refresh_token")
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** "Keep me signed in on this device". */
    @Column(nullable = false)
    private boolean remember;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant lastUsedAt;

    private Instant revokedAt;

    /** true = this token was swapped for a new one (using it again means it was copied). */
    @Column(nullable = false)
    private boolean replaced;

    @Column(length = 255)
    private String device;

    protected RefreshToken() {}

    public RefreshToken(UserAccount user, String tokenHash, boolean remember, Instant expiresAt, Instant now, String device) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.remember = remember;
        this.expiresAt = expiresAt;
        this.lastUsedAt = now;
        this.device = device == null ? null : device.substring(0, Math.min(255, device.length()));
    }

    public UserAccount getUser() { return user; }
    public boolean isRemember() { return remember; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public boolean isReplaced() { return replaced; }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now, boolean replaced) {
        if (revokedAt == null) {
            revokedAt = now;
        }
        this.replaced = this.replaced || replaced;
    }
}
