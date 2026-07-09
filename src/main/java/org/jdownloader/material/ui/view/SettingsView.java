package org.jdownloader.material.ui.view;

import io.github.palexdev.materialfx.controls.MFXToggleButton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import org.jdownloader.material.engine.Settings;
import org.jdownloader.material.ui.Icons;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.NotificationCenter;
import org.jdownloader.material.ui.dialog.BackupPanels;

/** Material preferences screen: a page rail on the left, setting rows on the right. */
public final class SettingsView extends BorderPane {

    private final Settings s;
    private final NotificationCenter notifier;
    private final StackPane content = new StackPane();
    private final ToggleGroup nav = new ToggleGroup();

    public SettingsView(Settings settings, NotificationCenter notifier) {
        this.s = settings;
        this.notifier = notifier;
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
        var browse = Mat.icon("folder", "Choose folder");
        browse.setOnAction(e -> {
            var dc = new javafx.stage.DirectoryChooser();
            var dir = dc.showDialog(getScene().getWindow());
            if (dir != null) s.downloadFolderProperty().set(dir.getAbsolutePath());
        });
        HBox folderCtl = new HBox(8, folder, browse);
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
                row("If a file already exists", "Behavior on filename collision", ifExists)
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
        var addBtn = Mat.filled("Add account", "add");
        addBtn.setOnAction(e -> notifier.info("Accounts",
                "Premium account management isn't available in this build yet."));
        var card = new VBox(12,
                Mat.label("Premium accounts", "title"),
                Mat.label("No premium accounts configured. Add an account to unlock full-speed "
                        + "downloads and skip hoster wait times.", "row-desc"),
                addBtn);
        card.getStyleClass().add("md-card-flat");
        card.setMaxWidth(560);

        TextField email = new TextField();
        email.setPromptText("email@example.com");
        email.textProperty().bindBidirectional(s.myjdEmailProperty());
        email.setPrefWidth(280);
        PasswordField password = new PasswordField();
        password.setPromptText("••••••••");
        password.textProperty().bindBidirectional(s.myjdPasswordProperty());
        password.setPrefWidth(280);

        return page(sectionTitle("Account Manager"), card,
                sectionTitle("My.JDownloader"),
                row("Email", "Remote-control account", email),
                row("Password", "Stored in memory; exported only encrypted", password));
    }

    private Node backupPage() {
        var exportBtn = Mat.filled("Export settings…", "download");
        exportBtn.setOnAction(e -> BackupPanels.openExport(notifier, s));
        var importBtn = Mat.outlined("Import settings…", "folder");
        importBtn.setOnAction(e -> BackupPanels.openImport(notifier, s));
        var card = new VBox(12,
                Mat.label("Settings backup", "title"),
                Mat.label("Exports every setting — including secrets like the My.JDownloader "
                        + "password — to a single file encrypted with AES-256-GCM under a "
                        + "passphrase you choose. Import restores the full configuration on "
                        + "any machine.", "row-desc"),
                new HBox(8, exportBtn, importBtn));
        card.getStyleClass().add("md-card-flat");
        card.setMaxWidth(560);
        return page(sectionTitle("Export / Import"), card);
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
