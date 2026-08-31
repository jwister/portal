package io.ztoken.portal.auth;

import io.ztoken.portal.config.PortalProperties;
import io.ztoken.portal.newapi.NewApiClient;
import io.ztoken.portal.newapi.NewApiLogin;
import io.ztoken.portal.session.PortalSession;
import io.ztoken.portal.session.PortalSessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final NewApiClient newApiClient;
    private final PortalSessionService sessions;
    private final PortalProperties properties;

    public AuthController(NewApiClient newApiClient, PortalSessionService sessions, PortalProperties properties) {
        this.newApiClient = newApiClient;
        this.sessions = sessions;
        this.properties = properties;
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request) {
        NewApiLogin login = newApiClient.login(request.username(), request.password());
        PortalSession session = sessions.create(login.identity(), login.accessToken());
        ResponseCookie cookie = ResponseCookie.from("PORTAL_SESSION", session.getId())
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(properties.getSessionTtl())
                .build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }
}
