package io.ztoken.portal.session;

import io.ztoken.portal.config.PortalProperties;
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
    private final SecureRandom random = new SecureRandom();

    public PortalSessionService(PortalSessionRepository repository, SessionCrypto crypto, PortalProperties properties) {
        this.repository = repository;
        this.crypto = crypto;
        this.properties = properties;
    }

    @Transactional
    public PortalSession create(NewApiIdentity identity, String accessToken) {
        if (identity.userId() <= 0 || identity.username() == null || identity.username().isBlank()
                || accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("NewAPI identity and access token are required");
        }
        Instant now = Instant.now();
        PortalSession session = new PortalSession(
                randomSessionId(),
                identity.userId(),
                identity.username(),
                crypto.encrypt(accessToken),
                now.plus(properties.getSessionTtl()),
                now
        );
        return repository.save(session);
    }

    @Transactional(readOnly = true)
    public PortalPrincipal require(String sessionId) {
        if (sessionId == null || sessionId.length() != 48) {
            throw new UnauthenticatedException();
        }
        PortalSession session = repository.findById(sessionId)
                .filter(item -> item.isActiveAt(Instant.now()))
                .orElseThrow(UnauthenticatedException::new);
        return new PortalPrincipal(session.getNewApiUserId(), session.getUsername(), crypto.decrypt(session.getEncryptedAccessToken()));
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
}
