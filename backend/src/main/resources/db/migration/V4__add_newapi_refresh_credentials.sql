ALTER TABLE portal_sessions ADD COLUMN encrypted_refresh_token VARCHAR(4096) NULL;
ALTER TABLE portal_sessions ADD COLUMN newapi_session_id VARCHAR(64) NULL;
ALTER TABLE portal_sessions ADD COLUMN access_expires_at DATETIME NULL;

-- 旧版仅保存 15 分钟 access token，无法安全续期；强制一次重新登录以消除假登录状态。
UPDATE portal_sessions SET expires_at = CURRENT_TIMESTAMP WHERE encrypted_refresh_token IS NULL;
