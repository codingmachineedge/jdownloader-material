package org.jdownloader.material.ui.stock;

import io.github.palexdev.materialfx.controls.MFXButton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jdownloader.material.engine.Settings;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.integration.jdownloader.ConfirmationToken;
import org.jdownloader.material.integration.jdownloader.HttpMethod;
import org.jdownloader.material.integration.jdownloader.JDownloaderRemoteClient;
import org.jdownloader.material.integration.jdownloader.RemoteCall;
import org.jdownloader.material.integration.jdownloader.RemoteCategory;
import org.jdownloader.material.integration.jdownloader.RemoteEndpoint;
import org.jdownloader.material.integration.jdownloader.RemoteOperation;
import org.jdownloader.material.integration.jdownloader.RemoteResponse;
import org.jdownloader.material.notification.NotificationService;
import org.jdownloader.material.search.SearchSpec;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.M3Dialogs;
import org.jdownloader.material.ui.search.SearchField;
import org.jdownloader.material.ui.workspace.WorkspaceContent;
import org.jdownloader.material.workspace.WorkspacePage;

/**
 * Reusable workspace surface for installed-JDownloader features that do not
 * yet need a purpose-built visual model. The view presents the actual bounded
 * Remote API response and never invents local records.
 */
public final class StockFeatureView extends BorderPane implements AutoCloseable {

    private static final int RESPONSE_CHUNK_CHARS = 16 * 1024;
    private static final int INPUT_LIMIT = 128 * 1024;
    private static final EnumSet<WorkspacePage> SUPPORTED = EnumSet.of(
            WorkspacePage.ACCOUNTS, WorkspacePage.PLUGINS, WorkspacePage.CAPTCHA,
            WorkspacePage.EXTRACTION, WorkspacePage.SCHEDULER, WorkspacePage.CONNECTIONS,
            WorkspacePage.REMOTE_CONTROL, WorkspacePage.AUTOMATION, WorkspacePage.LOGS);

    private final WorkspacePage page;
    private final Settings settings;
    private final I18n i18n;
    private final NotificationService notifications;
    private final JDownloaderRemoteClient client;
    private final boolean ownsClient;
    private final List<RemoteOperation> catalog;

    private final SearchField catalogSearch;
    private final SearchField responseSearch;
    private final SearchField settingsSearch;
    private final ObservableList<RemoteOperation> visibleOperations = FXCollections.observableArrayList();
    private final ObservableList<ResponseChunk> allChunks = FXCollections.observableArrayList();
    private final ObservableList<ResponseChunk> visibleChunks = FXCollections.observableArrayList();
    private final ListView<RemoteOperation> operationList = new ListView<>(visibleOperations);
    private final ListView<ResponseChunk> responseList = new ListView<>(visibleChunks);
    private final TextArea responseDetail = new TextArea();
    private final Label status = Mat.chip("", "status-offline");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final TabPane tabs = new TabPane();

    private final TextField baseUrl = new TextField();
    private final TextField accountHoster = new TextField();
    private final TextField accountUsername = new TextField();
    private final PasswordField accountPassword = new PasswordField();
    private final TextField advancedEndpoint = new TextField();
    private final ComboBox<HttpMethod> advancedMethod = new ComboBox<>();
    private final TextArea advancedParameters = new TextArea();
    private final TextArea advancedBody = new TextArea();
    private final List<SettingsRow> searchableSettingsRows = new ArrayList<>();

    private final ChangeListener<String> remoteUrlListener;
    private RemoteCall activeCall;
    private boolean disposed;

    /** Creates a view that owns and closes its dedicated client. */
    public static StockFeatureView create(WorkspacePage page, Settings settings, I18n i18n,
                                          NotificationService notifications) {
        return new StockFeatureView(page, settings, i18n, notifications,
                new JDownloaderRemoteClient(settings), true);
    }

    /** Creates a view around a caller-owned client for shared or test wiring. */
    public static StockFeatureView create(WorkspacePage page, Settings settings, I18n i18n,
                                          NotificationService notifications,
                                          JDownloaderRemoteClient client) {
        return new StockFeatureView(page, settings, i18n, notifications, client, false);
    }

