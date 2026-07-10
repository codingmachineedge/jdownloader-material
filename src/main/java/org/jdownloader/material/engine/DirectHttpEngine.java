package org.jdownloader.material.engine;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import org.jdownloader.material.model.CrawledLink;
import org.jdownloader.material.model.CrawledPackage;
import org.jdownloader.material.model.DownloadItem;
import org.jdownloader.material.model.DownloadLink;
import org.jdownloader.material.model.DownloadPackage;
import org.jdownloader.material.model.DownloadState;
import org.jdownloader.material.model.LinkAvailability;

/**
 * A real, nonblocking direct-download backend for HTTP and HTTPS URLs.
 * <p>
 * It intentionally handles direct files only: JDownloader hoster plugins,
 * containers, accounts, CAPTCHA, and remote-control APIs still need a future
 * JDownloader-core adapter. Direct transfers nevertheless provide a complete
 * local path from LinkGrabber metadata through a streamed file on disk.
 */
public final class DirectHttpEngine implements DownloadEngine {

    private static final long TICK_MS = 120;
    private static final int LINK_BATCH_SIZE = 128;
    private static final int BUFFER_SIZE = 64 * 1024;

    private final ObservableList<DownloadPackage> downloads = FXCollections.observableArrayList();
    private final ObservableList<CrawledPackage> crawled = FXCollections.observableArrayList();
    private final Settings settings = new Settings();

    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper paused = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyLongWrapper globalSpeed = new ReadOnlyLongWrapper(0);
    private final ReadOnlyIntegerWrapper runningCount = new ReadOnlyIntegerWrapper(0);
    private final ReadOnlyLongWrapper totalRemaining = new ReadOnlyLongWrapper(0);
    private final ReadOnlyBooleanWrapper reconnecting = new ReadOnlyBooleanWrapper(false);

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ExecutorService crawlWorkers = Executors.newFixedThreadPool(4, daemonFactory("link-probe"));
    private final ExecutorService downloadWorkers = Executors.newCachedThreadPool(daemonFactory("http-download"));
    private final ExecutorService stateWriter = Executors.newSingleThreadExecutor(daemonFactory("state-writer"));
    private final Map<String, DirectTransfer> activeTransfers = new ConcurrentHashMap<>();
    private final Map<String, Long> transferEpochs = new ConcurrentHashMap<>();
    private final AtomicLong transferSequence = new AtomicLong();
    private final java.util.Set<String> manuallyStopped = ConcurrentHashMap.newKeySet();
    /** Links deliberately started from a row action while the global queue is idle. */
    private final java.util.Set<String> selectedStartRequests = ConcurrentHashMap.newKeySet();
    /** Selected links allowed to bypass the global queue limits and pause state once. */
    private final java.util.Set<String> forceStartRequests = ConcurrentHashMap.newKeySet();
    private final Set<Path> reservedOutputs = new HashSet<>();
    private final Map<DownloadPackage, ListChangeListener<DownloadLink>> packageLinkListeners = new HashMap<>();
    private final Map<CrawledPackage, ListChangeListener<CrawledLink>> crawledLinkListeners = new HashMap<>();
    private final Object pauseMonitor = new Object();
    private final BandwidthGate bandwidthGate = new BandwidthGate();
    private final AppStateStore stateStore;
    private final PauseTransition stateSaveDelay = new PauseTransition(javafx.util.Duration.millis(650));
    private final InvalidationListener stateDirty = observable -> scheduleStateSave();
    private final AnimationTimer scheduler;

    private volatile boolean pauseRequested;
    private volatile long rateLimitBytesPerSecond;
    private long lastTick;
    private boolean restoringState;
    private boolean stateLoaded;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public DirectHttpEngine() {
        this(AppStateStore.defaultDirectory());
    }

    /** Allows isolated profiles for tests or portable launches. */
    public DirectHttpEngine(Path stateDirectory) {
        this.stateStore = new AppStateStore(stateDirectory);
        refreshRateLimit();
        observeSettings();
        observeDownloads();
        observeCrawled();
        stateSaveDelay.setOnFinished(event -> queueStateWrite());
        scheduler = new AnimationTimer() {
            @Override public void handle(long now) {
                long millis = now / 1_000_000L;
                if (lastTick == 0 || millis - lastTick >= TICK_MS) {
                    lastTick = millis;
                    scheduleQueue();
                }
            }
        };
        scheduler.start();
        loadStateAsync();
    }

    // ------------------------------------------------------------- LinkGrabber
    @Override
    public void addLinks(String text, String packageName, String destination,
                         boolean autoConfirm, boolean autoStart) {
        String source = text == null ? "" : text;
        String requestedName = packageName == null ? "" : packageName.trim();
        String requestedDestination = destination == null || destination.isBlank()
                ? settings.downloadFolderProperty().get() : destination.trim();
        boolean confirmWhenReady = autoConfirm || settings.autoConfirmProperty().get();
        boolean startWhenConfirmed = autoStart || settings.autoStartProperty().get();

        crawlWorkers.execute(() -> {
            List<String> urls = source.lines()
                    .map(String::trim)
                    .filter(DirectHttpEngine::isDirectHttpUrl)
                    .toList();
            if (urls.isEmpty()) return;
            fx(() -> beginCrawl(urls, requestedName, requestedDestination,
                    confirmWhenReady, startWhenConfirmed));
        });
    }

    private void beginCrawl(List<String> urls, String requestedName, String destination,
                            boolean autoConfirm, boolean autoStart) {
        String name = requestedName.isBlank() ? "New Package " + (crawled.size() + 1) : requestedName;
        CrawledPackage pkg = new CrawledPackage(name, destination);
        crawled.add(pkg);
        appendCrawledLinks(pkg, urls, 0, autoConfirm, autoStart);
    }

