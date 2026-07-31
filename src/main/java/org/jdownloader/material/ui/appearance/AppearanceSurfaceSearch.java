package org.jdownloader.material.ui.appearance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import org.jdownloader.material.search.SearchEvaluation;
import org.jdownloader.material.search.SearchSpec;
import org.jdownloader.material.search.SearchValidation;
import org.jdownloader.material.ui.search.SearchField;

/** Independent, bounded SearchField plus non-destructive results for one appearance surface. */
final class AppearanceSurfaceSearch extends VBox implements AutoCloseable {

    static final int MAX_INDEX_ENTRIES = 5_000;
    static final int MAX_VISIBLE_RESULTS = 200;

    record Entry(String display, String searchable, Runnable activation) {
        Entry {
            display = Objects.requireNonNullElse(display, "").strip();
            searchable = Objects.requireNonNullElse(searchable, display);
            activation = activation == null ? () -> { } : activation;
        }

        static Entry of(String display, String searchable) {
            return new Entry(display, searchable, null);
        }
    }

    private final AppearanceSearchContext context;
    private final SearchField searchField;
    private final Label status = new Label();
    private final ListView<Entry> results = new ListView<>();
    private final List<Entry> entries = new ArrayList<>();
    private final ChangeListener<SearchSpec> specListener = (observable, previous, current) -> refresh();
    private final ChangeListener<SearchValidation> validationListener =
            (observable, previous, current) -> refresh();
    private boolean disposed;

    AppearanceSurfaceSearch(Function<String, String> text, String promptKey, String styleClass) {
        context = new AppearanceSearchContext(text);
        searchField = new SearchField(context.i18n(), promptKey);
        getStyleClass().addAll("appearance-surface-search", styleClass);
        setSpacing(4);
        setPadding(new Insets(4, 0, 4, 0));

        status.getStyleClass().add("appearance-search-status");
        status.setWrapText(true);
        status.setAccessibleText(context.i18n().text("appearance.search.status.ready"));

        results.getStyleClass().add("appearance-search-results");
        results.setPrefHeight(116);
        results.setMaxHeight(160);
        results.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Entry item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.display());
                setAccessibleText(empty || item == null ? null : item.display());
            }
        });
        results.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) activateSelected();
        });
        results.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                activateSelected();
                event.consume();
            }
        });
        setResultsVisible(false);
        getChildren().setAll(searchField, status, results);

        searchField.searchSpecProperty().addListener(specListener);
        searchField.validationProperty().addListener(validationListener);
        refresh();
    }

    SearchField searchField() {
        return searchField;
    }

    void setEntries(Collection<Entry> replacement) {
        entries.clear();
        if (replacement != null) {
            for (Entry entry : replacement) {
                if (entry != null && !entry.display().isBlank()) entries.add(entry);
                if (entries.size() >= MAX_INDEX_ENTRIES) break;
            }
        }
        refresh();
    }

    boolean matchesCandidate(String candidate) {
        SearchSpec spec = searchField.searchSpec();
        if (spec.expression().isEmpty() || !searchField.validation().valid()) return true;
        SearchEvaluation evaluation = searchField.evaluate(Objects.requireNonNullElse(candidate, ""));
        return evaluation.valid() && !evaluation.matches().isEmpty();
    }

    List<String> resultDisplays() {
        return results.getItems().stream().map(Entry::display).toList();
    }

    void refresh() {
        if (disposed) return;
        context.refresh();
        SearchSpec spec = searchField.searchSpec();
        SearchValidation validation = searchField.validation();
        status.getStyleClass().remove("invalid");

        if (!validation.valid()) {
            status.getStyleClass().add("invalid");
            status.setText(validationMessage(validation));
            status.setAccessibleText(status.getText());
            results.getItems().clear();
            setResultsVisible(false);
            return;
        }
        if (spec.expression().isEmpty()) {
            status.setText(context.i18n().text("appearance.search.status.ready"));
            status.setAccessibleText(status.getText());
            results.getItems().clear();
            setResultsVisible(false);
            return;
        }

        List<Entry> visible = new ArrayList<>();
        int matches = 0;
        for (Entry entry : entries) {
            SearchEvaluation evaluation = searchField.evaluate(entry.searchable());
            if (!evaluation.valid() || evaluation.matches().isEmpty()) continue;
            matches++;
            if (visible.size() < MAX_VISIBLE_RESULTS) visible.add(entry);
        }
        results.getItems().setAll(visible);
        setResultsVisible(true);
        status.setText(matches == 0
                ? context.i18n().text("appearance.search.status.no_matches")
                : matches > visible.size()
                ? context.i18n().text("appearance.search.status.truncated", matches, visible.size())
                : context.i18n().text("appearance.search.status.results", matches));
        status.setAccessibleText(status.getText());
    }

    private String validationMessage(SearchValidation validation) {
        return switch (validation.code()) {
            case VALID -> context.i18n().text("search.regex.validation.valid");
            case EXPRESSION_TOO_LONG -> context.i18n().text("search.regex.validation.expression_too_long",
                    validation.actual(), validation.limit());
            case INPUT_TOO_LONG -> context.i18n().text("search.regex.validation.input_too_long",
                    validation.actual(), validation.limit());
            case TOO_MANY_CAPTURE_GROUPS -> context.i18n().text("search.regex.validation.too_many_groups",
                    validation.actual(), validation.limit());
            case INVALID_PATTERN -> validation.position() >= 0
                    ? context.i18n().text("search.regex.validation.invalid_at",
                    validation.position(), validation.detail())
                    : context.i18n().text("search.regex.validation.invalid", validation.detail());
        };
    }

    private void activateSelected() {
        Entry selected = results.getSelectionModel().getSelectedItem();
        if (selected != null) selected.activation().run();
    }

    private void setResultsVisible(boolean visible) {
        results.setVisible(visible);
        results.setManaged(visible);
    }

    @Override
    public void close() {
        if (disposed) return;
        disposed = true;
        searchField.searchSpecProperty().removeListener(specListener);
        searchField.validationProperty().removeListener(validationListener);
        searchField.dispose();
        results.getItems().clear();
        entries.clear();
    }
}
