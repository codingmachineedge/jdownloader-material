package org.jdownloader.material.ui.view;

import io.github.palexdev.materialfx.controls.MFXToggleButton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import org.jdownloader.material.engine.Settings;
import org.jdownloader.material.engine.SettingsIO;
import org.jdownloader.material.ui.Icons;
import org.jdownloader.material.ui.component.Mat;

/** Material preferences screen: a page rail on the left, setting rows on the right. */
public final class SettingsView extends BorderPane {

    private final Settings s;
    private final StackPane content = new StackPane();
    private final ToggleGroup nav = new ToggleGroup();

    public SettingsView(Settings settings) {
        this.s = settings;
        getStyleClass().add("content-area");

        VBox rail = new VBox(4);
        rail.getStyleClass().add("settings-nav");
        addTab(rail, "General", "settings", generalPage(), true);
        addTab(rail, "Connection", "speed", connectionPage(), false);
        addTab(rail, "Reconnect", "reconnect", reconnectPage(), false);
        addTab(rail, "LinkGrabber", "link", linkgrabberPage(), false);
        addTab(rail, "Appearance", "palette", appearancePage(), false);
        addTab(rail, "Accounts", "account", accountsPage(), false);
        addTab(rail, "Backup", "shield", backupPage(), false);
        addTab(rail, "About", "info", aboutPage(), false);

        var header = new HBox(Mat.label("Settings", "headline"));
        header.getStyleClass().add("view-header");
        setTop(header);
        setLeft(rail);
        setCenter(content);
    }

    private void addTab(VBox rail, String title, String icon, Node page, boolean selected) {
        ToggleButton tab = new ToggleButton(title);
        tab.setGraphic(Icons.of(icon, 20));
        tab.setGraphicTextGap(12);
        tab.getStyleClass().add("settings-tab");
        tab.setMaxWidth(Double.MAX_VALUE);
        tab.setAlignment(Pos.CENTER_LEFT);
        tab.setToggleGroup(nav);
        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("edge-to-edge");
        tab.setOnAction(e -> { tab.setSelected(true); content.getChildren().setAll(sp); });
        rail.getChildren().add(tab);
        if (selected) { tab.setSelected(true); content.getChildren().setAll(sp); }
    }

    // ------------------------------------------------------------- Pages
    private Node page(Node... rows) {
        VBox box = new VBox(rows);
        box.setPadding(new Insets(12, 28, 24, 28));
        box.setFillWidth(true);
        return box;
    }

    private Node generalPage() {
        TextField folder = new TextField(s.downloadFolderProperty().get());
        folder.textProperty().bindBidirectional(s.downloadFolderProperty());
        HBox.setHgrow(folder, Priority.ALWAYS);
        HBox folderCtl = new HBox(folder);
        folderCtl.setAlignment(Pos.CENTER_LEFT);
        folderCtl.setPrefWidth(360);

        ComboBox<Settings.IfExists> ifExists = new ComboBox<>();
        ifExists.getItems().setAll(Settings.IfExists.values());
        ifExists.valueProperty().bindBidirectional(s.ifFileExistsProperty());

        return page(
                sectionTitle("Downloads"),
                row("Default download folder", "Where finished files are saved", folderCtl),
                row("Simultaneous downloads", "How many files download at once",
                        slider(s.maxSimultaneousDownloadsProperty(), 1, 10, 1)),
                row("Connections per download", "Segments used to speed up a single file",
                        slider(s.maxChunksPerDownloadProperty(), 1, 20, 1)),
                row("If a file already exists", "Collisions resolve inline; the default safely auto-renames", ifExists)
        );
    }

    private Node connectionPage() {
        return page(
                sectionTitle("Bandwidth"),
                row("Enable speed limit", "Cap the total download rate", toggle(s.speedLimitEnabledProperty())),
                row("Speed limit (KiB/s)", "Applied when the limit is enabled",
                        slider(s.speedLimitKbpsProperty(), 128, 20000, 128)),
                row("Max connections per host", "Parallel connections to a single hoster",
                        slider(s.maxConnectionsPerHostProperty(), 1, 20, 1))
        );
    }

    private Node reconnectPage() {
        ComboBox<String> method = new ComboBox<>();
        method.getItems().setAll("External command", "Router (UPnP)", "Modem script", "ClR script");
        method.valueProperty().bindBidirectional(s.reconnectMethodProperty());
        return page(
                sectionTitle("Reconnect"),
                row("Automatic reconnect", "Request a new IP when a limit is reached",
                        toggle(s.autoReconnectProperty())),
                row("Reconnect method", "How JDownloader triggers a reconnect", method)
        );
    }