    private void appendCrawledLinks(CrawledPackage pkg, List<String> urls, int offset,
                                    boolean autoConfirm, boolean autoStart) {
        if (!crawled.contains(pkg)) return;
        int end = Math.min(offset + LINK_BATCH_SIZE, urls.size());
        for (int i = offset; i < end; i++) {
            String url = urls.get(i);
            pkg.links().add(new CrawledLink(fileNameOf(url), hostOf(url), url, 0));
        }
        if (end < urls.size()) {
            fx(() -> appendCrawledLinks(pkg, urls, end, autoConfirm, autoStart));
        } else {
            probePackage(pkg, autoConfirm, autoStart);
        }
    }

    private void probePackage(CrawledPackage pkg, boolean autoConfirm, boolean autoStart) {
        List<CrawledLink> links = new ArrayList<>(pkg.links());
        if (links.isEmpty()) return;
        AtomicInteger remaining = new AtomicInteger(links.size());
        for (CrawledLink link : links) {
            crawlWorkers.execute(() -> {
                Probe probe = probe(link.urlProperty().get(), link.name());
                fx(() -> {
                    if (crawled.contains(pkg) && pkg.links().contains(link)) {
                        if (probe.size >= 0) link.sizeProperty().set(probe.size);
                        if (!probe.fileName.isBlank()) link.nameProperty().set(probe.fileName);
                        link.availabilityProperty().set(probe.online ? LinkAvailability.ONLINE : LinkAvailability.OFFLINE);
                    }
                    if (remaining.decrementAndGet() == 0 && autoConfirm && crawled.contains(pkg)) {
                        confirmToDownloads(List.of(pkg), autoStart);
                    }
                });
            });
        }
    }

    private Probe probe(String url, String fallbackName) {
        try {
            URI uri = URI.create(url);
            HttpResponse<Void> response = http.send(HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build(), HttpResponse.BodyHandlers.discarding());
            if (isSuccessful(response.statusCode())
                    && response.headers().firstValue("content-length").isPresent()) {
                long size = contentLength(response);
                String fileName = fileNameFromDisposition(
                        response.headers().firstValue("content-disposition").orElse(""));
                return new Probe(true, size, fileName.isBlank() ? fallbackName : fileName);
            }

            // Do not discard a potentially huge body here. BodyHandlers.ofInputStream
            // completes as soon as headers arrive, so closing it immediately makes this
            // a true metadata probe even when a server ignores Range.
            HttpResponse<InputStream> ranged = http.send(HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("Range", "bytes=0-0")
                    .GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream ignored = ranged.body()) {
                if (!isSuccessful(ranged.statusCode())) return Probe.offline();
                long size = contentLength(ranged);
                String fileName = fileNameFromDisposition(
                        ranged.headers().firstValue("content-disposition").orElse(""));
                return new Probe(true, size, fileName.isBlank() ? fallbackName : fileName);
            }
        } catch (Exception ignored) {
            return Probe.offline();
        }
    }

    @Override
    public void confirmToDownloads(Collection<CrawledPackage> packages, boolean autoStart) {
        List<CrawledLink> links = new ArrayList<>();
        for (CrawledPackage pkg : packages) {
            if (crawled.contains(pkg)) links.addAll(pkg.links());
        }
        confirmLinksToDownloads(links, autoStart);
    }

    @Override
    public void confirmLinksToDownloads(Collection<CrawledLink> links, boolean autoStart) {
        Map<CrawledPackage, List<CrawledLink>> grouped = new java.util.LinkedHashMap<>();
        for (CrawledLink link : links) {
            CrawledPackage owner = crawled.stream().filter(pkg -> pkg.links().contains(link)).findFirst().orElse(null);
            if (owner != null) grouped.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(link);
        }
        boolean moved = false;
        for (Map.Entry<CrawledPackage, List<CrawledLink>> entry : grouped.entrySet()) {
            CrawledPackage source = entry.getKey();
            List<CrawledLink> ready = entry.getValue().stream()
                    .filter(source.links()::contains)
                    .filter(link -> link.availability() == LinkAvailability.ONLINE)
                    .toList();
            if (ready.isEmpty()) continue;

            DownloadPackage destination = new DownloadPackage(source.name(), source.destinationProperty().get());
            for (CrawledLink link : ready) {
                destination.links().add(link.toDownloadLink(source.destinationProperty().get()));
            }
            if (settings.addAtTopProperty().get()) downloads.add(0, destination);
            else downloads.add(destination);
            source.links().removeAll(ready);
            if (source.links().isEmpty()) crawled.remove(source);
            moved = true;
        }
        if (moved && autoStart) start();
    }

    @Override
    public void confirmAll(boolean autoStart) {
        confirmToDownloads(new ArrayList<>(crawled), autoStart);
    }

    @Override
    public void removeCrawled(Collection<CrawledPackage> packages) {
        crawled.removeAll(packages);
    }

    @Override
    public void removeCrawledLinks(Collection<CrawledLink> links) {
        for (CrawledLink link : new ArrayList<>(links)) {
            for (CrawledPackage pkg : new ArrayList<>(crawled)) {
                if (pkg.links().remove(link) && pkg.links().isEmpty()) {
                    crawled.remove(pkg);
                    break;
                }
            }
        }
    }

    // --------------------------------------------------------- Download queue
    private void scheduleQueue() {
        List<DownloadLink> all = allLinks();
        if (pauseRequested) {
            for (DirectTransfer transfer : activeTransfers.values()) {
                DownloadLink link = transfer.link;
                if (!forceStartRequests.contains(link.id()) && link.state() == DownloadState.RUNNING) {
                    link.setState(DownloadState.PAUSED);
                    link.speedProp().set(0);
                }
            }
            startForcedLinks(all);
            recomputeGlobals(all);
            return;
        }

        if (running.get()) {
            for (DirectTransfer transfer : activeTransfers.values()) {
                if (!transfer.cancelled.get() && transfer.link.state() == DownloadState.PAUSED) {
                    transfer.link.setState(DownloadState.RUNNING);
                }
            }
        }

        int active = activeTransferCount();
        active += startForcedLinks(all);
        int limit = Math.max(1, settings.maxSimultaneousDownloadsProperty().get());
        int hostLimit = Math.max(1, settings.maxConnectionsPerHostProperty().get());
        Map<String, Integer> activeByHost = activeByHost();
        for (DownloadLink link : all) {
            if (active >= limit) break;
            if (!isNormallyEligible(link)) continue;
            String host = normalizedHost(link);
            if (activeByHost.getOrDefault(host, 0) >= hostLimit) continue;
            if (startTransfer(link)) {
                selectedStartRequests.remove(link.id());
                active++;
                activeByHost.merge(host, 1, Integer::sum);
            }
        }
        boolean globalPending = all.stream().anyMatch(link -> link.state() == DownloadState.QUEUED
                && link.enabled().get() && !manuallyStopped.contains(link.id()));
        if (running.get() && active == 0 && !globalPending) running.set(false);
        recomputeGlobals(all);
    }

