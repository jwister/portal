package io.ztoken.portal.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "portal.session-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
class PortalSessionServiceTest {

    @Autowired
    private PortalSessionService sessions;

    @Autowired
    private PortalSessionRepository repository;

    @BeforeEach
    void clearSessions() {
        repository.deleteAll();
    }

    @Test
    void savesOnlyEncryptedNewApiTokenAndRestoresCurrentUser() {
        PortalSession session = sessions.create(new NewApiIdentity(42L, "alice"), "access-token");

        PortalSession stored = repository.findById(session.getId()).orElseThrow();
        PortalPrincipal principal = sessions.require(session.getId());

        assertThat(stored.getEncryptedAccessToken()).doesNotContain("access-token");
        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.username()).isEqualTo("alice");
        assertThat(principal.accessToken()).isEqualTo("access-token");
    }

    @Test
    void malformedSessionIdentifierIsRejected() {
        assertThatThrownBy(() -> sessions.require("not-a-session"))
                .isInstanceOf(UnauthenticatedException.class);
    }
}
