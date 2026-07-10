package org.jdownloader.material.ui.component;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import org.jdownloader.material.model.DownloadItem;
import org.jdownloader.material.model.DownloadState;
import org.jdownloader.material.ui.Icons;
import org.jdownloader.material.util.Formats;

/** Cell factories for the Downloads / LinkGrabber tree-tables. */
public final class DownloadCells {
    private DownloadCells() {
    }

    /** Name cell: file/folder icon + label, package rows emphasized. */
    public static Callback<TreeTableColumn<DownloadItem, String>, TreeTableCell<DownloadItem, String>> name() {
        return col -> new TreeTableCell<>() {
            private final Label label = new Label();
            private final HBox box = new HBox(8);
            {
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
            @Override protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                DownloadItem item = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                box.getChildren().setAll(Icons.of(item.isPackage() ? "folder" : "download", 16), label);
                if (item.isPackage()) {
                    int n = ((org.jdownloader.material.model.DownloadPackage) item).childCount();
                    label.setText(name + "  (" + n + ")");
                    label.setStyle("-fx-font-weight: 600;");
                } else {
                    String detail = item instanceof org.jdownloader.material.model.DownloadLink link
                            ? link.detailProperty().get() : "";
                    label.setText(detail == null || detail.isBlank() ? name : name + " — " + detail);
                    label.setStyle("");
                }
                setGraphic(box);
            }
        };
    }

    /** Right-aligned byte-size cell. */
    public static Callback<TreeTableColumn<DownloadItem, Number>, TreeTableCell<DownloadItem, Number>> bytes() {
        return col -> new TreeTableCell<>() {
            { setAlignment(Pos.CENTER_RIGHT); }
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null ? null : Formats.bytes(v.longValue()));
            }
        };
    }

    public static Callback<TreeTableColumn<DownloadItem, Number>, TreeTableCell<DownloadItem, Number>> speed() {
        return col -> new TreeTableCell<>() {
            { setAlignment(Pos.CENTER_RIGHT); }
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                setText(empty || v == null || v.longValue() <= 0 ? "—" : Formats.speed(v.longValue()));
            }
        };
    }

    /** ETA cell — driven by the speed observable, computed from the row item. */
    public static Callback<TreeTableColumn<DownloadItem, Number>, TreeTableCell<DownloadItem, Number>> eta() {
        return col -> new TreeTableCell<>() {
            { setAlignment(Pos.CENTER_RIGHT); }
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                DownloadItem item = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || item == null || item.state() != DownloadState.RUNNING) {
                    setText("—");
                } else {
                    setText(Formats.eta(item.etaSeconds()));
                }
            }
        };
    }

    /** Status chip cell, colored by state. */
    public static Callback<TreeTableColumn<DownloadItem, DownloadState>, TreeTableCell<DownloadItem, DownloadState>> status() {
        return col -> new TreeTableCell<>() {
            private final Label chip = new Label();
            { chip.getStyleClass().add("status-chip"); }
            @Override protected void updateItem(DownloadState st, boolean empty) {
                super.updateItem(st, empty);
                if (empty || st == null) { setGraphic(null); return; }
                chip.setText(st.label());
                chip.getStyleClass().removeIf(c -> c.startsWith("state-"));
                chip.getStyleClass().add(st.styleClass());
                setGraphic(chip);
                setText(null);
            }
        };
    }

    /** Progress-bar cell that recolors on state change via a tracked listener. */
    public static Callback<TreeTableColumn<DownloadItem, Number>, TreeTableCell<DownloadItem, Number>> progress() {
        return col -> new TreeTableCell<>() {
            private final ProgressBar bar = new ProgressBar(0);
            private final Label pct = new Label();
            private final HBox box = new HBox(8, bar, pct);
            private DownloadItem observed;
            private final ChangeListener<DownloadState> stateListener = (o, a, b) -> restyle();
            {
                bar.getStyleClass().add("cell-progress");
                bar.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(bar, javafx.scene.layout.Priority.ALWAYS);
                pct.getStyleClass().add("caption");
                pct.setMinWidth(38);
                box.setAlignment(Pos.CENTER_LEFT);
            }
            @Override protected void updateItem(Number v, boolean empty) {
                super.updateItem(v, empty);
                DownloadItem item = getTableRow() == null ? null : getTableRow().getItem();
                if (observed != item) {
                    if (observed != null) observed.stateProperty().removeListener(stateListener);
                    observed = item;
                    if (observed != null) observed.stateProperty().addListener(stateListener);
                }
                if (empty || item == null) { setGraphic(null); return; }
                double p = v == null ? -1 : v.doubleValue();
                bar.setProgress(p < 0 ? ProgressBar.INDETERMINATE_PROGRESS : p);
                pct.setText(p < 0 ? "" : Formats.percent(p));
                restyle();
                setGraphic(box);
            }
            private void restyle() {
                if (observed == null) return;
                bar.getStyleClass().removeIf(c -> c.startsWith("state-"));
                bar.getStyleClass().add(observed.state().styleClass());
            }
        };
    }
}
