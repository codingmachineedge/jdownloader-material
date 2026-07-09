package org.jdownloader.material.ui.component;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.input.Clipboard;
import javafx.util.Duration;
import org.jdownloader.material.engine.DownloadEngine;

import java.util.List;

/**
 * Watches the system clipboard while the "clipboard monitoring" setting is on
 * and auto-grabs copied URLs into the LinkGrabber — JDownloader's signature
 * behavior, reported through an in-app snackbar instead of a bubble window.
 * <p>
 * Polls on the JavaFX thread via a {@link Timeline}; the clipboard content at
 * startup is treated as already-seen so launching the app never grabs stale
 * clipboard contents.
 */
public final class ClipboardMonitor {

    private final DownloadEngine engine;
    private final NotificationCenter notifier;
    private final Runnable viewLinkGrabber;
    private final Timeline timeline;
    private String lastSeen;

    public ClipboardMonitor(DownloadEngine engine, NotificationCenter notifier, Runnable viewLinkGrabber) {
        this.engine = engine;
        this.notifier = notifier;
        this.viewLinkGrabber = viewLinkGrabber;
        this.lastSeen = readClipboard(); // ignore whatever is on the clipboard at launch
        this.timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> poll()));
        this.timeline.setCycleCount(Timeline.INDEFINITE);
    }

    public void start() {
        timeline.play();
    }

    public void stop() {
        timeline.stop();
    }

    private void poll() {
        if (System.getenv("JD_CLIP_DEBUG") != null) {
            System.out.println("[clip] poll enabled=" + engine.settings().clipboardMonitoringProperty().get()
                    + " value=" + abbrev(readClipboard()) + " lastSeen=" + abbrev(lastSeen));
        }
        if (!engine.settings().clipboardMonitoringProperty().get()) return;
        String text = readClipboard();
        if (text == null || text.equals(lastSeen)) return;
        lastSeen = text;

        List<String> urls = text.lines()
                .flatMap(line -> java.util.Arrays.stream(line.split("\\s+")))
                .map(String::trim)
                .filter(t -> t.startsWith("http://") || t.startsWith("https://"))
                .toList();
        if (urls.isEmpty()) return;

        engine.addLinks(String.join("\n", urls), null, false);
        notifier.snack(urls.size() + (urls.size() == 1 ? " link" : " links")
                + " grabbed from clipboard", "View", viewLinkGrabber);
    }

    private static String readClipboard() {
        try {
            return Clipboard.getSystemClipboard().getString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String abbrev(String s) {
        if (s == null) return "null";
        String one = s.replace("\n", "\\n");
        return one.length() > 60 ? one.substring(0, 60) + "…" : one;
    }
}
