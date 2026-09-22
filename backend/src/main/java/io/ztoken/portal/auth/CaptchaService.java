package io.ztoken.portal.auth;

import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    public CaptchaResponse create() {
        cleanup();
        String id = UUID.randomUUID().toString();
        
        com.wf.captcha.SpecCaptcha specCaptcha = new com.wf.captcha.SpecCaptcha(160, 52, 5);
        specCaptcha.setCharType(com.wf.captcha.base.Captcha.TYPE_DEFAULT);
        String answer = specCaptcha.text().toUpperCase();
        
        challenges.put(id, new Challenge(hash(answer), Instant.now().plus(TTL), 0));
        return new CaptchaResponse(id, specCaptcha.toBase64(), TTL.toSeconds());
    }

    public boolean verifyAndConsume(String id, String answer) {
        if (id == null || answer == null || answer.isBlank()) return false;
        Challenge challenge = challenges.get(id);
        if (challenge == null || challenge.expiresAt().isBefore(Instant.now()) || challenge.attempts() >= MAX_ATTEMPTS) {
            challenges.remove(id);
            return false;
        }
        if (!MessageDigest.isEqual(challenge.answerHash(), hash(answer.trim().toUpperCase()))) {
            challenges.computeIfPresent(id, (key, current) -> new Challenge(current.answerHash(), current.expiresAt(), current.attempts() + 1));
            return false;
        }
        return challenges.remove(id, challenge);
    }

    private byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void cleanup() {
        Instant now = Instant.now();
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record Challenge(byte[] answerHash, Instant expiresAt, int attempts) { }
}
