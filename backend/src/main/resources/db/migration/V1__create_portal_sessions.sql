CREATE TABLE portal_sessions (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    newapi_user_id BIGINT NOT NULL,
    username VARCHAR(255) NOT NULL,
    encrypted_access_token VARCHAR(4096) NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    revoked_at DATETIME NULL
);

CREATE INDEX idx_portal_sessions_expires_at ON portal_sessions (expires_at);
