package io.ztoken.portal.newapi;

/**
 * 可安全下发给浏览器以启动 OAuth 授权的公开配置。
 *
 * <p>不包含 client secret、token endpoint 或任何管理员配置。</p>
 */
public record OAuthProviderStatus(
        boolean githubEnabled,
        String githubClientId,
        boolean oidcEnabled,
        String oidcClientId,
        String oidcAuthorizationEndpoint,
        String oidcDisplayName) {
}
