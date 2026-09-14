package io.ztoken.portal.newapi;

import io.ztoken.portal.session.NewApiIdentity;

import java.time.Instant;

/** 登录响应中所有凭据均只在 Portal 后端流转，绝不返回浏览器。 */
public record NewApiLogin(NewApiIdentity identity, String accessToken, String refreshToken, String sessionId,
                          Instant accessExpiresAt) {

    /** 仅兼容旧单元测试的构造方式；真实登录响应必须包含可续期的完整会话。 */
    public NewApiLogin(NewApiIdentity identity, String accessToken) {
        this(identity, accessToken, "test-refresh-token", "test-session", Instant.now().plusSeconds(900));
    }
}
