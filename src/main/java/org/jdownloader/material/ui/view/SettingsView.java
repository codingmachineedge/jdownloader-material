package org.jdownloader.material.ui.view;

import io.github.palexdev.materialfx.controls.MFXToggleButton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Locale;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.AccessibleRole;
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
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
import org.jdownloader.material.integration.ExternalEditorActions;
import org.jdownloader.material.integration.ExternalEditorService;
import org.jdownloader.material.notification.NotificationService;
import org.jdownloader.material.ui.Icons;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.M3Dialogs;
import org.jdownloader.material.search.SearchSpec;
import org.jdownloader.material.ui.search.SearchField;

/** Material preferences screen: a page rail on the left, setting rows on the right. */
public final class SettingsView extends BorderPane {

    private final Settings s;
    private final I18n i18n;
    private final ExternalEditorActions externalEditors;
    private final NotificationService notifications;
    private final StackPane content = new StackPane();
    private final ToggleGroup nav = new ToggleGroup();
    private final Map<String, ToggleButton> tabs = new HashMap<>();
    private final Map<String, ScrollPane> pages = new HashMap<>();
    private final Map<String, Node> pageBodies = new HashMap<>();
    private final Map<String, SearchField> pageSearches = new HashMap<>();
    private final List<Runnable> disposers = new ArrayList<>();
    private final SearchField globalSearch;
    private final javafx.scene.control.MenuButton searchResults = new javafx.scene.control.MenuButton();
    private final Label searchStatus = Mat.label("", "caption");
    private ComboBox<EditorOption> externalEditorChoice;
    private TextField externalEditorCommand;
    private Label externalEditorStatus;
    private ButtonBase refreshExternalEditors;
    private boolean updatingExternalEditorChoice;
    private volatile boolean disposed;
    private String selectedTabKey;

    public SettingsView(Settings settings, I18n i18n) {
        this(settings, i18n, null, null, "settings.tab.general");
    }

    public SettingsView(Settings settings, I18n i18n, String selectedTabKey) {
        this(settings, i18n, null, null, selectedTabKey);
    }

    public SettingsView(Settings settings, I18n i18n, ExternalEditorActions externalEditors) {
        this(settings, i18n, externalEditors, null, "settings.tab.general");
    }

    public SettingsView(Settings settings, I18n i18n, ExternalEditorActions externalEditors,
                        NotificationService notifications) {
        this(settings, i18n, externalEditors, notifications, "settings.tab.general");
    }

    public SettingsView(Settings settings, I18n i18n, ExternalEditorActions externalEditors,
                        String selectedTabKey) {
        this(settings, i18n, externalEditors, null, selectedTabKey);
    }

    private SettingsView(Settings settings, I18n i18n, ExternalEditorActions externalEditors,
                         NotificationService notifications, String selectedTabKey) {
        this.s = settings;
        this.i18n = i18n;
        this.externalEditors = externalEditors;
        this.notifications = notifications;
        this.globalSearch = new SearchField(i18n, "settings.search.all");
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

        Label heading = Mat.label(t("settings.title"), "headline", "page-title");
        HBox.setHgrow(globalSearch, Priority.ALWAYS);
        searchResults.setText(t("settings.search.results"));
        searchResults.setAccessibleText(t("settings.search.results"));
        HBox headerRow = new HBox(12, heading, Mat.hSpacer(), globalSearch, searchResults);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(4, headerRow, searchStatus);
        header.getStyleClass().addAll("view-header", "page-head");
        setTop(header);
        BorderPane panel = new BorderPane();
        panel.getStyleClass().addAll("content-panel", "settings-layout");
        panel.setLeft(rail);
        panel.setCenter(content);
        setCenter(panel);
        globalSearch.searchSpecProperty().addListener((observable, previous, current) -> refreshSearches());
        refreshSearches();
    }

