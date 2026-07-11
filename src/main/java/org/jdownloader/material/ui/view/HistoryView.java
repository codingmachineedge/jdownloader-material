package org.jdownloader.material.ui.view;

import io.github.palexdev.materialfx.controls.MFXButton;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.jdownloader.material.engine.history.HistoryEntry;
import org.jdownloader.material.engine.history.HistoryService;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.util.Formats;

/**
 * A nonblocking local-history browser. The history service owns all Git and
 * disk work; this view only filters entries, presents a preview, and starts
 * asynchronous undo, redo, and restore operations.
 */
public final class HistoryView extends BorderPane {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final HistoryService history;
    private final I18n i18n;
    private final ObservableList<HistoryEntry> filteredEntries = FXCollections.observableArrayList();
    private final ListView<HistoryEntry> timeline = new ListView<>();
    private final ObjectProperty<HistoryEntry> selectedEntry =
            new SimpleObjectProperty<>(this, "selectedEntry");
    private final StringProperty operationNotice = new SimpleStringProperty(this, "operationNotice", "");
    private final TextField search = new TextField();
    private final ComboBox<ScopeFilter> scopeFilter = new ComboBox<>();

    private final Label previewTitle = Mat.label("", "title");
    private final Label previewSummary = Mat.label("", "body");
    private final Label operationValue = Mat.label("", "body");
    private final Label scopeValue = Mat.label("", "body");
    private final Label timeValue = Mat.label("", "body");
    private final Label statusValue = Mat.label("", "body");
    private final Label relatedValue = Mat.label("", "body");
    private final Label errorValue = Mat.label("", "row-desc");
    private HBox relatedRow;
    private VBox errorRow;
    private MFXButton restore;
    private boolean disposed;

    private final ChangeListener<HistoryEntry> selectionListener = (observable, previous, current) -> {
        selectedEntry.set(current);
        updatePreview(current);
    };
    private final ChangeListener<String> searchListener = (observable, previous, current) -> refreshFilter();
    private final ChangeListener<ScopeFilter> scopeListener = (observable, previous, current) -> refreshFilter();
    private final ListChangeListener<HistoryEntry> historyEntriesListener = change -> refreshFilter();

    public HistoryView(HistoryService history, I18n i18n) {
        this.history = Objects.requireNonNull(history, "history");
        this.i18n = Objects.requireNonNull(i18n, "i18n");
        getStyleClass().addAll("content-area", "page-view");
        setTop(buildHeader());
        setCenter(buildContent());

        search.textProperty().addListener(searchListener);
        scopeFilter.valueProperty().addListener(scopeListener);
        timeline.getSelectionModel().selectedItemProperty().addListener(selectionListener);
        history.entries().addListener(historyEntriesListener);
        updatePreview(null);
        refreshFilter();
    }

