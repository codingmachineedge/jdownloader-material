package org.jdownloader.material.engine;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
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
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.engine.history.GitHistoryService;
import org.jdownloader.material.engine.history.HistoryScope;
import org.jdownloader.material.engine.history.HistoryService;
import org.jdownloader.material.engine.history.HistorySnapshot;
import org.jdownloader.material.model.CrawledLink;
import org.jdownloader.material.model.CrawledPackage;
import org.jdownloader.material.model.DownloadItem;
import org.jdownloader.material.model.DownloadLink;
import org.jdownloader.material.model.DownloadPackage;
import org.jdownloader.material.model.DownloadPriority;
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
    private static final int MAX_AUTO_RETRIES = 4;
    private static final long INITIAL_RETRY_DELAY_MILLIS = 2_000;
    private static final long MAX_RETRY_DELAY_MILLIS = 30_000;
    // These identifiers deliberately remain stable English implementation text:
    // retry classification compares them without depending on presentation language.
    private static final String PARTIAL_IDENTITY_ERROR = "Partial file has no safe resume identity";
    private static final String INVALID_RESUME_RANGE_ERROR = "Server returned an invalid resume range";
    private static final String REMOTE_FILE_CHANGED_ERROR = "Remote file changed during resume";
    private static final String HISTORY_QUEUE_RUNNING = "history.queue.running";
    private static final String HISTORY_QUEUE_PAUSED = "history.queue.paused";
    private static final String HISTORY_QUEUE_MANUALLY_STOPPED = "history.queue.manuallyStopped";
    private static final String HISTORY_QUEUE_SELECTED = "history.queue.selected";
    private static final String HISTORY_QUEUE_FORCED = "history.queue.forced";

    private final ObservableList<DownloadPackage> downloads = FXCollections.observableArrayList();
    private final ObservableList<CrawledPackage> crawled = FXCollections.observableArrayList();
    private final Settings settings = new Settings();
    private final I18n i18n = new I18n(settings.languageProperty());

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
    /** Invalidates asynchronous crawler callbacks when history replaces the model. */
    private final AtomicLong modelEpoch = new AtomicLong();
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
    private final HistoryService history;
    private final PauseTransition stateSaveDelay = new PauseTransition(javafx.util.Duration.millis(650));
    private final PauseTransition historySettingsDelay = new PauseTransition(javafx.util.Duration.millis(420));
    private final InvalidationListener stateDirty = observable -> scheduleStateSave();
    private final InvalidationListener historySettingsDirty = observable -> scheduleSettingsHistory();
    private final AnimationTimer scheduler;

    private volatile boolean pauseRequested;
    private volatile long rateLimitBytesPerSecond;
    private long lastTick;
    private boolean restoringState;
    /** History remains usable even if the separate crash-recovery journal is damaged. */
    private boolean historyReady;
    private boolean stateLoaded;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public DirectHttpEngine() {
        this(AppStateStore.defaultDirectory());
    }

    /** Allows isolated profiles for tests or portable launches. */
    public DirectHttpEngine(Path stateDirectory) {
        this.stateStore = new AppStateStore(stateDirectory);
        this.history = new GitHistoryService(stateDirectory.resolve("history"),
                this::captureHistorySnapshot, this::applyHistorySnapshot);
        refreshRateLimit();
        observeSettings();
        observeDownloads();
        observeCrawled();
        stateSaveDelay.setOnFinished(event -> queueStateWrite());
        historySettingsDelay.setOnFinished(event ->
                recordHistory(HistoryScope.SETTINGS, i18n.text("history.summary.settings_changed")));
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
    public CompletableFuture<AddLinksResult> addLinks(String text, String packageName, String destination,
                                                        boolean autoConfirm, boolean autoStart) {
        String source = text == null ? "" : text;
        String requestedName = packageName == null ? "" : packageName.trim();
        String requestedDestination = destination == null || destination.isBlank()
                ? settings.downloadFolderProperty().get() : destination.trim();
        boolean confirmWhenReady = autoConfirm || settings.autoConfirmProperty().get();
        boolean startWhenConfirmed = autoStart || settings.autoStartProperty().get();
        long requestEpoch = modelEpoch.get();
        CompletableFuture<AddLinksResult> result = new CompletableFuture<>();

        try {
            crawlWorkers.execute(() -> {
                List<String> submitted = source.lines().map(String::trim)
                        .filter(line -> !line.isEmpty()).toList();
                List<String> urls = submitted.stream().filter(DirectHttpEngine::isDirectHttpUrl).toList();
                AddLinksResult summary = new AddLinksResult(submitted.size(), urls.size());
                if (urls.isEmpty()) {
                    result.complete(summary);
                    return;
                }
                fx(() -> {
                    if (isCurrentModelEpoch(requestEpoch)) {
                        beginCrawl(urls, requestedName, requestedDestination, confirmWhenReady, startWhenConfirmed,
                                requestEpoch);
                        result.complete(summary);
                    } else {
                        // An explicit history restore wins over an older, still
                        // parsing clipboard/paste submission.
                        result.complete(new AddLinksResult(submitted.size(), 0));
                    }
                });
            });
        } catch (RejectedExecutionException error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    private void beginCrawl(List<String> urls, String requestedName, String destination,
                            boolean autoConfirm, boolean autoStart, long epoch) {
        if (!isCurrentModelEpoch(epoch)) return;
        String name = requestedName.isBlank()
                ? i18n.text("engine.new_package", crawled.size() + 1) : requestedName;
        CrawledPackage pkg = new CrawledPackage(name, destination);
        crawled.add(pkg);
        appendCrawledLinks(pkg, urls, 0, autoConfirm, autoStart, epoch);
    }

    private void appendCrawledLinks(CrawledPackage pkg, List<String> urls, int offset,
                                    boolean autoConfirm, boolean autoStart, long epoch) {
        if (!isCurrentModelEpoch(epoch) || !crawled.contains(pkg)) return;
        int end = Math.min(offset + LINK_BATCH_SIZE, urls.size());
        for (int i = offset; i < end; i++) {
            String url = urls.get(i);
            pkg.links().add(new CrawledLink(fileNameOf(url), hostOf(url), url, 0));
        }
        if (end < urls.size()) {
            fx(() -> appendCrawledLinks(pkg, urls, end, autoConfirm, autoStart, epoch));
        } else {
            recordHistory(HistoryScope.LINKGRABBER,
                    i18n.text("history.summary.added_links", urls.size()));
            probePackage(pkg, autoConfirm, autoStart, epoch, true);
        }
    }

    private void probePackage(CrawledPackage pkg, boolean autoConfirm, boolean autoStart) {
        // Re-probing a restored or recovered LinkGrabber package can change
        // durable, visible metadata. Keep that resulting state in History as
        // well; the per-package checkpoint avoids a revision for each HTTP
        // field while still preserving the completed probe operation.
        probePackage(pkg, autoConfirm, autoStart, modelEpoch.get(), true);
    }

    private void probePackage(CrawledPackage pkg, boolean autoConfirm, boolean autoStart, long epoch,
                              boolean recordProbeResult) {
        if (!isCurrentModelEpoch(epoch)) return;
        List<CrawledLink> links = new ArrayList<>(pkg.links());
        if (links.isEmpty()) return;
        AtomicInteger remaining = new AtomicInteger(links.size());
        AtomicBoolean probeChanged = new AtomicBoolean(false);
        for (CrawledLink link : links) {
            crawlWorkers.execute(() -> {
                Probe probe = probe(link.urlProperty().get(), link.name());
                fx(() -> {
                    if (isCurrentModelEpoch(epoch) && crawled.contains(pkg) && pkg.links().contains(link)) {
                        boolean changed = false;
                        if (probe.size >= 0 && link.size() != probe.size) {
                            link.sizeProperty().set(probe.size);
                            changed = true;
                        }
                        if (!probe.fileName.isBlank() && !Objects.equals(link.name(), probe.fileName)) {
                            link.nameProperty().set(probe.fileName);
                            changed = true;
                        }
                        LinkAvailability availability = probe.online ? LinkAvailability.ONLINE : LinkAvailability.OFFLINE;
                        if (link.availability() != availability) {
                            link.availabilityProperty().set(availability);
                            changed = true;
                        }
                        if (changed) probeChanged.set(true);
                    }
                    if (remaining.decrementAndGet() == 0 && isCurrentModelEpoch(epoch) && crawled.contains(pkg)) {
                        if (recordProbeResult && probeChanged.get()) {
                            recordHistory(HistoryScope.LINKGRABBER, i18n.text("history.summary.probed_links"));
                        }
                        if (autoConfirm) confirmToDownloads(List.of(pkg), autoStart);
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
        if (moved) {
            recordHistory(HistoryScope.DOWNLOAD_LISTS, i18n.text("history.summary.confirmed"));
            if (autoStart) start();
        }
    }

    @Override
    public void confirmAll(boolean autoStart) {
        confirmToDownloads(new ArrayList<>(crawled), autoStart);
    }

    @Override
    public void removeCrawled(Collection<CrawledPackage> packages) {
        if (packages == null || packages.isEmpty()) return;
        if (crawled.removeAll(packages)) {
            recordHistory(HistoryScope.LINKGRABBER, i18n.text("history.summary.removed_linkgrabber_items"));
        }
    }

    @Override
    public void removeCrawledLinks(Collection<CrawledLink> links) {
        if (links == null || links.isEmpty()) return;
        boolean removed = false;
        for (CrawledLink link : new ArrayList<>(links)) {
            for (CrawledPackage pkg : new ArrayList<>(crawled)) {
                if (pkg.links().remove(link)) {
                    removed = true;
                    if (pkg.links().isEmpty()) crawled.remove(pkg);
                    break;
                }
            }
        }
        if (removed) recordHistory(HistoryScope.LINKGRABBER, i18n.text("history.summary.removed_linkgrabber_links"));
    }

    // --------------------------------------------------------- Download queue
    private void scheduleQueue() {
        List<DownloadLink> all = allLinks();
        refreshRetryCountdowns(all, System.currentTimeMillis());
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
        List<DownloadLink> candidates = new ArrayList<>(all);
        candidates.sort(Comparator.comparingInt((DownloadLink link) -> link.priorityProperty().get().weight())
                .reversed());
        for (DownloadLink link : candidates) {
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
            // A Force Start launched manually overrides queue limits, but an
            // automatic retry must still honor its short backoff window.
            if (link.state() == DownloadState.QUEUED && retryEligible(link) && startTransfer(link)) {
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
        return retryEligible(link) && (running.get() || selectedStartRequests.contains(link.id()));
    }

    private boolean retryEligible(DownloadLink link) {
        long retryAt = link.retryAtEpochMillisProperty().get();
        return retryAt <= 0 || (settings.autoReconnectProperty().get()
                && retryAt <= System.currentTimeMillis());
    }

    private void refreshRetryCountdowns(List<DownloadLink> links, long now) {
        boolean retryScheduled = false;
        for (DownloadLink link : links) {
            long retryAt = link.retryAtEpochMillisProperty().get();
            if (link.state() != DownloadState.QUEUED || retryAt <= now
                    || !settings.autoReconnectProperty().get()) continue;
            retryScheduled = true;
            long seconds = Math.max(1, (retryAt - now + 999) / 1_000);
            String detail = retryDetail(link.retryAttemptProperty().get(), seconds,
                    link.retryReasonProperty().get());
            if (!detail.equals(link.detailProperty().get())) link.detailProperty().set(detail);
        }
        // This legacy-named observable now reflects real automatic recovery
        // work rather than a simulated router reconnect.
        reconnecting.set(retryScheduled);
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
            fail(link, i18n.text("engine.invalid_url"));
            clearStartRequests(link);
            return false;
        }
        if (!isDirectHttpUri(uri)) {
            fail(link, i18n.text("engine.only_http"));
            clearStartRequests(link);
            return false;
        }
        String destination = link.destinationProperty().get();
        if (destination == null || destination.isBlank()) destination = settings.downloadFolderProperty().get();
        final Path folder;
        try {
            folder = Path.of(destination);
        } catch (Exception ex) {
            fail(link, i18n.text("engine.invalid_destination"));
            clearStartRequests(link);
            return false;
        }

        DirectTransfer transfer = new DirectTransfer(link, uri, folder, link.nameProperty().getValue(),
                settings.ifFileExistsProperty().get(), transferSequence.incrementAndGet());
        activeTransfers.put(link.id(), transfer);
        transferEpochs.put(link.id(), transfer.epoch);
        link.retryAtEpochMillisProperty().set(0);
        link.detailProperty().set("");
        link.setState(DownloadState.RUNNING);
        try {
            downloadWorkers.execute(transfer);
        } catch (RejectedExecutionException ex) {
            activeTransfers.remove(link.id(), transfer);
            transferEpochs.remove(link.id(), transfer.epoch);
            fail(link, i18n.text("engine.shutting_down"));
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
        recordHistory(HistoryScope.DOWNLOADS, i18n.text("history.summary.started_queue"));
    }

    @Override
    public void startLinks(Collection<DownloadLink> links) {
        if (links.isEmpty()) return;
        for (DownloadLink link : links) {
            if (link.state() == DownloadState.FINISHED || !link.enabled().get()) continue;
            manuallyStopped.remove(link.id());
            if (!activeTransfers.containsKey(link.id())
                    && (link.state() == DownloadState.PAUSED || link.state() == DownloadState.ERROR)) {
                link.setState(DownloadState.QUEUED);
            }
            resetRetry(link);
            selectedStartRequests.add(link.id());
        }
        // A selected Start must not resume unrelated queued work or clear a global pause.
        if (!pauseRequested) scheduleQueue();
        recordHistory(HistoryScope.DOWNLOADS, i18n.text("history.summary.started_selected"));
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
        recordHistory(HistoryScope.DOWNLOADS, i18n.text(shouldPause
                ? "history.summary.paused_queue" : "history.summary.resumed_queue"));
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
            resetRetry(link);
        }
        synchronized (pauseMonitor) { pauseMonitor.notifyAll(); }
        recomputeGlobals(allLinks());
        recordHistory(HistoryScope.DOWNLOADS, i18n.text("history.summary.stopped_queue"));
    }

    @Override
    public void stopLinks(Collection<DownloadLink> links) {
        if (links == null || links.isEmpty()) return;
        for (DownloadLink link : links) {
            if (link.state() == DownloadState.FINISHED) continue;
            manuallyStopped.add(link.id());
            selectedStartRequests.remove(link.id());
            forceStartRequests.remove(link.id());
            cancelTransfer(link);
            link.speedProp().set(0);
            link.setState(DownloadState.QUEUED);
            resetRetry(link);
        }
        recomputeGlobals(allLinks());
        recordHistory(HistoryScope.DOWNLOADS, i18n.text("history.summary.stopped_selected"));
    }

    @Override
    public void setEnabled(Collection<DownloadLink> links, boolean enabled) {
        if (links == null || links.isEmpty()) return;
        for (DownloadLink link : links) {
            link.enabled().set(enabled);
            if (enabled) {
                manuallyStopped.remove(link.id());
                if (link.state() == DownloadState.DISABLED) {
                    resetRetry(link);
                    link.setState(DownloadState.QUEUED);
                }
                continue;
            }
            selectedStartRequests.remove(link.id());
            forceStartRequests.remove(link.id());
            manuallyStopped.remove(link.id());
            resetRetry(link);
            if (link.state() != DownloadState.FINISHED) {
                cancelTransfer(link);
                link.speedProp().set(0);
                link.setState(DownloadState.DISABLED);
            }
        }
        recomputeGlobals(allLinks());
        recordHistory(HistoryScope.DOWNLOADS, i18n.text(enabled
                ? "history.summary.enabled_downloads" : "history.summary.disabled_downloads"));
    }

    @Override
    public void setPriority(Collection<DownloadLink> links, DownloadPriority priority) {
        if (links == null || links.isEmpty()) return;
        DownloadPriority next = priority == null ? DownloadPriority.NORMAL : priority;
        for (DownloadLink link : links) link.priorityProperty().set(next);
        recordHistory(HistoryScope.DOWNLOADS, i18n.text("history.summary.changed_priority"));
    }

    @Override
    public void forceStart(Collection<DownloadLink> links) {
        if (links == null || links.isEmpty()) return;
        for (DownloadLink link : links) {
            if (link.state() != DownloadState.FINISHED && link.enabled().get()) {
                link.detailProperty().set("");
                manuallyStopped.remove(link.id());
                if (!activeTransfers.containsKey(link.id())) link.setState(DownloadState.QUEUED);
                resetRetry(link);
                selectedStartRequests.add(link.id());
                forceStartRequests.add(link.id());
            }
        }
        // Force Start runs only this selection, even if the normal queue is paused.
        scheduleQueue();
        recordHistory(HistoryScope.DOWNLOADS, i18n.text("history.summary.force_started"));
    }

    @Override
    public void removeDownloads(Collection<DownloadItem> items) {
        if (items == null || items.isEmpty()) return;
        boolean changed = false;
        for (DownloadItem item : new ArrayList<>(items)) {
            if (item instanceof DownloadPackage pkg) {
                for (DownloadLink link : pkg.links()) {
                    cancelTransfer(link);
                    manuallyStopped.remove(link.id());
                    selectedStartRequests.remove(link.id());
                    forceStartRequests.remove(link.id());
                }
                changed |= downloads.remove(pkg);
            } else if (item instanceof DownloadLink link) {
                cancelTransfer(link);
                manuallyStopped.remove(link.id());
                selectedStartRequests.remove(link.id());
                forceStartRequests.remove(link.id());
                for (DownloadPackage pkg : downloads) {
                    if (pkg.links().remove(link)) {
                        changed = true;
                        break;
                    }
                }
            }
        }
        changed |= downloads.removeIf(pkg -> pkg.links().isEmpty());
        recomputeGlobals(allLinks());
        if (changed) recordHistory(HistoryScope.DOWNLOADS, i18n.text("history.summary.removed_downloads"));
    }

    private void cancelTransfer(DownloadLink link) {
        DirectTransfer transfer = activeTransfers.get(link.id());
        if (transfer != null) transfer.cancel();
    }

    @Override
    public void reconnect() {
        // Direct HTTP mode cannot reconnect a router. Keep the compatibility
        // hook side-effect free except for admitting any retry that is already due.
        scheduleQueue();
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
                if (!cancelled.get()) finishFailed(this, error);
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
                throw new IOException(PARTIAL_IDENTITY_ERROR);
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
                throw new HttpStatusException(status);
            }
            boolean append = existing > 0 && status == 206;
            if (append && rangeStart(response) != existing) {
                closeQuietly(response.body());
                throw new IOException(INVALID_RESUME_RANGE_ERROR);
            }
            String validator = responseValidator(response);
            if (append && !identity.validator.isBlank() && !validator.isBlank()
                    && !identity.validator.equals(validator)) {
                closeQuietly(response.body());
                Files.deleteIfExists(partial);
                Files.deleteIfExists(partialIdentityPath(partial));
                if (restartedAfterChange) {
                    throw new IOException(REMOTE_FILE_CHANGED_ERROR);
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
                    throw new IncompleteResponseException(written, total);
                }
                completed = written;
            }
            if (cancelled.get()) return;
            moveCompleted(partial, output.path);
            Files.deleteIfExists(partialIdentityPath(partial));
            fx(() -> finishCompleted(this, completed, output.path));
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

    private void finishCompleted(DirectTransfer transfer, long loaded, Path output) {
        DownloadLink link = transfer.link;
        if (!isCurrent(transfer)) return;
        // Never inflate the displayed byte count: a completed stream is exactly
        // as long as the file just finalized, even if stale probe metadata differs.
        link.totalProp().set(loaded);
        link.loadedProp().set(loaded);
        link.speedProp().set(0);
        link.outputPathProperty().set(output.toString());
        String requested = sanitizeFileName(transfer.requestedName);
        String resolved = output.getFileName() == null ? output.toString() : output.getFileName().toString();
        link.detailProperty().set(requested.equals(resolved) ? "" : i18n.text("engine.saved_as", resolved));
        resetRetry(link);
        link.setState(DownloadState.FINISHED);
        clearTransferEpoch(transfer);
        recordHistory(HistoryScope.DOWNLOADS, i18n.text("history.summary.download_completed"));
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
            link.outputPathProperty().set(output.toString());
            link.detailProperty().set(i18n.text("engine.existing_kept"));
            resetRetry(link);
            link.setState(DownloadState.FINISHED);
            clearTransferEpoch(transfer);
            recordHistory(HistoryScope.DOWNLOADS, i18n.text("history.summary.download_skipped"));
        });
    }

    private void finishFailed(DirectTransfer transfer, Exception error) {
        fx(() -> {
            DownloadLink link = transfer.link;
            if (!isCurrent(transfer)) return;
            String message = readableError(error);
            link.speedProp().set(0);
            if (shouldAutoRetry(link, error)) {
                int attempt = link.retryAttemptProperty().get() + 1;
                long delay = retryDelayMillis(attempt);
                long retryAt = System.currentTimeMillis() + delay;
                boolean preserveForceStart = forceStartRequests.contains(link.id());
                link.retryAttemptProperty().set(attempt);
                link.retryAtEpochMillisProperty().set(retryAt);
                link.retryReasonProperty().set(message);
                link.detailProperty().set(retryDetail(attempt, Math.max(1, delay / 1_000), message));
                link.setState(DownloadState.QUEUED);
                // A forced retry continues to bypass the global pause once
                // its own backoff expires; normal retries clear the one-shot
                // selection request as before.
                clearTransferEpoch(transfer, !preserveForceStart);
                // Retrying a selected-only start must not wake unrelated queue items.
                if (!running.get() && !preserveForceStart) selectedStartRequests.add(link.id());
            } else {
                boolean exhausted = settings.autoReconnectProperty().get()
                        && link.retryAttemptProperty().get() >= MAX_AUTO_RETRIES
                        && isTransientFailure(error);
                link.detailProperty().set(exhausted
                        ? i18n.text("engine.failed_after", MAX_AUTO_RETRIES, message) : message);
                resetRetry(link);
                link.setState(DownloadState.ERROR);
                clearTransferEpoch(transfer);
                recordHistory(HistoryScope.DOWNLOADS, i18n.text("history.summary.download_failed"));
            }
        });
    }

    private void fail(DownloadLink link, String message) {
        link.speedProp().set(0);
        link.detailProperty().set(message);
        resetRetry(link);
        link.setState(DownloadState.ERROR);
        // Do this before snapshotting so restoring the failed revision never
        // resurrects a one-shot selected/forced queue request.
        clearStartRequests(link);
        recordHistory(HistoryScope.DOWNLOADS, i18n.text("history.summary.download_failed"));
    }

    private boolean shouldAutoRetry(DownloadLink link, Exception error) {
        return settings.autoReconnectProperty().get()
                && link.retryAttemptProperty().get() < MAX_AUTO_RETRIES
                && isTransientFailure(error);
    }

    private static long retryDelayMillis(int attempt) {
        int exponent = Math.max(0, Math.min(attempt - 1, 4));
        return Math.min(MAX_RETRY_DELAY_MILLIS, INITIAL_RETRY_DELAY_MILLIS << exponent);
    }

    private String retryDetail(int attempt, long seconds, String reason) {
        return reason == null || reason.isBlank()
                ? i18n.text("engine.retrying_plain", seconds, attempt, MAX_AUTO_RETRIES)
                : i18n.text("engine.retrying", seconds, attempt, MAX_AUTO_RETRIES, reason);
    }

    private static boolean isTransientFailure(Exception error) {
        Throwable root = rootCause(error);
        if (root instanceof HttpStatusException status) {
            return status.statusCode == 408 || status.statusCode == 429
                    || status.statusCode >= 500 && status.statusCode <= 599;
        }
        if (root instanceof HttpTimeoutException || root instanceof SocketTimeoutException
                || root instanceof ConnectException) return true;
        if (root instanceof FileSystemException) return false;
        if (root instanceof IOException io) {
            String message = io.getMessage() == null ? "" : io.getMessage();
            return !message.startsWith(PARTIAL_IDENTITY_ERROR)
                    && !message.startsWith(INVALID_RESUME_RANGE_ERROR)
                    && !message.startsWith(REMOTE_FILE_CHANGED_ERROR);
        }
        return false;
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void resetRetry(DownloadLink link) {
        link.retryAttemptProperty().set(0);
        link.retryAtEpochMillisProperty().set(0);
        link.retryReasonProperty().set("");
    }

    /** Stops countdowns immediately when automatic recovery is switched off. */
    private void cancelPendingRetries() {
        for (DownloadLink link : allLinks()) {
            if (link.state() != DownloadState.QUEUED || link.retryAtEpochMillisProperty().get() <= 0) continue;
            String reason = link.retryReasonProperty().get();
            resetRetry(link);
            link.detailProperty().set(reason == null || reason.isBlank()
                    ? i18n.text("engine.retry_cancelled")
                    : i18n.text("engine.retry_cancelled_reason", reason));
            link.setState(DownloadState.ERROR);
            clearStartRequests(link);
        }
        reconnecting.set(false);
    }

    private boolean isCurrent(DirectTransfer transfer) {
        return !transfer.cancelled.get()
                && Objects.equals(transferEpochs.get(transfer.link.id()), transfer.epoch)
                && containsLink(transfer.link);
    }

    private void clearTransferEpoch(DirectTransfer transfer) {
        clearTransferEpoch(transfer, true);
    }

    private void clearTransferEpoch(DirectTransfer transfer, boolean clearRequests) {
        transferEpochs.remove(transfer.link.id(), transfer.epoch);
        if (clearRequests) clearStartRequests(transfer.link);
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
                    // than replacing the user's queue with an empty snapshot. The
                    // in-memory engine and append-only Git history remain usable;
                    // only normal-journal rewrites stay disabled for this run.
                    System.err.println(i18n.text("engine.state_journal_unreadable", readableError(loaded.error)));
                    historyReady = true;
                    history.seedIfEmpty(i18n.text("history.summary.initial"), captureHistorySnapshot());
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
                    historyReady = true;
                }
                scheduleStateSave();
                history.seedIfEmpty(i18n.text("history.summary.initial"), captureHistorySnapshot());
            });
        });
    }

    private void observeSettings() {
        settings.downloadFolderProperty().addListener(stateDirty);
        settings.downloadFolderProperty().addListener(historySettingsDirty);
        settings.maxSimultaneousDownloadsProperty().addListener(stateDirty);
        settings.maxSimultaneousDownloadsProperty().addListener(historySettingsDirty);
        settings.maxChunksPerDownloadProperty().addListener(stateDirty);
        settings.maxChunksPerDownloadProperty().addListener(historySettingsDirty);
        settings.ifFileExistsProperty().addListener(stateDirty);
        settings.ifFileExistsProperty().addListener(historySettingsDirty);
        settings.clipboardMonitoringProperty().addListener(stateDirty);
        settings.clipboardMonitoringProperty().addListener(historySettingsDirty);
        settings.autoConfirmProperty().addListener(stateDirty);
        settings.autoConfirmProperty().addListener(historySettingsDirty);
        settings.autoStartProperty().addListener(stateDirty);
        settings.autoStartProperty().addListener(historySettingsDirty);
        settings.addAtTopProperty().addListener(stateDirty);
        settings.addAtTopProperty().addListener(historySettingsDirty);
        settings.speedLimitEnabledProperty().addListener((o, was, is) -> {
            refreshRateLimit();
            scheduleStateSave();
            scheduleSettingsHistory();
        });
        settings.speedLimitKbpsProperty().addListener((o, was, is) -> {
            refreshRateLimit();
            scheduleStateSave();
            scheduleSettingsHistory();
        });
        settings.maxConnectionsPerHostProperty().addListener(stateDirty);
        settings.maxConnectionsPerHostProperty().addListener(historySettingsDirty);
        settings.autoReconnectProperty().addListener((o, wasEnabled, enabled) -> {
            if (!enabled) cancelPendingRetries();
            scheduleStateSave();
            scheduleSettingsHistory();
        });
        settings.reconnectMethodProperty().addListener(stateDirty);
        settings.reconnectMethodProperty().addListener(historySettingsDirty);
        settings.darkThemeProperty().addListener(stateDirty);
        settings.darkThemeProperty().addListener(historySettingsDirty);
        settings.speedInTitleProperty().addListener(stateDirty);
        settings.speedInTitleProperty().addListener(historySettingsDirty);
        settings.languageProperty().addListener(stateDirty);
        settings.languageProperty().addListener(historySettingsDirty);
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
        link.outputPathProperty().addListener(stateDirty);
        link.detailProperty().addListener(stateDirty);
        link.retryReasonProperty().addListener(stateDirty);
        link.retryAttemptProperty().addListener(stateDirty);
        link.retryAtEpochMillisProperty().addListener(stateDirty);
        link.loadedProp().addListener(stateDirty);
        link.totalProp().addListener(stateDirty);
        link.speedProp().addListener(stateDirty);
        link.stateProp().addListener(stateDirty);
        link.enabled().addListener(stateDirty);
        link.priorityProperty().addListener(stateDirty);
    }

    private void detachLink(DownloadLink link) {
        link.nameProperty().removeListener(stateDirty);
        link.hostProperty().removeListener(stateDirty);
        link.url().removeListener(stateDirty);
        link.destinationProperty().removeListener(stateDirty);
        link.outputPathProperty().removeListener(stateDirty);
        link.detailProperty().removeListener(stateDirty);
        link.retryReasonProperty().removeListener(stateDirty);
        link.retryAttemptProperty().removeListener(stateDirty);
        link.retryAtEpochMillisProperty().removeListener(stateDirty);
        link.loadedProp().removeListener(stateDirty);
        link.totalProp().removeListener(stateDirty);
        link.speedProp().removeListener(stateDirty);
        link.stateProp().removeListener(stateDirty);
        link.enabled().removeListener(stateDirty);
        link.priorityProperty().removeListener(stateDirty);
    }

    private void scheduleStateSave() {
        if (shutdown.get() || !stateLoaded || restoringState) return;
        if (stateSaveDelay.getStatus() != javafx.animation.Animation.Status.RUNNING) {
            stateSaveDelay.playFromStart();
        }
    }

    /** Coalesces text input and slider movement into one semantic Settings revision. */
    private void scheduleSettingsHistory() {
        if (shutdown.get() || !historyReady || restoringState) return;
        historySettingsDelay.playFromStart();
    }

    @Override
    public void recordHistory(HistoryScope scope, String summary) {
        if (shutdown.get() || !historyReady || restoringState) return;
        if (!Platform.isFxApplicationThread()) {
            fx(() -> recordHistory(scope, summary));
            return;
        }
        HistoryScope safeScope = scope == null ? HistoryScope.DOWNLOAD_LISTS : scope;
        String safeSummary = summary == null || summary.isBlank() ? "Changed " + safeScope.storageKey() : summary;
        history.record(safeScope, safeSummary, captureHistorySnapshot());
    }

    /** Captures only app-model data; downloaded files and .part files never enter Git history. */
    private HistorySnapshot captureHistorySnapshot() {
        Properties state = AppStateStore.snapshot(settings, new ArrayList<>(downloads), new ArrayList<>(crawled));
        state.setProperty(HISTORY_QUEUE_RUNNING, Boolean.toString(running.get()));
        state.setProperty(HISTORY_QUEUE_PAUSED, Boolean.toString(paused.get()));
        state.setProperty(HISTORY_QUEUE_MANUALLY_STOPPED, linkPositions(manuallyStopped));
        state.setProperty(HISTORY_QUEUE_SELECTED, linkPositions(selectedStartRequests));
        state.setProperty(HISTORY_QUEUE_FORCED, linkPositions(forceStartRequests));
        return HistorySnapshot.fromState(state);
    }

    /** Applies a historical model point on the FX thread without touching files on disk. */
    private void applyHistorySnapshot(HistorySnapshot snapshot) {
        if (shutdown.get()) throw new IllegalStateException("History restore cancelled while the engine is closing");
        modelEpoch.incrementAndGet();
        restoringState = true;
        try {
            historySettingsDelay.stop();
            // Cancelling transfers leaves their .part files in the normal resumable state.
            // The snapshot restores only in-memory list/settings state; no user file is removed.
            stop();
            Properties state = new Properties();
            state.putAll(snapshot.settingsProperties());
            state.putAll(snapshot.downloadsProperties());
            state.putAll(snapshot.linkGrabberProperties());
            boolean restoreRunning = Boolean.parseBoolean(state.getProperty(HISTORY_QUEUE_RUNNING));
            boolean restorePaused = Boolean.parseBoolean(state.getProperty(HISTORY_QUEUE_PAUSED));
            String manuallyStoppedPositions = state.getProperty(HISTORY_QUEUE_MANUALLY_STOPPED, "");
            String selectedPositions = state.getProperty(HISTORY_QUEUE_SELECTED, "");
            String forcedPositions = state.getProperty(HISTORY_QUEUE_FORCED, "");
            downloads.clear();
            crawled.clear();
            AppStateStore.restore(state, settings, downloads, crawled);
            for (CrawledPackage pkg : new ArrayList<>(crawled)) {
                if (pkg.links().stream().anyMatch(link -> link.availability() == LinkAvailability.UNKNOWN)) {
                    probePackage(pkg, false, false);
                }
            }
            manuallyStopped.clear();
            selectedStartRequests.clear();
            forceStartRequests.clear();
            restoreLinkPositions(manuallyStoppedPositions, manuallyStopped);
            restoreLinkPositions(selectedPositions, selectedStartRequests);
            restoreLinkPositions(forcedPositions, forceStartRequests);
            pauseRequested = restorePaused;
            paused.set(restorePaused);
            running.set(restoreRunning);
            recomputeGlobals(allLinks());
            if (restoreRunning || !selectedStartRequests.isEmpty() || !forceStartRequests.isEmpty()) scheduleQueue();
        } finally {
            restoringState = false;
            scheduleStateSave();
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

    private boolean isCurrentModelEpoch(long epoch) {
        return modelEpoch.get() == epoch;
    }

    /** Stable-within-snapshot package/link positions preserve queue command intent across rehydration. */
    private String linkPositions(Set<String> ids) {
        if (ids.isEmpty()) return "";
        List<String> positions = new ArrayList<>();
        for (int packageIndex = 0; packageIndex < downloads.size(); packageIndex++) {
            List<DownloadLink> links = downloads.get(packageIndex).links();
            for (int linkIndex = 0; linkIndex < links.size(); linkIndex++) {
                if (ids.contains(links.get(linkIndex).id())) positions.add(packageIndex + ":" + linkIndex);
            }
        }
        return String.join(",", positions);
    }

    private void restoreLinkPositions(String encoded, Set<String> target) {
        if (encoded == null || encoded.isBlank()) return;
        for (String position : encoded.split(",")) {
            String[] parts = position.split(":", 2);
            if (parts.length != 2) continue;
            try {
                int packageIndex = Integer.parseInt(parts[0]);
                int linkIndex = Integer.parseInt(parts[1]);
                if (packageIndex >= 0 && packageIndex < downloads.size()) {
                    List<DownloadLink> links = downloads.get(packageIndex).links();
                    if (linkIndex >= 0 && linkIndex < links.size()) target.add(links.get(linkIndex).id());
                }
            } catch (NumberFormatException ignored) {
                // A damaged history token must not prevent restoring the rest of the snapshot.
            }
        }
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

    private String readableError(Exception error) {
        Throwable root = rootCause(error);
        if (root instanceof HttpStatusException status) {
            return i18n.text("engine.http_status", status.statusCode);
        }
        if (root instanceof IncompleteResponseException incomplete) {
            return i18n.text("engine.incomplete_response", incomplete.received, incomplete.total);
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) return i18n.text("engine.failed");
        if (PARTIAL_IDENTITY_ERROR.equals(message)) return i18n.text("engine.partial_identity");
        if (INVALID_RESUME_RANGE_ERROR.equals(message)) return i18n.text("engine.invalid_resume_range");
        if (REMOTE_FILE_CHANGED_ERROR.equals(message)) return i18n.text("engine.remote_changed");
        return message;
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
    @Override public HistoryService history() { return history; }

    @Override
    public void shutdown() {
        // A slider/text-field change may still be inside the short semantic
        // debounce window. Queue its durable revision before closing so a
        // normal immediate exit cannot silently omit it from the timeline.
        if (!shutdown.get() && historyReady && !restoringState
                && historySettingsDelay.getStatus() == javafx.animation.Animation.Status.RUNNING) {
            historySettingsDelay.stop();
            recordHistory(HistoryScope.SETTINGS, i18n.text("history.summary.settings_changed"));
        }
        if (!shutdown.compareAndSet(false, true)) return;
        modelEpoch.incrementAndGet();
        scheduler.stop();
        stateSaveDelay.stop();
        historySettingsDelay.stop();
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
        history.shutdown();
    }

    private record Probe(boolean online, long size, String fileName) {
        private static Probe offline() { return new Probe(false, -1, ""); }
    }

    private static final class HttpStatusException extends IOException {
        private final int statusCode;

        private HttpStatusException(int statusCode) {
            super("HTTP " + statusCode);
            this.statusCode = statusCode;
        }
    }

    /** Keeps transfer accounting typed while preserving the prior IOException retry behavior. */
    private static final class IncompleteResponseException extends IOException {
        private final long received;
        private final long total;

        private IncompleteResponseException(long received, long total) {
            super("Incomplete HTTP response (received " + received + " of " + total + " bytes)");
            this.received = received;
            this.total = total;
        }
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