    private void addTab(VBox rail, String titleKey, String icon, Node page) {
        ToggleButton tab = new ToggleButton(t(titleKey));
        tab.setGraphic(Icons.of(icon, 20));
        tab.setGraphicTextGap(12);
        tab.getStyleClass().add("settings-tab");
        tab.setMaxWidth(Double.MAX_VALUE);
        tab.setAlignment(Pos.CENTER_LEFT);
        tab.setToggleGroup(nav);
        SearchField localSearch = new SearchField(i18n, "settings.search.tab");
        localSearch.searchSpecProperty().addListener((observable, previous, current) -> refreshPage(titleKey));
        VBox wrapper = new VBox(8, localSearch, page);
        wrapper.getStyleClass().add("settings-searchable-page");
        VBox.setVgrow(page, Priority.ALWAYS);
        ScrollPane sp = new ScrollPane(wrapper);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("edge-to-edge");
        tabs.put(titleKey, tab);
        pages.put(titleKey, sp);
        pageBodies.put(titleKey, page);
        pageSearches.put(titleKey, localSearch);
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
        refreshPage(tabKey);
    }

    /** Applies the workspace-level search while retaining each tab's independent local query. */
    public void setSearchSpec(SearchSpec spec) {
        globalSearch.setSearchSpec(spec == null ? SearchSpec.empty() : spec);
    }

    private void refreshSearches() {
        searchResults.getItems().clear();
        SearchSpec global = globalSearch.searchSpec();
        int total = 0;
        for (Map.Entry<String, Node> entry : pageBodies.entrySet()) {
            long count = searchableChildren(entry.getValue()).stream()
                    .filter(node -> matches(globalSearch, global, searchableText(node))).count();
            if (count > 0) {
                total += (int) count;
                String tabKey = entry.getKey();
                javafx.scene.control.MenuItem result = new javafx.scene.control.MenuItem(
                        t(tabKey) + " · " + t("settings.search.match_count", count));
                result.setOnAction(event -> showTab(tabKey));
                searchResults.getItems().add(result);
            }
        }
        searchStatus.setText(global.expression().isBlank() ? ""
                : total == 0 ? t("settings.search.no_match")
                : t("settings.search.summary", total, searchResults.getItems().size()));
        searchResults.setDisable(searchResults.getItems().isEmpty());
        pages.keySet().forEach(this::refreshPage);
    }

    private void refreshPage(String tabKey) {
        Node body = pageBodies.get(tabKey);
        SearchField local = pageSearches.get(tabKey);
        if (body == null || local == null) return;
        SearchSpec globalSpec = globalSearch.searchSpec();
        SearchSpec localSpec = local.searchSpec();
        for (Node node : searchableChildren(body)) {
            String text = searchableText(node);
            boolean visible = matches(globalSearch, globalSpec, text) && matches(local, localSpec, text);
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    private static List<Node> searchableChildren(Node body) {
        if (body instanceof VBox box) return List.copyOf(box.getChildren());
        return List.of(body);
    }

    private boolean matches(SearchField field, SearchSpec spec, String text) {
        return spec.expression().isBlank()
                || (field.validation().valid() && field.evaluator().matches(spec, text));
    }

    private static String searchableText(Node node) {
        StringBuilder result = new StringBuilder();
        appendText(node, result);
        return result.toString();
    }

    private static void appendText(Node node, StringBuilder output) {
        if (node instanceof Labeled labelled && labelled.getText() != null) output.append(labelled.getText()).append(' ');
        if (node instanceof TextInputControl input) {
            if (input.getText() != null) output.append(input.getText()).append(' ');
            if (input.getPromptText() != null) output.append(input.getPromptText()).append(' ');
        }
        if (node instanceof ComboBox<?> combo && combo.getValue() != null) output.append(combo.getValue()).append(' ');
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) appendText(child, output);
        }
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
        folder.setMaxWidth(Double.MAX_VALUE);
        ButtonBase openFolder = Mat.tonal(t("external_editor.open_folder"), "folder");
        openFolder.setAccessibleHelp(t("external_editor.open_folder_help"));
        openFolder.setDisable(externalEditors == null);
        openFolder.setOnAction(event -> {
            if (externalEditors != null) externalEditors.openDownloadFolder();
        });
        HBox folderCtl = new HBox(8, folder, openFolder);
        folderCtl.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Settings.IfExists> ifExists = ifExistsSelector();
        ifExists.valueProperty().bindBidirectional(s.ifFileExistsProperty());
        disposers.add(() -> ifExists.valueProperty().unbindBidirectional(s.ifFileExistsProperty()));

        externalEditorCommand = new TextField(s.externalEditorCommandProperty().get());
        externalEditorCommand.setPromptText(t("settings.external_editor_placeholder"));
        externalEditorCommand.textProperty().bindBidirectional(s.externalEditorCommandProperty());
        disposers.add(() -> externalEditorCommand.textProperty()
                .unbindBidirectional(s.externalEditorCommandProperty()));
        externalEditorCommand.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(externalEditorCommand, Priority.ALWAYS);
        Node editorChooser = externalEditorChooser();

        return page(
                sectionTitle(t("settings.section.downloads")),
                row(t("settings.default_folder"), t("desc.default_folder"), folderCtl),
                row(t("settings.simultaneous"), t("desc.simultaneous"),
                        slider(s.maxSimultaneousDownloadsProperty(), 1, 10, 1)),
                row(t("settings.if_exists"), t("desc.if_exists"), ifExists),
                sectionTitle(t("settings.section.experience")),
                row(t("settings.dim_sum"), t("desc.dim_sum"), toggle(s.dimSumSurpriseEnabledProperty())),
                row(t("settings.quiet_hours"), t("desc.quiet_hours"), toggle(s.quietHoursProperty())),
                row(t("settings.notification_history"), t("desc.notification_history"),
                        toggle(s.notificationHistoryEnabledProperty())),
                sectionTitle(t("settings.section.external_editor")),
                row(t("settings.external_editor_choice"), t("desc.external_editor_choice"), editorChooser),
                row(t("settings.external_editor"), t("desc.external_editor"), externalEditorCommand)
        );
    }

