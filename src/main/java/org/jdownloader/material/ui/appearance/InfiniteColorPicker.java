package org.jdownloader.material.ui.appearance;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.jdownloader.material.appearance.AppearanceTargetId;
import org.jdownloader.material.appearance.ColorSpace;
import org.jdownloader.material.appearance.ColorTranslator;
import org.jdownloader.material.appearance.ColorValue;
import org.jdownloader.material.ui.search.SearchField;

/** Continuous JavaFX color field plus numeric translation, alpha, gamut and contrast tooling. */
public final class InfiniteColorPicker extends VBox implements AutoCloseable {

    private static final int MAX_RECENT = 12;

    private final Function<String, String> text;
    private final AppearanceSurfaceSearch surfaceSearch;
    private final ObjectProperty<ColorValue> value = new SimpleObjectProperty<>(this, "value",
            ColorTranslator.fromHex("#006B5C"));
    private final ColorPicker field = new ColorPicker();
    private final ComboBox<ColorSpace> activeSpace = new ComboBox<>();
    private final TextField numeric = new TextField();
    private final Slider alpha = new Slider(0, 1, 1);
    private final ColorPicker contrastBackground = new ColorPicker(Color.WHITE);
    private final Region preview = new Region();
    private final Label status = new Label();
    private final Label contrast = new Label();
    private final ListView<Map.Entry<ColorSpace, String>> translations = new ListView<>();
    private final FlowPane recentsPane = new FlowPane(6, 6);
    private final Deque<ColorValue> recents = new ArrayDeque<>();
    private boolean updating;
    private boolean disposed;

    public InfiniteColorPicker() {
        this(Function.identity());
    }

