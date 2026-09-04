package io.ztoken.portal.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(@NotBlank String username, @Email @NotBlank String email, @NotBlank String password,
                              @NotBlank String verificationCode, String captchaId, String captchaCode) {
    public RegisterRequest(String username, String email, String password) {
        this(username, email, password, "", "", "");
    }
}