    private Node externalEditorChooser() {
        externalEditorChoice = new ComboBox<>();
        externalEditorChoice.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(externalEditorChoice, Priority.ALWAYS);
        externalEditorChoice.valueProperty().addListener((observable, previous, current) -> {
            if (updatingExternalEditorChoice || current == null) return;
            s.externalEditorSelectionProperty().set(current.id());
            updateExternalEditorCommandState();
        });
        ChangeListener<String> selectionListener = (observable, previous, current) ->
                rebuildExternalEditorChoices(externalEditors == null ? List.of() : externalEditors.detectedEditors());
        s.externalEditorSelectionProperty().addListener(selectionListener);
        disposers.add(() -> s.externalEditorSelectionProperty().removeListener(selectionListener));

        refreshExternalEditors = Mat.icon("reconnect", t("settings.external_editor_refresh_help"));
        refreshExternalEditors.setDisable(externalEditors == null);
        refreshExternalEditors.setOnAction(event -> refreshExternalEditors());
        HBox chooser = new HBox(8, externalEditorChoice, refreshExternalEditors);
        chooser.setAlignment(Pos.CENTER_LEFT);

        externalEditorStatus = Mat.label("", "row-desc");
        externalEditorStatus.setWrapText(true);
        externalEditorStatus.setMaxWidth(Double.MAX_VALUE);
        rebuildExternalEditorChoices(externalEditors == null ? List.of() : externalEditors.detectedEditors());
        if (externalEditors != null) refreshExternalEditors();
        return new VBox(4, chooser, externalEditorStatus);
    }

    private void refreshExternalEditors() {
        if (externalEditors == null || disposed) return;
        refreshExternalEditors.setDisable(true);
        setExternalEditorStatus(t("settings.external_editor_detecting"));
        notifyInfo(t("settings.section.external_editor"), t("settings.external_editor_detecting"));
        externalEditors.refreshDetectedEditors().whenComplete((editors, failure) -> Platform.runLater(() -> {
            if (disposed) return;
            refreshExternalEditors.setDisable(false);
            if (failure == null) {
                rebuildExternalEditorChoices(editors);
                refreshSearches();
                notifySuccess(t("settings.section.external_editor"),
                        t("settings.external_editor_detected", editors.size()));
            } else {
                setExternalEditorStatus(t("settings.external_editor_detection_failed"));
                notifyError(t("settings.section.external_editor"), t("settings.external_editor_detection_failed"));
            }
        }));
    }

