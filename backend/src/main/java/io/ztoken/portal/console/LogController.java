package io.ztoken.portal.console;

import io.ztoken.portal.newapi.NewApiClient;
import io.ztoken.portal.session.PortalSessionService;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console/logs")
public class LogController {
    private final NewApiClient client;
    private final PortalSessionService sessions;
    public LogController(NewApiClient client, PortalSessionService sessions) { this.client = client; this.sessions = sessions; }
    @GetMapping
    public LogPage logs(@CookieValue(value="PORTAL_SESSION", required=false) String sessionId,
                        @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="50") int pageSize,
                        @RequestParam(required=false) Long startTimestamp, @RequestParam(required=false) Long endTimestamp,
                        @RequestParam(required=false) String modelName, @RequestParam(required=false) String tokenName,
                        @RequestParam(required=false) Integer type) {
        return client.getLogs(sessions.require(sessionId), new LogQuery(page, pageSize, startTimestamp, endTimestamp, modelName, tokenName, type));
    }
    @GetMapping("/stats")
    public LogStats stats(@CookieValue(value="PORTAL_SESSION", required=false) String sessionId,
                          @RequestParam(required=false) Long startTimestamp, @RequestParam(required=false) Long endTimestamp,
                          @RequestParam(required=false) String modelName, @RequestParam(required=false) String tokenName,
                          @RequestParam(required=false) Integer type) {
        return client.getLogStats(sessions.require(sessionId), new LogQuery(1, LogQuery.MAX_PAGE_SIZE, startTimestamp, endTimestamp, modelName, tokenName, type));
    }
}
