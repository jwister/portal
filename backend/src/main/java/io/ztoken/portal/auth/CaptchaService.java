package io.ztoken.portal.auth;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
        String answer = randomText(5);
        challenges.put(id, new Challenge(hash(answer), Instant.now().plus(TTL), 0));
        return new CaptchaResponse(id, render(answer), TTL.toSeconds());
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

    private String randomText(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) result.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return result.toString();
    }

    private String render(String answer) {
        BufferedImage image = new BufferedImage(160, 52, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(244, 248, 253));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < 8; i++) {
            graphics.setColor(new Color(150, 180, 220, 120));
            graphics.drawLine(random.nextInt(160), random.nextInt(52), random.nextInt(160), random.nextInt(52));
        }
        graphics.setFont(new Font("SansSerif", Font.BOLD, 28));
        for (int i = 0; i < answer.length(); i++) {
            graphics.setColor(new Color(26 + random.nextInt(40), 72 + random.nextInt(60), 130 + random.nextInt(70)));
            graphics.drawString(String.valueOf(answer.charAt(i)), 15 + i * 28, 35 + random.nextInt(5));
        }
        graphics.dispose();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to render captcha", exception);
        }
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
