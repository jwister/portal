package io.ztoken.portal.console;

import io.ztoken.portal.newapi.NewApiClient;
import io.ztoken.portal.payment.config.PaymentProperties;
import io.ztoken.portal.session.PortalSessionService;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console")
public class DashboardController {

    private final NewApiClient newApiClient;
    private final PortalSessionService sessions;
    private final PaymentProperties paymentProperties;

    public DashboardController(NewApiClient newApiClient, PortalSessionService sessions, PaymentProperties paymentProperties) {
        this.newApiClient = newApiClient;
        this.sessions = sessions;
        this.paymentProperties = paymentProperties;
    }

    @GetMapping("/dashboard")
    public DashboardSummary dashboard(@CookieValue(value = "PORTAL_SESSION", required = false) String sessionId) {
        DashboardSummary summary = sessions.withAuthenticatedPrincipal(sessionId, newApiClient::getDashboard);
        return new DashboardSummary(summary.availableQuota(), summary.usedQuota(), summary.requestCount(),
                summary.tokenUsage(), paymentProperties.getQuotaPerUsd(), paymentProperties.isEnabled());
    }

    /**
     * 仅接受产品定义的两个统计区间，防止浏览器借由 BFF 发送任意范围的上游数据查询。
     */
    @GetMapping("/dashboard/analytics")
    public DashboardAnalytics analytics(@CookieValue(value = "PORTAL_SESSION", required = false) String sessionId,
                                        @RequestParam(defaultValue = "30d") String range) {
        int rangeDays = switch (range) {
            case "7d" -> 7;
            case "30d" -> 30;
            default -> throw new IllegalArgumentException("不支持的统计时间范围");
        };
        return sessions.withAuthenticatedPrincipal(sessionId,
                principal -> newApiClient.getDashboardAnalytics(principal, rangeDays));
    }
}
