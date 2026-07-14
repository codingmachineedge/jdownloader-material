package org.jdownloader.material.ui.view;

import io.github.palexdev.materialfx.controls.MFXToggleButton;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javafx.beans.value.ChangeListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.engine.Settings;
import org.jdownloader.material.engine.SettingsIO;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.ui.Icons;
import org.jdownloader.material.ui.component.Mat;

/** Material preferences screen: a page rail on the left, setting rows on the right. */
public final class SettingsView extends BorderPane {

    private final Settings s;
    private final I18n i18n;
    private final StackPane content = new StackPane();
    private final ToggleGroup nav = new ToggleGroup();
    private final Map<String, ToggleButton> tabs = new HashMap<>();
    private final Map<String, ScrollPane> pages = new HashMap<>();
    private final List<Runnable> disposers = new ArrayList<>();
    private String selectedTabKey;

    public SettingsView(Settings settings, I18n i18n) {
        this(settings, i18n, "settings.tab.general");
    }

    public SettingsView(Settings settings, I18n i18n, String selectedTabKey) {
        this.s = settings;
        this.i18n = i18n;
        this.selectedTabKey = selectedTabKey == null ? "settings.tab.general" : selectedTabKey;
        getStyleClass().addAll("content-area", "page-view");

        VBox rail = new VBox(4);
        rail.getStyleClass().add("settings-nav");
        addTab(rail, "settings.tab.general", "settings", generalPage());
        addTab(rail, "settings.tab.connection", "speed", connectionPage());
        addTab(rail, "settings.tab.recovery", "reconnect", recoveryPage());
        addTab(rail, "settings.tab.linkgrabber", "link", linkgrabberPage());
        addTab(rail, "settings.tab.appearance", "palette", appearancePage());
        addTab(rail, "settings.tab.backup", "shield", backupPage());
        addTab(rail, "settings.tab.about", "info", aboutPage());

        var header = new HBox(Mat.label(t("settings.title"), "headline", "page-title"));
        header.getStyleClass().addAll("view-header", "page-head");
        setTop(header);
        BorderPane panel = new BorderPane();
        panel.getStyleClass().addAll("content-panel", "settings-layout");
        panel.setLeft(rail);
        panel.setCenter(content);
        setCenter(panel);
    }

    private void addTab(VBox rail, String titleKey, String icon, Node page) {
        ToggleButton tab = new ToggleButton(t(titleKey));
        tab.setGraphic(Icons.of(icon, 20));
        tab.setGraphicTextGap(12);
        tab.getStyleClass().add("settings-tab");
        tab.setMaxWidth(Double.MAX_VALUE);
        tab.setAlignment(Pos.CENTER_LEFT);
        tab.setToggleGroup(nav);
        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("edge-to-edge");
        tabs.put(titleKey, tab);
        pages.put(titleKey, sp);
        tab.setOnAction(e -> showTab(titleKey));
        rail.getChildren().add(tab);
        if (titleKey.equals(selectedTabKey)) {
            showTab(titleKey);
        }
    }

    /** The active settings rail tab, retained when the translated shell is rebuilt. */
    public String selectedTabKey() {
        return selectedTabKey;
    }

    /** Used by documentation capture to show the persisted language picker. */
    public void showAppearanceForCapture() {
        showTab("settings.tab.appearance");
    }

    /** Used by documentation capture to return to the primary settings page. */
    public void showGeneralForCapture() {
        showTab("settings.tab.general");
    }

    private void showTab(String requestedKey) {
        String tabKey = tabs.containsKey(requestedKey) ? requestedKey : "settings.tab.general";
        ToggleButton tab = tabs.get(tabKey);
        ScrollPane page = pages.get(tabKey);
        if (tab == null || page == null) return;
        selectedTabKey = tabKey;
        tab.setSelected(true);
        content.getChildren().setAll(page);
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
        disposers.add(() -> folder.textProperty().unbindBidirectional(s.downloadFolderProperty()));
        HBox.setHgrow(folder, Priority.ALWAYS);
        HBox folderCtl = new HBox(folder);
        folderCtl.setAlignment(Pos.CENTER_LEFT);
        folderCtl.setPrefWidth(360);

        ComboBox<Settings.IfExists> ifExists = ifExistsSelector();
        ifExists.valueProperty().bindBidirectional(s.ifFileExistsProperty());
        disposers.add(() -> ifExists.valueProperty().unbindBidirectional(s.ifFileExistsProperty()));

        return page(
                sectionTitle(t("settings.section.downloads")),
                row(t("settings.default_folder"), t("desc.default_folder"), folderCtl),
                row(t("settings.simultaneous"), t("desc.simultaneous"),
                        slider(s.maxSimultaneousDownloadsProperty(), 1, 10, 1)),
                row(t("settings.if_exists"), t("desc.if_exists"), ifExists)
        );
    }

