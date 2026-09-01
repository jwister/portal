package io.ztoken.portal.console;

public record TokenWriteRequest(
        String name,
        boolean unlimited,
        long remainingQuota,
        long expiredTime
) {
}
