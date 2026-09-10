package io.ztoken.portal.newapi;

/**
 * OAuth 授权服务回调给 Portal 的一次性参数。
 *
 * <p>该对象只在 Portal 服务端转发到 NewAPI，不能写入日志或返回浏览器。</p>
 */
public record OAuthCallback(String code, String state, String error, String errorDescription) {
}