    private Node linkgrabberPage() {
        return page(
                sectionTitle("Link collection"),
                row("Clipboard monitoring", "Auto-grab links copied to the clipboard",
                        toggle(s.clipboardMonitoringProperty())),
                row("Auto-confirm", "Move crawled links to Downloads automatically",
                        toggle(s.autoConfirmProperty())),
                row("Auto-start", "Begin downloading as soon as links are confirmed",
                        toggle(s.autoStartProperty())),
                row("Add at top", "Insert new packages at the top of the list",
                        toggle(s.addAtTopProperty()))
        );
    }

    private Node appearancePage() {
        return page(
                sectionTitle("Theme"),
                row("Dark theme", "Use the Material dark color scheme", toggle(s.darkThemeProperty())),
                row("Show speed in window title", "Mirror the global speed into the title bar",
                        toggle(s.speedInTitleProperty()))
        );
    }

    private Node accountsPage() {
        TextField email = new TextField();
        email.setPromptText("email@example.com");
        email.textProperty().bindBidirectional(s.myjdEmailProperty());
        email.setPrefWidth(280);
        PasswordField password = new PasswordField();
        password.setPromptText("••••••••");
        password.textProperty().bindBidirectional(s.myjdPasswordProperty());
        password.setPrefWidth(280);

        return page(sectionTitle("My.JDownloader"),
                Mat.label("Remote control is optional. Downloading works without an account.", "row-desc"),
                row("Email", "Remote-control account", email),
                row("Password", "Stored in memory; exported only encrypted", password));
    }

    private Node backupPage() {
        String defaultPath = Path.of(System.getProperty("user.home", "."),
                "jdownloader-material-settings.jdmbackup").toString();

        TextField exportPath = new TextField(defaultPath);
        exportPath.setPromptText("Path for the encrypted backup file");
        exportPath.setMaxWidth(640);
        PasswordField exportPassphrase = new PasswordField();
        exportPassphrase.setPromptText("Passphrase");
        PasswordField exportConfirm = new PasswordField();
        exportConfirm.setPromptText("Confirm passphrase");
        Label exportStatus = Mat.label("Choose a path and passphrase; export runs in the background.", "row-desc");
        var exportBtn = Mat.filled("Export encrypted backup", "download");
        exportBtn.setOnAction(e -> exportSettings(exportPath, exportPassphrase, exportConfirm,
                exportStatus, exportBtn));
        VBox exportCard = backupCard("Export settings",
                "Every setting, including saved remote-control credentials, is encrypted with "
                        + "AES-256-GCM before it is written to this file.",
                exportPath, exportPassphrase, exportConfirm, exportBtn, exportStatus);

        TextField importPath = new TextField();
        importPath.setPromptText("Path to a .jdmbackup file");
        importPath.setMaxWidth(640);
        PasswordField importPassphrase = new PasswordField();
        importPassphrase.setPromptText("Backup passphrase");
        Label importStatus = Mat.label("Import is verified in the background before settings are applied.", "row-desc");
        var importBtn = Mat.outlined("Import encrypted backup", "folder");
        importBtn.setOnAction(e -> importSettings(importPath, importPassphrase, importStatus, importBtn));
        VBox importCard = backupCard("Import settings",
                "Paste the backup path and passphrase. Existing settings remain in place if "
                        + "the backup cannot be verified.",
                importPath, importPassphrase, importBtn, importStatus);

        return page(sectionTitle("Export / Import"), exportCard, importCard);
    }

    private VBox backupCard(String title, String description, Node... controls) {
        VBox card = new VBox(10, Mat.label(title, "title"), Mat.label(description, "row-desc"));
        card.getChildren().addAll(controls);
        card.getStyleClass().add("md-card-flat");
        card.setMaxWidth(680);
        return card;
    }

