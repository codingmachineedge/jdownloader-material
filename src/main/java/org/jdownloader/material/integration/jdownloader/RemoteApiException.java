package org.jdownloader.material.integration.jdownloader;

/** Sanitized failure that identifies an endpoint but never request parameters or bodies. */
public final class RemoteApiException extends RuntimeException {

    public enum Kind {
        CLOSED,
        TIMEOUT,
        TRANSPORT,
        REQUEST_TOO_LARGE,
        RESPONSE_TOO_LARGE,
        INVALID_RESPONSE
    }

    private final Kind kind;
    private final RemoteEndpoint endpoint;

    RemoteApiException(Kind kind, RemoteEndpoint endpoint, String message) {
        super(message);
        this.kind = kind;
        this.endpoint = endpoint;
    }

    RemoteApiException(Kind kind, RemoteEndpoint endpoint, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.endpoint = endpoint;
    }

    public Kind kind() { return kind; }
    public RemoteEndpoint endpoint() { return endpoint; }
}
