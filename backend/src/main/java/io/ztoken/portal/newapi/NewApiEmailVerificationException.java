package io.ztoken.portal.newapi;

public class NewApiEmailVerificationException extends NewApiException {
    public NewApiEmailVerificationException() {
        this("邮箱验证码发送服务暂不可用，请稍后重试");
    }

    public NewApiEmailVerificationException(String safeMessage) {
        super(safeMessage);
    }
}
