package io.ztoken.portal.newapi;

public class NewApiUnsupportedException extends RuntimeException {
    public NewApiUnsupportedException() {
        super("The requested NewAPI operation is not supported");
    }
}
