package org.jdownloader.material.engine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.jdownloader.material.engine.history.HistoryOperation;
import org.jdownloader.material.engine.history.HistoryScope;
import org.jdownloader.material.engine.history.HistoryService;
import org.jdownloader.material.engine.history.HistoryStatus;
import org.jdownloader.material.model.DownloadLink;
import org.jdownloader.material.model.DownloadPackage;

/**
 * Manual JavaFX smoke check for the real DirectHttpEngine + JGit history path.
 * It intentionally uses no system Git executable.
 */
public final class DirectHistorySmoke {

    private DirectHistorySmoke() {
    }

    public static void main(String[] args) throws Exception {
        Path profile = Files.createTempDirectory("jdm-direct-history-");
        AtomicReference<DirectHttpEngine> engineRef = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();
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
            require(ready.await(8, TimeUnit.SECONDS), "JavaFX did not start");
            DirectHttpEngine engine = engineRef.get();
            require(engine != null, "Direct engine was not created");
            HistoryService history = engine.history();
            awaitHistory(history, 1);
            require(onFx(() -> history.entries().stream().anyMatch(entry -> entry.operation() == HistoryOperation.SEED)),
                    "Initial DirectHttpEngine state was not seeded in history");

            Path originalDestination = profile.resolve("original-destination");
            Path editedDestination = profile.resolve("edited-destination");
            onFx(() -> {
                DownloadPackage pkg = new DownloadPackage("Original queue package", originalDestination.toString());
                DownloadLink link = new DownloadLink("history-example.bin", "example.test", 1024);
                link.url().set("https://example.test/history-example.bin");
                link.destinationProperty().set(originalDestination.toString());
                pkg.links().add(link);
                engine.downloadPackages().add(pkg);
                engine.recordHistory(HistoryScope.DOWNLOADS, "Added Direct history package");
                return null;
            });
            awaitHistory(history, 2);

            onFx(() -> {
                DownloadPackage pkg = onlyPackage(engine);
                pkg.nameProp().set("Edited queue package");
                pkg.destinationProperty().set(editedDestination.toString());
                pkg.links().getFirst().destinationProperty().set(editedDestination.toString());
                engine.recordHistory(HistoryScope.DOWNLOADS, "Edited Direct history package");
                return null;
            });
            awaitHistory(history, 3);
            assertPackage(engine, "Edited queue package", editedDestination);

            CompletableFuture<Void> undo = onFx(history::undo);
            undo.get(15, TimeUnit.SECONDS);
            awaitHistory(history, 4);
            assertPackage(engine, "Original queue package", originalDestination);

            CompletableFuture<Void> redo = onFx(history::redo);
            redo.get(15, TimeUnit.SECONDS);
            awaitHistory(history, 5);
            assertPackage(engine, "Edited queue package", editedDestination);

            // A terminal engine-originated failure is also a durable Downloads
            // revision, rather than only a normal state-journal mutation.
            onFx(() -> {
                DownloadLink link = onlyPackage(engine).links().getFirst();
                link.url().set("not-a-direct-http-url");
                link.setState(org.jdownloader.material.model.DownloadState.QUEUED);
                engine.start();
                return null;
            });
            awaitHistory(history, 7);
            require(onFx(() -> onlyPackage(engine).links().getFirst().state()
                            == org.jdownloader.material.model.DownloadState.ERROR),
                    "Invalid direct link did not enter the terminal error state");
            require(onFx(() -> history.entries().stream().anyMatch(entry ->
                            "Download failed".equals(entry.summary()))),
                    "Terminal download failure was not checkpointed in local history");

            Path historyRoot = profile.resolve("history");
            require(Files.isDirectory(historyRoot.resolve("settings/.git")), "Settings Git repo is missing");
            require(Files.isDirectory(historyRoot.resolve("download-lists/.git")), "Download-lists Git repo is missing");
            require(Files.isDirectory(historyRoot.resolve("manifest/.git")), "Manifest Git repo is missing");
            try (var events = Files.list(historyRoot.resolve("manifest/events"))) {
                require(events.count() >= 5, "Manifest did not retain every history operation");
            }
            require(onFx(() -> history.entries().stream().anyMatch(entry -> entry.operation() == HistoryOperation.UNDO)),
                    "Undo did not append a history operation");
            require(onFx(() -> history.entries().stream().anyMatch(entry -> entry.operation() == HistoryOperation.REDO)),
                    "Redo did not append a history operation");

            System.out.println("Direct history smoke check passed: " + historyRoot);
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
        }
    }

    private static void assertPackage(DirectHttpEngine engine, String name, Path destination) throws Exception {
        require(onFx(() -> {
            DownloadPackage pkg = onlyPackage(engine);
            return name.equals(pkg.nameProp().get())
                    && destination.toString().equals(pkg.destinationProperty().get())
                    && destination.toString().equals(pkg.links().getFirst().destinationProperty().get());
        }), "History did not restore expected queue values: " + name);
    }

    private static DownloadPackage onlyPackage(DirectHttpEngine engine) {
        if (engine.downloadPackages().size() != 1) {
            throw new IllegalStateException("Expected exactly one history package, found "
                    + engine.downloadPackages().size());
        }
        DownloadPackage pkg = engine.downloadPackages().getFirst();
        if (pkg.links().size() != 1) throw new IllegalStateException("Expected exactly one history link");
        return pkg;
    }

    private static void awaitHistory(HistoryService history, int expectedEntries) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            HistoryState state = onFx(() -> new HistoryState(history.entries().size(), history.busyProperty().get(),
                    history.statusProperty().get()));
            if (state.status() == HistoryStatus.FAILED) {
                throw new IllegalStateException("History service reported failure");
            }
            if (!state.busy() && state.entries() >= expectedEntries) return;
            Thread.sleep(25);
        }
        throw new IllegalStateException("History did not reach " + expectedEntries + " entries");
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.call();
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch complete = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(action.call());
            } catch (Throwable failure) {
                error.set(failure);
            } finally {
                complete.countDown();
            }
        });
        if (!complete.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("JavaFX action timed out");
        if (error.get() != null) {
            if (error.get() instanceof Exception exception) throw exception;
            if (error.get() instanceof Error fatal) throw fatal;
            throw new RuntimeException(error.get());
        }
        return result.get();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record HistoryState(int entries, boolean busy, HistoryStatus status) {
    }
}
