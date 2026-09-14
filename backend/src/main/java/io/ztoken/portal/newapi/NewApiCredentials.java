package io.ztoken.portal.newapi;

import java.time.Instant;

/** NewAPI 的短期访问令牌及仅由 Portal 服务端保管的续期凭据。 */
public record NewApiCredentials(String accessToken, String refreshToken, String sessionId, Instant accessExpiresAt) {
}