    private Node connectionPage() {
        return page(
                sectionTitle(t("settings.section.bandwidth")),
                row(t("settings.speed_limit_enable"), t("desc.speed_limit_enable"),
                        toggle(s.speedLimitEnabledProperty())),
                row(t("settings.speed_limit"), t("desc.speed_limit"),
                        slider(s.speedLimitKbpsProperty(), 128, 20000, 128)),
                row(t("settings.host_connections"), t("desc.host_connections"),
                        slider(s.maxConnectionsPerHostProperty(), 1, 20, 1))
        );
    }

    private Node recoveryPage() {
        return page(
                sectionTitle(t("settings.section.recovery")),
                row(t("settings.retry_transient"), t("desc.retry_transient"),
                        toggle(s.autoReconnectProperty()))
        );
    }

    private Node linkgrabberPage() {
        return page(
                sectionTitle(t("settings.section.collection")),
                row(t("settings.clipboard_monitoring"), t("desc.clipboard_monitoring"),
                        toggle(s.clipboardMonitoringProperty())),
                row(t("settings.auto_confirm"), t("desc.auto_confirm"),
                        toggle(s.autoConfirmProperty())),
                row(t("settings.auto_start"), t("desc.auto_start"),
                        toggle(s.autoStartProperty())),
                row(t("settings.add_at_top"), t("desc.add_at_top"),
                        toggle(s.addAtTopProperty()))
        );
    }

    private Node appearancePage() {
        return page(
                sectionTitle(t("settings.section.appearance")),
                row(t("settings.dark_theme"), t("desc.dark_theme"), toggle(s.darkThemeProperty())),
                row(t("settings.speed_in_title"), t("desc.speed_in_title"),
                        toggle(s.speedInTitleProperty())),
                row(t("settings.language"), t("desc.language"), languageSelector())
        );
    }

    private Node backupPage() {
        String defaultPath = Path.of(System.getProperty("user.home", "."),
                "jdownloader-material-settings.jdmbackup").toString();

        TextField exportPath = new TextField(defaultPath);
        exportPath.setPromptText(t("settings.path_export"));
        exportPath.setMaxWidth(640);
        PasswordField exportPassphrase = new PasswordField();
        exportPassphrase.setPromptText(t("settings.passphrase"));
        PasswordField exportConfirm = new PasswordField();
        exportConfirm.setPromptText(t("settings.confirm_passphrase"));
        Label exportStatus = Mat.label(t("status.backup.ready_export"), "row-desc");
        var exportBtn = Mat.filled(t("settings.export_button"), "download");
        exportBtn.setOnAction(e -> exportSettings(exportPath, exportPassphrase, exportConfirm,
                exportStatus, exportBtn));
        VBox exportCard = backupCard(t("settings.export"), t("desc.export"),
                exportPath, exportPassphrase, exportConfirm, exportBtn, exportStatus);

        TextField importPath = new TextField();
        importPath.setPromptText(t("settings.path_import"));
        importPath.setMaxWidth(640);
        PasswordField importPassphrase = new PasswordField();
        importPassphrase.setPromptText(t("settings.backup_passphrase"));
        Label importStatus = Mat.label(t("status.backup.ready_import"), "row-desc");
        var importBtn = Mat.outlined(t("settings.import_button"), "folder");
        importBtn.setOnAction(e -> importSettings(importPath, importPassphrase, importStatus, importBtn));
        VBox importCard = backupCard(t("settings.import"), t("desc.import"),
                importPath, importPassphrase, importBtn, importStatus);

        return page(sectionTitle(t("settings.section.backup")), exportCard, importCard);
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
            status.setText(t("status.backup.path_required"));
            return;
        }
        String passphrase = passphraseField.getText();
        if (passphrase == null || passphrase.isEmpty()) {
            status.setText(t("status.backup.passphrase_required"));
            return;
        }
        if (!passphrase.equals(confirmationField.getText())) {
            status.setText(t("status.backup.passphrase_mismatch"));
            return;
        }

        final Path output;
        try {
            output = Path.of(rawPath);
        } catch (RuntimeException ex) {
            status.setText(t("status.backup.invalid_path"));
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
        task.setOnRunning(e -> status.setText(t("status.backup.exporting")));
        task.setOnSucceeded(e -> {
            button.setDisable(false);
            status.setText(t("status.backup.exported", output.toAbsolutePath()));
        });
        task.setOnFailed(e -> {
            button.setDisable(false);
            status.setText(t("status.backup.export_failed", taskMessage(task.getException())));
        });
        button.setDisable(true);
        status.setText(t("status.backup.exporting"));
        startTask(task, "settings-export");
    }

