package io.ztoken.portal.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import com.anji.captcha.config.AjCaptchaAutoConfiguration;
import com.anji.captcha.config.AjCaptchaServiceAutoConfiguration;
import com.anji.captcha.config.AjCaptchaStorageAutoConfiguration;

@Configuration
@Import({
    AjCaptchaAutoConfiguration.class,
    AjCaptchaServiceAutoConfiguration.class,
    AjCaptchaStorageAutoConfiguration.class
})
public class CaptchaConfig {
}