    public InfiniteColorPicker(Function<String, String> text) {
        this.text = text == null ? Function.identity() : text;
        this.surfaceSearch = new AppearanceSurfaceSearch(this.text,
                "appearance.color.search", "appearance-color-surface-search");
        getStyleClass().add("infinite-color-picker");
        setSpacing(10);
        setPadding(new Insets(10));

        Label title = new Label(label("appearance.color.title", "Infinite color picker"));
        title.getStyleClass().add("appearance-section-title");

        field.setAccessibleText(label("appearance.color.field", "Continuous color field"));
        field.setAccessibleHelp(label("appearance.color.field_help",
                "Open the standard JavaFX continuous color field and custom color dialog."));
        field.valueProperty().addListener((observable, previous, current) -> {
            if (!updating && current != null) setValue(fromFx(current, activeSpace.getValue()));
        });

        activeSpace.getItems().setAll(ColorSpace.values());
        activeSpace.setValue(ColorSpace.HEX8);
        activeSpace.setAccessibleText(label("appearance.color.space", "Active color space"));
        activeSpace.valueProperty().addListener((observable, previous, current) -> {
            if (!updating && current != null && getValue() != null) setValue(getValue().withActiveSpace(current));
            else refresh();
        });

        numeric.setAccessibleText(label("appearance.color.numeric", "Numeric color value"));
        numeric.setAccessibleHelp(label("appearance.color.numeric_help",
                "Enter the current color using the selected color-space notation."));
        numeric.setOnAction(event -> parseNumeric());
        numeric.focusedProperty().addListener((observable, previous, focused) -> {
            if (!focused) parseNumeric();
        });
        HBox.setHgrow(numeric, Priority.ALWAYS);

        alpha.setShowTickLabels(true);
        alpha.setMajorTickUnit(0.25);
        alpha.setBlockIncrement(0.01);
        alpha.setAccessibleText(label("appearance.color.alpha", "Alpha"));
        alpha.valueProperty().addListener((observable, previous, current) -> {
            if (updating) return;
            ColorValue color = getValue();
            setValue(ColorValue.converted(color.red(), color.green(), color.blue(), current.doubleValue(),
                    activeSpace.getValue(), color.clipped(), color.clippingWarning()));
        });

        preview.setMinSize(110, 58);
        preview.setPrefSize(180, 58);
        preview.getStyleClass().add("appearance-color-preview");
        preview.setAccessibleText(label("appearance.color.preview", "Live color preview"));

        contrastBackground.setAccessibleText(label("appearance.color.contrast_background", "Contrast background"));
        contrastBackground.valueProperty().addListener((observable, previous, current) -> {
            refreshContrast();
            refreshSurfaceSearch();
        });
        contrast.getStyleClass().add("appearance-capability");
        contrast.setWrapText(true);
        status.getStyleClass().add("appearance-capability");
        status.setWrapText(true);

        translations.setPrefHeight(190);
        translations.setAccessibleText(label("appearance.color.translations", "Translated color representations"));
        translations.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Map.Entry<ColorSpace, String> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getKey() + "  " + item.getValue());
            }
        });
        Button copy = new Button(label("appearance.color.copy", "Copy representation"));
        copy.setOnAction(event -> copySelected());

        FlowPane swatches = new FlowPane(6, 6);
        swatches.getStyleClass().add("appearance-swatches");
        for (String hex : new String[]{"#000000", "#FFFFFF", "#006B5C", "#315F9F", "#BA1A1A", "#F0C36A"}) {
            ColorValue swatch = ColorTranslator.fromHex(hex);
            Button button = swatchButton(swatch);
            swatches.getChildren().add(button);
        }
        recentsPane.getStyleClass().add("appearance-recents");

        HBox first = new HBox(8, field, activeSpace, numeric);
        first.setAlignment(Pos.CENTER_LEFT);
        HBox alphaRow = new HBox(8, new Label(label("appearance.color.alpha", "Alpha")), alpha);
        alphaRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(alpha, Priority.ALWAYS);
        HBox contrastRow = new HBox(8,
                new Label(label("appearance.color.contrast_background", "Contrast background")), contrastBackground);
        contrastRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(title, surfaceSearch, first, alphaRow, preview, status, contrastRow, contrast,
                new Label(label("appearance.color.swatches", "Swatches")), swatches,
                new Label(label("appearance.color.recents", "Recent colors")), recentsPane,
                new Label(label("appearance.color.translations", "Translations")), translations, copy);
        value.addListener((observable, previous, current) -> {
            if (current != null && !updating) {
                updating = true;
                try { activeSpace.setValue(current.activeSpace()); }
                finally { updating = false; }
                remember(current);
                refresh();
            }
        });
        refresh();
    }

    public ObjectProperty<ColorValue> valueProperty() { return value; }
    public ColorValue getValue() { return value.get(); }

    public SearchField searchField() { return surfaceSearch.searchField(); }

    public void setValue(ColorValue color) {
        ColorValue next = Objects.requireNonNull(color, "color");
        if (next.equals(value.get()) && activeSpace.getValue() == next.activeSpace()) {
            refresh();
            return;
        }
        updating = true;
        try {
            activeSpace.setValue(next.activeSpace());
            value.set(next);
        } finally {
            updating = false;
        }
        refreshSurfaceSearch();
        remember(next);
        refresh();
    }

    public void installAppearanceRegistry(AppearanceRegistry registry, String targetPrefix) {
        registry.registerSubtree(this, targetPrefix == null ? "appearance.color-picker" : targetPrefix);
    }

    private void parseNumeric() {
        if (updating) return;
        try {
            ColorValue parsed = ColorTranslator.parse(activeSpace.getValue(), numeric.getText());
            double parsedAlpha = switch (activeSpace.getValue()) {
                case HEX, RGB, HSL -> alpha.getValue();
                default -> parsed.alpha();
            };
            setValue(ColorValue.converted(parsed.red(), parsed.green(), parsed.blue(), parsedAlpha,
                    activeSpace.getValue(), parsed.clipped(), parsed.clippingWarning()));
            numeric.getStyleClass().remove("invalid");
        } catch (RuntimeException invalid) {
            if (!numeric.getStyleClass().contains("invalid")) numeric.getStyleClass().add("invalid");
            status.setText(label("appearance.color.invalid", "Invalid color") + ": " + invalid.getMessage());
        }
    }

    private void refresh() {
        ColorValue color = getValue();
        if (color == null) return;
        ColorSpace space = activeSpace.getValue() == null ? color.activeSpace() : activeSpace.getValue();
        updating = true;
        try {
            field.setValue(toFx(color));
            alpha.setValue(color.alpha());
            if (!numeric.isFocused()) numeric.setText(ColorTranslator.format(color, space));
            numeric.getStyleClass().remove("invalid");
            preview.setStyle("-fx-background-color:" + color.toCssRgba() + ";");
            translations.setItems(FXCollections.observableArrayList(ColorTranslator.allRepresentations(color).entrySet()));
            String gamut = label("appearance.color.gamut", "Gamut") + ": " + color.gamut();
            String active = label("appearance.color.active_space", "Active space") + ": " + space;
            status.setText(active + " · " + gamut + (color.clipped() ? " · " + color.clippingWarning() : ""));
            status.getStyleClass().remove("warning");
            if (color.clipped()) status.getStyleClass().add("warning");
            refreshContrast();
        } finally {
            updating = false;
        }
    }

    private void refreshContrast() {
        ColorValue foreground = getValue();
        Color backgroundFx = contrastBackground.getValue();
        if (foreground == null || backgroundFx == null) return;
        ColorValue background = fromFx(backgroundFx, ColorSpace.RGBA);
        ColorValue composited = composite(foreground, background);
        double ratio = composited.contrastRatio(background);
        String rating = ratio >= 7 ? "AAA" : ratio >= 4.5 ? "AA" : ratio >= 3 ? "AA large text" : "Fail";
        contrast.setText(String.format(java.util.Locale.ROOT, "%s: %.2f:1 · %s",
                label("appearance.color.contrast", "Contrast"), ratio, rating));
    }

    private void remember(ColorValue color) {
        recents.removeIf(existing -> existing.toHex8().equals(color.toHex8()));
        recents.addFirst(color);
        while (recents.size() > MAX_RECENT) recents.removeLast();
        recentsPane.getChildren().setAll(recents.stream().map(this::swatchButton).toList());
    }

    private Button swatchButton(ColorValue color) {
        Button button = new Button();
        button.getStyleClass().add("appearance-swatch");
        button.setMinSize(40, 40);
        button.setPrefSize(40, 40);
        button.setStyle("-fx-background-color:" + color.toCssRgba() + ";");
        button.setAccessibleText(color.toHex8());
        button.setOnAction(event -> setValue(color));
        return button;
    }

    private void copySelected() {
        Map.Entry<ColorSpace, String> selected = translations.getSelectionModel().getSelectedItem();
        String copy = selected == null ? numeric.getText() : selected.getValue();
        ClipboardContent content = new ClipboardContent();
        content.putString(copy == null ? "" : copy);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private String label(String key, String fallback) {
        String value = text.apply(key);
        return value == null || value.isBlank() || value.equals(key) ? fallback : value;
    }

    private static Color toFx(ColorValue color) {
        return new Color(color.red(), color.green(), color.blue(), color.alpha());
    }

    private static ColorValue fromFx(Color color, ColorSpace space) {
        return ColorValue.converted(color.getRed(), color.getGreen(), color.getBlue(), color.getOpacity(),
                space == null ? ColorSpace.RGBA : space, false, "");
    }

    private static ColorValue composite(ColorValue foreground, ColorValue background) {
        double alpha = foreground.alpha();
        return ColorValue.srgb(foreground.red() * alpha + background.red() * (1 - alpha),
                foreground.green() * alpha + background.green() * (1 - alpha),
                foreground.blue() * alpha + background.blue() * (1 - alpha), 1);
    }

    private void refreshSurfaceSearch() {
        if (disposed || getValue() == null) return;
        List<AppearanceSurfaceSearch.Entry> entries = new java.util.ArrayList<>();
        ColorValue color = getValue();
        String current = label("appearance.color.current", "Current color") + " · " + color.toHex8();
        entries.add(AppearanceSurfaceSearch.Entry.of(current,
                current + " " + color.toStorageString() + " " + color.activeSpace() + " " + color.gamut()));
        entries.add(AppearanceSurfaceSearch.Entry.of(status.getText(), status.getText()));
        entries.add(AppearanceSurfaceSearch.Entry.of(contrast.getText(), contrast.getText()));
        for (Map.Entry<ColorSpace, String> translation : ColorTranslator.allRepresentations(color).entrySet()) {
            String display = label("appearance.color.translations", "Translations") + " · "
                    + translation.getKey() + " · " + translation.getValue();
            entries.add(new AppearanceSurfaceSearch.Entry(display, display, () -> {
                for (Map.Entry<ColorSpace, String> item : translations.getItems()) {
                    if (item.getKey() == translation.getKey()) {
                        translations.getSelectionModel().select(item);
                        translations.scrollTo(item);
                        break;
                    }
                }
            }));
        }
        for (ColorValue recent : recents) {
            String display = label("appearance.color.recents", "Recent colors") + " · " + recent.toHex8();
            entries.add(AppearanceSurfaceSearch.Entry.of(display, display));
        }
        for (String hex : new String[]{"#000000", "#FFFFFF", "#006B5C", "#315F9F", "#BA1A1A", "#F0C36A"}) {
            String display = label("appearance.color.swatches", "Swatches") + " · " + hex;
            entries.add(AppearanceSurfaceSearch.Entry.of(display, display));
        }
        surfaceSearch.setEntries(entries);
    }

    @Override
    public void close() {
        if (disposed) return;
        disposed = true;
        surfaceSearch.close();
    }
}
