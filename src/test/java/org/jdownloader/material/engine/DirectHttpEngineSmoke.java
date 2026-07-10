package org.jdownloader.material.engine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.jdownloader.material.model.DownloadLink;
import org.jdownloader.material.model.DownloadPackage;
import org.jdownloader.material.model.DownloadPriority;
import org.jdownloader.material.model.DownloadState;

/** Manual local smoke check for the direct HTTP path; it is not a Surefire test. */
public final class DirectHttpEngineSmoke {

    private DirectHttpEngineSmoke() {
    }

    public static void main(String[] args) throws Exception {
        byte[] payload = new byte[512 * 1024];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i * 31);
        AtomicInteger flakyGets = new AtomicInteger();
        AtomicInteger cancelledRetryGets = new AtomicInteger();
        AtomicInteger forcedRetryGets = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/artifact.bin", exchange -> respond(exchange, payload));
        server.createContext("/truncated.bin", exchange -> respondTruncated(exchange, payload));
        server.createContext("/slow.bin", exchange -> respondSlow(exchange, payload));
        server.createContext("/flaky.bin", exchange -> respondFlaky(exchange, payload, flakyGets));
        server.createContext("/always-503.bin", exchange -> respondAlways503(exchange, cancelledRetryGets));
        server.createContext("/force-retry.bin", exchange -> respondFlaky(exchange, payload, forcedRetryGets));
        server.start();

