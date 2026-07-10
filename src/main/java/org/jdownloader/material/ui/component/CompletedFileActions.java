package org.jdownloader.material.ui.component;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import org.jdownloader.material.model.DownloadLink;

/** Nonblocking completed-file actions for the Downloads context menu. */
public final class CompletedFileActions {

    private CompletedFileActions() {
    }

    public static void openFile(DownloadLink link) {
        open(link, false);
    }

    public static void showInFolder(DownloadLink link) {
        open(link, true);
    }

    private static void open(DownloadLink link, boolean folder) {
        String raw = link.outputPathProperty().get();
        if (raw == null || raw.isBlank()) {
            link.detailProperty().set("No completed file is available yet");
            return;
        }
        final Path file;
        try {
            file = Path.of(raw);
        } catch (Exception error) {
            link.detailProperty().set("Saved file path is invalid");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Path target = folder ? file.getParent() : file;
                if (target == null || !Files.exists(target)) {
                    throw new IllegalStateException(folder ? "Saved folder is unavailable" : "Saved file is unavailable");
                }
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    throw new UnsupportedOperationException("Opening files is not supported on this desktop");
                }
                Desktop.getDesktop().open(target.toFile());
            } catch (Exception error) {
                Platform.runLater(() -> link.detailProperty().set(
                        error.getMessage() == null ? "Could not open completed file" : error.getMessage()));
            }
        });
    }
}
