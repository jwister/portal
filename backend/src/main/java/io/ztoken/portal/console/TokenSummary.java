package io.ztoken.portal.console;

public record TokenSummary(long id, String name, boolean enabled, long remainingQuota) {
}
