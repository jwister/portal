package io.ztoken.portal.console;

public record LogEntry(
        long id,
        long createdAt,
        int type,
        String content,
        String tokenName,
        String modelName,
        long quota,
        long promptTokens,
        long completionTokens,
        long useTime,
        boolean stream,
        String requestId
) {}
