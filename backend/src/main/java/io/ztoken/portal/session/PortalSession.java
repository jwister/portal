package io.ztoken.portal.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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

    @Lob
    @Column(name = "encrypted_access_token", nullable = false)
    private String encryptedAccessToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected PortalSession() {
    }

    public PortalSession(String id, long newApiUserId, String username, String encryptedAccessToken, Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.newApiUserId = newApiUserId;
        this.username = username;
        this.encryptedAccessToken = encryptedAccessToken;
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

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
