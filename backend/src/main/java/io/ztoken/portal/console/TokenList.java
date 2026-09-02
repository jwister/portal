package io.ztoken.portal.console;

import java.util.List;

public record TokenList(int page, int pageSize, long total, List<TokenSummary> items) {
}