    private StockFeatureView(WorkspacePage page, Settings settings, I18n i18n,
                             NotificationService notifications, JDownloaderRemoteClient client,
                             boolean ownsClient) {
        this.page = requireSupported(page);
        this.settings = Objects.requireNonNull(settings, "settings");
        this.i18n = Objects.requireNonNull(i18n, "i18n");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.client = Objects.requireNonNull(client, "client");
        this.ownsClient = ownsClient;
        this.catalog = catalogFor(page);
        this.catalogSearch = new SearchField(i18n, "stock.remote.catalog_search");
        this.responseSearch = new SearchField(i18n, "stock.remote.response_search");
        this.settingsSearch = new SearchField(i18n, "stock.remote.settings_search");
        this.remoteUrlListener = (observable, previous, current) -> {
            if (!baseUrl.isFocused()) baseUrl.setText(Objects.requireNonNullElse(current, ""));
            refreshSettingsFilter();
        };

        getStyleClass().addAll("content-area", "page-view", "stock-feature-view");
        setPadding(new Insets(0));
        setTop(buildHeader());
        setCenter(buildTabs());

        baseUrl.setText(Objects.requireNonNullElse(settings.remoteApiBaseUrlProperty().get(), ""));
        settings.remoteApiBaseUrlProperty().addListener(remoteUrlListener);
        catalogSearch.searchSpecProperty().addListener((observable, previous, current) -> refreshCatalog());
        responseSearch.searchSpecProperty().addListener((observable, previous, current) -> refreshResponseFilter());
        settingsSearch.searchSpecProperty().addListener((observable, previous, current) -> refreshSettingsFilter());
        responseList.getSelectionModel().selectedItemProperty().addListener((observable, previous, current) ->
                responseDetail.setText(current == null ? "" : current.text()));

        refreshCatalog();
        refreshSettingsFilter();
        setStatus("stock.remote.status.offline", "status-offline");
        Platform.runLater(this::refreshPrimary);
    }

    public static boolean supports(WorkspacePage page) {
        return page != null && SUPPORTED.contains(page);
    }

    /** Immutable operation catalog used by the page. */
    public static List<RemoteOperation> catalogFor(WorkspacePage page) {
        requireSupported(page);
        List<RemoteCategory> categories = switch (page) {
            case ACCOUNTS -> List.of(RemoteCategory.ACCOUNTS);
            case PLUGINS -> List.of(RemoteCategory.EXTENSIONS, RemoteCategory.PLUGINS, RemoteCategory.CONFIG);
            case CAPTCHA -> List.of(RemoteCategory.CAPTCHA, RemoteCategory.DIALOGS);
            case EXTRACTION -> List.of(RemoteCategory.EXTRACTION);
            case SCHEDULER -> List.of(RemoteCategory.EXTENSIONS, RemoteCategory.PLUGINS,
                    RemoteCategory.CONFIG, RemoteCategory.DOWNLOADS, RemoteCategory.UPDATE);
            case CONNECTIONS -> List.of(RemoteCategory.DEVICE, RemoteCategory.RECONNECT,
                    RemoteCategory.CONFIG);
            case REMOTE_CONTROL -> List.of(RemoteCategory.DEVICE, RemoteCategory.UPDATE,
                    RemoteCategory.SYSTEM, RemoteCategory.SESSION, RemoteCategory.CONFIG);
            case AUTOMATION -> List.of(RemoteCategory.DOWNLOADS, RemoteCategory.LINKGRABBER,
                    RemoteCategory.EXTRACTION, RemoteCategory.RECONNECT, RemoteCategory.CONFIG);
            case LOGS -> List.of(RemoteCategory.LOGS);
            default -> throw new IllegalArgumentException("Unsupported stock page: " + page);
        };
        return categories.stream().flatMap(category -> RemoteOperation.inCategory(category).stream()).toList();
    }

    public void setSearchSpec(SearchSpec spec) {
        SearchSpec replacement = spec == null ? SearchSpec.empty() : spec;
        switch (tabs.getSelectionModel().getSelectedIndex()) {
            case 0 -> catalogSearch.setSearchSpec(replacement);
            case 2 -> settingsSearch.setSearchSpec(replacement);
            default -> responseSearch.setSearchSpec(replacement);
        }
    }

