package io.ztoken.portal.console;

public record Profile(
        long id,
        String username,
        String displayName,
        String email,
        String language
) {}
