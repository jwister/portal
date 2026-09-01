package io.ztoken.portal.auth;

public record AuthStatus(boolean authenticated, AuthProfile profile) {
}
