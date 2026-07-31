package org.jdownloader.material.integration.jdownloader;

import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.jdownloader.material.engine.Settings;

/**
 * Loopback-only, bounded client for an installed JDownloader Remote API.
 *
 * <p>The client never follows redirects, never logs, never stores credentials,
 * and retains only bounded response text. Positional parameters follow the
 * published order and are percent encoded as UTF-8 query components.</p>
 */
public final class JDownloaderRemoteClient implements AutoCloseable {

    public record Options(Duration connectTimeout, Duration requestTimeout,
                          int maxRequestBytes, int maxResponseBytes) {
        public static final Options DEFAULT = new Options(Duration.ofSeconds(2),
                Duration.ofSeconds(8), 128 * 1024, 2 * 1024 * 1024);

        public Options {
            connectTimeout = positive(connectTimeout, "connectTimeout");
            requestTimeout = positive(requestTimeout, "requestTimeout");
            if (maxRequestBytes < 1 || maxRequestBytes > 8 * 1024 * 1024) {
                throw new IllegalArgumentException("maxRequestBytes is outside the safe range");
            }
            if (maxResponseBytes < 1 || maxResponseBytes > 16 * 1024 * 1024) {
                throw new IllegalArgumentException("maxResponseBytes is outside the safe range");
            }
        }

        private static Duration positive(Duration value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isZero() || value.isNegative() || value.compareTo(Duration.ofMinutes(2)) > 0) {
                throw new IllegalArgumentException(name + " is outside the safe range");
            }
            return value;
        }
    }

    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private final Supplier<String> baseUrl;
    private final Options options;
    private final ExecutorService executor;
    private final HttpClient http;
    private final Set<RemoteCall> active = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public JDownloaderRemoteClient(Settings settings) {
        this(() -> Objects.requireNonNull(settings, "settings").remoteApiBaseUrlProperty().get(), Options.DEFAULT);
    }

    public JDownloaderRemoteClient(Settings settings, Options options) {
        this(() -> Objects.requireNonNull(settings, "settings").remoteApiBaseUrlProperty().get(), options);
    }

    public JDownloaderRemoteClient(Supplier<String> baseUrl, Options options) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.options = Objects.requireNonNull(options, "options");
        this.executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "jdownloader-remote-" + THREAD_IDS.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.http = HttpClient.newBuilder()
                .connectTimeout(options.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(executor)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public Options options() { return options; }

    public RemoteCall execute(RemoteOperation operation, List<String> positionalJson) {
        return execute(operation, positionalJson, "", null);
    }

    public RemoteCall execute(RemoteOperation operation, List<String> positionalJson,
                              ConfirmationToken confirmation) {
        return execute(operation, positionalJson, "", confirmation);
    }

    public RemoteCall execute(RemoteOperation operation, List<String> positionalJson,
                              String body, ConfirmationToken confirmation) {
        Objects.requireNonNull(operation, "operation");
        List<String> parameters = List.copyOf(positionalJson == null ? List.of() : positionalJson);
        if (parameters.size() != operation.parameterCount()) {
            throw new IllegalArgumentException("Expected " + operation.parameterCount()
                    + " positional parameters for " + operation.endpoint());
        }
        if (operation.confirmationRequired()) requireConfirmation(operation.endpoint(), confirmation);
        return send(operation.endpoint(), operation.method(), parameters, body);
    }

    /**
     * Complete escape hatch for documented local endpoints. Unknown endpoints,
     * mutating verbs, and known destructive endpoints always require confirmation.
     */
    public RemoteCall advanced(RemoteEndpoint endpoint, HttpMethod method,
                               List<String> positionalJson, String body,
                               ConfirmationToken confirmation) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(method, "method");
        List<String> parameters = List.copyOf(positionalJson == null ? List.of() : positionalJson);
        List<RemoteOperation> known = RemoteOperation.forEndpoint(endpoint);
        boolean confirmationRequired = known.isEmpty() || method != HttpMethod.GET
                || known.stream().anyMatch(RemoteOperation::confirmationRequired);
        if (confirmationRequired) requireConfirmation(endpoint, confirmation);
        return send(endpoint, method, parameters, body);
    }

    // ---------------------------------------------------------- Typed catalog
    public RemoteCall ping() { return execute(RemoteOperation.DEVICE_PING, List.of()); }
    public RemoteCall directConnectionInfo() { return execute(RemoteOperation.DEVICE_DIRECT_CONNECTIONS, List.of()); }
    public RemoteCall systemInfo() { return execute(RemoteOperation.SYSTEM_INFO, List.of()); }

    public RemoteCall listAccounts(String queryJson) {
        return execute(RemoteOperation.ACCOUNTS_LIST, List.of(RemoteJson.structured(queryJson)));
    }

    public RemoteCall addAccount(String hoster, String username, char[] password) {
        Objects.requireNonNull(password, "password");
        try {
            return execute(RemoteOperation.ACCOUNTS_ADD, List.of(RemoteJson.string(hoster),
                    RemoteJson.string(username), RemoteJson.secret(password)));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public RemoteCall enableAccounts(long[] ids) {
        return execute(RemoteOperation.ACCOUNTS_ENABLE, List.of(RemoteJson.longs(ids)));
    }
    public RemoteCall disableAccounts(long[] ids) {
        return execute(RemoteOperation.ACCOUNTS_DISABLE, List.of(RemoteJson.longs(ids)));
    }
    public RemoteCall removeAccounts(long[] ids, ConfirmationToken token) {
        return execute(RemoteOperation.ACCOUNTS_REMOVE, List.of(RemoteJson.longs(ids)), token);
    }

    public RemoteCall listExtensions(String queryJson) {
        return execute(RemoteOperation.EXTENSIONS_LIST, List.of(RemoteJson.structured(queryJson)));
    }
    public RemoteCall installExtension(String id) {
        return execute(RemoteOperation.EXTENSIONS_INSTALL, List.of(RemoteJson.string(id)));
    }
    public RemoteCall setExtensionEnabled(String className, boolean enabled) {
        return execute(RemoteOperation.EXTENSIONS_ENABLE,
                List.of(RemoteJson.string(className), RemoteJson.bool(enabled)));
    }
    public RemoteCall listPlugins(String queryJson) {
        return execute(RemoteOperation.PLUGINS_LIST, List.of(RemoteJson.structured(queryJson)));
    }
    public RemoteCall queryPlugins(String queryJson) {
        return execute(RemoteOperation.PLUGINS_QUERY, List.of(RemoteJson.structured(queryJson)));
    }
    public RemoteCall pluginSetting(String interfaceName, String displayName, String key) {
        return execute(RemoteOperation.PLUGINS_GET, List.of(RemoteJson.string(interfaceName),
                RemoteJson.string(displayName), RemoteJson.string(key)));
    }
    public RemoteCall setPluginSetting(String interfaceName, String displayName,
                                       String key, String rawJsonValue) {
        return execute(RemoteOperation.PLUGINS_SET, List.of(RemoteJson.string(interfaceName),
                RemoteJson.string(displayName), RemoteJson.string(key), Objects.requireNonNull(rawJsonValue)));
    }

    public RemoteCall listConfig() { return execute(RemoteOperation.CONFIG_LIST, List.of()); }
    public RemoteCall listConfig(String pattern, boolean descriptions, boolean values,
                                 boolean defaults, boolean enumInfo) {
        return execute(RemoteOperation.CONFIG_LIST_FILTERED, List.of(RemoteJson.string(pattern),
                RemoteJson.bool(descriptions), RemoteJson.bool(values), RemoteJson.bool(defaults),
                RemoteJson.bool(enumInfo)));
    }
    public RemoteCall queryConfig(String queryJson) {
        return execute(RemoteOperation.CONFIG_QUERY, List.of(RemoteJson.structured(queryJson)));
    }
    public RemoteCall configValue(String interfaceName, String storage, String key) {
        return execute(RemoteOperation.CONFIG_GET, List.of(RemoteJson.string(interfaceName),
                RemoteJson.string(storage), RemoteJson.string(key)));
    }
    public RemoteCall configDefault(String interfaceName, String storage, String key) {
        return execute(RemoteOperation.CONFIG_GET_DEFAULT, List.of(RemoteJson.string(interfaceName),
                RemoteJson.string(storage), RemoteJson.string(key)));
    }
    public RemoteCall configEnum(String type) {
        return execute(RemoteOperation.CONFIG_LIST_ENUM, List.of(RemoteJson.string(type)));
    }
    public RemoteCall setConfig(String interfaceName, String storage, String key, String rawJsonValue) {
        return execute(RemoteOperation.CONFIG_SET, List.of(RemoteJson.string(interfaceName),
                RemoteJson.string(storage), RemoteJson.string(key), Objects.requireNonNull(rawJsonValue)));
    }
    public RemoteCall resetConfig(String interfaceName, String storage, String key, ConfirmationToken token) {
        return execute(RemoteOperation.CONFIG_RESET, List.of(RemoteJson.string(interfaceName),
                RemoteJson.string(storage), RemoteJson.string(key)), token);
    }

    public RemoteCall listCaptchaJobs() { return execute(RemoteOperation.CAPTCHA_LIST, List.of()); }
    public RemoteCall captchaJob(long id) {
        return execute(RemoteOperation.CAPTCHA_GET, List.of(RemoteJson.number(id)));
    }
    public RemoteCall answerCaptcha(long id, String result, String resultFormat) {
        return execute(RemoteOperation.CAPTCHA_ANSWER, List.of(RemoteJson.number(id),
                RemoteJson.string(result), RemoteJson.string(resultFormat)));
    }
    public RemoteCall listDialogs() { return execute(RemoteOperation.DIALOGS_LIST, List.of()); }
    public RemoteCall dialog(long id, boolean icon, boolean properties) {
        return execute(RemoteOperation.DIALOGS_GET, List.of(RemoteJson.number(id),
                RemoteJson.bool(icon), RemoteJson.bool(properties)));
    }
    public RemoteCall answerDialog(long id, String answerJson) {
        return execute(RemoteOperation.DIALOGS_ANSWER,
                List.of(RemoteJson.number(id), RemoteJson.structured(answerJson)));
    }

    public RemoteCall queryDownloadPackages(String queryJson) {
        return execute(RemoteOperation.DOWNLOADS_QUERY_PACKAGES, List.of(RemoteJson.structured(queryJson)));
    }
    public RemoteCall queryDownloadLinks(String queryJson) {
        return execute(RemoteOperation.DOWNLOADS_QUERY_LINKS, List.of(RemoteJson.structured(queryJson)));
    }
    public RemoteCall startDownloads(long[] linkIds, long[] packageIds) {
        return execute(RemoteOperation.DOWNLOADS_START_SELECTED,
                List.of(RemoteJson.longs(linkIds), RemoteJson.longs(packageIds)));
    }
    public RemoteCall forceDownloads(long[] linkIds, long[] packageIds) {
        return execute(RemoteOperation.DOWNLOADS_FORCE,
                List.of(RemoteJson.longs(linkIds), RemoteJson.longs(packageIds)));
    }
    public RemoteCall removeDownloads(long[] linkIds, long[] packageIds, ConfirmationToken token) {
        return execute(RemoteOperation.DOWNLOADS_REMOVE,
                List.of(RemoteJson.longs(linkIds), RemoteJson.longs(packageIds)), token);
    }
    public RemoteCall startDownloadController() { return execute(RemoteOperation.DOWNLOAD_CONTROLLER_START, List.of()); }
    public RemoteCall stopDownloadController() { return execute(RemoteOperation.DOWNLOAD_CONTROLLER_STOP, List.of()); }
    public RemoteCall pauseDownloads(boolean paused) {
        return execute(RemoteOperation.DOWNLOAD_CONTROLLER_PAUSE, List.of(RemoteJson.bool(paused)));
    }

    public RemoteCall queryLinkgrabberPackages(String queryJson) {
        return execute(RemoteOperation.LINKGRABBER_QUERY_PACKAGES, List.of(RemoteJson.structured(queryJson)));
    }
    public RemoteCall queryLinkgrabberLinks(String queryJson) {
        return execute(RemoteOperation.LINKGRABBER_QUERY_LINKS, List.of(RemoteJson.structured(queryJson)));
    }
    public RemoteCall addLinks(String queryJson) {
        return execute(RemoteOperation.LINKGRABBER_ADD_LINKS, List.of(RemoteJson.structured(queryJson)));
    }
    public RemoteCall addContainer(String type, String content) {
        return execute(RemoteOperation.LINKGRABBER_ADD_CONTAINER,
                List.of(RemoteJson.string(type), RemoteJson.string(content)));
    }
    public RemoteCall moveToDownloads(long[] linkIds, long[] packageIds) {
        return execute(RemoteOperation.LINKGRABBER_MOVE_TO_DOWNLOADS,
                List.of(RemoteJson.longs(linkIds), RemoteJson.longs(packageIds)));
    }
    public RemoteCall removeLinkgrabber(long[] linkIds, long[] packageIds, ConfirmationToken token) {
        return execute(RemoteOperation.LINKGRABBER_REMOVE,
                List.of(RemoteJson.longs(linkIds), RemoteJson.longs(packageIds)), token);
    }
    public RemoteCall clearLinkgrabber(ConfirmationToken token) {
        return execute(RemoteOperation.LINKGRABBER_CLEAR, List.of(), token);
    }

    public RemoteCall extractionQueue() { return execute(RemoteOperation.EXTRACTION_QUEUE, List.of()); }
    public RemoteCall archiveInfo(long[] linkIds, long[] packageIds) {
        return execute(RemoteOperation.EXTRACTION_INFO,
                List.of(RemoteJson.longs(linkIds), RemoteJson.longs(packageIds)));
    }
    public RemoteCall archiveSettings(List<String> archiveIds) {
        return execute(RemoteOperation.EXTRACTION_GET_SETTINGS, List.of(RemoteJson.strings(archiveIds)));
    }
    public RemoteCall setArchiveSettings(String archiveId, String settingsJson) {
        return execute(RemoteOperation.EXTRACTION_SET_SETTINGS,
                List.of(RemoteJson.string(archiveId), RemoteJson.structured(settingsJson)));
    }
    public RemoteCall startExtraction(long[] linkIds, long[] packageIds) {
        return execute(RemoteOperation.EXTRACTION_START,
                List.of(RemoteJson.longs(linkIds), RemoteJson.longs(packageIds)));
    }

    public RemoteCall reconnect(ConfirmationToken token) {
        return execute(RemoteOperation.RECONNECT_NOW, List.of(), token);
    }
    public RemoteCall availableLogs() { return execute(RemoteOperation.LOGS_LIST, List.of()); }
    public RemoteCall sendLogs(String logFoldersJson) {
        return execute(RemoteOperation.LOGS_SEND, List.of(RemoteJson.structured(logFoldersJson)));
    }
    public RemoteCall updateAvailable() { return execute(RemoteOperation.UPDATE_AVAILABLE, List.of()); }
    public RemoteCall runUpdateCheck() { return execute(RemoteOperation.UPDATE_CHECK, List.of()); }
    public RemoteCall restartAndUpdate(ConfirmationToken token) {
        return execute(RemoteOperation.UPDATE_RESTART, List.of(), token);
    }

    public RemoteCall exitJDownloader(ConfirmationToken token) {
        return execute(RemoteOperation.SYSTEM_EXIT_JDOWNLOADER, List.of(), token);
    }
    public RemoteCall shutdownSystem(boolean force, ConfirmationToken token) {
        return execute(RemoteOperation.SYSTEM_SHUTDOWN, List.of(RemoteJson.bool(force)), token);
    }
    public RemoteCall standbySystem(ConfirmationToken token) {
        return execute(RemoteOperation.SYSTEM_STANDBY, List.of(), token);
    }
    public RemoteCall hibernateSystem(ConfirmationToken token) {
        return execute(RemoteOperation.SYSTEM_HIBERNATE, List.of(), token);
    }
    public RemoteCall restartJDownloader(ConfirmationToken token) {
        return execute(RemoteOperation.SYSTEM_RESTART_JDOWNLOADER, List.of(), token);
    }

    /** Optional direct-session handshake. The supplied password is cleared synchronously. */
    public RemoteCall directSessionHandshake(String username, char[] password) {
        Objects.requireNonNull(password, "password");
        try {
            return execute(RemoteOperation.SESSION_HANDSHAKE,
                    List.of(RemoteJson.string(username), RemoteJson.secret(password)));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    // ------------------------------------------------------------- Transport
    private RemoteCall send(RemoteEndpoint endpoint, HttpMethod method,
                            List<String> positionalJson, String body) {
        if (closed.get()) throw new RemoteApiException(RemoteApiException.Kind.CLOSED,
                endpoint, "Remote client is closed");
        URI base = validateBaseUrl(baseUrl.get());
        String safeBody = body == null ? "" : body;
        if (method == HttpMethod.GET && !safeBody.isEmpty()) {
            throw new IllegalArgumentException("GET requests cannot carry a body");
        }
        byte[] bodyBytes = safeBody.getBytes(StandardCharsets.UTF_8);
        URI requestUri = requestUri(base, endpoint, positionalJson);
        int requestBytes = requestUri.toASCIIString().getBytes(StandardCharsets.US_ASCII).length + bodyBytes.length;
        if (requestBytes > options.maxRequestBytes()) {
            throw new RemoteApiException(RemoteApiException.Kind.REQUEST_TOO_LARGE, endpoint,
                    "Remote request exceeds the configured safety limit");
        }

        HttpRequest.Builder request = HttpRequest.newBuilder(requestUri)
                .timeout(options.requestTimeout())
                .header("Accept", "application/json")
                .header("User-Agent", "JDownloader-Material/0.1");
        if (method == HttpMethod.POST) {
            request.header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
        } else {
            request.GET();
        }

        long started = System.nanoTime();
        CompletableFuture<HttpResponse<byte[]>> transport = http.sendAsync(request.build(), limitedBodyHandler(endpoint));
        CompletableFuture<RemoteResponse> result = transport.handle((response, failure) -> {
            if (failure != null) throw new CompletionException(sanitizedFailure(endpoint, failure));
            byte[] bytes = response.body();
            return new RemoteResponse(endpoint, response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse(""),
                    new String(bytes, StandardCharsets.UTF_8), bytes.length,
                    Duration.ofNanos(System.nanoTime() - started));
        });
        RemoteCall call = new RemoteCall(result, transport);
        active.add(call);
        result.whenComplete((response, failure) -> active.remove(call));
        return call;
    }

    private HttpResponse.BodyHandler<byte[]> limitedBodyHandler(RemoteEndpoint endpoint) {
        return info -> {
            OptionalLong declared = info.headers().firstValueAsLong("Content-Length");
            if (declared.isPresent() && declared.getAsLong() > options.maxResponseBytes()) {
                return new LimitedBodySubscriber(endpoint, options.maxResponseBytes(), true);
            }
            return new LimitedBodySubscriber(endpoint, options.maxResponseBytes(), false);
        };
    }

    private static Throwable sanitizedFailure(RemoteEndpoint endpoint, Throwable failure) {
        Throwable cause = unwrap(failure);
        RemoteApiException boundedFailure = findCause(cause, RemoteApiException.class);
        if (boundedFailure != null) return boundedFailure;
        HttpTimeoutException timeout = findCause(cause, HttpTimeoutException.class);
        if (timeout != null || findCause(cause, HttpConnectTimeoutException.class) != null) {
            return new RemoteApiException(RemoteApiException.Kind.TIMEOUT, endpoint,
                    "Remote request timed out for " + endpoint);
        }
        java.util.concurrent.CancellationException cancelled =
                findCause(cause, java.util.concurrent.CancellationException.class);
        if (cancelled != null) return cancelled;
        return new RemoteApiException(RemoteApiException.Kind.TRANSPORT, endpoint,
                "Remote request failed for " + endpoint);
    }

    private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 32; depth++, current = current.getCause()) {
            if (type.isInstance(current)) return type.cast(current);
            if (current.getCause() == current) break;
        }
        return null;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static void requireConfirmation(RemoteEndpoint endpoint, ConfirmationToken confirmation) {
        if (confirmation == null) throw new SecurityException("Explicit UI confirmation is required");
        confirmation.consume(endpoint);
    }

    public static URI validateBaseUrl(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 2_048) {
            throw new IllegalArgumentException("Remote API base URL is missing or too long");
        }
        final URI parsed;
        try {
            parsed = new URI(raw.strip());
        } catch (URISyntaxException invalid) {
            throw new IllegalArgumentException("Remote API base URL is invalid", invalid);
        }
        String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Remote API base URL must use http or https");
        }
        if (parsed.getUserInfo() != null || parsed.getQuery() != null || parsed.getFragment() != null) {
            throw new IllegalArgumentException("Remote API base URL cannot contain credentials, query, or fragment");
        }
        String host = parsed.getHost();
        if (host == null || !isStrictLoopback(host)) {
            throw new IllegalArgumentException("Remote API base URL must use localhost, 127.0.0.0/8, or ::1");
        }
        String decodedPath = Objects.requireNonNullElse(parsed.getPath(), "");
        for (String segment : decodedPath.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Remote API base path cannot traverse directories");
            }
        }
        try {
            return new URI(scheme, null, stripIpv6Brackets(host), parsed.getPort(), decodedPath, null, null);
        } catch (URISyntaxException impossible) {
            throw new IllegalArgumentException("Remote API base URL is invalid", impossible);
        }
    }

    private static boolean isStrictLoopback(String rawHost) {
        String host = stripIpv6Brackets(rawHost).toLowerCase(Locale.ROOT);
        if (host.equals("::1") || host.equals("0:0:0:0:0:0:0:1")) return true;
        if (host.equals("localhost")) {
            try {
                InetAddress[] addresses = InetAddress.getAllByName(host);
                return addresses.length > 0 && Arrays.stream(addresses).allMatch(InetAddress::isLoopbackAddress);
            } catch (Exception unavailable) {
                return false;
            }
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) return false;
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) return false;
            } catch (NumberFormatException invalid) {
                return false;
            }
        }
        return Integer.parseInt(parts[0]) == 127;
    }

    private static String stripIpv6Brackets(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static URI requestUri(URI base, RemoteEndpoint endpoint, List<String> positionalJson) {
        String basePath = Objects.requireNonNullElse(base.getPath(), "");
        while (basePath.endsWith("/") && !basePath.isEmpty()) basePath = basePath.substring(0, basePath.length() - 1);
        String path = basePath + endpoint.path();
        final URI withoutQuery;
        try {
            withoutQuery = new URI(base.getScheme(), null, stripIpv6Brackets(base.getHost()),
                    base.getPort(), path, null, null);
        } catch (URISyntaxException impossible) {
            throw new IllegalArgumentException("Remote endpoint could not be resolved", impossible);
        }
        if (positionalJson == null || positionalJson.isEmpty()) return withoutQuery;
        StringBuilder query = new StringBuilder();
        for (String parameter : positionalJson) {
            if (!query.isEmpty()) query.append('&');
            query.append(percentEncode(Objects.requireNonNull(parameter, "positional parameter")));
        }
        return URI.create(withoutQuery.toASCIIString() + "?" + query);
    }

    static String percentEncode(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length * 3);
        for (byte item : bytes) {
            int octet = item & 0xff;
            if ((octet >= 'a' && octet <= 'z') || (octet >= 'A' && octet <= 'Z')
                    || (octet >= '0' && octet <= '9') || octet == '-' || octet == '_'
                    || octet == '.' || octet == '~') {
                encoded.append((char) octet);
            } else {
                encoded.append('%').append(HEX[octet >>> 4]).append(HEX[octet & 0xf]);
            }
        }
        return encoded.toString();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        for (RemoteCall call : List.copyOf(active)) call.cancel();
        active.clear();
        executor.shutdownNow();
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final RemoteEndpoint endpoint;
        private final int maximum;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream output;
        private final boolean rejectImmediately;
        private Flow.Subscription subscription;
        private int received;

        private LimitedBodySubscriber(RemoteEndpoint endpoint, int maximum, boolean rejectImmediately) {
            this.endpoint = endpoint;
            this.maximum = maximum;
            this.rejectImmediately = rejectImmediately;
            this.output = new ByteArrayOutputStream(Math.min(maximum, 32 * 1024));
        }

        @Override public java.util.concurrent.CompletionStage<byte[]> getBody() { return body; }

        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (rejectImmediately) {
                subscription.cancel();
                tooLarge();
            } else {
                subscription.request(1);
            }
        }

        @Override public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) return;
            for (ByteBuffer source : buffers) {
                ByteBuffer buffer = source.asReadOnlyBuffer();
                int count = buffer.remaining();
                if (count > maximum - received) {
                    subscription.cancel();
                    tooLarge();
                    return;
                }
                byte[] chunk = new byte[count];
                buffer.get(chunk);
                output.writeBytes(chunk);
                received += count;
            }
            subscription.request(1);
        }

        @Override public void onError(Throwable failure) { body.completeExceptionally(failure); }
        @Override public void onComplete() { if (!body.isDone()) body.complete(output.toByteArray()); }

        private void tooLarge() {
            body.completeExceptionally(new RemoteApiException(RemoteApiException.Kind.RESPONSE_TOO_LARGE,
                    endpoint, "Remote response exceeds the configured safety limit"));
        }
    }
}
