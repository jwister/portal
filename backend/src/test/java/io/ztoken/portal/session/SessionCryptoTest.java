package io.ztoken.portal.session;

import io.ztoken.portal.config.PortalProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionCryptoTest {

    private final SessionCrypto crypto = new SessionCrypto(properties());

    @Test
    void encryptionUsesFreshNonceAndRoundTrips() {
        String first = crypto.encrypt("new-api-token");
        String second = crypto.encrypt("new-api-token");

        assertThat(first).isNotEqualTo(second);
        assertThat(crypto.decrypt(first)).isEqualTo("new-api-token");
    }

    @Test
    void tamperedCiphertextIsRejectedAsInvalidInput() {
        String encrypted = crypto.encrypt("new-api-token");

        assertThatThrownBy(() -> crypto.decrypt(encrypted + "x"))
                .isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void wrongKeyCannotDecryptCiphertext() {
        String encrypted = crypto.encrypt("new-api-token");
        PortalProperties other = properties();
        other.setSessionKey("//////////////////////////////////////////8=");

        assertThatThrownBy(() -> new SessionCrypto(other).decrypt(encrypted))
                .isInstanceOf(UnauthenticatedException.class);
    }

    private static PortalProperties properties() {
        PortalProperties properties = new PortalProperties();
        properties.setSessionKey("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
        return properties;
    }
}
