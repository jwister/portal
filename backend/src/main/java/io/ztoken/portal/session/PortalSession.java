package io.ztoken.portal.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "portal_sessions")
public class PortalSession {

    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "newapi_user_id", nullable = false, updatable = false)
    private long newApiUserId;

    @Column(nullable = false, length = 255)
    private String username;

    @Column(name = "encrypted_access_token", nullable = false, length = 4096)
    private String encryptedAccessToken;

    @Column(name = "encrypted_refresh_token", length = 4096)
    private String encryptedRefreshToken;

    @Column(name = "newapi_session_id", length = 64)
    private String newApiSessionId;

    @Column(name = "access_expires_at")
    private Instant accessExpiresAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected PortalSession() {
    }

    public PortalSession(String id, long newApiUserId, String username, String encryptedAccessToken,
                         String encryptedRefreshToken, String newApiSessionId, Instant accessExpiresAt,
                         Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.newApiUserId = newApiUserId;
        this.username = username;
        this.encryptedAccessToken = encryptedAccessToken;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.newApiSessionId = newApiSessionId;
        this.accessExpiresAt = accessExpiresAt;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public long getNewApiUserId() {
        return newApiUserId;
    }

    public String getUsername() {
        return username;
    }

    public String getEncryptedAccessToken() {
        return encryptedAccessToken;
    }

    public String getEncryptedRefreshToken() { return encryptedRefreshToken; }

    public String getNewApiSessionId() { return newApiSessionId; }

    public Instant getAccessExpiresAt() { return accessExpiresAt; }

    /** refresh token 轮换后，三个上游凭据必须作为同一个原子会话状态更新。 */
    public void replaceNewApiCredentials(String encryptedAccessToken, String encryptedRefreshToken,
                                         String newApiSessionId, Instant accessExpiresAt) {
        this.encryptedAccessToken = encryptedAccessToken;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.newApiSessionId = newApiSessionId;
        this.accessExpiresAt = accessExpiresAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revokeAt(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
