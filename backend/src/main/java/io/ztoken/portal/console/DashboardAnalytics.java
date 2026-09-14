package io.ztoken.portal.console;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 控制台仪表盘的图表专用数据。该对象只保留已聚合的统计结果，避免把 New API 的原始明细和敏感字段交给浏览器。
 */
public record DashboardAnalytics(
        List<DailyUsage> dailyUsage,
        List<ModelUsage> topModels,
        List<TokenUsage> tokenUsage
) {

    /**
     * 合并 Top 5 以外模型时使用的稳定协议值；客户端必须根据自身语言包渲染展示文案，不能把中文文本当作数据标识。
     */
    public static final String OTHER_MODEL_KEY = "__other__";

    public record DailyUsage(String date, long quota, long requestCount) {
    }

    public record ModelUsage(String modelName, long quota) {
    }

    public record TokenUsage(String date, long tokenUsage) {
    }

    /**
     * 将上游的小时明细聚合结果转换为图表序列。每日额度和请求量只返回实际存在的日期；Token 序列按所选范围补齐，
     * 使折线图在某些日期没有调用时仍能正确表现为零，而不是中断或伪造调用数据。
     */
    public static DashboardAnalytics from(Map<LocalDate, DailyAggregate> dailyTotals,
                                          Map<String, Long> modelQuotas,
                                          LocalDate endDate,
                                          int rangeDays) {
        List<DailyUsage> dailyUsage = dailyTotals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DailyUsage(entry.getKey().toString(), entry.getValue().quota(), entry.getValue().requestCount()))
                .toList();

        List<Map.Entry<String, Long>> sortedModels = modelQuotas.entrySet().stream()
                .filter(entry -> entry.getValue() != 0L)
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .toList();
        List<ModelUsage> topModels = new ArrayList<>();
        long otherQuota = 0L;
        for (int index = 0; index < sortedModels.size(); index++) {
            Map.Entry<String, Long> entry = sortedModels.get(index);
            if (index < 5) {
                topModels.add(new ModelUsage(entry.getKey(), entry.getValue()));
            } else {
                otherQuota += entry.getValue();
            }
        }
        if (otherQuota != 0L) {
            topModels.add(new ModelUsage(OTHER_MODEL_KEY, otherQuota));
        }

        // 上游没有任何真实明细时保持空数组，前端据此展示空状态，不能为了图表结构虚构零值数据。
        if (dailyTotals.isEmpty()) {
            return new DashboardAnalytics(dailyUsage, topModels, List.of());
        }
        List<TokenUsage> tokenUsage = new ArrayList<>();
        for (int offset = rangeDays - 1; offset >= 0; offset--) {
            LocalDate date = endDate.minusDays(offset);
            DailyAggregate total = dailyTotals.get(date);
            tokenUsage.add(new TokenUsage(date.toString(), total == null ? 0L : total.tokenUsage()));
        }
        return new DashboardAnalytics(dailyUsage, topModels, tokenUsage);
    }

    /**
     * 单日累计值由 BFF 在读取上游按小时记录时递增，保证额度、请求数和 Token 使用量始终来自同一批真实数据。
     */
    public static final class DailyAggregate {
        private long quota;
        private long requestCount;
        private long tokenUsage;

        public void add(long quota, long requestCount, long tokenUsage) {
            this.quota += quota;
            this.requestCount += requestCount;
            this.tokenUsage += tokenUsage;
        }

        public long quota() {
            return quota;
        }

        public long requestCount() {
            return requestCount;
        }

        public long tokenUsage() {
            return tokenUsage;
        }
    }
}
