package io.ztoken.portal.console;

import io.ztoken.portal.newapi.NewApiClient;
import io.ztoken.portal.session.PortalSessionService;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console/profile")
public class ProfileController {
    private final NewApiClient client;
    private final PortalSessionService sessions;
    public ProfileController(NewApiClient client, PortalSessionService sessions) { this.client = client; this.sessions = sessions; }
    @GetMapping
    public Profile get(@CookieValue(value="PORTAL_SESSION", required=false) String sessionId) {
        return client.getProfile(sessions.require(sessionId));
    }
    @PutMapping
    public Profile update(@RequestBody ProfileUpdateRequest request,
                          @CookieValue(value="PORTAL_SESSION", required=false) String sessionId) {
        return client.updateProfile(sessions.require(sessionId), request);
    }
}
