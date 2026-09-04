package io.ztoken.portal.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaptchaServiceTest {

    @Test
    void createsPngChallengeAndConsumesItOnlyOnce() {
        CaptchaService service = new CaptchaService();

        CaptchaResponse challenge = service.create();

        assertThat(challenge.captchaId()).isNotBlank();
        assertThat(challenge.image()).startsWith("data:image/png;base64,");
        assertThat(challenge.expiresIn()).isPositive();
        assertThat(service.verifyAndConsume(challenge.captchaId(), "wrong")).isFalse();
        assertThat(service.verifyAndConsume(challenge.captchaId(), "wrong")).isFalse();
    }

    @Test
    void rejectsMissingOrUnknownChallenge() {
        CaptchaService service = new CaptchaService();

        assertThat(service.verifyAndConsume(null, "ABCDE")).isFalse();
        assertThat(service.verifyAndConsume("missing", "ABCDE")).isFalse();
    }
}
