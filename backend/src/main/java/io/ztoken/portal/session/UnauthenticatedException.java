package io.ztoken.portal.session;

public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("Authentication is required");
    }
}