    private void importSettings(TextField pathField, PasswordField passphraseField,
                                Label status, ButtonBase button) {
        String rawPath = pathField.getText() == null ? "" : pathField.getText().trim();
        if (rawPath.isEmpty()) {
            status.setText(t("status.backup.import_path_required"));
            return;
        }
        String passphrase = passphraseField.getText();
        if (passphrase == null || passphrase.isEmpty()) {
            status.setText(t("status.backup.import_passphrase_required"));
            return;
        }

        final Path input;
        try {
            input = Path.of(rawPath);
        } catch (RuntimeException ex) {
            status.setText(t("status.backup.invalid_path"));
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
        task.setOnRunning(e -> status.setText(t("status.backup.importing")));
        task.setOnSucceeded(e -> {
            button.setDisable(false);
            SettingsIO.apply(task.getValue(), s);
            status.setText(t("status.backup.imported", input.toAbsolutePath()));
        });
        task.setOnFailed(e -> {
            button.setDisable(false);
            status.setText(t("status.backup.import_failed", taskMessage(task.getException())));
        });
        button.setDisable(true);
        status.setText(t("status.backup.importing"));
        startTask(task, "settings-import");
    }

    private static void startTask(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }

    private String taskMessage(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SettingsIO.BackupException) {
                return t("status.backup.verify_failed");
            }
        }
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return t("status.backup.file_error");
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
                Mat.label(t("app.title"), "display"),
                Mat.label(t("about.version", version), "body"),
                Mat.label(t("about.scope"), "row-desc"));
        HBox head = new HBox(20, mark, about);
        head.setAlignment(Pos.CENTER_LEFT);
        return page(head);
    }

    // ------------------------------------------------------------- Widgets
    private ComboBox<Settings.IfExists> ifExistsSelector() {
        ComboBox<Settings.IfExists> selector = new ComboBox<>();
        selector.getItems().setAll(Settings.IfExists.values());
        selector.setConverter(new StringConverter<>() {
            @Override public String toString(Settings.IfExists value) {
                return value == null ? "" : t("ifexists." + value.name());
            }

            @Override public Settings.IfExists fromString(String text) {
                return Arrays.stream(Settings.IfExists.values())
                        .filter(value -> toString(value).equals(text))
                        .findFirst()
                        .orElse(s.ifFileExistsProperty().get());
            }
        });
        return selector;
    }

    private ComboBox<LanguageMode> languageSelector() {
        ComboBox<LanguageMode> selector = new ComboBox<>();
        selector.getItems().setAll(LanguageMode.values());
        selector.setConverter(new StringConverter<>() {
            @Override public String toString(LanguageMode value) {
                return value == null ? "" : i18n.languageName(value);
            }

            @Override public LanguageMode fromString(String text) {
                return Arrays.stream(LanguageMode.values())
                        .filter(value -> i18n.languageName(value).equals(text))
                        .findFirst()
                        .orElse(s.languageProperty().get());
            }
        });
        selector.setPrefWidth(300);
        selector.valueProperty().bindBidirectional(s.languageProperty());
        disposers.add(() -> selector.valueProperty().unbindBidirectional(s.languageProperty()));
        return selector;
    }

    private Label sectionTitle(String text) {
        Label label = Mat.label(text, "subtitle");
        label.setPadding(new Insets(8, 0, 4, 0));
        return label;
    }

    private HBox row(String title, String desc, Node control) {
        Label titleLabel = Mat.label(title, "row-title");
        Label descriptionLabel = Mat.label(desc, "row-desc");
        Control labelledControl = firstControl(control);
        if (labelledControl != null) {
            titleLabel.setLabelFor(labelledControl);
            labelledControl.setAccessibleText(title);
            labelledControl.setAccessibleHelp(desc);
        }
        VBox text = new VBox(2, titleLabel, descriptionLabel);
        HBox row = new HBox(16, text, Mat.hSpacer(), control);
        row.getStyleClass().add("settings-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Finds the primary interactive control inside a compact row wrapper. */
    private static Control firstControl(Node node) {
        if (node instanceof Control control) return control;
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Control found = firstControl(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private MFXToggleButton toggle(BooleanProperty prop) {
        MFXToggleButton toggle = new MFXToggleButton();
        toggle.setSelected(prop.get());
        toggle.selectedProperty().bindBidirectional(prop);
        disposers.add(() -> toggle.selectedProperty().unbindBidirectional(prop));
        return toggle;
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
        ChangeListener<Number> sliderListener = (o, a, b) -> {
            int v = (int) Math.round(b.doubleValue());
            prop.set(v);
            value.setText(String.valueOf(v));
        };
        ChangeListener<Number> propertyListener = (o, a, b) -> {
            slider.setValue(b.intValue());
            value.setText(String.valueOf(b.intValue()));
        };
        slider.valueProperty().addListener(sliderListener);
        prop.addListener(propertyListener);
        disposers.add(() -> slider.valueProperty().removeListener(sliderListener));
        disposers.add(() -> prop.removeListener(propertyListener));
        HBox box = new HBox(12, slider, value);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private String t(String key, Object... arguments) {
        return i18n.text(key, arguments);
    }

    public void dispose() {
        disposers.forEach(Runnable::run);
        disposers.clear();
    }
}