        Path destination = Files.createTempDirectory("jdm-direct-smoke-");
        AtomicReference<DirectHttpEngine> engine = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(() -> {
            stage.set(new Stage());
            stage.get().show();
            DirectHttpEngine direct = new DirectHttpEngine(destination.resolve("state"));
            direct.settings().downloadFolderProperty().set(destination.toString());
            direct.addLinks("http://127.0.0.1:" + server.getAddress().getPort() + "/artifact.bin",
                    "Smoke", destination.toString(), true, true);
            engine.set(direct);
            ready.countDown();
        });
        if (!ready.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("JavaFX did not start");

        DownloadEngine.AddLinksResult invalidSubmission = engine.get()
                .addLinks("not a direct url", "Invalid", destination.toString(), false, false)
                .get(5, TimeUnit.SECONDS);
        if (invalidSubmission.submittedLines() != 1 || invalidSubmission.acceptedLinks() != 0) {
            throw new IllegalStateException("Background URL validation accepted unsupported input");
        }

        DownloadLink completed = waitForCompletion(engine.get(), 20_000);
        Path output = destination.resolve("artifact.bin");
        if (completed == null || completed.state() != DownloadState.FINISHED || !Files.exists(output)
                || !output.toString().equals(completed.outputPathProperty().get())
                || !Arrays.equals(payload, Files.readAllBytes(output))) {
            throw new IllegalStateException("Direct HTTP smoke download did not complete correctly");
        }

        Path collisions = destination.resolve("collisions");
        CountDownLatch collisionQueued = new CountDownLatch(1);
        Platform.runLater(() -> {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/artifact.bin";
            engine.get().addLinks(url + "\n" + url, "Collision", collisions.toString(), true, true);
            collisionQueued.countDown();
        });
        collisionQueued.await(5, TimeUnit.SECONDS);
        List<DownloadLink> collisionLinks = waitForPackage(engine.get(), "Collision", 2, 20_000);
        if (collisionLinks.size() != 2 || collisionLinks.stream().anyMatch(link -> link.state() != DownloadState.FINISHED)
                || !Arrays.equals(payload, Files.readAllBytes(collisions.resolve("artifact.bin")))
                || !Arrays.equals(payload, Files.readAllBytes(collisions.resolve("artifact (1).bin")))) {
            throw new IllegalStateException("Same-name downloads were not isolated safely");
        }

        CountDownLatch truncatedQueued = new CountDownLatch(1);
        Platform.runLater(() -> {
            engine.get().addLinks("http://127.0.0.1:" + server.getAddress().getPort() + "/truncated.bin",
                    "Truncated", destination.toString(), true, true);
            truncatedQueued.countDown();
        });
        truncatedQueued.await(5, TimeUnit.SECONDS);
        List<DownloadLink> truncatedLinks = waitForPackage(engine.get(), "Truncated", 1, 20_000);
        if (truncatedLinks.size() != 1 || truncatedLinks.getFirst().state() != DownloadState.ERROR
                || Files.exists(destination.resolve("truncated.bin"))) {
            throw new IllegalStateException("Truncated HTTP response was promoted as a completed file");
        }

        CountDownLatch cancelRetryQueued = new CountDownLatch(1);
        Platform.runLater(() -> {
            engine.get().settings().autoReconnectProperty().set(true);
            engine.get().addLinks("http://127.0.0.1:" + server.getAddress().getPort() + "/always-503.bin",
                    "Cancel retry", destination.toString(), true, true);
            cancelRetryQueued.countDown();
        });
        cancelRetryQueued.await(5, TimeUnit.SECONDS);
        DownloadLink cancellingRetry = waitForRetryScheduled(engine.get(), "Cancel retry", 5_000);
        if (cancellingRetry == null) throw new IllegalStateException("Transient retry was not scheduled");
        CountDownLatch recoveryDisabled = new CountDownLatch(1);
        Platform.runLater(() -> {
            engine.get().settings().autoReconnectProperty().set(false);
            recoveryDisabled.countDown();
        });
        recoveryDisabled.await(5, TimeUnit.SECONDS);
        waitForState(cancellingRetry, DownloadState.ERROR, 5_000);
        Thread.sleep(2_300);
        if (stateOf(cancellingRetry) != DownloadState.ERROR || cancelledRetryGets.get() != 1
                || !cancellingRetry.detailProperty().get().startsWith("Automatic retry cancelled")) {
            throw new IllegalStateException("Disabling recovery did not cancel the queued retry");
        }

        CountDownLatch retryQueued = new CountDownLatch(1);
        Platform.runLater(() -> {
            engine.get().settings().autoReconnectProperty().set(true);
            engine.get().addLinks("http://127.0.0.1:" + server.getAddress().getPort() + "/flaky.bin",
                    "Retry", destination.toString(), true, true);
            retryQueued.countDown();
        });
        retryQueued.await(5, TimeUnit.SECONDS);
        List<DownloadLink> retriedLinks = waitForPackage(engine.get(), "Retry", 1, 20_000);
        if (retriedLinks.size() != 1 || retriedLinks.getFirst().state() != DownloadState.FINISHED
                || flakyGets.get() < 2 || !Arrays.equals(payload, Files.readAllBytes(destination.resolve("flaky.bin")))) {
            throw new IllegalStateException("Transient HTTP failure was not retried to completion");
        }

        AtomicReference<DownloadLink> forcedRetry = new AtomicReference<>();
        CountDownLatch forceRetryStarted = new CountDownLatch(1);
        Platform.runLater(() -> {
            engine.get().stop();
            engine.get().settings().autoReconnectProperty().set(true);
            engine.get().pause(true);
            DownloadPackage pkg = new DownloadPackage("Forced retry", destination.toString());
            DownloadLink link = new DownloadLink("force-retry.bin", "127.0.0.1", payload.length);
            link.url().set("http://127.0.0.1:" + server.getAddress().getPort() + "/force-retry.bin");
            link.destinationProperty().set(destination.toString());
            pkg.links().add(link);
            engine.get().downloadPackages().add(pkg);
            forcedRetry.set(link);
            engine.get().forceStart(List.of(link));
            forceRetryStarted.countDown();
        });
        forceRetryStarted.await(5, TimeUnit.SECONDS);
        waitForState(forcedRetry.get(), DownloadState.FINISHED, 10_000);
        if (stateOf(forcedRetry.get()) != DownloadState.FINISHED || forcedRetryGets.get() < 2) {
            throw new IllegalStateException("Forced retry stalled while the normal queue was paused");
        }

        AtomicReference<DownloadLink> lowest = new AtomicReference<>();
        AtomicReference<DownloadLink> highest = new AtomicReference<>();
        CountDownLatch priorityStart = new CountDownLatch(1);
        Platform.runLater(() -> {
            engine.get().stop();
            engine.get().settings().maxSimultaneousDownloadsProperty().set(1);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow.bin";
            DownloadPackage pkg = new DownloadPackage("Priority", destination.toString());
            DownloadLink low = new DownloadLink("low.bin", "127.0.0.1", payload.length);
            low.url().set(url);
            low.destinationProperty().set(destination.toString());
            low.priorityProperty().set(DownloadPriority.LOWEST);
            DownloadLink high = new DownloadLink("high.bin", "127.0.0.1", payload.length);
            high.url().set(url);
            high.destinationProperty().set(destination.toString());
            high.priorityProperty().set(DownloadPriority.HIGHEST);
            pkg.links().addAll(low, high);
            engine.get().downloadPackages().add(pkg);
            lowest.set(low);
            highest.set(high);
            engine.get().start();
            priorityStart.countDown();
        });
        priorityStart.await(5, TimeUnit.SECONDS);
        Thread.sleep(150);
        if ((stateOf(highest.get()) != DownloadState.RUNNING && stateOf(highest.get()) != DownloadState.FINISHED)
                || stateOf(lowest.get()) != DownloadState.QUEUED) {
            throw new IllegalStateException("Queue priority did not outrank insertion order");
        }

        AtomicReference<DownloadLink> selectedOnly = new AtomicReference<>();
        AtomicReference<DownloadLink> untouched = new AtomicReference<>();
        CountDownLatch selectedStart = new CountDownLatch(1);
        Platform.runLater(() -> {
            engine.get().stop();
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow.bin";
            DownloadPackage pkg = new DownloadPackage("Selected only", destination.toString());
            DownloadLink selected = new DownloadLink("selected.bin", "127.0.0.1", payload.length);
            selected.url().set(url);
            selected.destinationProperty().set(destination.toString());
            DownloadLink queued = new DownloadLink("untouched.bin", "127.0.0.1", payload.length);
            queued.url().set(url);
            queued.destinationProperty().set(destination.toString());
            pkg.links().addAll(selected, queued);
            engine.get().downloadPackages().add(pkg);
            selectedOnly.set(selected);
            untouched.set(queued);
            engine.get().startLinks(List.of(selected));
            selectedStart.countDown();
        });
        selectedStart.await(5, TimeUnit.SECONDS);
        Thread.sleep(150);
        DownloadState selectedState = stateOf(selectedOnly.get());
        DownloadState untouchedState = stateOf(untouched.get());
        if ((selectedState != DownloadState.RUNNING && selectedState != DownloadState.FINISHED)
                || untouchedState != DownloadState.QUEUED) {
            throw new IllegalStateException("Selected Start launched an unrelated queued link");
        }
        CountDownLatch disable = new CountDownLatch(1);
        Platform.runLater(() -> {
            engine.get().setEnabled(List.of(untouched.get()), false);
            disable.countDown();
        });
        disable.await(5, TimeUnit.SECONDS);
        if (stateOf(untouched.get()) != DownloadState.DISABLED) {
            throw new IllegalStateException("Disabled queue link did not enter the disabled state");
        }
        CountDownLatch enable = new CountDownLatch(1);
        Platform.runLater(() -> {
            engine.get().setEnabled(List.of(untouched.get()), true);
            enable.countDown();
        });
        enable.await(5, TimeUnit.SECONDS);
        if (stateOf(untouched.get()) != DownloadState.QUEUED) {
            throw new IllegalStateException("Re-enabled queue link did not return to queued state");
        }

        CountDownLatch firstShutdown = new CountDownLatch(1);
        Platform.runLater(() -> {
            engine.get().shutdown();
            engine.get().shutdown(); // Closing through both Stage and Application.stop is safe.
            firstShutdown.countDown();
        });
        firstShutdown.await(5, TimeUnit.SECONDS);
        Path stateFile = destination.resolve("state").resolve("state.properties");
        waitForStateFile(stateFile);

        AtomicReference<DirectHttpEngine> restored = new AtomicReference<>();
        CountDownLatch restart = new CountDownLatch(1);
        Platform.runLater(() -> {
            restored.set(new DirectHttpEngine(destination.resolve("state")));
            restart.countDown();
        });
        restart.await(5, TimeUnit.SECONDS);
        DownloadLink recovered = waitForCompletion(restored.get(), 5_000);
        if (recovered == null || recovered.state() != DownloadState.FINISHED
                || !output.toString().equals(recovered.outputPathProperty().get())) {
            throw new IllegalStateException("Queue state was not restored after restart");
        }

        CountDownLatch shutdown = new CountDownLatch(1);
        Platform.runLater(() -> {
            restored.get().shutdown();
            stage.get().close();
            shutdown.countDown();
        });
        shutdown.await(5, TimeUnit.SECONDS);
        server.stop(0);
        Platform.exit();
        System.out.println("Direct HTTP smoke check passed: " + output);
    }