    /** Starts Force Start selections first, intentionally bypassing queue limits. */
    private int startForcedLinks(List<DownloadLink> all) {
        int started = 0;
        for (DownloadLink link : all) {
            if (!forceStartRequests.contains(link.id())) continue;
            if (link.state() == DownloadState.FINISHED || !link.enabled().get()) {
                forceStartRequests.remove(link.id());
                selectedStartRequests.remove(link.id());
                continue;
            }
            if (link.state() == DownloadState.QUEUED && startTransfer(link)) {
                selectedStartRequests.remove(link.id());
                started++;
            }
        }
        return started;
    }

    private boolean isNormallyEligible(DownloadLink link) {
        if (link.state() != DownloadState.QUEUED || !link.enabled().get()
                || manuallyStopped.contains(link.id()) || forceStartRequests.contains(link.id())) {
            return false;
        }
        return running.get() || selectedStartRequests.contains(link.id());
    }

    private int activeTransferCount() {
        int active = 0;
        for (DirectTransfer transfer : activeTransfers.values()) {
            if (!transfer.cancelled.get()) active++;
        }
        return active;
    }

    private Map<String, Integer> activeByHost() {
        Map<String, Integer> result = new java.util.HashMap<>();
        for (DirectTransfer transfer : activeTransfers.values()) {
            if (!transfer.cancelled.get()) result.merge(transfer.host, 1, Integer::sum);
        }
        return result;
    }

