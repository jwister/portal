package io.ztoken.portal.console;

import io.ztoken.portal.newapi.NewApiClient;
import io.ztoken.portal.newapi.NewApiUnsupportedException;
import io.ztoken.portal.session.PortalPrincipal;
import io.ztoken.portal.session.PortalSessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/console/tokens")
public class TokenController {

    private final NewApiClient newApiClient;
    private final PortalSessionService sessions;

    public TokenController(NewApiClient newApiClient, PortalSessionService sessions) {
        this.newApiClient = newApiClient;
        this.sessions = sessions;
    }

    @GetMapping
    public TokenList list(@CookieValue(value = "PORTAL_SESSION", required = false) String sessionId) {
        return newApiClient.getTokens(sessions.require(sessionId));
    }

    @PostMapping
    public ResponseEntity<Void> create(@RequestBody TokenWriteRequest request,
                                       @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId) {
        newApiClient.createToken(sessions.require(sessionId), request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public TokenSummary update(@PathVariable long id, @RequestBody TokenWriteRequest request,
                               @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId) {
        return newApiClient.updateToken(sessions.require(sessionId), id, request);
    }

    @PutMapping("/{id}/status")
    public TokenSummary status(@PathVariable long id, @RequestBody TokenStatusRequest request,
                               @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId) {
        return newApiClient.updateTokenStatus(sessions.require(sessionId), id, request.enabled());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id,
                                       @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId) {
        newApiClient.deleteToken(sessions.require(sessionId), id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/key")
    public TokenKey key(@PathVariable long id,
                        @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId) {
        return newApiClient.getTokenKey(sessions.require(sessionId), id);
    }

    @GetMapping("/{id}/usage")
    public void usage(@PathVariable long id,
                      @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId) {
        sessions.require(sessionId);
        throw new NewApiUnsupportedException();
    }
}
