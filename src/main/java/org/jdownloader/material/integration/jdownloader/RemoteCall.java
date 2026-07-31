package org.jdownloader.material.integration.jdownloader;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cancellable handle for one asynchronous Remote API exchange. */
public final class RemoteCall implements AutoCloseable {
    private final CompletableFuture<RemoteResponse> result;
    private final Future<?> transport;
    private final AtomicBoolean cancelled = new AtomicBoolean();

    RemoteCall(CompletableFuture<RemoteResponse> result, Future<?> transport) {
        this.result = Objects.requireNonNull(result, "result");
        this.transport = Objects.requireNonNull(transport, "transport");
        result.whenComplete((response, error) -> {
            if (result.isCancelled()) cancel();
        });
    }

    public CompletableFuture<RemoteResponse> future() { return result; }

    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true)) return false;
        transport.cancel(true);
        result.cancel(true);
        return true;
    }

    public boolean isCancelled() { return cancelled.get() || result.isCancelled(); }

    @Override public void close() { cancel(); }

    @Override public String toString() {
        return "RemoteCall[done=" + result.isDone() + ", cancelled=" + isCancelled() + "]";
    }
}
