package io.ztoken.portal.newapi;

public class NewApiException extends RuntimeException {

    public NewApiException(String message) {
        super(message);
    }

    public NewApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