    private boolean startTransfer(DownloadLink link) {
        if (activeTransfers.containsKey(link.id())) return false;
        URI uri;
        try {
            uri = URI.create(link.url().get());
        } catch (Exception ex) {
            fail(link, "Invalid download URL");
            clearStartRequests(link);
            return false;
        }
        if (!isDirectHttpUri(uri)) {
            fail(link, "Only direct HTTP(S) URLs are supported");
            clearStartRequests(link);
            return false;
        }
        String destination = link.destinationProperty().get();
        if (destination == null || destination.isBlank()) destination = settings.downloadFolderProperty().get();
        final Path folder;
        try {
            folder = Path.of(destination);
        } catch (Exception ex) {
            fail(link, "Invalid destination folder");
            clearStartRequests(link);
            return false;
        }

        DirectTransfer transfer = new DirectTransfer(link, uri, folder, link.nameProperty().getValue(),
                settings.ifFileExistsProperty().get(), transferSequence.incrementAndGet());
        activeTransfers.put(link.id(), transfer);
        transferEpochs.put(link.id(), transfer.epoch);
        link.detailProperty().set("");
        link.setState(DownloadState.RUNNING);
        try {
            downloadWorkers.execute(transfer);
        } catch (RejectedExecutionException ex) {
            activeTransfers.remove(link.id(), transfer);
            transferEpochs.remove(link.id(), transfer.epoch);
            fail(link, "Download engine is shutting down");
            clearStartRequests(link);
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        manuallyStopped.clear();
        selectedStartRequests.clear();
        forceStartRequests.clear();
        pauseRequested = false;
        paused.set(false);
        running.set(true);
        synchronized (pauseMonitor) { pauseMonitor.notifyAll(); }
    }

    @Override
    public void startLinks(Collection<DownloadLink> links) {
        if (links.isEmpty()) return;
        for (DownloadLink link : links) {
            if (link.state() == DownloadState.FINISHED) continue;
            manuallyStopped.remove(link.id());
            if (!activeTransfers.containsKey(link.id())
                    && (link.state() == DownloadState.PAUSED || link.state() == DownloadState.ERROR)) {
                link.setState(DownloadState.QUEUED);
            }
            selectedStartRequests.add(link.id());
        }
        // A selected Start must not resume unrelated queued work or clear a global pause.
        if (!pauseRequested) scheduleQueue();
    }

    @Override
    public void pause(boolean shouldPause) {
        pauseRequested = shouldPause;
        paused.set(shouldPause);
        if (shouldPause) {
            for (DownloadLink link : allLinks()) {
                if (link.state() == DownloadState.RUNNING) {
                    link.setState(DownloadState.PAUSED);
                    link.speedProp().set(0);
                }
            }
        } else {
            running.set(true);
            synchronized (pauseMonitor) { pauseMonitor.notifyAll(); }
        }
    }

    @Override
    public void stop() {
        running.set(false);
        paused.set(false);
        pauseRequested = false;
        selectedStartRequests.clear();
        forceStartRequests.clear();
        for (DirectTransfer transfer : new ArrayList<>(activeTransfers.values())) transfer.cancel();
        for (DownloadLink link : allLinks()) {
            if (link.state() == DownloadState.RUNNING || link.state() == DownloadState.PAUSED) {
                link.setState(DownloadState.QUEUED);
                link.speedProp().set(0);
            }
        }
        synchronized (pauseMonitor) { pauseMonitor.notifyAll(); }
        recomputeGlobals(allLinks());
    }

    @Override
    public void stopLinks(Collection<DownloadLink> links) {
        for (DownloadLink link : links) {
            if (link.state() == DownloadState.FINISHED) continue;
            manuallyStopped.add(link.id());
            selectedStartRequests.remove(link.id());
            forceStartRequests.remove(link.id());
            cancelTransfer(link);
            link.speedProp().set(0);
            link.setState(DownloadState.QUEUED);
        }
        recomputeGlobals(allLinks());
    }

    @Override
    public void forceStart(Collection<DownloadLink> links) {
        for (DownloadLink link : links) {
            if (link.state() != DownloadState.FINISHED) {
                link.detailProperty().set("");
                manuallyStopped.remove(link.id());
                if (!activeTransfers.containsKey(link.id())) link.setState(DownloadState.QUEUED);
                selectedStartRequests.add(link.id());
                forceStartRequests.add(link.id());
            }
        }
        // Force Start runs only this selection, even if the normal queue is paused.
        scheduleQueue();
    }

    @Override
    public void removeDownloads(Collection<DownloadItem> items) {
        for (DownloadItem item : new ArrayList<>(items)) {
            if (item instanceof DownloadPackage pkg) {
                for (DownloadLink link : pkg.links()) {
                    cancelTransfer(link);
                    manuallyStopped.remove(link.id());
                    selectedStartRequests.remove(link.id());
                    forceStartRequests.remove(link.id());
                }
                downloads.remove(pkg);
            } else if (item instanceof DownloadLink link) {
                cancelTransfer(link);
                manuallyStopped.remove(link.id());
                selectedStartRequests.remove(link.id());
                forceStartRequests.remove(link.id());
                for (DownloadPackage pkg : downloads) {
                    if (pkg.links().remove(link)) break;
                }
            }
        }
        downloads.removeIf(pkg -> pkg.links().isEmpty());
        recomputeGlobals(allLinks());
    }

    private void cancelTransfer(DownloadLink link) {
        DirectTransfer transfer = activeTransfers.get(link.id());
        if (transfer != null) transfer.cancel();
    }

    @Override
    public void reconnect() {
        if (reconnecting.get()) return;
        reconnecting.set(true);
        PauseTransition wait = new PauseTransition(javafx.util.Duration.seconds(2));
        wait.setOnFinished(e -> reconnecting.set(false));
        wait.play();
    }

    // -------------------------------------------------------------- Transfer
    private final class DirectTransfer implements Runnable {
        private final DownloadLink link;
        private final URI uri;
        private final Path folder;
        private final String requestedName;
        private final Settings.IfExists collisionPolicy;
        private final String host;
        private final long epoch;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile Closeable openInput;
        private volatile Closeable openOutput;
        private volatile CompletableFuture<HttpResponse<InputStream>> requestFuture;

        private DirectTransfer(DownloadLink link, URI uri, Path folder, String requestedName,
                               Settings.IfExists collisionPolicy, long epoch) {
            this.link = link;
            this.uri = uri;
            this.folder = folder;
            this.requestedName = requestedName;
            this.collisionPolicy = Objects.requireNonNullElse(collisionPolicy, Settings.IfExists.RENAME);
            this.host = normalizedHost(link);
            this.epoch = epoch;
        }

        @Override
        public void run() {
            OutputPlan output = null;
            try {
                output = prepareOutput(link, folder, requestedName, collisionPolicy);
                if (output.skip) {
                    finishSkipped(this, output.path);
                    return;
                }
                stream(output);
            } catch (Exception error) {
                if (!cancelled.get()) finishFailed(this, readableError(error));
            } finally {
                closeQuietly(openInput);
                closeQuietly(openOutput);
                activeTransfers.remove(link.id(), this);
                if (cancelled.get()) transferEpochs.remove(link.id(), epoch);
                if (output != null && !output.skip) releaseOutput(output.path);
            }
        }

        private void stream(OutputPlan output) throws Exception {
            stream(output, output.resume, false);
        }

        private void stream(OutputPlan output, boolean allowResume, boolean restartedAfterChange) throws Exception {
            if (cancelled.get()) return;
            Path partial = output.partial;
            long existing = allowResume && Files.exists(partial) ? Files.size(partial) : 0;
            PartialIdentity identity = existing > 0 ? readPartialIdentity(partial) : null;
            if (existing > 0 && identity == null) {
                throw new IOException("Partial file has no safe resume identity");
            }
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).GET();
            if (existing > 0) {
                request.header("Range", "bytes=" + existing + "-");
                if (!identity.validator.isBlank()) request.header("If-Range", identity.validator);
            }
            requestFuture = http.sendAsync(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            HttpResponse<InputStream> response = requestFuture.get();
            if (cancelled.get()) {
                closeQuietly(response.body());
                return;
            }
            int status = response.statusCode();
            if (!(status == 200 || status == 206)) {
                closeQuietly(response.body());
                throw new IOException("HTTP " + status);
            }
            boolean append = existing > 0 && status == 206;
            if (append && rangeStart(response) != existing) {
                closeQuietly(response.body());
                throw new IOException("Server returned an invalid resume range");
            }
            String validator = responseValidator(response);
            if (append && !identity.validator.isBlank() && !validator.isBlank()
                    && !identity.validator.equals(validator)) {
                closeQuietly(response.body());
                Files.deleteIfExists(partial);
                Files.deleteIfExists(partialIdentityPath(partial));
                if (restartedAfterChange) {
                    throw new IOException("Remote file changed during resume");
                }
                stream(output, false, true);
                return;
            }
            if (!append) existing = 0;
            try {
                writePartialIdentity(partial, uri.toString(), validator);
            } catch (IOException error) {
                closeQuietly(response.body());
                throw error;
            }
            long total = transferTotal(response, existing, append);
            long resumed = existing;
            fx(() -> {
                if (isCurrent(this)) {
                    if (total >= 0) link.totalProp().set(total);
                    if (total >= 0) link.loadedProp().set(Math.min(resumed, total));
                    link.stateProp().set(DownloadState.RUNNING);
                    link.speedProp().set(0);
                }
            });

            long completed;
            try (InputStream input = new BufferedInputStream(response.body());
                 OutputStream outputStream = Files.newOutputStream(partial,
                         StandardOpenOption.CREATE,
                         StandardOpenOption.WRITE,
                         append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING)) {
                openInput = input;
                openOutput = outputStream;
                byte[] buffer = new byte[BUFFER_SIZE];
                long written = existing;
                long sampleBytes = 0;
                long sampleAt = System.nanoTime();
                int read;
                while (!cancelled.get() && (read = input.read(buffer)) >= 0) {
                    waitWhilePaused();
                    if (cancelled.get()) break;
                    outputStream.write(buffer, 0, read);
                    written += read;
                    sampleBytes += read;
                    bandwidthGate.consume(read, rateLimitBytesPerSecond);
                    long now = System.nanoTime();
                    if (now - sampleAt >= 100_000_000L) {
                        long currentWritten = written;
                        long speed = (long) (sampleBytes * 1_000_000_000d / (now - sampleAt));
                        fx(() -> updateProgress(this, currentWritten, speed));
                        sampleAt = now;
                        sampleBytes = 0;
                    }
                }
                outputStream.flush();
                if (cancelled.get()) return;
                if (total >= 0 && written != total) {
                    throw new IOException("Incomplete HTTP response (received " + written
                            + " of " + total + " bytes)");
                }
                completed = written;
            }
            if (cancelled.get()) return;
            moveCompleted(partial, output.path);
            Files.deleteIfExists(partialIdentityPath(partial));
            fx(() -> finishCompleted(this, completed));
        }

        private void waitWhilePaused() {
            while (pauseRequested && !forceStartRequests.contains(link.id()) && !cancelled.get()) {
                fx(() -> {
                    if (isCurrent(this)) {
                        link.setState(DownloadState.PAUSED);
                        link.speedProp().set(0);
                    }
                });
                synchronized (pauseMonitor) {
                    try { pauseMonitor.wait(250); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
                }
            }
            if (!cancelled.get()) fx(() -> {
                if (isCurrent(this)) link.setState(DownloadState.RUNNING);
            });
        }

        private void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            transferEpochs.remove(link.id(), epoch);
            CompletableFuture<HttpResponse<InputStream>> future = requestFuture;
            if (future != null) future.cancel(true);
            closeQuietly(openInput);
            closeQuietly(openOutput);
            synchronized (pauseMonitor) { pauseMonitor.notifyAll(); }
        }
    }

    private void updateProgress(DirectTransfer transfer, long loaded, long speed) {
        DownloadLink link = transfer.link;
        if (!isCurrent(transfer) || link.state() == DownloadState.FINISHED) return;
        link.loadedProp().set(loaded);
        link.speedProp().set(Math.max(0, speed));
    }

    private void finishCompleted(DirectTransfer transfer, long loaded) {
        DownloadLink link = transfer.link;
        if (!isCurrent(transfer)) return;
        // Never inflate the displayed byte count: a completed stream is exactly
        // as long as the file just finalized, even if stale probe metadata differs.
        link.totalProp().set(loaded);
        link.loadedProp().set(loaded);
        link.speedProp().set(0);
        link.detailProperty().set("");
        link.setState(DownloadState.FINISHED);
        clearTransferEpoch(transfer);
    }

    private void finishSkipped(DirectTransfer transfer, Path output) {
        fx(() -> {
            DownloadLink link = transfer.link;
            if (!isCurrent(transfer)) return;
            try {
                long size = Files.size(output);
                link.loadedProp().set(size);
                link.totalProp().set(size);
            } catch (IOException ignored) {
            }
            link.speedProp().set(0);
            link.detailProperty().set("Existing file kept");
            link.setState(DownloadState.FINISHED);
            clearTransferEpoch(transfer);
        });
    }

    private void finishFailed(DirectTransfer transfer, String message) {
        fx(() -> {
            DownloadLink link = transfer.link;
            if (!isCurrent(transfer)) return;
            link.speedProp().set(0);
            link.detailProperty().set(message);
            link.setState(DownloadState.ERROR);
            clearTransferEpoch(transfer);
        });
    }

    private void fail(DownloadLink link, String message) {
        link.speedProp().set(0);
        link.detailProperty().set(message);
        link.setState(DownloadState.ERROR);
    }

    private boolean isCurrent(DirectTransfer transfer) {
        return !transfer.cancelled.get()
                && Objects.equals(transferEpochs.get(transfer.link.id()), transfer.epoch)
                && containsLink(transfer.link);
    }

    private void clearTransferEpoch(DirectTransfer transfer) {
        transferEpochs.remove(transfer.link.id(), transfer.epoch);
        clearStartRequests(transfer.link);
    }

    private void clearStartRequests(DownloadLink link) {
        selectedStartRequests.remove(link.id());
        forceStartRequests.remove(link.id());
    }

    // --------------------------------------------------------------- Helpers
    private void loadStateAsync() {
        stateWriter.execute(() -> {
            PropertiesLoad result;
            try {
                result = new PropertiesLoad(stateStore.read(), null);
            } catch (Exception error) {
                result = new PropertiesLoad(new java.util.Properties(), error);
            }
            PropertiesLoad loaded = result;
            fx(() -> {
                if (shutdown.get()) return;
                if (loaded.error != null) {
                    // Preserve an unreadable or oversized journal in place rather
                    // than replacing the user's queue with an empty snapshot.
                    System.err.println("JDownloader Material kept its unreadable state journal: "
                            + readableError(loaded.error));
                    return;
                }
                restoringState = true;
                try {
                    AppStateStore.restore(loaded.state, settings, downloads, crawled);
                    for (CrawledPackage pkg : new ArrayList<>(crawled)) {
                        if (pkg.links().stream().anyMatch(link -> link.availability() == LinkAvailability.UNKNOWN)) {
                            probePackage(pkg, false, false);
                        }
                    }
                } finally {
                    restoringState = false;
                    stateLoaded = true;
                }
                scheduleStateSave();
            });
        });
    }

    private void observeSettings() {
        settings.downloadFolderProperty().addListener(stateDirty);
        settings.maxSimultaneousDownloadsProperty().addListener(stateDirty);
        settings.maxChunksPerDownloadProperty().addListener(stateDirty);
        settings.ifFileExistsProperty().addListener(stateDirty);
        settings.clipboardMonitoringProperty().addListener(stateDirty);
        settings.autoConfirmProperty().addListener(stateDirty);
        settings.autoStartProperty().addListener(stateDirty);
        settings.addAtTopProperty().addListener(stateDirty);
        settings.speedLimitEnabledProperty().addListener((o, was, is) -> {
            refreshRateLimit();
            scheduleStateSave();
        });
        settings.speedLimitKbpsProperty().addListener((o, was, is) -> {
            refreshRateLimit();
            scheduleStateSave();
        });
        settings.maxConnectionsPerHostProperty().addListener(stateDirty);
        settings.autoReconnectProperty().addListener(stateDirty);
        settings.reconnectMethodProperty().addListener(stateDirty);
        settings.darkThemeProperty().addListener(stateDirty);
        settings.speedInTitleProperty().addListener(stateDirty);
    }

    private void observeDownloads() {
        downloads.addListener((ListChangeListener<DownloadPackage>) change -> {
            while (change.next()) {
                for (DownloadPackage pkg : change.getAddedSubList()) attachPackage(pkg);
                for (DownloadPackage pkg : change.getRemoved()) detachPackage(pkg);
            }
            scheduleStateSave();
        });
        for (DownloadPackage pkg : downloads) attachPackage(pkg);
    }

    private void observeCrawled() {
        crawled.addListener((ListChangeListener<CrawledPackage>) change -> {
            while (change.next()) {
                for (CrawledPackage pkg : change.getAddedSubList()) attachCrawledPackage(pkg);
                for (CrawledPackage pkg : change.getRemoved()) detachCrawledPackage(pkg);
            }
            scheduleStateSave();
        });
        for (CrawledPackage pkg : crawled) attachCrawledPackage(pkg);
    }

    private void attachCrawledPackage(CrawledPackage pkg) {
        if (crawledLinkListeners.containsKey(pkg)) return;
        pkg.nameProperty().addListener(stateDirty);
        pkg.destinationProperty().addListener(stateDirty);
        ListChangeListener<CrawledLink> linksListener = change -> {
            while (change.next()) {
                for (CrawledLink link : change.getAddedSubList()) attachCrawledLink(link);
                for (CrawledLink link : change.getRemoved()) detachCrawledLink(link);
            }
            scheduleStateSave();
        };
        crawledLinkListeners.put(pkg, linksListener);
        pkg.links().addListener(linksListener);
        for (CrawledLink link : pkg.links()) attachCrawledLink(link);
    }

    private void detachCrawledPackage(CrawledPackage pkg) {
        pkg.nameProperty().removeListener(stateDirty);
        pkg.destinationProperty().removeListener(stateDirty);
        ListChangeListener<CrawledLink> linksListener = crawledLinkListeners.remove(pkg);
        if (linksListener != null) pkg.links().removeListener(linksListener);
        for (CrawledLink link : pkg.links()) detachCrawledLink(link);
    }

    private void attachCrawledLink(CrawledLink link) {
        link.nameProperty().addListener(stateDirty);
        link.hostProperty().addListener(stateDirty);
        link.urlProperty().addListener(stateDirty);
        link.sizeProperty().addListener(stateDirty);
        link.availabilityProperty().addListener(stateDirty);
    }

    private void detachCrawledLink(CrawledLink link) {
        link.nameProperty().removeListener(stateDirty);
        link.hostProperty().removeListener(stateDirty);
        link.urlProperty().removeListener(stateDirty);
        link.sizeProperty().removeListener(stateDirty);
        link.availabilityProperty().removeListener(stateDirty);
    }

    private void attachPackage(DownloadPackage pkg) {
        if (packageLinkListeners.containsKey(pkg)) return;
        pkg.nameProp().addListener(stateDirty);
        pkg.destinationProperty().addListener(stateDirty);
        ListChangeListener<DownloadLink> linksListener = change -> {
            while (change.next()) {
                for (DownloadLink link : change.getAddedSubList()) attachLink(link);
                for (DownloadLink link : change.getRemoved()) detachLink(link);
            }
            scheduleStateSave();
        };
        packageLinkListeners.put(pkg, linksListener);
        pkg.links().addListener(linksListener);
        for (DownloadLink link : pkg.links()) attachLink(link);
    }

    private void detachPackage(DownloadPackage pkg) {
        pkg.nameProp().removeListener(stateDirty);
        pkg.destinationProperty().removeListener(stateDirty);
        ListChangeListener<DownloadLink> linksListener = packageLinkListeners.remove(pkg);
        if (linksListener != null) pkg.links().removeListener(linksListener);
        for (DownloadLink link : pkg.links()) detachLink(link);
    }

    private void attachLink(DownloadLink link) {
        link.nameProperty().addListener(stateDirty);
        link.hostProperty().addListener(stateDirty);
        link.url().addListener(stateDirty);
        link.destinationProperty().addListener(stateDirty);
        link.detailProperty().addListener(stateDirty);
        link.loadedProp().addListener(stateDirty);
        link.totalProp().addListener(stateDirty);
        link.speedProp().addListener(stateDirty);
        link.stateProp().addListener(stateDirty);
        link.enabled().addListener(stateDirty);
    }

    private void detachLink(DownloadLink link) {
        link.nameProperty().removeListener(stateDirty);
        link.hostProperty().removeListener(stateDirty);
        link.url().removeListener(stateDirty);
        link.destinationProperty().removeListener(stateDirty);
        link.detailProperty().removeListener(stateDirty);
        link.loadedProp().removeListener(stateDirty);
        link.totalProp().removeListener(stateDirty);
        link.speedProp().removeListener(stateDirty);
        link.stateProp().removeListener(stateDirty);
        link.enabled().removeListener(stateDirty);
    }

    private void scheduleStateSave() {
        if (shutdown.get() || !stateLoaded || restoringState) return;
        if (stateSaveDelay.getStatus() != javafx.animation.Animation.Status.RUNNING) {
            stateSaveDelay.playFromStart();
        }
    }

    private Future<?> queueStateWrite() {
        if (shutdown.get() || !stateLoaded || restoringState) return null;
        return submitStateWrite();
    }

    private Future<?> submitStateWrite() {
        if (!stateLoaded || restoringState) return null;
        java.util.Properties snapshot = AppStateStore.snapshot(settings, new ArrayList<>(downloads),
                new ArrayList<>(crawled));
        try {
            return stateWriter.submit(() -> {
                try {
                    stateStore.write(snapshot);
                } catch (IOException ignored) {
                    // A size cap or temporary filesystem error leaves the last
                    // valid journal untouched; transfers remain usable in memory.
                }
            });
        } catch (RejectedExecutionException ignored) {
            return null;
        }
    }

    private void refreshRateLimit() {
        rateLimitBytesPerSecond = settings.speedLimitEnabledProperty().get()
                ? Math.max(1, settings.speedLimitKbpsProperty().get()) * 1024L : 0;
    }

    private List<DownloadLink> allLinks() {
        List<DownloadLink> result = new ArrayList<>();
        for (DownloadPackage pkg : downloads) result.addAll(pkg.links());
        return result;
    }

    private boolean containsLink(DownloadLink link) {
        return downloads.stream().anyMatch(pkg -> pkg.links().contains(link));
    }

    private void recomputeGlobals(List<DownloadLink> links) {
        long speed = 0;
        long remaining = 0;
        int active = 0;
        for (DownloadLink link : links) {
            if (link.state() == DownloadState.RUNNING) {
                speed += link.speedProp().get();
                active++;
            }
            if (link.state() != DownloadState.FINISHED && link.state() != DownloadState.ERROR
                    && link.state() != DownloadState.DISABLED) {
                remaining += Math.max(0, link.total() - link.loadedProp().get());
            }
        }
        globalSpeed.set(speed);
        runningCount.set(active);
        totalRemaining.set(remaining);
    }

    /**
     * Reserves a final path before a worker opens its partial file. This keeps
     * same-name jobs from writing to the same .part file concurrently.
     */
    private OutputPlan prepareOutput(DownloadLink link, Path folder, String requestedName,
                                     Settings.IfExists policy) throws IOException {
        Files.createDirectories(folder);
        Path base = folder.resolve(sanitizeFileName(requestedName)).toAbsolutePath().normalize();
        synchronized (reservedOutputs) {
            boolean baseExists = Files.exists(base);
            if (baseExists && policy == Settings.IfExists.SKIP) {
                return new OutputPlan(base, partialPath(base), true, false);
            }
            if (!baseExists && !reservedOutputs.contains(base)) {
                Path partial = partialPath(base);
                boolean partialExists = Files.exists(partial);
                boolean resume = partialExists && partialIdentityMatches(partial, link.url().get());
                if (!partialExists || resume) {
                    reservedOutputs.add(base);
                    // A partial file belongs only to the URL fingerprint recorded
                    // beside it; legacy/foreign partials never get mixed in.
                    return new OutputPlan(base, partial, false, resume);
                }
            }
            if (baseExists && policy == Settings.IfExists.OVERWRITE && !reservedOutputs.contains(base)) {
                reservedOutputs.add(base);
                // Overwrite intentionally starts from byte zero, even if an old
                // partial file is still present.
                return new OutputPlan(base, partialPath(base), false, false);
            }
            Path candidate = nextAvailableLocked(base);
            reservedOutputs.add(candidate);
            return new OutputPlan(candidate, partialPath(candidate), false, false);
        }
    }

    private void releaseOutput(Path output) {
        synchronized (reservedOutputs) {
            reservedOutputs.remove(output);
        }
    }

    private Path nextAvailableLocked(Path original) {
        String name = original.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        Path parent = original.getParent();
        for (int number = 1; ; number++) {
            Path candidate = parent.resolve(stem + " (" + number + ")" + extension);
            if (!Files.exists(candidate) && !Files.exists(partialPath(candidate))
                    && !reservedOutputs.contains(candidate)) return candidate;
        }
    }

    private static Path partialPath(Path output) {
        return output.resolveSibling(output.getFileName() + ".part");
    }

    private static Path partialIdentityPath(Path partial) {
        return partial.resolveSibling(partial.getFileName() + ".properties");
    }

    private static boolean partialIdentityMatches(Path partial, String url) throws IOException {
        if (!Files.isRegularFile(partial)) return false;
        if (Files.size(partial) == 0) return true;
        PartialIdentity identity = readPartialIdentity(partial);
        return identity != null && identity.sourceFingerprint.equals(sourceFingerprint(url));
    }

    private static PartialIdentity readPartialIdentity(Path partial) throws IOException {
        Path metadata = partialIdentityPath(partial);
        if (!Files.isRegularFile(metadata) || Files.size(metadata) > 4 * 1024) return null;
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(metadata)) {
            values.load(input);
        }
        String source = values.getProperty("source", "");
        if (source.isBlank()) return null;
        return new PartialIdentity(source, values.getProperty("validator", ""));
    }

