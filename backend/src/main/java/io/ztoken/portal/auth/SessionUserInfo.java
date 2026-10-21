package io.ztoken.portal.auth;

/**
 * 专供 SSO 与内部应用获取当前会话用户基础身份及可用 API Key 的数据对象。
 */
public record SessionUserInfo(long userId, String username, String email, String apiKey) {

    public SessionUserInfo(long userId, String username, String email) {
        this(userId, username, email, null);
    }
}
