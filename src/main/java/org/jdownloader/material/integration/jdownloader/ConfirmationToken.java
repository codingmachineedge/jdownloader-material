package org.jdownloader.material.integration.jdownloader;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Short-lived, single-use proof that the UI obtained an explicit decision. */
public final class ConfirmationToken {

    private static final Duration LIFETIME = Duration.ofMinutes(2);
    private final String scope;
    private final Instant expiresAt;
    private final AtomicBoolean consumed = new AtomicBoolean();

    private ConfirmationToken(String scope) {
        this.scope = scope;
        this.expiresAt = Instant.now().plus(LIFETIME);
    }

    /** Call only after a visible UI confirmation returned an affirmative decision. */
    public static ConfirmationToken afterUserConfirmation(RemoteOperation operation) {
        Objects.requireNonNull(operation, "operation");
        if (!operation.confirmationRequired()) {
            throw new IllegalArgumentException("This operation does not require a confirmation token");
        }
        return new ConfirmationToken(operation.endpoint().path());
    }

    /** Call only after confirming a generic advanced endpoint with the user. */
    public static ConfirmationToken afterUserConfirmation(RemoteEndpoint endpoint) {
        return new ConfirmationToken(Objects.requireNonNull(endpoint, "endpoint").path());
    }

    void consume(RemoteEndpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        if (!scope.equals(endpoint.path())) throw new SecurityException("Confirmation is for a different endpoint");
        if (Instant.now().isAfter(expiresAt)) throw new SecurityException("Confirmation has expired");
        if (!consumed.compareAndSet(false, true)) throw new SecurityException("Confirmation was already used");
    }

    @Override
    public String toString() {
        return "ConfirmationToken[scope=" + scope + ", consumed=" + consumed.get() + "]";
    }
}