    public boolean hasUnsavedWork() {
        return !Objects.equals(baseUrl.getText(), settings.remoteApiBaseUrlProperty().get())
                || nonBlank(accountHoster) || nonBlank(accountUsername) || nonBlank(accountPassword)
                || nonBlank(advancedEndpoint) || nonBlank(advancedParameters) || nonBlank(advancedBody);
    }

    public WorkspaceContent asWorkspaceContent() {
        return new WorkspaceContent(this, this::setSearchSpec, this::dispose, this::hasUnsavedWork,
                i18n.text("stock.remote.unsaved"));
    }

    private VBox buildHeader() {
        Label title = Mat.label("", "headline", "page-title");
        title.textProperty().bind(i18n.bind(pageTitleKey(page)));
        Label description = Mat.label("", "row-desc");
        description.textProperty().bind(i18n.bind("stock.bridge_loading"));
        status.setAccessibleRole(AccessibleRole.TEXT);
        progress.setMaxSize(20, 20);
        progress.setVisible(false);
        progress.setManaged(false);
        HBox statusRow = new HBox(8, progress, status);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(6, title, description, statusRow);
        header.getStyleClass().addAll("view-header", "page-head");
        header.setPadding(new Insets(20, 24, 12, 24));
        return header;
    }

    private TabPane buildTabs() {
        tabs.getStyleClass().add("stock-feature-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().setAll(
                tab("stock.remote.tab.operations", buildOperationsSurface()),
                tab("stock.remote.tab.response", buildResponseSurface()),
                tab("stock.remote.tab.connection", buildSettingsSurface()));
        return tabs;
    }

    private Tab tab(String key, javafx.scene.Node content) {
        Tab tab = new Tab();
        tab.textProperty().bind(i18n.bind(key));
        tab.setContent(content);
        tab.setClosable(false);
        return tab;
    }

    private VBox buildOperationsSurface() {
        catalogSearch.setMaxWidth(Double.MAX_VALUE);
        operationList.setAccessibleText(i18n.text("stock.remote.operations_list"));
        operationList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        operationList.setCellFactory(ignored -> new OperationCell());
        VBox.setVgrow(operationList, Priority.ALWAYS);

        MFXButton refresh = Mat.filled(i18n.text("stock.remote.refresh"), "refresh");
        refresh.setOnAction(event -> refreshPrimary());
        MFXButton execute = Mat.tonal(i18n.text("stock.remote.execute"), "play_arrow");
        execute.setOnAction(event -> executeSelected());
        MFXButton cancel = Mat.outlined(i18n.text("stock.remote.cancel"), "close");
        cancel.setOnAction(event -> cancelActive());
        FlowPane actions = new FlowPane(8, 8, refresh, execute, cancel);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox surface = new VBox(10, catalogSearch, operationList, actions);
        surface.setPadding(new Insets(16, 24, 20, 24));
        surface.setFillWidth(true);
        return surface;
    }