    private void rebuildExternalEditorChoices(List<ExternalEditorService.Editor> editors) {
        List<ExternalEditorService.Editor> safe = editors == null ? List.of() : List.copyOf(editors);
        List<EditorOption> choices = new ArrayList<>();
        choices.add(new EditorOption(ExternalEditorService.AUTO_SELECTION, safe.isEmpty()
                ? t("settings.external_editor_auto")
                : t("settings.external_editor_auto_with", safe.getFirst().name())));
        safe.forEach(editor -> choices.add(new EditorOption(editor.id(), editor.name())));
        String selected = s.externalEditorSelectionProperty().get();
        boolean selectionAvailable = choices.stream().anyMatch(option -> option.id().equals(selected));
        if (!selectionAvailable && !ExternalEditorService.CUSTOM_SELECTION.equals(selected)
                && selected != null && !selected.isBlank()) {
            choices.add(new EditorOption(selected, t("settings.external_editor_missing", selected)));
        }
        choices.add(new EditorOption(ExternalEditorService.CUSTOM_SELECTION,
                t("settings.external_editor_custom")));
        updatingExternalEditorChoice = true;
        try {
            externalEditorChoice.getItems().setAll(choices);
            selectExternalEditor(selected);
        } finally {
            updatingExternalEditorChoice = false;
        }
        updateExternalEditorCommandState();
        setExternalEditorStatus(safe.isEmpty() ? t("settings.external_editor_none_detected")
                : t("settings.external_editor_detected", safe.size()));
    }

    private void selectExternalEditor(String id) {
        if (externalEditorChoice == null) return;
        String selected = id == null || id.isBlank() ? ExternalEditorService.AUTO_SELECTION : id;
        EditorOption option = externalEditorChoice.getItems().stream()
                .filter(candidate -> candidate.id().equals(selected)).findFirst()
                .orElseGet(() -> externalEditorChoice.getItems().stream()
                        .filter(candidate -> candidate.id().equals(ExternalEditorService.AUTO_SELECTION))
                        .findFirst().orElse(null));
        updatingExternalEditorChoice = true;
        try {
            externalEditorChoice.setValue(option);
        } finally {
            updatingExternalEditorChoice = false;
        }
        updateExternalEditorCommandState();
    }

    private void updateExternalEditorCommandState() {
        if (externalEditorCommand == null || externalEditorChoice == null) return;
        EditorOption selected = externalEditorChoice.getValue();
        boolean custom = selected != null
                && ExternalEditorService.CUSTOM_SELECTION.equals(selected.id());
        externalEditorCommand.setDisable(!custom);
    }

    private void setExternalEditorStatus(String text) {
        externalEditorStatus.setText(text);
        externalEditorStatus.setAccessibleText(text);
    }

