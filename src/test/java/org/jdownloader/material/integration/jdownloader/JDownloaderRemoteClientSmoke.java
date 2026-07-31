package org.jdownloader.material.integration.jdownloader;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jdownloader.material.ui.stock.StockFeatureView;
import org.jdownloader.material.workspace.WorkspacePage;

/** Headless loopback transport checks; no JavaFX toolkit is started. */
public final class JDownloaderRemoteClientSmoke {

    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedQuery = new AtomicReference<>();
    private final AtomicInteger redirectTargetHits = new AtomicInteger();
    private final AtomicInteger shutdownHits = new AtomicInteger();
    private final AtomicInteger advancedHits = new AtomicInteger();
    private final AtomicInteger handshakeHits = new AtomicInteger();
    private final AtomicInteger accountAddHits = new AtomicInteger();
    private final CountDownLatch cancellableStarted = new CountDownLatch(1);
    private int assertions;
    private HttpServer server;
    private ExecutorService serverExecutor;
    private String baseUrl;

    public static void main(String[] arguments) throws Exception {
        new JDownloaderRemoteClientSmoke().run();
    }

    private void run() throws Exception {
        startServer();
        try {
            routesAndEncoding();
            loopbackValidation();
            redirectsAreNotFollowed();
            requestTimeoutIsBounded();
            responseSizeIsBounded();
            asynchronousCancellationWorks();
            confirmationTokensGateChanges();
            secretArraysAreClearedAndNeverLogged();
            requestSizeIsBounded();
            catalogAndStockPageCoverage();
        } finally {
            stopServer();
        }
        System.out.println("JDownloaderRemoteClient smoke passed " + assertions + " assertions");
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        serverExecutor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "jdownloader-remote-smoke-server");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(serverExecutor);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void stopServer() {
        if (server != null) server.stop(0);
        if (serverExecutor != null) serverExecutor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getRawPath();
        String route = path.startsWith("/api/") ? path.substring(4) : path;
        try {
            switch (route) {
                case "/plugins/get" -> {
                    capturedPath.set(path);
                    capturedQuery.set(exchange.getRequestURI().getRawQuery());
                    respond(exchange, 200, "{\"route\":\"plugin\"}");
                }
                case "/redirect" -> {
                    exchange.getResponseHeaders().set("Location", baseUrl + "/redirect-target");
                    respond(exchange, 302, "redirect-not-followed");
                }
                case "/redirect-target" -> {
                    redirectTargetHits.incrementAndGet();
                    respond(exchange, 200, "wrong-target");
                }
                case "/slow" -> {
                    pause(500);
                    respond(exchange, 200, "late");
                }
                case "/large" -> respond(exchange, 200, "x".repeat(2_048));
                case "/cancellable" -> {
                    cancellableStarted.countDown();
                    pause(2_000);
                    respond(exchange, 200, "late");
                }
                case "/system/shutdownOS" -> {
                    shutdownHits.incrementAndGet();
                    respond(exchange, 200, "true");
                }
                case "/advanced" -> {
                    advancedHits.incrementAndGet();
                    respond(exchange, 200, "{\"advanced\":true}");
                }
                case "/session/handshake" -> {
                    handshakeHits.incrementAndGet();
                    respond(exchange, 200, "{\"session\":true}");
                }
                case "/accountsV2/addAccount" -> {
                    accountAddHits.incrementAndGet();
                    respond(exchange, 200, "{\"added\":true}");
                }
                case "/device/ping" -> respond(exchange, 200, "{\"pong\":true}");
                default -> respond(exchange, 404, "{\"error\":\"unknown route\"}");
            }
        } catch (RuntimeException ignoredAfterClientCancellation) {
            exchange.close();
        }
    }

