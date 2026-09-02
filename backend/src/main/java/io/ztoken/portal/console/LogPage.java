package io.ztoken.portal.console;

import java.util.List;

public record LogPage(int page, int pageSize, long total, List<LogEntry> items) {}