    private static void writePartialIdentity(Path partial, String url, String validator) throws IOException {
        Path metadata = partialIdentityPath(partial);
        Properties values = new Properties();
        values.setProperty("source", sourceFingerprint(url));
        values.setProperty("validator", validator == null ? "" : validator);
        Path temporary = Files.createTempFile(metadata.getParent(), metadata.getFileName().toString(), ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
                values.store(output, "JDownloader Material partial transfer identity");
            }
            try {
                Files.move(temporary, metadata, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, metadata, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String responseValidator(HttpResponse<?> response) {
        return response.headers().firstValue("etag")
                .or(() -> response.headers().firstValue("last-modified"))
                .orElse("");
    }

    private static String sourceFingerprint(String url) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((url == null ? "" : url).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toUnsignedString(Objects.hashCode(url), 16);
        }
    }

    private static void moveCompleted(Path partial, Path output) throws IOException {
        try {
            Files.move(partial, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(partial, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isDirectHttpUrl(String value) {
        try { return isDirectHttpUri(URI.create(value)); } catch (Exception ignored) { return false; }
    }

    private static boolean isDirectHttpUri(URI uri) {
        String scheme = uri.getScheme();
        return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                && uri.getHost() != null;
    }

    private static boolean isSuccessful(int status) {
        return status >= 200 && status < 300;
    }

    private static long contentLength(HttpResponse<?> response) {
        String range = response.headers().firstValue("content-range").orElse("");
        int slash = range.lastIndexOf('/');
        if (slash >= 0) {
            try { return Long.parseLong(range.substring(slash + 1)); } catch (NumberFormatException ignored) { }
        }
        return response.headers().firstValueAsLong("content-length").orElse(-1);
    }

    private static long transferTotal(HttpResponse<?> response, long existing, boolean append) {
        String range = response.headers().firstValue("content-range").orElse("");
        int slash = range.lastIndexOf('/');
        if (slash >= 0) {
            try { return Long.parseLong(range.substring(slash + 1)); } catch (NumberFormatException ignored) { }
        }
        long content = response.headers().firstValueAsLong("content-length").orElse(-1);
        return append && content >= 0 ? existing + content : content;
    }

    private static long rangeStart(HttpResponse<?> response) {
        String range = response.headers().firstValue("content-range").orElse("");
        int space = range.indexOf(' ');
        int dash = range.indexOf('-', space + 1);
        if (space < 0 || dash < 0) return -1;
        try { return Long.parseLong(range.substring(space + 1, dash)); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static String fileNameFromDisposition(String disposition) {
        String lower = disposition.toLowerCase(java.util.Locale.ROOT);
        int index = lower.indexOf("filename=");
        if (index < 0) return "";
        String value = disposition.substring(index + "filename=".length()).trim();
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) value = value.substring(0, semicolon);
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return sanitizeFileName(value);
    }

    private static String fileNameOf(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path == null || path.isBlank() || path.endsWith("/")) return "download.bin";
            int slash = path.lastIndexOf('/');
            return sanitizeFileName(java.net.URLDecoder.decode(path.substring(slash + 1), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return "download.bin";
        }
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host == null ? "unknown" : host.replaceFirst("^www\\.", "");
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static String normalizedHost(DownloadLink link) {
        String host = link.hostProperty().getValue();
        return host == null || host.isBlank() ? "unknown" : host.toLowerCase(java.util.Locale.ROOT);
    }

    private static String sanitizeFileName(String value) {
        String candidate = value == null ? "" : value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (candidate.isBlank() || candidate.equals(".") || candidate.equals("..")) return "download.bin";
        return candidate.length() > 180 ? candidate.substring(0, 180) : candidate;
    }

    private static String readableError(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "Download failed" : message;
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (IOException ignored) { }
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static void fx(Runnable action) {
        try { Platform.runLater(action); } catch (IllegalStateException ignored) { }
    }

    // -------------------------------------------------------------- Accessors
    @Override public ObservableList<DownloadPackage> downloadPackages() { return downloads; }
    @Override public ObservableList<CrawledPackage> crawledPackages() { return crawled; }
    @Override public ReadOnlyBooleanProperty runningProperty() { return running.getReadOnlyProperty(); }
    @Override public ReadOnlyBooleanProperty pausedProperty() { return paused.getReadOnlyProperty(); }
    @Override public ReadOnlyLongProperty globalSpeedProperty() { return globalSpeed.getReadOnlyProperty(); }
    @Override public ReadOnlyIntegerProperty runningCountProperty() { return runningCount.getReadOnlyProperty(); }
    @Override public ReadOnlyLongProperty totalRemainingProperty() { return totalRemaining.getReadOnlyProperty(); }
    @Override public ReadOnlyBooleanProperty reconnectingProperty() { return reconnecting.getReadOnlyProperty(); }
    @Override public Settings settings() { return settings; }

    @Override
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) return;
        scheduler.stop();
        stateSaveDelay.stop();
        Future<?> finalStateWrite = submitStateWrite();
        for (DirectTransfer transfer : new ArrayList<>(activeTransfers.values())) transfer.cancel();
        crawlWorkers.shutdownNow();
        downloadWorkers.shutdownNow();
        stateWriter.shutdown();
        if (finalStateWrite != null) {
            try {
                // Closing the app is the one place where a short bounded flush is
                // worthwhile: it makes a queued .part file recoverable next launch.
                finalStateWrite.get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // The prior journal remains atomic and valid if the final flush times out.
            }
        }
        try {
            stateWriter.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private record Probe(boolean online, long size, String fileName) {
        private static Probe offline() { return new Probe(false, -1, ""); }
    }

    private record OutputPlan(Path path, Path partial, boolean skip, boolean resume) {
    }

    private record PartialIdentity(String sourceFingerprint, String validator) {
    }

    private record PropertiesLoad(java.util.Properties state, Exception error) {
    }

    /** Shared one-second window limiter used by all transfer workers. */
    private static final class BandwidthGate {
        private long windowStartNanos = System.nanoTime();
        private long used;

        void consume(int bytes, long limitPerSecond) {
            if (limitPerSecond <= 0 || bytes <= 0) return;
            while (true) {
                long waitNanos;
                synchronized (this) {
                    long now = System.nanoTime();
                    if (now - windowStartNanos >= 1_000_000_000L) {
                        windowStartNanos = now;
                        used = 0;
                    }
                    if (used + bytes <= limitPerSecond) {
                        used += bytes;
                        return;
                    }
                    waitNanos = 1_000_000_000L - (now - windowStartNanos);
                }
                if (waitNanos <= 0) continue;
                try {
                    Thread.sleep(Math.max(1, waitNanos / 1_000_000L));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