    private void exportSettings(TextField pathField, PasswordField passphraseField,
                                PasswordField confirmationField, Label status, ButtonBase button) {
        String rawPath = pathField.getText() == null ? "" : pathField.getText().trim();
        if (rawPath.isEmpty()) {
            status.setText("Enter a path for the encrypted backup file.");
            return;
        }
        String passphrase = passphraseField.getText();
        if (passphrase == null || passphrase.isEmpty()) {
            status.setText("Enter a passphrase to protect the backup.");
            return;
        }
        if (!passphrase.equals(confirmationField.getText())) {
            status.setText("The passphrases do not match.");
            return;
        }

        final Path output;
        try {
            output = Path.of(rawPath);
        } catch (RuntimeException ex) {
            status.setText("That backup path is not valid.");
            return;
        }
        Properties snapshot = SettingsIO.snapshot(s);
        char[] secret = SettingsIO.chars(passphrase);
        passphraseField.clear();
        confirmationField.clear();

        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                try {
                    SettingsIO.exportTo(output, snapshot, secret);
                    return null;
                } finally {
                    Arrays.fill(secret, '\0');
                }
            }
        };
        task.setOnRunning(e -> {
            status.setText("Exporting encrypted settings in the background...");
        });
        task.setOnSucceeded(e -> {
            button.setDisable(false);
            status.setText("Settings exported to " + output.toAbsolutePath() + ".");
        });
        task.setOnFailed(e -> {
            button.setDisable(false);
            status.setText("Could not export settings: " + taskMessage(task.getException()));
        });
        button.setDisable(true);
        status.setText("Exporting encrypted settings in the background...");
        startTask(task, "settings-export");
    }

    private void importSettings(TextField pathField, PasswordField passphraseField,
                                Label status, ButtonBase button) {
        String rawPath = pathField.getText() == null ? "" : pathField.getText().trim();
        if (rawPath.isEmpty()) {
            status.setText("Enter the path to a .jdmbackup file.");
            return;
        }
        String passphrase = passphraseField.getText();
        if (passphrase == null || passphrase.isEmpty()) {
            status.setText("Enter the backup passphrase.");
            return;
        }

        final Path input;
        try {
            input = Path.of(rawPath);
        } catch (RuntimeException ex) {
            status.setText("That backup path is not valid.");
            return;
        }
        char[] secret = SettingsIO.chars(passphrase);
        passphraseField.clear();

        Task<Properties> task = new Task<>() {
            @Override protected Properties call() throws Exception {
                try {
                    return SettingsIO.importFrom(input, secret);
                } finally {
                    Arrays.fill(secret, '\0');
                }
            }
        };
        task.setOnRunning(e -> {
            status.setText("Verifying and reading the backup in the background...");
        });
        task.setOnSucceeded(e -> {
            button.setDisable(false);
            SettingsIO.apply(task.getValue(), s);
            status.setText("Settings imported from " + input.toAbsolutePath() + ".");
        });
        task.setOnFailed(e -> {
            button.setDisable(false);
            status.setText("Could not import settings: " + taskMessage(task.getException()));
        });
        button.setDisable(true);
        status.setText("Verifying and reading the backup in the background...");
        startTask(task, "settings-import");
    }

    private static void startTask(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }

    private static String taskMessage(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "The file could not be processed.";
        }
        return error.getMessage();
    }

    private Node aboutPage() {
        String version = System.getProperty("jdownloader.material.version", "0.1.0");
        var mark = new StackPane(Icons.of("download", 28, "icon-on-primary"));
        mark.getStyleClass().add("app-mark");
        mark.setMinSize(56, 56);
        mark.setMaxSize(56, 56);
        var about = new VBox(6,
                Mat.label("JDownloader Material", "display"),
                Mat.label("Version " + version + " — a ground-up Material Design GUI for JDownloader.", "body"),
                Mat.label("JavaFX + MaterialFX front end over the JDownloader core engine.", "row-desc"));
        HBox head = new HBox(20, mark, about);
        head.setAlignment(Pos.CENTER_LEFT);
        return page(head);
    }

    // ------------------------------------------------------------- Widgets
    private Label sectionTitle(String t) {
        Label l = Mat.label(t, "subtitle");
        l.setPadding(new Insets(8, 0, 4, 0));
        return l;
    }

    private HBox row(String title, String desc, Node control) {
        VBox text = new VBox(2, Mat.label(title, "row-title"), Mat.label(desc, "row-desc"));
        HBox row = new HBox(16, text, Mat.hSpacer(), control);
        row.getStyleClass().add("settings-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private MFXToggleButton toggle(BooleanProperty prop) {
        MFXToggleButton t = new MFXToggleButton();
        t.setSelected(prop.get());
        t.selectedProperty().bindBidirectional(prop);
        return t;
    }

    private HBox slider(IntegerProperty prop, int min, int max, int step) {
        Slider slider = new Slider(min, max, prop.get());
        slider.setPrefWidth(240);
        slider.setBlockIncrement(step);
        slider.setMajorTickUnit(Math.max(step, (max - min) / 4.0));
        Label value = new Label(String.valueOf(prop.get()));
        value.getStyleClass().add("subtitle");
        value.setMinWidth(48);
        value.setAlignment(Pos.CENTER_RIGHT);
        slider.valueProperty().addListener((o, a, b) -> {
            int v = (int) Math.round(b.doubleValue());
            prop.set(v);
            value.setText(String.valueOf(v));
        });
        prop.addListener((o, a, b) -> {
            slider.setValue(b.intValue());
            value.setText(String.valueOf(b.intValue()));
        });
        HBox box = new HBox(12, slider, value);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
