package io.ztoken.portal.auth;

import io.ztoken.portal.config.PortalProperties;
import io.ztoken.portal.newapi.NewApiClient;
import io.ztoken.portal.newapi.NewApiLogin;
import io.ztoken.portal.newapi.OAuthProviderStatus;
import io.ztoken.portal.session.PortalSession;
import io.ztoken.portal.session.PortalSessionService;
import io.ztoken.portal.session.PortalPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final NewApiClient newApiClient;
    private final PortalSessionService sessions;
    private final PortalProperties properties;
    private final CaptchaService captcha;

    public AuthController(NewApiClient newApiClient, PortalSessionService sessions, PortalProperties properties, CaptchaService captcha) {
        this.newApiClient = newApiClient;
        this.sessions = sessions;
        this.properties = properties;
        this.captcha = captcha;
    }

    @org.springframework.web.bind.annotation.GetMapping("/captcha")
    public CaptchaResponse captcha() { return captcha.create(); }

    @org.springframework.web.bind.annotation.GetMapping("/verification")
    public ResponseEntity<Void> verification(@org.springframework.web.bind.annotation.RequestParam String email,
                                             @org.springframework.web.bind.annotation.RequestParam String captchaId,
                                             @org.springframework.web.bind.annotation.RequestParam String captchaCode) {
        if (!captcha.verifyAndConsume(captchaId, captchaCode)) throw new IllegalArgumentException("Captcha is invalid or expired");
        newApiClient.sendEmailVerification(email);
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/login", "/sign-in"})
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request) {
        NewApiLogin login = newApiClient.login(request.username(), request.password());
        return withPortalSession(login);
    }

    /** 仅向浏览器公开启动 GitHub 或 Google OIDC 所需的非敏感配置。 */
    @GetMapping("/oauth/providers")
    public OAuthProviderStatus oauthProviders() {
        return newApiClient.getOAuthProviderStatus();
    }

    /** 使用 NewAPI 已有的短期 state，维持其 OAuth CSRF 防护与一次性消费语义。 */
    @PostMapping("/oauth/{provider}/state")
    public Map<String, String> createOAuthState(@PathVariable String provider) {
        requireSupportedOAuthProvider(provider);
        return Map.of("state", newApiClient.createOAuthState(provider));
    }

    /** 完成 OAuth 后仅建立 Portal 会话；NewAPI 登录包不会暴露给浏览器。 */
    @PostMapping("/oauth/{provider}/complete")
    public ResponseEntity<Void> completeOAuth(@PathVariable String provider,
                                              @Valid @RequestBody OAuthCompleteRequest request) {
        requireSupportedOAuthProvider(provider);
        return withPortalSession(newApiClient.completeOAuth(provider, request.toCallback()));
    }

    /** 密码与 OAuth 登录共用同一套受保护 cookie 属性，避免认证路径产生会话差异。 */
    private ResponseEntity<Void> withPortalSession(NewApiLogin login) {
        PortalSession session = sessions.create(login.identity(), login.accessToken());
        ResponseCookie cookie = ResponseCookie.from("PORTAL_SESSION", session.getId())
                .httpOnly(true)
                .secure(properties.isSessionSecureCookie())
                .sameSite("Lax")
                .path("/")
                .maxAge(properties.getSessionTtl())
                .build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }

    /** Portal 只接入 NewAPI 已配置的 GitHub 与 OIDC 两种 OAuth provider。 */
    private void requireSupportedOAuthProvider(String provider) {
        if (!"github".equals(provider) && !"oidc".equals(provider)) {
            throw new IllegalArgumentException("Unsupported OAuth provider");
        }
    }

    @PostMapping({"/register", "/sign-up"})
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        newApiClient.register(request.username(), request.email(), request.password(), request.verificationCode());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthProfile currentProfile(@CookieValue(value = "PORTAL_SESSION", required = false) String sessionId) {
        PortalPrincipal principal = sessions.require(sessionId);
        return new AuthProfile(principal.userId(), principal.username());
    }

    @GetMapping("/status")
    public AuthStatus status(@CookieValue(value = "PORTAL_SESSION", required = false) String sessionId) {
        if (sessionId == null) {
            return new AuthStatus(false, null);
        }
        try {
            PortalPrincipal principal = sessions.require(sessionId);
            return new AuthStatus(true, new AuthProfile(principal.userId(), principal.username()));
        } catch (io.ztoken.portal.session.UnauthenticatedException exception) {
            return new AuthStatus(false, null);
        }
    }

    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(
            @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId) {
        sessions.revoke(sessionId);
        ResponseCookie cookie = ResponseCookie.from("PORTAL_SESSION", "")
                .httpOnly(true)
                .secure(properties.isSessionSecureCookie())
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).build();
    }
}