    private VBox buildResponseSurface() {
        responseSearch.setMaxWidth(Double.MAX_VALUE);
        responseList.setAccessibleText(i18n.text("stock.remote.response_chunks"));
        responseList.setCellFactory(ignored -> new ResponseCell());
        responseDetail.setEditable(false);
        responseDetail.setWrapText(false);
        responseDetail.setPromptText(i18n.text("stock.remote.response_empty"));
        responseDetail.setAccessibleText(i18n.text("stock.remote.response_detail"));
        SplitPane split = new SplitPane(responseList, responseDetail);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.28);
        VBox.setVgrow(split, Priority.ALWAYS);
        VBox surface = new VBox(10, responseSearch, split);
        surface.setPadding(new Insets(16, 24, 20, 24));
        return surface;
    }

    private VBox buildSettingsSurface() {
        settingsSearch.setMaxWidth(Double.MAX_VALUE);
        VBox content = new VBox(18);
        content.setFillWidth(true);

        baseUrl.setPromptText(i18n.text("stock.remote.base_url_hint"));
        baseUrl.setAccessibleText(i18n.text("stock.remote.base_url"));
        baseUrl.setTextFormatter(lengthLimit(2_048));
        HBox.setHgrow(baseUrl, Priority.ALWAYS);
        MFXButton saveUrl = Mat.filled(i18n.text("stock.remote.save_url"), "save");
        saveUrl.setOnAction(event -> saveBaseUrl());
        HBox urlInputs = new HBox(8, baseUrl, saveUrl);
        urlInputs.setAlignment(Pos.CENTER_LEFT);
        VBox urlCard = card("stock.remote.connection_title", "stock.remote.connection_description", urlInputs);
        addSearchable(urlCard, "stock.remote.base_url", () -> baseUrl.getText());
        content.getChildren().add(urlCard);

        if (page == WorkspacePage.ACCOUNTS) {
            accountHoster.setPromptText(i18n.text("stock.remote.account_hoster"));
            accountUsername.setPromptText(i18n.text("stock.remote.account_username"));
            accountPassword.setPromptText(i18n.text("stock.remote.account_password"));
            accountHoster.setAccessibleText(i18n.text("stock.remote.account_hoster"));
            accountUsername.setAccessibleText(i18n.text("stock.remote.account_username"));
            accountPassword.setAccessibleText(i18n.text("stock.remote.account_password"));
            accountHoster.setTextFormatter(lengthLimit(512));
            accountUsername.setTextFormatter(lengthLimit(2_048));
            accountPassword.setTextFormatter(lengthLimit(8_192));
            MFXButton add = Mat.tonal(i18n.text("stock.remote.account_add"), "person_add");
            add.setOnAction(event -> addAccount());
            GridPane fields = new GridPane();
            fields.setHgap(8);
            fields.setVgap(8);
            fields.add(accountHoster, 0, 0);
            fields.add(accountUsername, 1, 0);
            fields.add(accountPassword, 0, 1, 2, 1);
            fields.add(add, 2, 1);
            GridPane.setHgrow(accountHoster, Priority.ALWAYS);
            GridPane.setHgrow(accountUsername, Priority.ALWAYS);
            GridPane.setHgrow(accountPassword, Priority.ALWAYS);
            VBox accountCard = card("stock.remote.account_title", "stock.remote.account_description", fields);
            addSearchable(accountCard, "stock.remote.account_searchable",
                    () -> accountHoster.getText() + " " + accountUsername.getText());
            content.getChildren().add(accountCard);
        }

        configureAdvancedInputs();
        GridPane advanced = new GridPane();
        advanced.setHgap(8);
        advanced.setVgap(8);
        advanced.add(label("stock.remote.method"), 0, 0);
        advanced.add(advancedMethod, 1, 0);
        advanced.add(label("stock.remote.endpoint"), 0, 1);
        advanced.add(advancedEndpoint, 1, 1);
        advanced.add(label("stock.remote.parameters"), 0, 2);
        advanced.add(advancedParameters, 1, 2);
        advanced.add(label("stock.remote.request_body"), 0, 3);
        advanced.add(advancedBody, 1, 3);
        GridPane.setHgrow(advancedEndpoint, Priority.ALWAYS);
        GridPane.setHgrow(advancedParameters, Priority.ALWAYS);
        GridPane.setHgrow(advancedBody, Priority.ALWAYS);
        MFXButton runAdvanced = Mat.tonal(i18n.text("stock.remote.advanced_execute"), "terminal");
        runAdvanced.setOnAction(event -> executeAdvanced());
        VBox advancedCard = card("stock.remote.advanced_title", "stock.remote.advanced_description",
                advanced, runAdvanced);
        addSearchable(advancedCard, "stock.remote.advanced_searchable", () ->
                advancedMethod.getValue() + " " + advancedEndpoint.getText() + " "
                        + advancedParameters.getText() + " " + advancedBody.getText());
        content.getChildren().add(advancedCard);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setAccessibleText(i18n.text("stock.remote.connection_title"));
        VBox.setVgrow(scroll, Priority.ALWAYS);
        VBox surface = new VBox(10, settingsSearch, scroll);
        surface.setPadding(new Insets(16, 24, 20, 24));
        return surface;
    }

    private void configureAdvancedInputs() {
        advancedMethod.getItems().setAll(HttpMethod.values());
        advancedMethod.setValue(HttpMethod.GET);
        advancedMethod.setAccessibleText(i18n.text("stock.remote.method"));
        advancedEndpoint.setPromptText("/device/ping");
        advancedEndpoint.setAccessibleText(i18n.text("stock.remote.endpoint"));
        advancedEndpoint.setTextFormatter(lengthLimit(2_048));
        advancedParameters.setPromptText(i18n.text("stock.remote.parameters_hint"));
        advancedParameters.setAccessibleText(i18n.text("stock.remote.parameters"));
        advancedParameters.setPrefRowCount(4);
        advancedParameters.setTextFormatter(lengthLimit(INPUT_LIMIT));
        advancedBody.setPromptText(i18n.text("stock.remote.body_hint"));
        advancedBody.setAccessibleText(i18n.text("stock.remote.request_body"));
        advancedBody.setPrefRowCount(4);
        advancedBody.setTextFormatter(lengthLimit(INPUT_LIMIT));
        advancedBody.disableProperty().bind(advancedMethod.valueProperty().isEqualTo(HttpMethod.GET));
    }

    private VBox card(String titleKey, String descriptionKey, javafx.scene.Node... nodes) {
        Label title = Mat.label("", "section-title");
        title.textProperty().bind(i18n.bind(titleKey));
        Label description = Mat.label("", "row-desc");
        description.textProperty().bind(i18n.bind(descriptionKey));
        description.setWrapText(true);
        VBox card = new VBox(8, title, description);
        card.getChildren().addAll(nodes);
        card.getStyleClass().add("settings-card");
        return card;
    }

    private Label label(String key) {
        Label label = Mat.label("", "field-label");
        label.textProperty().bind(i18n.bind(key));
        return label;
    }

    private void addSearchable(VBox row, String key, Supplier<String> currentValue) {
        searchableSettingsRows.add(new SettingsRow(row, key, currentValue));
        currentValueInputs(row).forEach(field -> field.textProperty().addListener(
                (observable, previous, current) -> refreshSettingsFilter()));
    }

    private static List<TextInputControl> currentValueInputs(javafx.scene.Parent parent) {
        List<TextInputControl> fields = new ArrayList<>();
        for (javafx.scene.Node node : parent.getChildrenUnmodifiable()) {
            if (node instanceof TextInputControl field && !(field instanceof PasswordField)) fields.add(field);
            if (node instanceof javafx.scene.Parent child) fields.addAll(currentValueInputs(child));
        }
        return fields;
    }

    private void refreshCatalog() {
        visibleOperations.setAll(catalog.stream().filter(operation -> matches(catalogSearch,
                operation.name() + " " + operation.category() + " " + operation.endpoint().path())).toList());
    }

    private void refreshResponseFilter() {
        visibleChunks.setAll(allChunks.stream().filter(chunk -> matches(responseSearch,
                chunk.label() + " " + chunk.text())).toList());
        if (!visibleChunks.isEmpty() && responseList.getSelectionModel().getSelectedItem() == null) {
            responseList.getSelectionModel().selectFirst();
        }
        if (visibleChunks.isEmpty()) responseDetail.clear();
    }

    private void refreshSettingsFilter() {
        for (SettingsRow row : searchableSettingsRows) {
            String searchable = i18n.text(row.key()) + " " + Objects.requireNonNullElse(row.value().get(), "");
            boolean visible = matches(settingsSearch, searchable);
            row.node().setVisible(visible);
            row.node().setManaged(visible);
        }
    }

    private static boolean matches(SearchField search, String value) {
        SearchSpec spec = search.searchSpec();
        return spec.expression().isEmpty()
                || (search.validation().valid() && search.evaluator().matches(spec, value));
    }

    private void refreshPrimary() {
        if (disposed) return;
        executeCall(primaryCall(), primaryEndpoint());
    }

    private Supplier<RemoteCall> primaryCall() {
        return switch (page) {
            case ACCOUNTS -> () -> client.listAccounts("{}");
            case PLUGINS -> () -> client.listPlugins("{}");
            case CAPTCHA -> client::listCaptchaJobs;
            case EXTRACTION -> client::extractionQueue;
            case SCHEDULER -> client::updateAvailable;
            case CONNECTIONS -> client::ping;
            case REMOTE_CONTROL -> client::systemInfo;
            case AUTOMATION -> () -> client.queryDownloadPackages("{}");
            case LOGS -> client::availableLogs;
            default -> throw new IllegalStateException("Unsupported stock page: " + page);
        };
    }

    private RemoteEndpoint primaryEndpoint() {
        return switch (page) {
            case ACCOUNTS -> RemoteOperation.ACCOUNTS_LIST.endpoint();
            case PLUGINS -> RemoteOperation.PLUGINS_LIST.endpoint();
            case CAPTCHA -> RemoteOperation.CAPTCHA_LIST.endpoint();
            case EXTRACTION -> RemoteOperation.EXTRACTION_QUEUE.endpoint();
            case SCHEDULER -> RemoteOperation.UPDATE_AVAILABLE.endpoint();
            case CONNECTIONS -> RemoteOperation.DEVICE_PING.endpoint();
            case REMOTE_CONTROL -> RemoteOperation.SYSTEM_INFO.endpoint();
            case AUTOMATION -> RemoteOperation.DOWNLOADS_QUERY_PACKAGES.endpoint();
            case LOGS -> RemoteOperation.LOGS_LIST.endpoint();
            default -> throw new IllegalStateException("Unsupported stock page: " + page);
        };
    }

    private void executeSelected() {
        RemoteOperation operation = operationList.getSelectionModel().getSelectedItem();
        if (operation == null) {
            notifications.info(i18n.text("stock.remote.selection_title"),
                    i18n.text("stock.remote.selection_body"));
            return;
        }
        if (operation.parameterCount() > 0) {
            advancedEndpoint.setText(operation.endpoint().path());
            advancedMethod.setValue(operation.method());
            tabs.getSelectionModel().select(2);
            notifications.info(i18n.text("stock.remote.parameters_title"),
                    i18n.text("stock.remote.parameters_body", operation.parameterCount()));
            advancedParameters.requestFocus();
            return;
        }
        ConfirmationToken token = null;
        if (operation.confirmationRequired()) {
            if (!confirm(operation.endpoint())) return;
            token = ConfirmationToken.afterUserConfirmation(operation);
        }
        ConfirmationToken confirmed = token;
        executeCall(() -> client.execute(operation, List.of(), confirmed), operation.endpoint());
    }

    private void addAccount() {
        String hoster = accountHoster.getText().strip();
        String username = accountUsername.getText().strip();
        char[] password = accountPassword.getText().toCharArray();
        accountPassword.clear();
        if (hoster.isEmpty() || username.isEmpty() || password.length == 0) {
            Arrays.fill(password, '\0');
            notifications.warning(i18n.text("stock.remote.account_missing_title"),
                    i18n.text("stock.remote.account_missing_body"));
            return;
        }
        try {
            executeCall(() -> client.addAccount(hoster, username, password),
                    RemoteOperation.ACCOUNTS_ADD.endpoint());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void saveBaseUrl() {
        try {
            String validated = JDownloaderRemoteClient.validateBaseUrl(baseUrl.getText()).toString();
            settings.remoteApiBaseUrlProperty().set(validated);
            baseUrl.setText(validated);
            notifications.success(i18n.text("stock.remote.url_saved_title"),
                    i18n.text("stock.remote.url_saved_body"));
            refreshPrimary();
        } catch (RuntimeException failure) {
            setStatus("stock.remote.status.invalid_url", "status-error");
            notifications.error(i18n.text("stock.remote.invalid_url_title"),
                    i18n.text("stock.remote.invalid_url_body"));
        }
    }

    private void executeAdvanced() {
        final RemoteEndpoint endpoint;
        final List<String> parameters;
        try {
            endpoint = RemoteEndpoint.of(advancedEndpoint.getText().strip());
            parameters = advancedParameters.getText().lines().map(String::strip)
                    .filter(value -> !value.isEmpty()).toList();
        } catch (RuntimeException failure) {
            notifications.error(i18n.text("stock.remote.advanced_invalid_title"),
                    i18n.text("stock.remote.advanced_invalid_body"));
            return;
        }
        HttpMethod method = Objects.requireNonNullElse(advancedMethod.getValue(), HttpMethod.GET);
        List<RemoteOperation> known = RemoteOperation.forEndpoint(endpoint);
        boolean needsConfirmation = known.isEmpty() || method != HttpMethod.GET
                || known.stream().anyMatch(RemoteOperation::confirmationRequired);
        ConfirmationToken token = null;
        if (needsConfirmation) {
            if (!confirm(endpoint)) return;
            token = ConfirmationToken.afterUserConfirmation(endpoint);
        }
        ConfirmationToken confirmed = token;
        String body = method == HttpMethod.POST ? advancedBody.getText() : "";
        executeCall(() -> client.advanced(endpoint, method, parameters, body, confirmed), endpoint);
    }

    private boolean confirm(RemoteEndpoint endpoint) {
        return M3Dialogs.confirm(this, i18n.text("stock.remote.confirm_title"),
                i18n.text("stock.remote.confirm_header"),
                i18n.text("stock.remote.confirm_body", endpoint.path()),
                i18n.text("stock.remote.confirm_cancel"), i18n.text("stock.remote.confirm_proceed"));
    }

    private void executeCall(Supplier<RemoteCall> callFactory, RemoteEndpoint endpoint) {
        if (disposed) return;
        cancelActive();
        final RemoteCall call;
        try {
            call = Objects.requireNonNull(callFactory.get(), "call");
        } catch (RuntimeException failure) {
            handleFailure(endpoint, failure);
            return;
        }
        activeCall = call;
        progress.setVisible(true);
        progress.setManaged(true);
        setStatus("stock.remote.status.connecting", "status-working");
        call.future().whenComplete((response, failure) -> runOnFx(() -> {
            if (disposed || activeCall != call) return;
            activeCall = null;
            progress.setVisible(false);
            progress.setManaged(false);
            if (failure == null) handleResponse(response);
            else handleFailure(endpoint, failure);
        }));
    }

    private void handleResponse(RemoteResponse response) {
        setResponse(response.body());
        tabs.getSelectionModel().select(1);
        if (response.successful()) {
            setStatus("stock.remote.status.online", "status-online", response.statusCode(),
                    response.bodyBytes(), millis(response.elapsed()));
            notifications.success(i18n.text("stock.remote.success_title"),
                    i18n.text("stock.remote.success_body", response.endpoint().path(), response.statusCode()));
        } else {
            setStatus("stock.remote.status.http_error", "status-error", response.statusCode(),
                    response.bodyBytes());
            notifications.error(i18n.text("stock.remote.http_error_title"),
                    i18n.text("stock.remote.http_error_body", response.endpoint().path(), response.statusCode()));
        }
    }

    private void handleFailure(RemoteEndpoint endpoint, Throwable failure) {
        Throwable cause = unwrap(failure);
        progress.setVisible(false);
        progress.setManaged(false);
        if (cause instanceof CancellationException) {
            setStatus("stock.remote.status.cancelled", "status-offline");
            return;
        }
        setStatus("stock.remote.status.offline", "status-error");
        notifications.error(i18n.text("stock.remote.offline_title"),
                i18n.text("stock.remote.offline_body", endpoint.path(), safeFailure(cause)));
    }

    private void setResponse(String body) {
        allChunks.clear();
        String value = Objects.requireNonNullElse(body, "");
        if (value.isEmpty()) {
            responseDetail.clear();
            refreshResponseFilter();
            return;
        }
        int count = Math.max(1, (value.length() + RESPONSE_CHUNK_CHARS - 1) / RESPONSE_CHUNK_CHARS);
        for (int start = 0, index = 1; start < value.length(); index++) {
            int end = Math.min(value.length(), start + RESPONSE_CHUNK_CHARS);
            allChunks.add(new ResponseChunk(index, count, value.substring(start, end)));
            start = end;
        }
        refreshResponseFilter();
        responseList.getSelectionModel().selectFirst();
    }

    private void cancelActive() {
        RemoteCall current = activeCall;
        activeCall = null;
        if (current != null) current.cancel();
        progress.setVisible(false);
        progress.setManaged(false);
    }

    private void setStatus(String key, String styleClass, Object... arguments) {
        status.setText(i18n.text(key, arguments));
        status.getStyleClass().removeAll("status-offline", "status-online", "status-working", "status-error");
        status.getStyleClass().add(styleClass);
        status.setAccessibleText(status.getText());
    }

    private static long millis(Duration duration) {
        return Math.max(0, duration.toMillis());
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static String safeFailure(Throwable failure) {
        String message = failure == null ? "" : Objects.requireNonNullElse(failure.getMessage(), "").strip();
        if (!message.isEmpty()) return message;
        return failure == null ? "Remote request failed" : failure.getClass().getSimpleName();
    }

    private static boolean nonBlank(TextInputControl field) {
        return field.getText() != null && !field.getText().isBlank();
    }

    private static TextFormatter<String> lengthLimit(int maximum) {
        return new TextFormatter<>(change -> change.getControlNewText().length() <= maximum ? change : null);
    }

    private static WorkspacePage requireSupported(WorkspacePage page) {
        Objects.requireNonNull(page, "page");
        if (!SUPPORTED.contains(page)) throw new IllegalArgumentException("Unsupported stock page: " + page);
        return page;
    }

    private static String pageTitleKey(WorkspacePage page) {
        return switch (page) {
            case ACCOUNTS -> "stock.accounts";
            case PLUGINS -> "stock.plugins";
            case CAPTCHA -> "stock.captcha";
            case EXTRACTION -> "stock.extraction";
            case SCHEDULER -> "stock.scheduler";
            case CONNECTIONS -> "stock.connections";
            case REMOTE_CONTROL -> "stock.remote";
            case AUTOMATION -> "stock.automation";
            case LOGS -> "stock.logs";
            default -> throw new IllegalArgumentException("Unsupported stock page: " + page);
        };
    }

    private static void runOnFx(Runnable action) {
        try {
            if (Platform.isFxApplicationThread()) action.run();
            else Platform.runLater(action);
        } catch (IllegalStateException toolkitNotStarted) {
            action.run();
        }
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        cancelActive();
        catalogSearch.dispose();
        responseSearch.dispose();
        settingsSearch.dispose();
        settings.remoteApiBaseUrlProperty().removeListener(remoteUrlListener);
        advancedBody.disableProperty().unbind();
        accountPassword.clear();
        if (ownsClient) client.close();
    }

    @Override
    public void close() {
        dispose();
    }

    private record SettingsRow(VBox node, String key, Supplier<String> value) { }

    private record ResponseChunk(int index, int count, String text) {
        private String label() { return index + "/" + count + " · " + text.length() + " chars"; }
    }

    private final class OperationCell extends ListCell<RemoteOperation> {
        @Override protected void updateItem(RemoteOperation operation, boolean empty) {
            super.updateItem(operation, empty);
            if (empty || operation == null) {
                setText(null);
                setGraphic(null);
                return;
            }
            Label path = Mat.label(operation.endpoint().path(), "row-title");
            String details = operation.category() + " · " + operation.method() + " · "
                    + i18n.text("stock.remote.parameter_count", operation.parameterCount());
            Label metadata = Mat.label(details, "row-desc");
            VBox box = new VBox(3, path, metadata);
            if (operation.confirmationRequired()) {
                box.getChildren().add(Mat.chip(i18n.text("stock.remote.confirmation_required"), "status-warning"));
            }
            setGraphic(box);
            setText(null);
            setAccessibleText(path.getText() + ". " + details);
        }
    }

    private final class ResponseCell extends ListCell<ResponseChunk> {
        @Override protected void updateItem(ResponseChunk chunk, boolean empty) {
            super.updateItem(chunk, empty);
            if (empty || chunk == null) {
                setText(null);
                return;
            }
            setText(i18n.text("stock.remote.chunk", chunk.index(), chunk.count(), chunk.text().length()));
            setAccessibleText(getText());
        }
    }
}
