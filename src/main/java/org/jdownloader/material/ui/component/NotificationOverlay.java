package org.jdownloader.material.ui.component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javafx.animation.PauseTransition;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.notification.AppNotification;
import org.jdownloader.material.notification.NotificationService;

/** Bottom-right M3 toast stack; decisions remain in their originating controls. */
public final class NotificationOverlay extends VBox {
    private final NotificationService service;
    private final I18n i18n;
    private final Map<UUID, PauseTransition> timers = new HashMap<>();
    private final ListChangeListener<AppNotification> listener = change -> rebuild();
    private boolean suppressed;

    public NotificationOverlay(NotificationService service, I18n i18n) {
        this.service = service;
        this.i18n = i18n;
        getStyleClass().add("notification-stack");
        setAlignment(Pos.BOTTOM_RIGHT);
        setSpacing(10);
        setPadding(new Insets(16));
        setMaxWidth(440);
        setPickOnBounds(false);
        service.active().addListener(listener);
        rebuild();
    }

    private void rebuild() {
        timers.values().forEach(PauseTransition::stop);
        timers.clear();
        getChildren().clear();
        int start = Math.max(0, service.active().size() - 5);
        for (int index = start; index < service.active().size(); index++) {
            AppNotification item = service.active().get(index);
            getChildren().add(card(item));
            if (!item.severity().persistent() && item.severity().timeoutMillis() > 0) {
                PauseTransition timer = new PauseTransition(Duration.millis(item.severity().timeoutMillis()));
                timer.setOnFinished(event -> service.dismiss(item.id()));
                timers.put(item.id(), timer);
                timer.play();
            }
        }
        updateVisibility();
    }

    /** Keeps durable history active while hiding live cards for clean captures. */
    public void setSuppressed(boolean suppressed) {
        this.suppressed = suppressed;
        updateVisibility();
    }

    private void updateVisibility() {
        boolean shown = !suppressed && !getChildren().isEmpty();
        setVisible(shown);
        setManaged(shown);
    }

    private Region card(AppNotification item) {
        Label title = Mat.label(item.title(), "notification-title");
        Label body = Mat.label(item.body(), "notification-body");
        title.setWrapText(true);
        body.setWrapText(true);
        VBox copy = new VBox(2, title, body);
        HBox.setHgrow(copy, Priority.ALWAYS);

        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (!item.actionLabel().isBlank()) {
            Button action = new Button(item.actionLabel());
            action.getStyleClass().addAll("notification-action", "text-button");
            action.setAccessibleText(item.actionLabel());
            action.setOnAction(event -> service.invokeAction(item.id()));
            actions.getChildren().add(action);
        }
        Button dismiss = new Button("×");
        dismiss.getStyleClass().addAll("notification-dismiss", "icon-button");
        dismiss.setAccessibleText(i18n.text("notifications.dismiss"));
        dismiss.setAccessibleHelp(i18n.text("notifications.dismiss_help"));
        dismiss.setOnAction(event -> service.dismiss(item.id()));
        actions.getChildren().add(dismiss);

        HBox card = new HBox(12, copy, actions);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().addAll("notification-card", "notification-" + item.severity().name().toLowerCase());
        // JavaFX has no ARIA-alert equivalent in AccessibleRole; a named,
        // focusable parent gives assistive technology the complete title/body.
        card.setAccessibleRole(AccessibleRole.PARENT);
        card.setAccessibleText(item.title() + ". " + item.body());
        card.setFocusTraversable(true);
        return card;
    }

    public void dispose() {
        service.active().removeListener(listener);
        timers.values().forEach(PauseTransition::stop);
        timers.clear();
    }
}
