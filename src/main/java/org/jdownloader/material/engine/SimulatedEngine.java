package org.jdownloader.material.engine;

import javafx.animation.AnimationTimer;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.model.CrawledLink;
import org.jdownloader.material.model.CrawledPackage;
import org.jdownloader.material.model.DownloadItem;
import org.jdownloader.material.model.DownloadLink;
import org.jdownloader.material.model.DownloadPackage;
import org.jdownloader.material.model.DownloadPriority;
import org.jdownloader.material.model.DownloadState;
import org.jdownloader.material.model.LinkAvailability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A self-contained, in-memory engine that makes the GUI fully interactive
 * without a real backend: it schedules queued links, advances running
 * downloads on the JavaFX pulse, simulates online-availability checks and
 * reconnects, and publishes global speed / count / remaining statistics.
 * <p>
 * It is intentionally the <em>only</em> place that would be replaced by a real
 * JDownloader-core adapter; the views never reference it directly.
 */
public final class SimulatedEngine implements DownloadEngine {

    private static final long TICK_MS = 150;
    private static final long KIB = 1024;
    private static final int CRAWL_BATCH_SIZE = 128;

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

    /** Per-link target throughput assigned on promotion to RUNNING. */
    private final Map<String, Long> targetSpeed = new HashMap<>();
    private final Set<String> manuallyStopped = new java.util.HashSet<>();
    private long lastTick = 0;
    private DownloadLink demoRetryLink;
    private final AnimationTimer timer;
    private final ExecutorService crawlParser = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "link-crawl-parser");
        thread.setDaemon(true);
        return thread;
    });

    public SimulatedEngine() {
        settings.languageProperty().addListener((observable, previous, current) -> refreshDemoLocalizedCopy());
        timer = new AnimationTimer() {
            @Override public void handle(long now) {
                long ms = now / 1_000_000L;
                if (lastTick == 0) { lastTick = ms; return; }
                if (ms - lastTick < TICK_MS) return;
                double dt = (ms - lastTick) / 1000.0;
                lastTick = ms;
                tick(dt);
            }
        };
        timer.start();
    }

    // -------------------------------------------------------------- Scheduling
    private void tick(double dt) {
        List<DownloadLink> all = allLinks();

        if (paused.get()) {
            for (DownloadLink link : all) {
                if (link.state() == DownloadState.RUNNING) {
                    link.setState(DownloadState.PAUSED);
                    link.speedProp().set(0);
                }
            }
            recomputeGlobals(all);
            return;
        }

        if (running.get()) {
            int limit = Math.max(1, settings.maxSimultaneousDownloadsProperty().get());
            int active = (int) all.stream().filter(l -> l.state() == DownloadState.RUNNING).count();
            // Resume paused work before admitting fresh queued links.
            for (DownloadLink l : all) {
                if (active >= limit) break;
                if (l.state() == DownloadState.PAUSED && l.enabled().get()) {
                    l.setState(DownloadState.RUNNING);
                    active++;
                }
            }
            List<DownloadLink> candidates = new ArrayList<>(all);
            candidates.sort(java.util.Comparator.comparingInt(
                    (DownloadLink link) -> link.priorityProperty().get().weight()).reversed());
            for (DownloadLink l : candidates) {
                if (active >= limit) break;
                if (l.state() == DownloadState.QUEUED && l.enabled().get() && !manuallyStopped.contains(l.id())) {
                    l.setState(DownloadState.RUNNING);
                    targetSpeed.put(l.id(), randomSpeed());
                    active++;
                }
            }
        }

        // Advance every running link.
        List<DownloadLink> runningLinks = all.stream()
                .filter(l -> l.state() == DownloadState.RUNNING).toList();
        long assigned = 0;
        Map<String, Long> speeds = new HashMap<>();
        for (DownloadLink l : runningLinks) {
            long base = targetSpeed.getOrDefault(l.id(), randomSpeed());
            // small jitter so the speedmeter looks alive
            long spd = (long) (base * (0.85 + ThreadLocalRandom.current().nextDouble() * 0.3));
            speeds.put(l.id(), spd);
            assigned += spd;
        }
        // Global speed cap.
        if (settings.speedLimitEnabledProperty().get() && assigned > 0) {
            long cap = (long) settings.speedLimitKbpsProperty().get() * KIB;
            if (assigned > cap) {
                double scale = cap / (double) assigned;
                speeds.replaceAll((k, v) -> (long) (v * scale));
            }
        }
        for (DownloadLink l : runningLinks) {
            long spd = speeds.getOrDefault(l.id(), 0L);
            l.speedProp().set(spd);
            long next = l.loadedProp().get() + (long) (spd * dt);
            if (next >= l.total()) {
                l.loadedProp().set(l.total());
                l.speedProp().set(0);
                l.setState(DownloadState.FINISHED);
            } else {
                l.loadedProp().set(next);
            }
        }

        // If nothing left to run, the queue is idle.
        boolean anyActionable = all.stream()
                .anyMatch(l -> l.state() == DownloadState.RUNNING
                        || (running.get() && l.state() == DownloadState.QUEUED && l.enabled().get()
                        && !manuallyStopped.contains(l.id())));
        if (!anyActionable && running.get()) running.set(false);

        recomputeGlobals(all);
    }

    private void recomputeGlobals(List<DownloadLink> all) {
        long spd = 0, remaining = 0;
        int active = 0;
        for (DownloadLink l : all) {
            if (l.state() == DownloadState.RUNNING) { spd += l.speedProp().get(); active++; }
            if (l.state() != DownloadState.FINISHED) remaining += Math.max(0, l.total() - l.loadedProp().get());
        }
        globalSpeed.set(spd);
        runningCount.set(active);
        totalRemaining.set(remaining);
    }

    private List<DownloadLink> allLinks() {
        List<DownloadLink> out = new ArrayList<>();
        for (DownloadPackage p : downloads) out.addAll(p.links());
        return out;
    }

    private static long randomSpeed() {
        // 400 KiB/s .. 3.2 MiB/s
        return (400 + ThreadLocalRandom.current().nextInt(2800)) * KIB;
    }

    // ------------------------------------------------------------- LinkGrabber
    @Override
    public CompletableFuture<AddLinksResult> addLinks(String text, String packageName, String destination,
                                                        boolean autoConfirm, boolean autoStart) {
        String source = text == null ? "" : text;
        String requestedName = packageName == null ? "" : packageName.trim();
        String requestedDestination = destination == null ? "" : destination.trim();
        boolean confirmWhenChecked = autoConfirm || settings.autoConfirmProperty().get();
        boolean startWhenConfirmed = autoStart || settings.autoStartProperty().get();
        CompletableFuture<AddLinksResult> result = new CompletableFuture<>();

        // Parsing can be expensive for a large paste. Do it off the FX thread,
        // then add and inspect the resulting links in small UI-thread batches.
        try {
            crawlParser.execute(() -> {
                List<String> urls = source.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
                AddLinksResult summary = new AddLinksResult(urls.size(), urls.size());
                if (urls.isEmpty()) {
                    result.complete(summary);
                    return;
                }
                Platform.runLater(() -> {
                    beginCrawl(urls, requestedName, requestedDestination, confirmWhenChecked, startWhenConfirmed);
                    result.complete(summary);
                });
            });
        } catch (RejectedExecutionException error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    private void beginCrawl(List<String> urls, String requestedName, String destination,
                            boolean autoConfirm, boolean autoStart) {
        String name = requestedName.isBlank()
                ? i18n.text("engine.new_package", crawled.size() + 1) : requestedName;
        CrawledPackage pkg = new CrawledPackage(name, destination);
        crawled.add(pkg);
        appendCrawledLinks(pkg, urls, 0, autoConfirm, autoStart);
    }

    private void appendCrawledLinks(CrawledPackage pkg, List<String> urls, int offset,
                                    boolean autoConfirm, boolean autoStart) {
        // A user may remove or manually confirm this package while background work is pending.
        if (!crawled.contains(pkg)) return;
        int end = Math.min(offset + CRAWL_BATCH_SIZE, urls.size());
        for (int i = offset; i < end; i++) {
            String url = urls.get(i);
            pkg.links().add(new CrawledLink(fileNameOf(url), hostOf(url), url, 0));
        }
        if (end < urls.size()) {
            Platform.runLater(() -> appendCrawledLinks(pkg, urls, end, autoConfirm, autoStart));
        } else {
            simulateAvailabilityCheck(pkg, autoConfirm, autoStart);
        }
    }

    private void simulateAvailabilityCheck(CrawledPackage pkg, boolean autoConfirm, boolean autoStart) {
        PauseTransition delay = new PauseTransition(Duration.millis(900));
        delay.setOnFinished(e -> updateAvailability(pkg, autoConfirm, autoStart, 0));
        delay.play();
    }

    private void updateAvailability(CrawledPackage pkg, boolean autoConfirm, boolean autoStart, int offset) {
        // Make the deferred result idempotent: it must never resurrect a removed package.
        if (!crawled.contains(pkg)) return;
        int end = Math.min(offset + CRAWL_BATCH_SIZE, pkg.links().size());
        for (int i = offset; i < end; i++) {
            CrawledLink link = pkg.links().get(i);
            boolean online = ThreadLocalRandom.current().nextInt(100) < 90;
            link.availabilityProperty().set(online ? LinkAvailability.ONLINE : LinkAvailability.OFFLINE);
            if (online) {
                link.sizeProperty().set((long) (30 + ThreadLocalRandom.current().nextInt(1500)) * 1024 * 1024);
            }
        }
        if (end < pkg.links().size()) {
            Platform.runLater(() -> updateAvailability(pkg, autoConfirm, autoStart, end));
        } else if (autoConfirm && crawled.contains(pkg)) {
            confirmToDownloads(List.of(pkg), autoStart);
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
            CrawledPackage cp = entry.getKey();
            List<CrawledLink> ready = entry.getValue().stream()
                    .filter(cp.links()::contains)
                    .filter(link -> link.availability() == LinkAvailability.ONLINE)
                    .toList();
            if (ready.isEmpty()) continue;
            DownloadPackage dp = new DownloadPackage(cp.name(), cp.destinationProperty().get());
            for (CrawledLink cl : ready) {
                dp.links().add(cl.toDownloadLink(cp.destinationProperty().get()));
                }
            if (settings.addAtTopProperty().get()) downloads.add(0, dp);
            else downloads.add(dp);
            cp.links().removeAll(ready);
            if (cp.links().isEmpty()) crawled.remove(cp);
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

    // --------------------------------------------------------- Download control
    @Override public void start() { manuallyStopped.clear(); paused.set(false); running.set(true); }

    @Override
    public void startLinks(Collection<DownloadLink> links) {
        if (links.isEmpty()) return;
        paused.set(false);
        for (DownloadLink link : links) {
            if (link.state() == DownloadState.FINISHED || !link.enabled().get()) continue;
            manuallyStopped.remove(link.id());
            if (link.state() == DownloadState.PAUSED || link.state() == DownloadState.ERROR) {
                link.setState(DownloadState.QUEUED);
            }
        }
        running.set(true);
    }

    @Override
    public void pause(boolean p) {
        paused.set(p);
        if (p) running.set(true);
    }

    @Override
    public void stop() {
        running.set(false);
        paused.set(false);
        for (DownloadLink l : allLinks()) {
            if (l.state() == DownloadState.RUNNING || l.state() == DownloadState.PAUSED) {
                l.setState(DownloadState.QUEUED);
                l.speedProp().set(0);
            }
        }
        recomputeGlobals(allLinks());
    }

    @Override
    public void stopLinks(Collection<DownloadLink> links) {
        for (DownloadLink link : links) {
            if (link.state() == DownloadState.FINISHED) continue;
            manuallyStopped.add(link.id());
            if (link.state() == DownloadState.RUNNING || link.state() == DownloadState.PAUSED) {
                link.setState(DownloadState.QUEUED);
                link.speedProp().set(0);
            }
        }
        recomputeGlobals(allLinks());
    }

    @Override
    public void setEnabled(Collection<DownloadLink> links, boolean enabled) {
        for (DownloadLink link : links) {
            link.enabled().set(enabled);
            manuallyStopped.remove(link.id());
            if (enabled && link.state() == DownloadState.DISABLED) {
                link.setState(DownloadState.QUEUED);
            } else if (!enabled && link.state() != DownloadState.FINISHED) {
                targetSpeed.remove(link.id());
                link.speedProp().set(0);
                link.setState(DownloadState.DISABLED);
            }
        }
        recomputeGlobals(allLinks());
    }

    @Override
    public void setPriority(Collection<DownloadLink> links, DownloadPriority priority) {
        DownloadPriority next = priority == null ? DownloadPriority.NORMAL : priority;
        for (DownloadLink link : links) link.priorityProperty().set(next);
    }

    @Override
    public void forceStart(Collection<DownloadLink> links) {
        paused.set(false);
        for (DownloadLink l : links) {
            if (l.state() != DownloadState.FINISHED && l.enabled().get()) {
                manuallyStopped.remove(l.id());
                l.setState(DownloadState.RUNNING);
                targetSpeed.put(l.id(), randomSpeed());
            }
        }
        running.set(true);
    }

    @Override
    public void removeDownloads(Collection<DownloadItem> items) {
        for (DownloadItem it : new ArrayList<>(items)) {
            if (it instanceof DownloadPackage p) {
                p.links().forEach(link -> manuallyStopped.remove(link.id()));
                downloads.remove(p);
            } else if (it instanceof DownloadLink l) {
                manuallyStopped.remove(l.id());
                for (DownloadPackage p : downloads) {
                    if (p.links().remove(l)) break;
                }
            }
        }
        downloads.removeIf(p -> p.links().isEmpty());
        recomputeGlobals(allLinks());
    }

    @Override
    public void reconnect() {
        if (reconnecting.get()) return;
        reconnecting.set(true);
        PauseTransition p = new PauseTransition(Duration.seconds(2.5));
        p.setOnFinished(e -> reconnecting.set(false));
        p.play();
    }

    // ------------------------------------------------------------- Accessors
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
        timer.stop();
        crawlParser.shutdownNow();
    }

    // ------------------------------------------------------------- Demo data
    /** Seeds a few packages so the app opens with representative content. */
    public void seedDemoData() {
        DownloadPackage ubuntu = new DownloadPackage("Ubuntu 24.04 LTS");
        DownloadLink iso = new DownloadLink("ubuntu-24.04-desktop-amd64.iso", "releases.ubuntu.com", 6_100L * 1024 * 1024);
        iso.loadedProp().set((long) (iso.total() * 0.62));
        iso.setState(DownloadState.QUEUED);
        DownloadLink checksum = new DownloadLink("SHA256SUMS", "releases.ubuntu.com", 512);
        checksum.loadedProp().set(512);
        checksum.setState(DownloadState.FINISHED);
        ubuntu.links().addAll(iso, checksum);

        DownloadPackage media = new DownloadPackage("Documentary Series (4 files)");
        for (int i = 1; i <= 4; i++) {
            DownloadLink ep = new DownloadLink("episode_0" + i + ".mkv", "rapidgator.net",
                    (700L + i * 40) * 1024 * 1024);
            if (i == 1) { ep.loadedProp().set(ep.total()); ep.setState(DownloadState.FINISHED); }
            media.links().add(ep);
        }

        DownloadPackage archive = new DownloadPackage("project-backup.rar");
        DownloadLink part1 = new DownloadLink("project-backup.part1.rar", "mega.nz", 1_500L * 1024 * 1024);
        DownloadLink part2 = new DownloadLink("project-backup.part2.rar", "mega.nz", 1_500L * 1024 * 1024);
        part2.setState(DownloadState.ERROR);
        demoRetryLink = part2;
        refreshDemoLocalizedCopy();
        archive.links().addAll(part1, part2);

        downloads.addAll(ubuntu, media, archive);

        CrawledPackage staged = new CrawledPackage("Wallpapers 4K");
        for (int i = 1; i <= 3; i++) {
            CrawledLink cl = new CrawledLink("wallpaper_" + i + ".png", "imgur.com",
                    "https://imgur.com/wallpaper_" + i + ".png", (8L + i) * 1024 * 1024);
            cl.availabilityProperty().set(LinkAvailability.ONLINE);
            staged.links().add(cl);
        }
        crawled.add(staged);

        Platform.runLater(() -> downloads.forEach(DownloadPackage::recompute));
    }

    private void refreshDemoLocalizedCopy() {
        if (demoRetryLink != null) {
            demoRetryLink.detailProperty().set(i18n.text("engine.temporary_host_error"));
        }
    }

    private static String hostOf(String url) {
        try {
            String h = java.net.URI.create(url).getHost();
            return h == null ? "unknown" : h.replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String fileNameOf(String url) {
        int q = url.indexOf('?');
        String base = q >= 0 ? url.substring(0, q) : url;
        int slash = base.lastIndexOf('/');
        String name = slash >= 0 && slash < base.length() - 1 ? base.substring(slash + 1) : base;
        return name.isBlank() ? "download.bin" : name;
    }
}
