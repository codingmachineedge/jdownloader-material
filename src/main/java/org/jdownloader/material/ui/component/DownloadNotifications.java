package org.jdownloader.material.ui.component;

import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import org.jdownloader.material.engine.DownloadEngine;
import org.jdownloader.material.model.DownloadLink;
import org.jdownloader.material.model.DownloadPackage;
import org.jdownloader.material.model.DownloadState;
import org.jdownloader.material.util.Formats;

import java.util.HashMap;
import java.util.Map;

/**
 * Surfaces transfer lifecycle events as in-app notification cards — the
 * replacement for JDownloader's bubble notifications. A card appears when a
 * download finishes or fails; only state <em>transitions</em> notify, so
 * pre-finished items present at startup stay silent.
 */
public final class DownloadNotifications {

    private final NotificationCenter notifier;
    private final Map<DownloadLink, ChangeListener<DownloadState>> listeners = new HashMap<>();
    private final ListChangeListener<DownloadLink> linkListListener = c -> {
        while (c.next()) {
            c.getAddedSubList().forEach(this::attach);
            c.getRemoved().forEach(this::detach);
        }
    };

    public DownloadNotifications(DownloadEngine engine, NotificationCenter notifier) {
        this.notifier = notifier;
        engine.downloadPackages().addListener((ListChangeListener<DownloadPackage>) c -> {
            while (c.next()) {
                for (DownloadPackage p : c.getAddedSubList()) {
                    p.links().addListener(linkListListener);
                    p.links().forEach(this::attach);
                }
                for (DownloadPackage p : c.getRemoved()) {
                    p.links().removeListener(linkListListener);
                    p.links().forEach(this::detach);
                }
            }
        });
        for (DownloadPackage p : engine.downloadPackages()) {
            p.links().addListener(linkListListener);
            p.links().forEach(this::attach);
        }
    }

    private void attach(DownloadLink link) {
        if (listeners.containsKey(link)) return;
        ChangeListener<DownloadState> l = (o, was, is) -> {
            if (is == DownloadState.FINISHED && was != DownloadState.FINISHED) {
                notifier.success("Download finished",
                        link.nameProperty().getValue() + "  ·  " + Formats.bytes(link.total()));
            } else if (is == DownloadState.ERROR && was != DownloadState.ERROR) {
                notifier.error("Download failed", link.nameProperty().getValue());
            }
        };
        listeners.put(link, l);
        link.stateProperty().addListener(l);
    }

    private void detach(DownloadLink link) {
        ChangeListener<DownloadState> l = listeners.remove(link);
        if (l != null) link.stateProperty().removeListener(l);
    }
}
