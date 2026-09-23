package io.ztoken.portal.console;

public record DashboardSummary(
        long availableQuota,
        long usedQuota,
        long requestCount,
        Long tokenUsage,
        long quotaPerUsd,
        boolean enableRecharge
) {
}
