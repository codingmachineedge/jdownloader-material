package org.jdownloader.material.ui.view;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.notification.AppNotification;
import org.jdownloader.material.notification.NotificationService;
import org.jdownloader.material.ui.component.Mat;
import org.jdownloader.material.ui.component.M3Dialogs;
import org.jdownloader.material.search.SearchSpec;
import org.jdownloader.material.ui.search.SearchField;

/** Searchable history for dismissed and active notifications. */
public final class NotificationCenterView extends BorderPane {
    private final NotificationService service;
    private final I18n i18n;
    private final SearchField search;
    private final ObservableList<AppNotification> filtered = FXCollections.observableArrayList();
    private final ListChangeListener<AppNotification> historyListener = change -> refresh();

    public NotificationCenterView(NotificationService service, I18n i18n) {
        this.service = service;
        this.i18n = i18n;
        this.search = new SearchField(i18n, "notifications.search");
        getStyleClass().addAll("content-area", "page-view", "notification-center");

        Label title = Mat.label(i18n.text("notifications.title"), "headline", "page-title");
        Label description = Mat.label(i18n.text("notifications.description"), "row-desc");
        search.searchSpecProperty().addListener((observable, previous, current) -> refresh());
        HBox.setHgrow(search, Priority.ALWAYS);
        Button clear = new Button(i18n.text("notifications.clear"));
        clear.getStyleClass().add("outlined-button");
        clear.disableProperty().bind(Bindings.isEmpty(service.history()));
        clear.setOnAction(event -> {
            if (M3Dialogs.confirm(this, i18n.text("notifications.clear_confirm_title"),
                    i18n.text("notifications.clear_confirm_header"),
                    i18n.text("notifications.clear_confirm_body", service.history().size()),
                    i18n.text("stock.remote.confirm_cancel"), i18n.text("notifications.clear"))) {
                service.clearHistory();
            }
        });
        HBox tools = new HBox(10, search, clear);
        tools.setAlignment(Pos.CENTER_LEFT);
        VBox header = new VBox(6, title, description, tools);
        header.getStyleClass().addAll("view-header", "page-head");
        setTop(header);

        ListView<AppNotification> list = new ListView<>(filtered);
        list.getStyleClass().add("notification-history-list");
        list.setAccessibleRole(AccessibleRole.LIST_VIEW);
        list.setAccessibleText(i18n.text("notifications.history"));
        list.setPlaceholder(Mat.label(i18n.text("notifications.empty"), "empty-table-hint"));
        list.setCellFactory(ignored -> new NotificationCell());
        BorderPane.setMargin(list, new Insets(0, 24, 24, 24));
        setCenter(list);

        service.history().addListener(historyListener);
        refresh();
    }

    public void setFilter(String value) {
        search.setSearchSpec(SearchSpec.plain(value == null ? "" : value));
    }

    public void setSearchSpec(SearchSpec value) {
        search.setSearchSpec(value == null ? SearchSpec.empty() : value);
    }

    private void refresh() {
        SearchSpec spec = search.searchSpec();
        filtered.setAll(service.history().stream().filter(item -> spec.expression().isEmpty()
                || (search.validation().valid() && search.evaluator().matches(spec,
                    item.title() + " " + item.body() + " " + item.severity().name()))).toList());
    }

    public void dispose() {
        service.history().removeListener(historyListener);
        search.dispose();
    }

    private final class NotificationCell extends ListCell<AppNotification> {
        private final DateTimeFormatter date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);

        @Override protected void updateItem(AppNotification item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label title = Mat.label(item.title(), "row-title");
            Label body = Mat.label(item.body(), "row-desc");
            Label meta = Mat.label(item.severity().name() + " · "
                    + date.format(item.timestamp().atZone(ZoneId.systemDefault())), "caption");
            VBox card = new VBox(3, title, body, meta);
            card.getStyleClass().add("notification-history-card");
            card.setAccessibleText(item.title() + ". " + item.body());
            setGraphic(card);
            setText(null);
            service.markRead(item.id());
        }
    }
}