    private Node connectionPage() {
        TextField remoteBase = new TextField(s.remoteApiBaseUrlProperty().get());
        remoteBase.setPromptText(t("settings.remote_api_placeholder"));
        remoteBase.textProperty().bindBidirectional(s.remoteApiBaseUrlProperty());
        disposers.add(() -> remoteBase.textProperty().unbindBidirectional(s.remoteApiBaseUrlProperty()));
        HBox.setHgrow(remoteBase, Priority.ALWAYS);
        return page(
                sectionTitle(t("settings.section.bandwidth")),
                row(t("settings.speed_limit_enable"), t("desc.speed_limit_enable"),
                        toggle(s.speedLimitEnabledProperty())),
                row(t("settings.speed_limit"), t("desc.speed_limit"),
                        slider(s.speedLimitKbpsProperty(), 128, 20000, 128)),
                row(t("settings.host_connections"), t("desc.host_connections"),
                        slider(s.maxConnectionsPerHostProperty(), 1, 20, 1)),
                sectionTitle(t("settings.section.remote_api")),
                row(t("settings.remote_api"), t("desc.remote_api"), remoteBase)
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
        Label disclosure = Mat.label(t("settings.funny_disclosure"), "row-desc");
        disclosure.setWrapText(true);
        return page(
                sectionTitle(t("settings.section.appearance")),
                row(t("settings.dark_theme"), t("desc.dark_theme"), toggle(s.darkThemeProperty())),
                row(t("settings.speed_in_title"), t("desc.speed_in_title"),
                        toggle(s.speedInTitleProperty())),
                row(t("settings.language"), t("desc.language"), languageSelector()),
                row(t("settings.english_funny"), t("desc.english_funny"),
                        slider(s.englishFunnyLevelProperty(), 1, 5, 1)),
                row(t("settings.cantonese_funny"), t("desc.cantonese_funny"),
                        slider(s.cantoneseFunnyLevelProperty(), 1, 5, 1)),
                disclosure,
                row(t("settings.reduced_motion"), t("desc.reduced_motion"), toggle(s.reducedMotionProperty()))
        );
    }

    private Node backupPage() {
        String defaultPath = Path.of(System.getProperty("user.home", "."),
                "jdownloader-material-settings.jdmbackup").toString();

        TextField exportPath = new TextField(defaultPath);
        exportPath.setPromptText(t("settings.path_export"));
        HBox.setHgrow(exportPath, Priority.ALWAYS); exportPath.setMaxWidth(Double.MAX_VALUE);
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
        HBox.setHgrow(importPath, Priority.ALWAYS); importPath.setMaxWidth(Double.MAX_VALUE);
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
// card fills available space — 680px was a hostage to fortune
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
            output = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            status.setText(t("status.backup.invalid_path"));
            return;
        }
        if (Files.exists(output) && !M3Dialogs.confirm(this, t("settings.export_overwrite_title"),
                t("settings.export_overwrite_header"), t("settings.export_overwrite_body", output),
                t("stock.remote.confirm_cancel"), t("settings.export_button"))) return;
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
            status.setText(t("status.backup.exporting"));
            notifyInfo(t("settings.export"), t("status.backup.exporting"));
        });
        task.setOnSucceeded(e -> {
            button.setDisable(false);
            status.setText(t("status.backup.exported", output.toAbsolutePath()));
            notifySuccess(t("settings.export"), t("status.backup.exported", output.toAbsolutePath()));
        });
        task.setOnFailed(e -> {
            button.setDisable(false);
            String message = t("status.backup.export_failed", taskMessage(task.getException()));
            status.setText(message);
            notifyError(t("settings.export"), message);
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
        task.setOnRunning(e -> {
            status.setText(t("status.backup.importing"));
            notifyInfo(t("settings.import"), t("status.backup.importing"));
        });
        task.setOnSucceeded(e -> {
            button.setDisable(false);
            if (M3Dialogs.confirm(this, t("settings.import_confirm_title"),
                    t("settings.import_confirm_header"), t("settings.import_confirm_body", input.toAbsolutePath()),
                    t("stock.remote.confirm_cancel"), t("settings.import_button"))) {
                SettingsIO.apply(task.getValue(), s);
                status.setText(t("status.backup.imported", input.toAbsolutePath()));
                notifySuccess(t("settings.import"), t("status.backup.imported", input.toAbsolutePath()));
            } else {
                status.setText(t("status.backup.import_cancelled"));
                notifyInfo(t("settings.import"), t("status.backup.import_cancelled"));
            }
        });
        task.setOnFailed(e -> {
            button.setDisable(false);
            String message = t("status.backup.import_failed", taskMessage(task.getException()));
            status.setText(message);
            notifyError(t("settings.import"), message);
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
        Node logo = Icons.of("download", 28, "icon-on-primary");
        var resource = SettingsView.class.getResource("/icons/app.png");
        if (resource != null) {
            Image image = new Image(resource.toExternalForm(), 48, 48, true, true, false);
            if (!image.isError()) {
                ImageView imageView = new ImageView(image);
                imageView.setFitWidth(48);
                imageView.setFitHeight(48);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                imageView.setAccessibleRole(AccessibleRole.IMAGE_VIEW);
                imageView.setAccessibleText("JDownloader Material");
                logo = imageView;
            }
        }
        var mark = new StackPane(logo);
        mark.getStyleClass().add("app-mark");
        mark.setMinSize(56, 56);
        mark.setMaxSize(56, 56);
        mark.setAccessibleRole(AccessibleRole.IMAGE_VIEW);
        mark.setAccessibleText("JDownloader Material");
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
        selector.setMaxWidth(Double.MAX_VALUE); // flex — 200px clipped bilingual text like a bad haircut
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
        HBox.setHgrow(selector, Priority.ALWAYS);
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
        // text section compresses; spacer fills gap; control holds its preferred size
        HBox.setHgrow(text, Priority.ALWAYS);
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
        HBox.setHgrow(slider, Priority.ALWAYS);
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
        if (disposed) return;
        disposed = true;
        globalSearch.dispose();
        pageSearches.values().forEach(SearchField::dispose);
        pageSearches.clear();
        disposers.forEach(Runnable::run);
        disposers.clear();
    }

    private void notifyInfo(String title, String body) {
        if (notifications != null) notifications.info(title, body);
    }

    private void notifySuccess(String title, String body) {
        if (notifications != null) notifications.success(title, body);
    }

    private void notifyError(String title, String body) {
        if (notifications != null) notifications.error(title, body);
    }

    private record EditorOption(String id, String label) {
        @Override public String toString() { return label; }
    }
}
