package org.jdownloader.material.ui.component;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.input.Clipboard;
import javafx.util.Duration;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.i18n.I18n;

import java.util.List;

/**
 * Watches the system clipboard while the "clipboard monitoring" setting is on
 * and auto-grabs copied URLs into the LinkGrabber — JDownloader's signature
 * behavior, reported through the fixed status bar instead of a bubble window
 * or floating notification.
 * <p>
 * Polls on the JavaFX thread via a {@link Timeline}; the clipboard content at
 * startup is treated as already-seen so launching the app never grabs stale
 * clipboard contents.
 */
public final class ClipboardMonitor {

    private final DownloadEngine engine;
    private final ActivityStatus activity;
    private final I18n i18n;
    private final Timeline timeline;
    private String lastSeen;

    public ClipboardMonitor(DownloadEngine engine, ActivityStatus activity, I18n i18n) {
        this.engine = engine;
        this.activity = activity;
        this.i18n = i18n;
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
        String text = readClipboard();
        if (!engine.settings().clipboardMonitoringProperty().get()) {
            // Do not enqueue data copied while monitoring was intentionally off.
            lastSeen = text;
            return;
        }
        if (text == null || text.equals(lastSeen)) return;
        lastSeen = text;

        List<String> urls = text.lines()
                .flatMap(line -> java.util.Arrays.stream(line.split("\\s+")))
                .map(String::trim)
                .filter(t -> t.startsWith("http://") || t.startsWith("https://"))
                .toList();
        if (urls.isEmpty()) return;

        engine.addLinks(String.join("\n", urls), null,
                engine.settings().downloadFolderProperty().get(), false, false);
        activity.info(i18n.text(urls.size() == 1 ? "clipboard.grabbed.one" : "clipboard.grabbed.many",
                urls.size()));
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