    private Node buildHeader() {
        Label title = Mat.label(t("history.title"), "headline", "page-title");
        Label storage = Mat.chip("", "history-storage-chip");
        storage.textProperty().bind(Bindings.createStringBinding(
                () -> t("history.storage", Formats.bytes(Math.max(0, history.storageBytesProperty().get()))),
                history.storageBytesProperty(), i18n.modeProperty()));

        MFXButton undo = Mat.tonal(t("history.undo"), "undo");
        undo.disableProperty().bind(history.busyProperty().or(history.canUndoProperty().not()));
        undo.setOnAction(event -> perform(history::undo));

        MFXButton redo = Mat.outlined(t("history.redo"), "redo");
        redo.disableProperty().bind(history.busyProperty().or(history.canRedoProperty().not()));
        redo.setOnAction(event -> perform(history::redo));

        HBox titleRow = new HBox(12, title, storage, Mat.hSpacer(), undo, redo);
        titleRow.getStyleClass().addAll("view-header", "page-head");
        titleRow.setAlignment(Pos.CENTER_LEFT);

        search.setPromptText(t("history.search"));
        search.getStyleClass().add("search-field");
        search.setPrefWidth(250);

        scopeFilter.getItems().setAll(ScopeFilter.values());
        scopeFilter.setValue(ScopeFilter.ALL);
        scopeFilter.setPrefWidth(180);
        scopeFilter.setConverter(new StringConverter<>() {
            @Override public String toString(ScopeFilter value) {
                return value == null ? "" : t(value.labelKey);
            }

            @Override public ScopeFilter fromString(String value) {
                for (ScopeFilter scope : ScopeFilter.values()) {
                    if (toString(scope).equals(value)) return scope;
                }
                return ScopeFilter.ALL;
            }
        });

        Label status = Mat.label("", "row-desc");
        status.setWrapText(true);
        status.textProperty().bind(historyStatusBinding());
        Label description = Mat.label(t("history.description"), "row-desc");
        description.setWrapText(true);
        HBox controls = new HBox(12, scopeFilter, Mat.hSpacer(), status);
        controls.getStyleClass().add("history-toolbar");
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(0, 20, 12, 20));
        description.setPadding(new Insets(0, 20, 8, 20));
        return new VBox(titleRow, description, controls);
    }

    private Node buildContent() {
        timeline.setItems(filteredEntries);
        timeline.setCellFactory(view -> new EntryCell());
        timeline.setPlaceholder(Mat.label(t("history.empty"), "empty-table-hint"));
        timeline.getStyleClass().add("history-timeline");

        VBox timelineBox = new VBox(8, Mat.label(t("history.timeline"), "subtitle"), timeline);
        timelineBox.getStyleClass().add("history-pane");
        timelineBox.setPadding(new Insets(12, 16, 20, 20));
        VBox.setVgrow(timeline, Priority.ALWAYS);

        ScrollPane previewScroll = new ScrollPane(buildPreview());
        previewScroll.setFitToWidth(true);
        previewScroll.getStyleClass().add("edge-to-edge");
        SplitPane split = new SplitPane(timelineBox, previewScroll);
        split.getStyleClass().add("history-split");
        split.setDividerPositions(0.44);
        return split;
    }

    /** Applies the search field hosted by the global application toolbar. */
    public void setFilter(String value) {
        String next = value == null ? "" : value;
        if (!Objects.equals(search.getText(), next)) search.setText(next);
    }

    private Node buildPreview() {
        previewTitle.setWrapText(true);
        previewSummary.setWrapText(true);
        previewSummary.getStyleClass().add("history-summary");

        relatedRow = detailRow("history.detail_related", relatedValue);
        errorValue.setWrapText(true);
        errorRow = new VBox(4, Mat.label(t("history.detail_error"), "label-md"), errorValue);
        errorRow.getStyleClass().add("history-error-row");

        VBox metadata = new VBox(0,
                detailRow("history.detail_operation", operationValue),
                detailRow("history.detail_scope", scopeValue),
                detailRow("history.detail_time", timeValue),
                detailRow("history.detail_status", statusValue),
                relatedRow,
                errorRow);
        metadata.getStyleClass().add("md-card-flat");

        restore = Mat.filled(t("history.restore"), "history-restore");
        restore.disableProperty().bind(history.busyProperty().or(Bindings.createBooleanBinding(
                () -> !isRestorable(selectedEntry.get()), selectedEntry)));
        restore.setOnAction(event -> {
            HistoryEntry entry = selectedEntry.get();
            if (entry != null) perform(() -> history.restore(entry.id()));
        });
        Label restoreHint = Mat.label(t("history.restore_hint"), "row-desc");
        restoreHint.setWrapText(true);
        VBox restoreCard = new VBox(8, Mat.label(t("history.restore_title"), "title"),
                restoreHint, restore);
        restoreCard.getStyleClass().add("md-card-flat");

        VBox preview = new VBox(14,
                Mat.label(t("history.preview"), "subtitle"),
                previewTitle,
                previewSummary,
                metadata,
                restoreCard);
        preview.getStyleClass().add("history-preview");
        preview.setPadding(new Insets(18, 24, 28, 20));
        return preview;
    }

    private HBox detailRow(String labelKey, Label value) {
        Label label = Mat.label(t(labelKey), "label-md");
        label.setMinWidth(118);
        value.setWrapText(true);
        HBox row = new HBox(12, label, value);
        row.getStyleClass().add("history-detail-row");
        row.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(value, Priority.ALWAYS);
        return row;
    }

    private StringBinding historyStatusBinding() {
        return Bindings.createStringBinding(() -> {
            String notice = operationNotice.get();
            if (notice != null && !notice.isBlank()) return notice;
            Object serviceStatus = history.statusProperty().get();
            if (serviceStatus != null) return localizedToken("history.status.", serviceStatus);
            return history.busyProperty().get() ? t("history.working") : t("history.status");
        }, history.statusProperty(), history.busyProperty(), operationNotice, i18n.modeProperty());
    }

    private void refreshFilter() {
        String needle = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        ScopeFilter requestedScope = scopeFilter.getValue() == null ? ScopeFilter.ALL : scopeFilter.getValue();
        filteredEntries.setAll(history.entries().stream()
                .filter(entry -> matches(entry, needle, requestedScope))
                .toList());
        HistoryEntry selected = selectedEntry.get();
        if (filteredEntries.isEmpty()) {
            timeline.getSelectionModel().clearSelection();
        } else if (!filteredEntries.contains(selected)) {
            timeline.getSelectionModel().select(0);
        }
    }

    private boolean matches(HistoryEntry entry, String needle, ScopeFilter requestedScope) {
        if (entry == null || !requestedScope.matches(entry.scope())) return false;
        if (needle.isEmpty()) return true;
        return searchable(entry.operation()).contains(needle)
                || searchable(entry.summary()).contains(needle)
                || searchable(entry.scope()).contains(needle)
                || searchable(entry.status()).contains(needle)
                || searchable(entry.id()).contains(needle)
                || searchable(entry.targetId()).contains(needle);
    }

    private static String searchable(Object value) {
        return value == null ? "" : String.valueOf(value).toLowerCase(Locale.ROOT);
    }

    private void updatePreview(HistoryEntry entry) {
        boolean hasEntry = entry != null;
        previewTitle.setText(hasEntry ? operationLabel(entry) : t("history.select_entry"));
        previewSummary.setText(hasEntry ? emptyToFallback(entry.summary(), t("history.no_summary"))
                : t("history.select_entry_hint"));
        operationValue.setText(hasEntry ? operationLabel(entry) : "—");
        scopeValue.setText(hasEntry ? scopeLabel(entry) : "—");
        timeValue.setText(hasEntry ? timestamp(entry.timestamp()) : "—");
        statusValue.setText(hasEntry ? statusLabel(entry) : "—");

        String target = hasEntry ? value(entry.targetId()) : "";
        relatedValue.setText(target);
        relatedRow.setVisible(!target.isBlank());
        relatedRow.setManaged(!target.isBlank());

        String error = hasEntry ? value(entry.error()) : "";
        errorValue.setText(error);
        errorRow.setVisible(!error.isBlank());
        errorRow.setManaged(!error.isBlank());
    }

    private void perform(Supplier<CompletableFuture<Void>> command) {
        if (disposed || history.busyProperty().get()) return;
        operationNotice.set("");
        final CompletableFuture<Void> future;
        try {
            future = command.get();
        } catch (RuntimeException error) {
            operationNotice.set(t("history.failed", message(error)));
            return;
        }
        if (future == null) return;
        future.whenComplete((ignored, error) -> Platform.runLater(() -> {
            if (disposed) return;
            operationNotice.set(error == null ? "" : t("history.failed", message(error)));
        }));
    }

    private String operationLabel(HistoryEntry entry) {
        return localizedToken("history.operation.", entry.operation());
    }

    private String scopeLabel(HistoryEntry entry) {
        return localizedToken("history.scope.", entry.scope());
    }

    private String statusLabel(HistoryEntry entry) {
        return localizedToken("history.status.", entry.status());
    }

    private String localizedToken(String prefix, Object value) {
        String raw = value(value);
        if (raw.isBlank()) return "—";
        String key = prefix + raw.toLowerCase(Locale.ROOT).replace(' ', '_');
        String translated = t(key);
        return key.equals(translated) ? pretty(raw) : translated;
    }

    private static String timestamp(Object value) {
        if (value instanceof Instant instant) return TIMESTAMP.format(instant);
        return value(value);
    }

    private static String pretty(String token) {
        String normalized = token.replace('_', ' ').replace('-', ' ').trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return "";
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String emptyToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean isRestorable(HistoryEntry entry) {
        return entry != null && (entry.status() == org.jdownloader.material.engine.history.HistoryStatus.COMMITTED
                || entry.status() == org.jdownloader.material.engine.history.HistoryStatus.RESTORED);
    }

    private static String message(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getMessage() == null) cause = cause.getCause();
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private String t(String key, Object... arguments) {
        return i18n.text(key, arguments);
    }

    /** Releases listeners when a language switch replaces the application shell. */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        timeline.getSelectionModel().selectedItemProperty().removeListener(selectionListener);
        search.textProperty().removeListener(searchListener);
        scopeFilter.valueProperty().removeListener(scopeListener);
        history.entries().removeListener(historyEntriesListener);
        filteredEntries.clear();
        timeline.setItems(FXCollections.emptyObservableList());
    }

    private enum ScopeFilter {
        ALL("history.filter.all"),
        DOWNLOADS("history.filter.downloads"),
        LINKGRABBER("history.filter.linkgrabber"),
        SETTINGS("history.filter.settings");

        private final String labelKey;

        ScopeFilter(String labelKey) {
            this.labelKey = labelKey;
        }

        private boolean matches(Object scope) {
            if (this == ALL) return true;
            String raw = searchable(scope).replace('-', '_').replace(' ', '_');
            return switch (this) {
                case DOWNLOADS -> raw.contains("download");
                case LINKGRABBER -> raw.contains("linkgrabber") || raw.contains("crawler") || raw.contains("crawl")
                        || raw.contains("download_lists");
                case SETTINGS -> raw.contains("setting");
                case ALL -> true;
            };
        }
    }

    private final class EntryCell extends ListCell<HistoryEntry> {
        EntryCell() {
            getStyleClass().add("history-entry-cell");
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override protected void updateItem(HistoryEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label operation = Mat.label(operationLabel(entry), "subtitle");
            operation.setWrapText(true);
            Label summary = Mat.label(emptyToFallback(entry.summary(), t("history.no_summary")), "row-desc");
            summary.setWrapText(true);
            Label scope = Mat.chip(scopeLabel(entry), "history-scope-chip");
            Label state = Mat.chip(statusLabel(entry), "history-status-chip");
            Label time = Mat.label(timestamp(entry.timestamp()), "caption");
            HBox meta = new HBox(6, scope, state, Mat.hSpacer(), time);
            meta.setAlignment(Pos.CENTER_LEFT);
            VBox box = new VBox(5, operation, summary, meta);
            box.getStyleClass().add("history-entry");
            setGraphic(box);
        }
    }
}
