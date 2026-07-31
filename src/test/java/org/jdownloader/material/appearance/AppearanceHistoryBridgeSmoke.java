package org.jdownloader.material.appearance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.jdownloader.material.engine.DirectHttpEngine;
import org.jdownloader.material.engine.history.HistoryOperation;
import org.jdownloader.material.engine.history.HistoryScope;
import org.jdownloader.material.engine.history.HistoryService;
import org.jdownloader.material.engine.history.HistoryStatus;

/** Verifies appearance Settings payloads are append-only, restorable, and no-op suppressed. */
public final class AppearanceHistoryBridgeSmoke {

    private AppearanceHistoryBridgeSmoke() {
    }

    public static void main(String[] args) throws Exception {
        Path profile = Files.createTempDirectory("appearance-history-bridge-");
        AtomicReference<DirectHttpEngine> engineRef = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicInteger assertions = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(1);

        Platform.startup(() -> {
            try {
                Stage stage = new Stage();
                stage.show();
                stageRef.set(stage);
                engineRef.set(new DirectHttpEngine(profile));
            } finally {
                ready.countDown();
            }
        });

        try {
            check(ready.await(8, TimeUnit.SECONDS), "JavaFX did not start", assertions);
            DirectHttpEngine engine = engineRef.get();
            check(engine != null, "Direct engine was not constructed", assertions);
            HistoryService history = engine.history();
            awaitHistory(history, 1);
            int seeded = onFx(() -> history.entries().size());

            AppearanceProfile first = new AppearanceProfile();
            first.setTheme(ThemeMode.DARK);
            first.target(AppearanceTargetId.of("history.appearance.sample"))
                    .style(AppearanceState.NORMAL).set(AppearanceProperty.FONT_SIZE, 18);
            String firstPayload = AppearanceProfileStore.serialize(first);
            onFx(() -> {
                engine.settings().setAppearanceProfilePayload(firstPayload);
                return null;
            });
            awaitHistory(history, seeded + 1);
            int afterFirst = onFx(() -> history.entries().size());
            check(firstPayload.equals(onFx(() -> engine.settings().appearanceProfilePayloadProperty().get())),
                    "first appearance payload was not stored", assertions);

            onFx(() -> {
                engine.settings().setAppearanceProfilePayload(firstPayload);
                return null;
            });
            Thread.sleep(650);
            awaitIdle(history);
            check(onFx(() -> history.entries().size()) == afterFirst,
                    "identical appearance payload created a no-op history revision", assertions);

            AppearanceProfile second = AppearanceProfileStore.deserialize(firstPayload);
            second.target(AppearanceTargetId.of("history.appearance.sample"))
                    .style(AppearanceState.NORMAL).set(AppearanceProperty.FONT_SIZE, 24);
            String secondPayload = AppearanceProfileStore.serialize(second);
            onFx(() -> {
                engine.settings().setAppearanceProfilePayload(secondPayload);
                return null;
            });
            awaitHistory(history, afterFirst + 1);
            int afterSecond = onFx(() -> history.entries().size());

            CompletableFuture<Void> undo = onFx(history::undo);
            undo.get(15, TimeUnit.SECONDS);
            awaitHistory(history, afterSecond + 1);
            check(firstPayload.equals(onFx(() -> engine.settings().appearanceProfilePayloadProperty().get())),
                    "undo did not restore the previous appearance payload", assertions);
            check(onFx(() -> history.entries().stream().anyMatch(entry ->
                            entry.operation() == HistoryOperation.UNDO)),
                    "appearance undo was not appended as its own history event", assertions);

            CompletableFuture<Void> redo = onFx(history::redo);
            redo.get(15, TimeUnit.SECONDS);
            awaitHistory(history, afterSecond + 2);
            check(secondPayload.equals(onFx(() -> engine.settings().appearanceProfilePayloadProperty().get())),
                    "redo did not restore the later appearance payload", assertions);
            check(onFx(() -> history.entries().stream().anyMatch(entry ->
                            entry.operation() == HistoryOperation.REDO)),
                    "appearance redo was not appended as its own history event", assertions);

            int beforeExplicitNoOp = onFx(() -> history.entries().size());
            onFx(() -> {
                engine.recordHistory(HistoryScope.SETTINGS, "No-op appearance checkpoint");
                return null;
            });
            Thread.sleep(200);
            awaitIdle(history);
            check(onFx(() -> history.entries().size()) == beforeExplicitNoOp,
                    "history accepted a byte-identical appearance checkpoint", assertions);

            System.out.println("AppearanceHistoryBridgeSmoke: " + assertions.get() + " assertions passed");
        } finally {
            DirectHttpEngine engine = engineRef.get();
            Stage stage = stageRef.get();
            if (engine != null || stage != null) {
                onFx(() -> {
                    if (engine != null) engine.shutdown();
                    if (stage != null) stage.close();
                    return null;
                });
            }
            Platform.exit();
            deleteTree(profile);
        }
    }

    private static void awaitHistory(HistoryService history, int expectedEntries) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            HistoryState state = onFx(() -> new HistoryState(history.entries().size(),
                    history.busyProperty().get(), history.statusProperty().get()));
            if (state.status() == HistoryStatus.FAILED) throw new AssertionError("history service failed");
            if (!state.busy() && state.entries() >= expectedEntries) return;
            Thread.sleep(25);
        }
        throw new AssertionError("history did not reach " + expectedEntries + " entries");
    }

    private static void awaitIdle(HistoryService history) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            HistoryState state = onFx(() -> new HistoryState(history.entries().size(),
                    history.busyProperty().get(), history.statusProperty().get()));
            if (state.status() == HistoryStatus.FAILED) throw new AssertionError("history service failed");
            if (!state.busy()) return;
            Thread.sleep(25);
        }
        throw new AssertionError("history did not become idle");
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.call();
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch complete = new CountDownLatch(1);
        Platform.runLater(() -> {
            try { result.set(action.call()); }
            catch (Throwable failure) { error.set(failure); }
            finally { complete.countDown(); }
        });
        if (!complete.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaFX action timed out");
        if (error.get() instanceof Exception exception) throw exception;
        if (error.get() instanceof Error fatal) throw fatal;
        if (error.get() != null) throw new RuntimeException(error.get());
        return result.get();
    }

    private static void check(boolean condition, String message, AtomicInteger assertions) {
        assertions.incrementAndGet();
        if (!condition) throw new AssertionError(message);
    }

    private static void deleteTree(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) return;
        IOException lastFailure = null;
        for (int attempt = 0; attempt < 6 && Files.exists(directory); attempt++) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                return;
            } catch (IOException transientLock) {
                lastFailure = transientLock;
                try { Thread.sleep(100L * (attempt + 1)); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw transientLock;
                }
            }
        }
        if (Files.exists(directory)) {
            // Windows may retain a just-closed JGit object briefly; keep the
            // behavior assertions authoritative and schedule bounded temp cleanup.
            try (var paths = Files.walk(directory)) {
                // Delete-on-exit runs registrations in reverse order, so register
                // parents first and children last to delete children first at exit.
                paths.sorted().forEach(path -> path.toFile().deleteOnExit());
            }
            if (lastFailure != null) System.err.println("Deferred temporary history cleanup: " + lastFailure.getMessage());
        }
    }

    private record HistoryState(int entries, boolean busy, HistoryStatus status) { }
}