    private static DownloadLink waitForCompletion(DirectHttpEngine engine, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            AtomicReference<DownloadLink> result = new AtomicReference<>();
            CountDownLatch observed = new CountDownLatch(1);
            Platform.runLater(() -> {
                if (!engine.downloadPackages().isEmpty() && !engine.downloadPackages().getFirst().links().isEmpty()) {
                    result.set(engine.downloadPackages().getFirst().links().getFirst());
                }
                observed.countDown();
            });
            observed.await(2, TimeUnit.SECONDS);
            DownloadLink link = result.get();
            if (link != null && (link.state() == DownloadState.FINISHED || link.state() == DownloadState.ERROR)) {
                return link;
            }
            Thread.sleep(100);
        }
        return null;
    }

    private static List<DownloadLink> waitForPackage(DirectHttpEngine engine, String packageName,
                                                      int expectedLinks, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            AtomicReference<List<DownloadLink>> result = new AtomicReference<>(List.of());
            CountDownLatch observed = new CountDownLatch(1);
            Platform.runLater(() -> {
                engine.downloadPackages().stream()
                        .filter(pkg -> packageName.equals(pkg.nameProp().get()))
                        .findFirst()
                        .ifPresent(pkg -> result.set(new ArrayList<>(pkg.links())));
                observed.countDown();
            });
            observed.await(2, TimeUnit.SECONDS);
            List<DownloadLink> links = result.get();
            if (links.size() == expectedLinks && links.stream().allMatch(link ->
                    link.state() == DownloadState.FINISHED || link.state() == DownloadState.ERROR)) {
                return links;
            }
            Thread.sleep(100);
        }
        return List.of();
    }

    private static DownloadState stateOf(DownloadLink link) throws InterruptedException {
        AtomicReference<DownloadState> result = new AtomicReference<>();
        CountDownLatch observed = new CountDownLatch(1);
        Platform.runLater(() -> {
            result.set(link.state());
            observed.countDown();
        });
        observed.await(2, TimeUnit.SECONDS);
        return result.get();
    }

    private static DownloadLink waitForRetryScheduled(DirectHttpEngine engine, String packageName,
                                                       long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            AtomicReference<DownloadLink> result = new AtomicReference<>();
            CountDownLatch observed = new CountDownLatch(1);
            Platform.runLater(() -> {
                engine.downloadPackages().stream()
                        .filter(pkg -> packageName.equals(pkg.nameProp().get()))
                        .findFirst()
                        .filter(pkg -> !pkg.links().isEmpty())
                        .map(pkg -> pkg.links().getFirst())
                        .filter(link -> link.state() == DownloadState.QUEUED
                                && link.retryAtEpochMillisProperty().get() > System.currentTimeMillis())
                        .ifPresent(result::set);
                observed.countDown();
            });
            observed.await(2, TimeUnit.SECONDS);
            if (result.get() != null) return result.get();
            Thread.sleep(50);
        }
        return null;
    }

    private static void waitForState(DownloadLink link, DownloadState expected, long timeoutMillis)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (stateOf(link) == expected) return;
            Thread.sleep(50);
        }
    }

    private static void waitForStateFile(Path stateFile) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(stateFile) && Files.size(stateFile) > 0) return;
            Thread.sleep(50);
        }
        throw new IllegalStateException("State journal was not written");
    }

    private static void respond(HttpExchange exchange, byte[] payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=artifact.bin");
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(payload.length));
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        String range = exchange.getRequestHeaders().getFirst("Range");
        int start = 0;
        if (range != null && range.startsWith("bytes=")) {
            String raw = range.substring("bytes=".length()).replace("-", "");
            try { start = Math.max(0, Integer.parseInt(raw)); } catch (NumberFormatException ignored) { }
            exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + (payload.length - 1)
                    + "/" + payload.length);
            exchange.sendResponseHeaders(206, payload.length - start);
        } else {
            exchange.sendResponseHeaders(200, payload.length);
        }
        exchange.getResponseBody().write(payload, start, payload.length - start);
        exchange.close();
    }

    private static void respondTruncated(HttpExchange exchange, byte[] payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=truncated.bin");
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(payload.length));
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload, 0, 32 * 1024);
        exchange.close();
    }

    private static void respondSlow(HttpExchange exchange, byte[] payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(payload.length));
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, payload.length);
        for (int offset = 0; offset < payload.length; offset += 16 * 1024) {
            int length = Math.min(16 * 1024, payload.length - offset);
            exchange.getResponseBody().write(payload, offset, length);
            exchange.getResponseBody().flush();
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        exchange.close();
    }

    private static void respondFlaky(HttpExchange exchange, byte[] payload, AtomicInteger gets) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=flaky.bin");
        exchange.getResponseHeaders().set("Content-Length", String.valueOf(payload.length));
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }
        if (gets.incrementAndGet() == 1) {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    private static void respondAlways503(HttpExchange exchange, AtomicInteger gets) throws IOException {
        exchange.getResponseHeaders().set("Content-Length", "1");
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
        } else {
            gets.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
        }
        exchange.close();
    }
}
