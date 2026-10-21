package io.ztoken.portal.auth;

/**
 * 专供 SSO 与内部应用获取当前会话用户基础身份信息的数据对象。
 */
public record SessionUserInfo(long userId, String username, String email) {
}
