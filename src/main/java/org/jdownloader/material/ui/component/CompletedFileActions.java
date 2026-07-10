package org.jdownloader.material.ui.component;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.model.DownloadLink;

/** Nonblocking completed-file actions for the Downloads context menu. */
public final class CompletedFileActions {

    private CompletedFileActions() {
    }

    public static void openFile(DownloadLink link, I18n i18n) {
        open(link, false, i18n);
    }

    public static void showInFolder(DownloadLink link, I18n i18n) {
        open(link, true, i18n);
    }

    private static void open(DownloadLink link, boolean folder, I18n i18n) {
        String raw = link.outputPathProperty().get();
        if (raw == null || raw.isBlank()) {
            setDetail(link, i18n.text("completed.no_file"));
            return;
        }
        final Path file;
        try {
            file = Path.of(raw);
        } catch (Exception error) {
            setDetail(link, i18n.text("completed.invalid_path"));
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Path target = folder ? file.getParent() : file;
                if (target == null || !Files.exists(target)) {
                    setDetail(link, i18n.text(folder
                            ? "completed.folder_unavailable" : "completed.file_unavailable"));
                    return;
                }
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    setDetail(link, i18n.text("completed.unsupported"));
                    return;
                }
                Desktop.getDesktop().open(target.toFile());
            } catch (Exception error) {
                String reason = error.getMessage();
                setDetail(link, reason == null || reason.isBlank()
                        ? i18n.text("completed.open_failed")
                        : i18n.text("completed.open_failed_reason", reason));
            }
        });
    }

    private static void setDetail(DownloadLink link, String text) {
        Platform.runLater(() -> link.detailProperty().set(text));
    }
}
