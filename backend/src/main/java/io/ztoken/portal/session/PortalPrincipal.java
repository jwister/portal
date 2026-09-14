package io.ztoken.portal.session;

/** 已解密的上游会话凭据，仅限当前服务端请求生命周期使用。 */
public record PortalPrincipal(String portalSessionId, long userId, String username, String accessToken,
                              String refreshToken, String newApiSessionId, java.time.Instant accessExpiresAt) {

    /** 兼容不涉及续期的既有测试和支付领域对象构造。 */
    public PortalPrincipal(long userId, String username, String accessToken) {
        this("test-portal-session", userId, username, accessToken, "test-refresh-token", "test-session",
                java.time.Instant.now().plusSeconds(900));
    }
}
