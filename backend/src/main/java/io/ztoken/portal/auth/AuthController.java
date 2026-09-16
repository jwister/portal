package io.ztoken.portal.auth;

import io.ztoken.portal.config.PortalProperties;
import io.ztoken.portal.newapi.NewApiClient;
import io.ztoken.portal.newapi.NewApiLogin;
import io.ztoken.portal.newapi.NewApiException;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String OAUTH_STATE_COOKIE = "PORTAL_OAUTH_STATE";
    private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(10);

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
    public ResponseEntity<Map<String, String>> createOAuthState(@PathVariable String provider) {
        requireSupportedOAuthProvider(provider);
        String state = newApiClient.createOAuthState(provider);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, oauthStateCookie(state, OAUTH_STATE_TTL).toString())
                .body(Map.of("state", state));
    }

    /** 完成 OAuth 后仅建立 Portal 会话；NewAPI 登录包不会暴露给浏览器。 */
    @PostMapping("/oauth/{provider}/complete")
    public ResponseEntity<Void> completeOAuth(@PathVariable String provider,
                                              @Valid @RequestBody OAuthCompleteRequest request,
                                              @CookieValue(value = OAUTH_STATE_COOKIE, required = false) String browserState,
                                              HttpServletResponse response) {
        requireSupportedOAuthProvider(provider);
        if (browserState == null || !MessageDigest.isEqual(browserState.getBytes(StandardCharsets.UTF_8),
                request.state().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("OAuth state does not match this browser");
        }
        // 无论上游成功或拒绝，均清除绑定 cookie，避免同一 state 被浏览器重复提交。
        response.addHeader(HttpHeaders.SET_COOKIE, oauthStateCookie("", Duration.ZERO).toString());
        return withPortalSession(newApiClient.completeOAuth(provider, request.toCallback()));
    }

    /** OAuth state 同时绑定到当前浏览器，阻止其他浏览器重放授权回调来置换 Portal 会话。 */
    private ResponseCookie oauthStateCookie(String state, Duration maxAge) {
        return ResponseCookie.from(OAUTH_STATE_COOKIE, state)
                .httpOnly(true)
                .secure(properties.isSessionSecureCookie())
                .sameSite("Lax")
                .path("/api/auth/oauth")
                .maxAge(maxAge)
                .build();
    }

    /** 密码与 OAuth 登录共用同一套受保护 cookie 属性，避免认证路径产生会话差异。 */
    private ResponseEntity<Void> withPortalSession(NewApiLogin login) {
        PortalSession session = sessions.create(login);
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
            return sessions.withAuthenticatedPrincipal(sessionId, principal -> {
                io.ztoken.portal.session.NewApiIdentity identity = newApiClient.getSelf(principal);
                return new AuthStatus(true, new AuthProfile(identity.userId(), identity.username()));
            });
        } catch (io.ztoken.portal.session.UnauthenticatedException exception) {
            return new AuthStatus(false, null);
        }
    }

    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(
            @CookieValue(value = "PORTAL_SESSION", required = false) String sessionId) {
        try {
            newApiClient.logout(sessions.require(sessionId));
        } catch (io.ztoken.portal.session.UnauthenticatedException | NewApiException ignored) {
            // 无论上游会话是否已失效或暂不可达，都必须清除本地会话与浏览器 cookie。
        } finally {
            sessions.revoke(sessionId);
        }
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
