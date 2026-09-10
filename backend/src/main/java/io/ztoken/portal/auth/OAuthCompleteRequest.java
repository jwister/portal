package io.ztoken.portal.auth;

import io.ztoken.portal.newapi.OAuthCallback;
import jakarta.validation.constraints.NotBlank;

/** OAuth 授权服务回调至 Portal 后允许交给 NewAPI 的标准字段。 */
public record OAuthCompleteRequest(
        String code,
        @NotBlank String state,
        String error,
        String errorDescription) {

    /** 回调必须包含成功授权码或 OAuth 服务明确返回的错误，避免无效上游调用。 */
    public OAuthCallback toCallback() {
        if ((code == null || code.isBlank()) && (error == null || error.isBlank())) {
            throw new IllegalArgumentException("OAuth code or error is required");
        }
        return new OAuthCallback(code, state, error, errorDescription);
    }
}
