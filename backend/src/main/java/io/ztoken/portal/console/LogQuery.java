package io.ztoken.portal.console;

/**
 * User-supplied log query/filter parameters. Fields are the only filter knobs the
 * portal exposes; {@code pageSize} is capped at NewAPI-safe maximum of 50.
 */
public record LogQuery(
        int page,
        int pageSize,
        Long startTimestamp,
        Long endTimestamp,
        String modelName,
        String tokenName,
        Integer type
) {
    public static final int MAX_PAGE_SIZE = 50;

    public int page() {
        return page < 0 ? 0 : page;
    }

    public int pageSize() {
        if (pageSize <= 0) {
            return MAX_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}