    private static void pause(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (exchange; var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private void routesAndEncoding() throws Exception {
        try (JDownloaderRemoteClient client = client(baseUrl + "/api", JDownloaderRemoteClient.Options.DEFAULT)) {
            RemoteResponse response = await(client.pluginSetting("plug in/廣", "visible name", "key+value?"));
            check(response.successful(), "encoded plugin route succeeds");
            check("/api/plugins/get".equals(capturedPath.get()), "base path and typed route compose");
            String query = capturedQuery.get();
            check(query != null && query.split("&", -1).length == 3, "positional parameter order has three slots");
            check(query.contains("%20"), "spaces use percent encoding");
            check(query.contains("%2F"), "slashes stay inside one positional parameter");
            check(query.contains("%E5%BB%A3"), "Unicode uses UTF-8 percent encoding");
            check(query.contains("%2B") && query.contains("%3F"), "reserved characters are encoded");
            check(!query.contains("+"), "query encoding never substitutes plus for space");
            check(response.toString().contains("bodyBytes=") && !response.toString().contains("route"),
                    "response diagnostic text omits the response body");
        } finally {
            capturedPath.set(null);
            capturedQuery.set(null);
        }
    }

    private void loopbackValidation() {
        List<String> valid = List.of(
                "http://localhost:3128",
                "https://127.0.0.1",
                "http://127.255.42.9:65535/api",
                "http://[::1]:3128");
        for (String candidate : valid) {
            check(JDownloaderRemoteClient.validateBaseUrl(candidate) != null,
                    "accepted strict loopback URL: " + candidate);
        }
        List<String> invalid = Arrays.asList(
                null, "", "ftp://127.0.0.1", "http://example.com", "http://192.168.1.2",
                "http://127.0.0.1.example.com", "http://2130706433", "http://127.1",
                "http://user:pass@127.0.0.1", "http://localhost:3128?query=1",
                "http://localhost:3128#fragment", "http://localhost:3128/a/../b");
        for (String candidate : invalid) {
            expectThrows(IllegalArgumentException.class,
                    () -> JDownloaderRemoteClient.validateBaseUrl(candidate),
                    "rejected non-strict URL");
        }
        expectThrows(IllegalArgumentException.class, () -> RemoteEndpoint.of("//other-host/path"),
                "endpoint cannot override authority");
        expectThrows(IllegalArgumentException.class, () -> RemoteEndpoint.of("/device/../system"),
                "endpoint cannot traverse");
    }

    private void redirectsAreNotFollowed() throws Exception {
        try (JDownloaderRemoteClient client = client(baseUrl, JDownloaderRemoteClient.Options.DEFAULT)) {
            RemoteEndpoint endpoint = RemoteEndpoint.of("/redirect");
            RemoteResponse response = await(client.advanced(endpoint, HttpMethod.GET, List.of(), "",
                    ConfirmationToken.afterUserConfirmation(endpoint)));
            check(response.statusCode() == 302, "redirect is returned to the caller");
            check(redirectTargetHits.get() == 0, "redirect target is never contacted");
        }
    }

    private void requestTimeoutIsBounded() throws Exception {
        JDownloaderRemoteClient.Options options = new JDownloaderRemoteClient.Options(
                Duration.ofSeconds(1), Duration.ofMillis(100), 4_096, 4_096);
        try (JDownloaderRemoteClient client = client(baseUrl, options)) {
            RemoteEndpoint endpoint = RemoteEndpoint.of("/slow");
            RemoteCall call = client.advanced(endpoint, HttpMethod.GET, List.of(), "",
                    ConfirmationToken.afterUserConfirmation(endpoint));
            check(expectRemoteFailure(call, RemoteApiException.Kind.TIMEOUT),
                    "slow response reaches the sanitized timeout state");
        }
    }

    private void responseSizeIsBounded() throws Exception {
        JDownloaderRemoteClient.Options options = new JDownloaderRemoteClient.Options(
                Duration.ofSeconds(1), Duration.ofSeconds(2), 4_096, 128);
        try (JDownloaderRemoteClient client = client(baseUrl, options)) {
            RemoteEndpoint endpoint = RemoteEndpoint.of("/large");
            RemoteCall call = client.advanced(endpoint, HttpMethod.GET, List.of(), "",
                    ConfirmationToken.afterUserConfirmation(endpoint));
            check(expectRemoteFailure(call, RemoteApiException.Kind.RESPONSE_TOO_LARGE),
                    "declared oversized response is cancelled before buffering");
        }
    }

    private void asynchronousCancellationWorks() throws Exception {
        try (JDownloaderRemoteClient client = client(baseUrl, JDownloaderRemoteClient.Options.DEFAULT)) {
            RemoteEndpoint endpoint = RemoteEndpoint.of("/cancellable");
            RemoteCall call = client.advanced(endpoint, HttpMethod.GET, List.of(), "",
                    ConfirmationToken.afterUserConfirmation(endpoint));
            check(cancellableStarted.await(2, TimeUnit.SECONDS), "cancellable request reaches loopback server");
            check(call.cancel(), "first cancellation changes call state");
            check(call.isCancelled(), "call reports cancellation");
            expectThrows(CancellationException.class, call.future()::join,
                    "cancelled future completes as cancelled");
            check(!call.cancel(), "cancellation is idempotent");
        }
    }

    private void confirmationTokensGateChanges() throws Exception {
        try (JDownloaderRemoteClient client = client(baseUrl, JDownloaderRemoteClient.Options.DEFAULT)) {
            expectThrows(SecurityException.class, () -> client.shutdownSystem(false, null),
                    "system shutdown requires an explicit token");
            ConfirmationToken wrong = ConfirmationToken.afterUserConfirmation(
                    RemoteOperation.SYSTEM_EXIT_JDOWNLOADER);
            expectThrows(SecurityException.class, () -> client.shutdownSystem(false, wrong),
                    "token is bound to one endpoint");

            ConfirmationToken shutdown = ConfirmationToken.afterUserConfirmation(RemoteOperation.SYSTEM_SHUTDOWN);
            check(await(client.shutdownSystem(false, shutdown)).successful(),
                    "matching system confirmation permits one call");
            check(shutdownHits.get() == 1, "confirmed shutdown route is reached once");
            expectThrows(SecurityException.class, () -> client.shutdownSystem(false, shutdown),
                    "confirmation token cannot be reused");

            RemoteEndpoint advanced = RemoteEndpoint.of("/advanced");
            expectThrows(SecurityException.class,
                    () -> client.advanced(advanced, HttpMethod.GET, List.of(), "", null),
                    "unknown advanced endpoint requires confirmation");
            ConfirmationToken permit = ConfirmationToken.afterUserConfirmation(advanced);
            check(await(client.advanced(advanced, HttpMethod.GET, List.of(), "", permit)).successful(),
                    "confirmed advanced endpoint executes");
            check(advancedHits.get() == 1, "advanced route is reached exactly once");

            check(await(client.ping()).successful(), "known read-only ping needs no confirmation");
            expectThrows(SecurityException.class,
                    () -> client.advanced(RemoteOperation.DEVICE_PING.endpoint(), HttpMethod.POST,
                            List.of(), "{}", null),
                    "POST requires confirmation even for a known read-only path");
        }
    }

    private void secretArraysAreClearedAndNeverLogged() throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        String marker = "never-print-this-secret";
        try (PrintStream capture = new PrintStream(capturedOutput, true, StandardCharsets.UTF_8);
             JDownloaderRemoteClient client = client(baseUrl, JDownloaderRemoteClient.Options.DEFAULT)) {
            System.setOut(capture);
            System.setErr(capture);
            char[] handshakeSecret = marker.toCharArray();
            RemoteCall handshake = client.directSessionHandshake("local-user", handshakeSecret);
            check(allCleared(handshakeSecret), "handshake password is cleared before the method returns");
            check(await(handshake).successful(), "direct-session handshake reaches loopback API");

            char[] accountSecret = marker.toCharArray();
            RemoteCall add = client.addAccount("example-hoster", "account-user", accountSecret);
            check(allCleared(accountSecret), "account password is cleared before the method returns");
            check(await(add).successful(), "account-add reaches loopback API");
            check(!handshake.toString().contains(marker) && !add.toString().contains(marker),
                    "call diagnostic text omits secret parameters");
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        String output = capturedOutput.toString(StandardCharsets.UTF_8);
        check(!output.contains(marker), "secret never reaches standard output or error");
        check(handshakeHits.get() == 1 && accountAddHits.get() == 1,
                "both credential-bearing routes execute without server-side test retention");
    }

    private void requestSizeIsBounded() {
        JDownloaderRemoteClient.Options options = new JDownloaderRemoteClient.Options(
                Duration.ofSeconds(1), Duration.ofSeconds(1), 128, 4_096);
        try (JDownloaderRemoteClient client = client(baseUrl, options)) {
            RemoteEndpoint endpoint = RemoteEndpoint.of("/advanced");
            ConfirmationToken token = ConfirmationToken.afterUserConfirmation(endpoint);
            RemoteApiException failure = expectThrows(RemoteApiException.class,
                    () -> client.advanced(endpoint, HttpMethod.POST, List.of(), "x".repeat(512), token),
                    "oversized request is rejected synchronously");
            check(failure.kind() == RemoteApiException.Kind.REQUEST_TOO_LARGE,
                    "oversized request has a dedicated safe failure kind");
        }
    }

    private void catalogAndStockPageCoverage() {
        for (RemoteCategory category : RemoteCategory.values()) {
            check(!RemoteOperation.inCategory(category).isEmpty(), "typed operation category covered: " + category);
        }
        Set<String> paths = Arrays.stream(RemoteOperation.values())
                .map(operation -> operation.endpoint().path()).collect(java.util.stream.Collectors.toSet());
        List<String> required = List.of(
                "/device/ping", "/system/getSystemInfos",
                "/accountsV2/listAccounts", "/accountsV2/addAccount", "/accountsV2/enableAccounts",
                "/accountsV2/disableAccounts", "/accountsV2/removeAccounts",
                "/extensions/list", "/extensions/install", "/extensions/setEnabled", "/plugins/list",
                "/config/list", "/config/query", "/config/get", "/config/getDefault",
                "/config/listEnum", "/config/set", "/config/reset",
                "/captcha/list", "/captcha/solve", "/dialogs/list", "/dialogs/answer",
                "/downloadsV2/queryPackages", "/downloadsV2/queryLinks", "/downloadcontroller/start",
                "/downloadcontroller/stop", "/downloadcontroller/pause",
                "/linkgrabberv2/queryPackages", "/linkgrabberv2/queryLinks", "/linkgrabberv2/addLinks",
                "/linkgrabberv2/addContainer", "/linkgrabberv2/moveToDownloadlist",
                "/extraction/getQueue", "/extraction/getArchiveInfo", "/extraction/getArchiveSettings",
                "/extraction/setArchiveSettings", "/extraction/startExtractionNow",
                "/reconnect/doReconnect", "/log/getAvailableLogs", "/log/sendLogFile",
                "/update/isUpdateAvailable", "/update/runUpdateCheck", "/update/restartAndUpdate",
                "/system/exitJD", "/system/shutdownOS", "/system/standbyOS", "/system/hibernateOS",
                "/system/restartJD", "/session/handshake");
        for (String path : required) check(paths.contains(path), "required typed route covered: " + path);

        EnumSet<WorkspacePage> stockPages = EnumSet.of(
                WorkspacePage.ACCOUNTS, WorkspacePage.PLUGINS, WorkspacePage.CAPTCHA,
                WorkspacePage.EXTRACTION, WorkspacePage.SCHEDULER, WorkspacePage.CONNECTIONS,
                WorkspacePage.REMOTE_CONTROL, WorkspacePage.AUTOMATION, WorkspacePage.LOGS);
        for (WorkspacePage page : stockPages) {
            check(StockFeatureView.supports(page), "stock view supports " + page);
            check(!StockFeatureView.catalogFor(page).isEmpty(), "stock view has real API catalog for " + page);
        }
        check(!StockFeatureView.supports(WorkspacePage.DOWNLOADS),
                "purpose-built download page is not misclassified as stock");
    }

    private JDownloaderRemoteClient client(String url, JDownloaderRemoteClient.Options options) {
        return new JDownloaderRemoteClient(() -> url, options);
    }

    private static RemoteResponse await(RemoteCall call) throws Exception {
        return call.future().get(4, TimeUnit.SECONDS);
    }

    private boolean expectRemoteFailure(RemoteCall call, RemoteApiException.Kind expected) throws Exception {
        try {
            await(call);
            return false;
        } catch (ExecutionException failure) {
            Throwable cause = deepestRelevant(failure);
            return cause instanceof RemoteApiException remote && remote.kind() == expected;
        }
    }

    private static Throwable deepestRelevant(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null
                && !(current instanceof RemoteApiException)
                && !(current instanceof CancellationException)) current = current.getCause();
        return current;
    }

    private static boolean allCleared(char[] value) {
        for (char character : value) if (character != '\0') return false;
        return true;
    }

    private void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }

    private <T extends Throwable> T expectThrows(Class<T> expected, ThrowingAction action,
                                                  String description) {
        assertions++;
        try {
            action.run();
        } catch (Throwable failure) {
            Throwable cause = deepestRelevant(failure);
            if (expected.isInstance(cause)) return expected.cast(cause);
            throw new AssertionError(description + ": expected " + expected.getSimpleName()
                    + " but got " + cause.getClass().getSimpleName(), cause);
        }
        throw new AssertionError(description + ": expected " + expected.getSimpleName());
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
