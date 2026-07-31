package org.jdownloader.material.ui.view;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.jdownloader.material.changelog.ChangelogEntry;
import org.jdownloader.material.changelog.ChangelogService;
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.notification.NotificationService;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.M3Dialogs;
import org.jdownloader.material.search.SearchSpec;
import org.jdownloader.material.ui.search.SearchField;

/** In-app, all-version changelog with composable date/search filters and matching export. */
public final class ChangelogView extends BorderPane {
    private final ChangelogService changelog;
    private final NotificationService notifications;
    private final I18n i18n;
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final SearchField search;
    private final TextField exportPath = new TextField(Path.of(System.getProperty("user.home", "."), "Downloads",
            "JDownloader-Material-changelog.md").toString());
    private final Label validation = Mat.label("", "input-error");
    private final ObservableList<ChangelogEntry> filtered = FXCollections.observableArrayList();

    public ChangelogView(ChangelogService changelog, NotificationService notifications, I18n i18n) {
        this.changelog = changelog;
        this.notifications = notifications;
        this.i18n = i18n;
        this.search = new SearchField(i18n, "changelog.search");
        getStyleClass().addAll("content-area", "page-view", "changelog-view");

        Label title = Mat.label(i18n.text("changelog.title"), "headline", "page-title");
        Label description = Mat.label(i18n.text("changelog.description"), "row-desc");
        configureDate(from, "changelog.from");
        configureDate(to, "changelog.to");
        search.searchSpecProperty().addListener((observable, previous, current) -> refresh());
        HBox.setHgrow(search, Priority.ALWAYS);

        Button all = Mat.text(i18n.text("changelog.preset.all"), "history");
        all.setOnAction(event -> { setDates(null, null); });
        Button thirty = Mat.text(i18n.text("changelog.preset.30days"), "history");
        thirty.setOnAction(event -> setDates(LocalDate.now().minusDays(30), LocalDate.now()));
        Button year = Mat.text(i18n.text("changelog.preset.year"), "history");
        year.setOnAction(event -> setDates(LocalDate.now().withDayOfYear(1), LocalDate.now()));

        HBox filters = new HBox(8, search, from, to);
        filters.setAlignment(Pos.CENTER_LEFT);
        FlowPane presets = new FlowPane(8, 8, all, thirty, year);
        VBox header = new VBox(6, title, description, filters, presets, validation);
        header.getStyleClass().addAll("view-header", "page-head");
        setTop(header);

        ListView<ChangelogEntry> list = new ListView<>(filtered);
        list.getStyleClass().add("changelog-list");
        list.setAccessibleRole(AccessibleRole.LIST_VIEW);
        list.setAccessibleText(i18n.text("changelog.results"));
        list.setPlaceholder(Mat.label(i18n.text("changelog.no_matches"), "empty-table-hint"));
        list.setCellFactory(ignored -> new EntryCell());
        setCenter(list);
        BorderPane.setMargin(list, new Insets(0, 24, 12, 24));

        exportPath.setPromptText(i18n.text("changelog.export_path"));
        exportPath.setAccessibleText(i18n.text("changelog.export_path"));
        HBox.setHgrow(exportPath, Priority.ALWAYS);
        Button copy = Mat.outlined(i18n.text("changelog.copy"), "copy");
        copy.setOnAction(event -> copyFiltered());
        Button export = Mat.filled(i18n.text("changelog.export"), "download");
        export.setOnAction(event -> exportFiltered());
        HBox footer = new HBox(8, exportPath, copy, export);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(0, 24, 20, 24));
        setBottom(footer);
        refresh();
    }

    public void setFilter(String value) { search.setSearchSpec(SearchSpec.plain(value == null ? "" : value)); }
    public void setSearchSpec(SearchSpec value) { search.setSearchSpec(value == null ? SearchSpec.empty() : value); }
    public void dispose() { search.dispose(); }

    private void configureDate(DatePicker picker, String key) {
        picker.setPromptText(i18n.text(key));
        picker.setAccessibleText(i18n.text(key));
        picker.setConverter(dateConverter());
        picker.valueProperty().addListener((observable, previous, current) -> refresh());
        picker.getEditor().textProperty().addListener((observable, previous, current) -> refresh());
    }

    private StringConverter<LocalDate> dateConverter() {
        DateTimeFormatter display = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale());
        return new StringConverter<>() {
            @Override public String toString(LocalDate value) { return value == null ? "" : display.format(value); }
            @Override public LocalDate fromString(String value) {
                ParsedDate parsed = parseDate(value);
                return parsed.valid ? parsed.date : null;
            }
        };
    }

    private Locale locale() {
        return i18n.modeProperty().get() == LanguageMode.HONG_KONG_CANTONESE
                ? Locale.forLanguageTag("yue-HK") : Locale.getDefault(Locale.Category.FORMAT);
    }

    private ParsedDate parseDate(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) return new ParsedDate(null, true);
        try { return new ParsedDate(LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE), true); }
        catch (DateTimeParseException ignored) { }
        try {
            return new ParsedDate(LocalDate.parse(value,
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale())), true);
        } catch (DateTimeParseException ignored) {
            return new ParsedDate(null, false);
        }
    }

    private void refresh() {
        ParsedDate start = parseDate(from.getEditor().getText());
        ParsedDate end = parseDate(to.getEditor().getText());
        if (!start.valid || !end.valid) {
            validation.setText(i18n.text("changelog.invalid_date"));
            return;
        }
        if (start.date != null && end.date != null && start.date.isAfter(end.date)) {
            validation.setText(i18n.text("changelog.invalid_range"));
            return;
        }
        validation.setText("");
        SearchSpec spec = search.searchSpec();
        filtered.setAll(changelog.filter(start.date, end.date, entry -> spec.expression().isEmpty()
                || (search.validation().valid() && search.evaluator().matches(spec, entry.searchable()))));
    }

    private void setDates(LocalDate start, LocalDate end) {
        from.setValue(start);
        from.getEditor().setText(start == null ? "" : from.getConverter().toString(start));
        to.setValue(end);
        to.getEditor().setText(end == null ? "" : to.getConverter().toString(end));
        refresh();
    }

    private void copyFiltered() {
        ClipboardContent content = new ClipboardContent();
        content.putString(changelog.markdown(List.copyOf(filtered), i18n));
        Clipboard.getSystemClipboard().setContent(content);
        notifications.success(i18n.text("changelog.copied_title"), i18n.text("changelog.copied_body", filtered.size()));
    }

    private void exportFiltered() {
        try {
            Path destination = Path.of(exportPath.getText()).toAbsolutePath().normalize();
            if (java.nio.file.Files.exists(destination)) {
                if (!M3Dialogs.confirm(this, i18n.text("changelog.overwrite_title"),
                        i18n.text("changelog.overwrite_header"),
                        i18n.text("changelog.overwrite_body", destination),
                        i18n.text("stock.remote.confirm_cancel"), i18n.text("changelog.export"))) return;
            }
            Path output = changelog.exportMarkdown(destination, List.copyOf(filtered), i18n);
            notifications.success(i18n.text("changelog.exported_title"),
                    i18n.text("changelog.exported_body", output));
        } catch (IOException | RuntimeException error) {
            notifications.error(i18n.text("changelog.export_failed_title"),
                    i18n.text("changelog.export_failed_body", message(error)));
        }
    }

    private static String message(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    private record ParsedDate(LocalDate date, boolean valid) { }

    private final class EntryCell extends ListCell<ChangelogEntry> {
        @Override protected void updateItem(ChangelogEntry entry, boolean empty) {
            super.updateItem(entry, empty);
            if (empty || entry == null) { setGraphic(null); setText(null); return; }
            Label heading = Mat.label(entry.version() + " · " + entry.date(), "row-title");
            Label category = Mat.chip(entry.category(), "changelog-category");
            Label changes = Mat.label(entry.localized(i18n), "row-desc");
            Label commit = Mat.label("commit " + entry.commit(), "caption");
            VBox card = new VBox(5, new HBox(8, heading, category), changes, commit);
            card.getStyleClass().add("changelog-card");
            card.setAccessibleText(heading.getText() + ". " + changes.getText());
            setGraphic(card);
            setText(null);
        }
    }
}
