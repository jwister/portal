package io.ztoken.portal.newapi;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** 上游明确返回 429，调用方应等待而非自动重放一次性授权或写操作。 */
public class NewApiRateLimitException extends NewApiException {
    private final Integer retryAfterSeconds;

    /** 仅接受标准秒数或 HTTP 日期，最多等待一天，拒绝将任意上游头值反射给浏览器。 */
    public NewApiRateLimitException(String retryAfter, Clock clock) {
        super("NewAPI request was rate limited");
        Integer seconds = null;
        if (retryAfter != null && retryAfter.length() <= 64) {
            try {
                long value = retryAfter.matches("[0-9]{1,10}") ? Long.parseLong(retryAfter)
                        : Duration.between(clock.instant(), ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds();
                if (value >= 0) seconds = (int) Math.min(value, 86400);
            } catch (NumberFormatException | DateTimeParseException ignored) {
                // 非法或已过期等待头不向外转发，前端使用保守的短暂等待提示。
            }
        }
        this.retryAfterSeconds = seconds;
    }

    public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
}
