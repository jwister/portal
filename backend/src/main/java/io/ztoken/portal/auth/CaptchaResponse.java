package io.ztoken.portal.auth;

public record CaptchaResponse(String captchaId, String image, long expiresIn) {
}
