package vn.cxn.apache_camel.exception;

public class ConnectionTestException extends RuntimeException {

    public ConnectionTestException(String message) {
        super(message);
    }

    public ConnectionTestException(String message, Throwable cause) {
        super(message, cause);
    }
}
