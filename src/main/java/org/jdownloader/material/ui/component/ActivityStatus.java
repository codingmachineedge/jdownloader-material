package org.jdownloader.material.ui.component;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Small shared, in-layout activity message for the status bar.
 *
 * <p>It deliberately has no window, overlay, animation, timeout, or action
 * button. A user can keep working while the most recent result remains visible
 * in the existing status bar instead of appearing as a floating notification.
 */
public final class ActivityStatus {

    private final StringProperty message = new SimpleStringProperty(this, "message", "");
    private final BooleanProperty error = new SimpleBooleanProperty(this, "error", false);

    public StringProperty messageProperty() {
        return message;
    }

    public BooleanProperty errorProperty() {
        return error;
    }

    public void info(String text) {
        set(text, false);
    }

    public void error(String text) {
        set(text, true);
    }

    public void clear() {
        message.set("");
        error.set(false);
    }

    private void set(String text, boolean isError) {
        message.set(text == null ? "" : text.trim());
        error.set(isError && !message.get().isBlank());
    }
}
