package io.ztoken.portal.console;

import io.ztoken.portal.newapi.NewApiClient;
import io.ztoken.portal.session.PortalPrincipal;
import io.ztoken.portal.session.PortalSessionService;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console")
public class DashboardController {

    private final NewApiClient newApiClient;
    private final PortalSessionService sessions;

    public DashboardController(NewApiClient newApiClient, PortalSessionService sessions) {
        this.newApiClient = newApiClient;
        this.sessions = sessions;
    }

    @GetMapping("/dashboard")
    public DashboardSummary dashboard(@CookieValue(value = "PORTAL_SESSION", required = false) String sessionId) {
        PortalPrincipal principal = sessions.require(sessionId);
        return newApiClient.getDashboard(principal);
    }
}
