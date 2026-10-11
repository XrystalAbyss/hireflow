package com.hireflow.network.protocol;

/**
 * Exception thrown when protocol framing, serialization, or version requirements are violated.
 */
public class ProtocolException extends Exception {

    private final int statusCode;

    public ProtocolException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public ProtocolException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
