package io.ztoken.portal.console;

public record TokenSummary(
        long id,
        String name,
        boolean enabled,
        long remainingQuota,
        long usedQuota,
        boolean unlimited,
        long expiredTime,
        String maskedKey
) {
}
