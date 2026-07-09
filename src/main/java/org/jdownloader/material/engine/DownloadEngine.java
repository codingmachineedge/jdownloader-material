package org.jdownloader.material.engine;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.collections.ObservableList;
import org.jdownloader.material.model.CrawledPackage;
import org.jdownloader.material.model.DownloadItem;
import org.jdownloader.material.model.DownloadLink;
import org.jdownloader.material.model.DownloadPackage;

import java.util.Collection;

/**
 * Contract between the Material GUI and a download backend.
 * <p>
 * The GUI depends on this interface only — never on a concrete engine — so the
 * bundled {@link SimulatedEngine} can be swapped for a real adapter over the
 * JDownloader core (link crawler, plugin/hoster system, download controller)
 * without touching a single view. See {@code docs/ENGINE_API.md} for the
 * mapping to the JD core classes each method corresponds to.
 */
public interface DownloadEngine {

    // ---- Observable model exposed to the views ----------------------------
    ObservableList<DownloadPackage> downloadPackages();

    ObservableList<CrawledPackage> crawledPackages();

    // ---- LinkGrabber ------------------------------------------------------
    /** Crawls the given text (one URL per line) into the LinkGrabber. */
    void addLinks(String text, String packageName, boolean autoConfirm);

    /** Moves the selected crawled packages into the Downloads list. */
    void confirmToDownloads(Collection<CrawledPackage> packages, boolean autoStart);

    /** Moves every crawled package into the Downloads list. */
    void confirmAll(boolean autoStart);

    /** Removes crawled packages from the staging area. */
    void removeCrawled(Collection<CrawledPackage> packages);

    // ---- Download control -------------------------------------------------
    /** Starts / resumes the download queue. */
    void start();

    /** Pauses (throttles) all active downloads without dequeuing them. */
    void pause(boolean paused);

    /** Stops all downloads and returns them to the queue. */
    void stop();

    /** Forces the given links to start immediately. */
    void forceStart(Collection<DownloadLink> links);

    /** Removes the given items (packages or links) from the Downloads list. */
    void removeDownloads(Collection<DownloadItem> items);

    /** Triggers a reconnect of the internet connection. */
    void reconnect();

    // ---- Global observable state -----------------------------------------
    ReadOnlyBooleanProperty runningProperty();

    ReadOnlyBooleanProperty pausedProperty();

    ReadOnlyLongProperty globalSpeedProperty();

    ReadOnlyIntegerProperty runningCountProperty();

    ReadOnlyLongProperty totalRemainingProperty();

    ReadOnlyBooleanProperty reconnectingProperty();

    // ---- Configuration ----------------------------------------------------
    Settings settings();

    /** Releases any resources / timers held by the engine. */
    void shutdown();
}
