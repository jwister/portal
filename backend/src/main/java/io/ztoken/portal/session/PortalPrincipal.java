package io.ztoken.portal.session;

public record PortalPrincipal(long userId, String username, String accessToken) {
}
