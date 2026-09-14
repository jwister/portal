package io.ztoken.portal.session;

import io.ztoken.portal.config.PortalProperties;
import io.ztoken.portal.newapi.NewApiAuthenticationException;
import io.ztoken.portal.newapi.NewApiCredentials;
import io.ztoken.portal.newapi.NewApiLogin;
import io.ztoken.portal.newapi.NewApiSessionRefresher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class PortalSessionService {

    private final PortalSessionRepository repository;
    private final SessionCrypto crypto;
    private final PortalProperties properties;
    private final NewApiSessionRefresher refresher;
    private final SecureRandom random = new SecureRandom();

    public PortalSessionService(PortalSessionRepository repository, SessionCrypto crypto, PortalProperties properties,
                                NewApiSessionRefresher refresher) {
        this.repository = repository;
        this.crypto = crypto;
        this.properties = properties;
        this.refresher = refresher;
    }

    @Transactional
    public PortalSession create(NewApiIdentity identity, String accessToken) {
        // 兼容仅用于既有集成测试的构造入口；生产登录必须使用包含 refresh token 的完整登录包。
        Instant now = Instant.now();
        return repository.save(new PortalSession(randomSessionId(), identity.userId(), identity.username(), crypto.encrypt(accessToken),
                crypto.encrypt("legacy-test-refresh-token"), "legacy-test-session", now.plusSeconds(900),
                now.plus(properties.getSessionTtl()), now));
    }

    @Transactional
    public PortalSession create(NewApiLogin login) {
        NewApiIdentity identity = login.identity();
        String accessToken = login.accessToken();
        if (identity.userId() <= 0 || identity.username() == null || identity.username().isBlank()
                || accessToken == null || accessToken.isBlank() || login.refreshToken() == null || login.refreshToken().isBlank()
                || login.sessionId() == null || login.sessionId().isBlank() || login.accessExpiresAt() == null) {
            throw new IllegalArgumentException("NewAPI login bundle is incomplete");
        }
        Instant now = Instant.now();
        PortalSession session = new PortalSession(
                randomSessionId(),
                identity.userId(),
                identity.username(),
                crypto.encrypt(accessToken),
                crypto.encrypt(login.refreshToken()),
                login.sessionId(),
                login.accessExpiresAt(),
                now.plus(properties.getSessionTtl()),
                now
        );
        return repository.save(session);
    }

    @Transactional
    public PortalPrincipal require(String sessionId) {
        if (sessionId == null || sessionId.length() != 48) {
            throw new UnauthenticatedException();
        }
        PortalSession session = activeSession(sessionId);
        PortalPrincipal principal = principalFrom(session);
        if (session.getAccessExpiresAt().isAfter(Instant.now())) {
            return principal;
        }
        return refresh(session, principal);
    }

    /** 上游拒绝当前 access token 时续期并仅重试调用方明确传入的一次操作。 */
    @Transactional
    public <T> T withAuthenticatedPrincipal(String sessionId, java.util.function.Function<PortalPrincipal, T> operation) {
        PortalPrincipal principal = require(sessionId);
        try {
            return operation.apply(principal);
        } catch (NewApiAuthenticationException exception) {
            PortalPrincipal renewed = refresh(activeSession(sessionId), principal);
            try {
                return operation.apply(renewed);
            } catch (NewApiAuthenticationException rejectedAgain) {
                revoke(sessionId);
                throw new UnauthenticatedException();
            }
        }
    }

    @Transactional
    public void revoke(String sessionId) {
        if (sessionId == null || sessionId.length() != 48) {
            return;
        }
        repository.findById(sessionId).ifPresent(session -> session.revokeAt(Instant.now()));
    }

    private String randomSessionId() {
        byte[] bytes = new byte[36];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private PortalSession activeSession(String sessionId) {
        return repository.findById(sessionId).filter(item -> item.isActiveAt(Instant.now()))
                .orElseThrow(UnauthenticatedException::new);
    }

    private PortalPrincipal principalFrom(PortalSession session) {
        if (session.getEncryptedRefreshToken() == null || session.getNewApiSessionId() == null || session.getAccessExpiresAt() == null) {
            throw new UnauthenticatedException();
        }
        return new PortalPrincipal(session.getId(), session.getNewApiUserId(), session.getUsername(),
                crypto.decrypt(session.getEncryptedAccessToken()), crypto.decrypt(session.getEncryptedRefreshToken()),
                session.getNewApiSessionId(), session.getAccessExpiresAt());
    }

    private PortalPrincipal refresh(PortalSession session, PortalPrincipal principal) {
        try {
            NewApiCredentials credentials = refresher.refresh(principal);
            session.replaceNewApiCredentials(crypto.encrypt(credentials.accessToken()), crypto.encrypt(credentials.refreshToken()),
                    credentials.sessionId(), credentials.accessExpiresAt());
            return principalFrom(repository.save(session));
        } catch (NewApiAuthenticationException exception) {
            session.revokeAt(Instant.now());
            repository.save(session);
            throw new UnauthenticatedException();
        }
    }
}
