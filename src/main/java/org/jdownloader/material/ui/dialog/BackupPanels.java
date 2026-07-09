package org.jdownloader.material.ui.dialog;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jdownloader.material.engine.Settings;
import org.jdownloader.material.engine.SettingsIO;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.NotificationCenter;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Settings export / import as in-app notification panels (no modal dialogs).
 * Both flows take a passphrase; the backup file is fully encrypted, so secrets
 * ride along safely. Results are reported with snackbars / error cards.
 */
public final class BackupPanels {

    private static final FileChooser.ExtensionFilter FILTER =
            new FileChooser.ExtensionFilter("JDownloader Material backup (*.jdmbackup)", "*.jdmbackup");

    private BackupPanels() {
    }

    // ---------------------------------------------------------------- Export
    public static void openExport(NotificationCenter notifier, Settings settings) {
        notifier.panel("Export settings", "shield", close -> {
            TextField path = new TextField(defaultFile("jdownloader-material-settings.jdmbackup"));
            HBox.setHgrow(path, Priority.ALWAYS);
            var browse = Mat.icon("folder", "Choose destination");
            browse.setOnAction(e -> {
                FileChooser fc = new FileChooser();
                fc.getExtensionFilters().add(FILTER);
                fc.setInitialFileName("jdownloader-material-settings.jdmbackup");
                File f = fc.showSaveDialog(notifier.getScene() == null ? null : notifier.getScene().getWindow());
                if (f != null) path.setText(f.getAbsolutePath());
            });

            PasswordField pass = new PasswordField();
            pass.setPromptText("Passphrase (required)");
            PasswordField confirm = new PasswordField();
            confirm.setPromptText("Repeat passphrase");

            var cancel = Mat.text("Cancel", null);
            cancel.setOnAction(e -> close.run());
            var export = Mat.filled("Export", "check");
            export.setOnAction(e -> {
                if (pass.getText().isEmpty()) {
                    notifier.error("Export failed", "A passphrase is required — it protects the secrets in the backup.");
                    return;
                }
                if (!pass.getText().equals(confirm.getText())) {
                    notifier.error("Export failed", "The passphrases don't match.");
                    return;
                }
                char[] pw = pass.getText().toCharArray();
                try {
                    SettingsIO.exportTo(Path.of(path.getText().trim()), settings, pw);
                    close.run();
                    notifier.snack("Settings exported (encrypted, secrets included)");
                } catch (Exception ex) {
                    notifier.error("Export failed", ex.getMessage() == null
                            ? ex.getClass().getSimpleName() : ex.getMessage());
                } finally {
                    Arrays.fill(pw, '\0');
                }
            });

            HBox pathRow = new HBox(8, path, browse);
            pathRow.setAlignment(Pos.CENTER_LEFT);
            HBox actions = new HBox(8, Mat.hSpacer(), cancel, export);
            actions.setAlignment(Pos.CENTER_RIGHT);
            return form(
                    Mat.label("Destination", "label-md"), pathRow,
                    Mat.label("Encryption", "label-md"), pass, confirm,
                    Mat.label("The whole file is AES-256-GCM encrypted — without the passphrase "
                            + "it cannot be read.", "caption"),
                    actions);
        });
    }

    // ---------------------------------------------------------------- Import
    public static void openImport(NotificationCenter notifier, Settings settings) {
        notifier.panel("Import settings", "shield", close -> {
            TextField path = new TextField();
            path.setPromptText("Select a .jdmbackup file");
            HBox.setHgrow(path, Priority.ALWAYS);
            var browse = Mat.icon("folder", "Choose backup file");
            browse.setOnAction(e -> {
                FileChooser fc = new FileChooser();
                fc.getExtensionFilters().add(FILTER);
                File f = fc.showOpenDialog(notifier.getScene() == null ? null : notifier.getScene().getWindow());
                if (f != null) path.setText(f.getAbsolutePath());
            });

            PasswordField pass = new PasswordField();
            pass.setPromptText("Passphrase");

            var cancel = Mat.text("Cancel", null);
            cancel.setOnAction(e -> close.run());
            var doImport = Mat.filled("Import", "check");
            doImport.setOnAction(e -> {
                char[] pw = pass.getText().toCharArray();
                try {
                    SettingsIO.importFrom(Path.of(path.getText().trim()), settings, pw);
                    close.run();
                    notifier.success("Settings imported",
                            "All settings restored — including My.JDownloader credentials.");
                } catch (SettingsIO.BackupException ex) {
                    notifier.error("Import failed", ex.getMessage());
                } catch (Exception ex) {
                    notifier.error("Import failed", ex.getMessage() == null
                            ? ex.getClass().getSimpleName() : ex.getMessage());
                } finally {
                    Arrays.fill(pw, '\0');
                }
            });

            HBox pathRow = new HBox(8, path, browse);
            pathRow.setAlignment(Pos.CENTER_LEFT);
            HBox actions = new HBox(8, Mat.hSpacer(), cancel, doImport);
            actions.setAlignment(Pos.CENTER_RIGHT);
            return form(
                    Mat.label("Backup file", "label-md"), pathRow,
                    Mat.label("Passphrase", "label-md"), pass,
                    actions);
        });
    }

    private static Node form(Node... children) {
        VBox box = new VBox(10, children);
        box.setFillWidth(true);
        return box;
    }

    private static String defaultFile(String name) {
        return System.getProperty("user.home") + File.separator + name;
    }
}
