package org.jdownloader.material.integration.jdownloader;

import java.time.Duration;
import java.util.Objects;

/** Bounded raw response. Its string representation deliberately omits the body. */
public final class RemoteResponse {
    private final RemoteEndpoint endpoint;
    private final int statusCode;
    private final String contentType;
    private final String body;
    private final int bodyBytes;
    private final Duration elapsed;

    RemoteResponse(RemoteEndpoint endpoint, int statusCode, String contentType,
                   String body, int bodyBytes, Duration elapsed) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.statusCode = statusCode;
        this.contentType = Objects.requireNonNullElse(contentType, "");
        this.body = Objects.requireNonNullElse(body, "");
        this.bodyBytes = Math.max(0, bodyBytes);
        this.elapsed = Objects.requireNonNullElse(elapsed, Duration.ZERO);
    }

    public RemoteEndpoint endpoint() { return endpoint; }
    public int statusCode() { return statusCode; }
    public String contentType() { return contentType; }
    public String body() { return body; }
    public int bodyBytes() { return bodyBytes; }
    public Duration elapsed() { return elapsed; }
    public boolean successful() { return statusCode >= 200 && statusCode < 300; }

    @Override
    public String toString() {
        return "RemoteResponse[endpoint=" + endpoint + ", statusCode=" + statusCode
                + ", bodyBytes=" + bodyBytes + ", elapsed=" + elapsed + "]";
    }
}
