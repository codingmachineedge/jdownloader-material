package org.jdownloader.material.ui.appearance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import org.jdownloader.material.appearance.AppearanceProperty;
import org.jdownloader.material.appearance.AppearanceStyle;
import org.jdownloader.material.search.SearchSpec;
import org.jdownloader.material.search.SearchValidation;
import org.jdownloader.material.ui.search.SearchField;

/** Searchable installed/bundled font picker with free/step size and live preview. */
public final class FontPicker extends VBox implements AutoCloseable {

    private final Function<String, String> text;
    private final AppearanceSurfaceSearch surfaceSearch;
    private final ObservableList<String> families = FXCollections.observableArrayList();
    private final FilteredList<String> filtered = new FilteredList<>(families, ignored -> true);
    private final ListView<String> familyList = new ListView<>(filtered);
    private final TextField freeSize = new TextField("14");
    private final Spinner<Double> steppedSize = new Spinner<>(6, 160, 14, 0.5);
    private final ComboBox<Integer> weight = new ComboBox<>();
    private final ComboBox<String> posture = choice("NORMAL", "ITALIC", "OBLIQUE");
    private final ComboBox<String> underline = choice("NONE", "SINGLE", "DOUBLE", "DOTTED", "DASHED", "WAVY");
    private final ComboBox<String> strike = choice("NONE", "SINGLE", "DOUBLE");
    private final TextField axes = new TextField();
    private final TextField characterSpacing = new TextField("0");
    private final TextField lineHeight = new TextField("1");
    private final TextField cjkFallback = new TextField();
    private final Label sample = new Label("JDownloader Material · 字型預覽 · 012345");
    private final Label capability = new Label();
    private Consumer<Map<AppearanceProperty, String>> changeHandler = ignored -> { };
    private final ChangeListener<SearchSpec> searchSpecListener =
            (observable, previous, current) -> refreshSurfaceSearch();
    private final ChangeListener<SearchValidation> searchValidationListener =
            (observable, previous, current) -> refreshSurfaceSearch();
    private boolean updating;
    private boolean disposed;

    public FontPicker() {
        this(List.of(), Function.identity());
    }

    public FontPicker(Collection<String> bundledFamilies, Function<String, String> text) {
        this.text = text == null ? Function.identity() : text;
        this.surfaceSearch = new AppearanceSurfaceSearch(this.text,
                "appearance.font.search", "appearance-font-surface-search");
        getStyleClass().add("font-picker");
        setSpacing(10);
        setPadding(new Insets(10));

        List<String> merged = new ArrayList<>(Font.getFamilies());
        if (bundledFamilies != null) merged.addAll(bundledFamilies);
        families.setAll(merged.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty())
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList());

        surfaceSearch.searchField().searchSpecProperty().addListener(searchSpecListener);
        surfaceSearch.searchField().validationProperty().addListener(searchValidationListener);

        familyList.setPrefHeight(180);
        familyList.getStyleClass().add("font-family-list");
        familyList.setAccessibleText(label("appearance.font.family", "Font family"));
        familyList.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(String family, boolean empty) {
                super.updateItem(family, empty);
                setText(empty ? null : family);
                setFont(empty || family == null ? Font.getDefault() : Font.font(family, 13));
            }
        });
        familyList.getSelectionModel().selectedItemProperty().addListener((observable, previous, current) ->
                changed(AppearanceProperty.FONT_FAMILY));

        steppedSize.setEditable(true);
        freeSize.setAccessibleText(label("appearance.font.size_free", "Free-entry font size"));
        steppedSize.setAccessibleText(label("appearance.font.size_step", "Stepped font size"));
        freeSize.setOnAction(event -> commitFreeSize());
        freeSize.focusedProperty().addListener((observable, previous, focused) -> { if (!focused) commitFreeSize(); });
        steppedSize.valueProperty().addListener((observable, previous, current) -> {
            if (updating) return;
            updating = true;
            freeSize.setText(format(current));
            updating = false;
            changed(AppearanceProperty.FONT_SIZE);
        });

        for (int value = 100; value <= 1_000; value += 50) weight.getItems().add(value);
        weight.setValue(400);
        posture.setValue("NORMAL");
        underline.setValue("NONE");
        strike.setValue("NONE");
        for (ComboBox<String> combo : List.of(posture, underline, strike)) localizeCombo(combo);
        weight.valueProperty().addListener((observable, previous, current) -> changed(AppearanceProperty.FONT_WEIGHT));
        posture.valueProperty().addListener((observable, previous, current) -> changed(AppearanceProperty.FONT_POSTURE));
        underline.valueProperty().addListener((observable, previous, current) -> changed(AppearanceProperty.UNDERLINE_STYLE));
        strike.valueProperty().addListener((observable, previous, current) -> changed(AppearanceProperty.STRIKETHROUGH_STYLE));
        commit(axes, AppearanceProperty.VARIABLE_FONT_AXES);
        commit(characterSpacing, AppearanceProperty.CHARACTER_SPACING);
        commit(lineHeight, AppearanceProperty.LINE_HEIGHT);
        cjkFallback.setOnAction(event -> changed());
        cjkFallback.focusedProperty().addListener((observable, previous, focused) -> { if (!focused) changed(); });

        axes.setPromptText("wght=450;wdth=100");
        axes.setAccessibleText(label("appearance.font.axes", "Variable font axes"));
        cjkFallback.setText("Microsoft JhengHei UI, Noto Sans CJK TC, Segoe UI, sans-serif");
        capability.setText(label("appearance.font.axes_capability",
                "Variable-axis values are retained. JavaFX cannot expose every installed font axis at runtime."));
        capability.getStyleClass().add("appearance-capability");
        capability.setWrapText(true);

        sample.getStyleClass().add("font-live-sample");
        sample.setWrapText(true);
        sample.setMinHeight(72);
        sample.setMaxWidth(Double.MAX_VALUE);
        sample.setAccessibleText(label("appearance.font.preview", "Live font preview"));

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        int row = 0;
        row(form, row++, label("appearance.font.size", "Size"), new HBox(8, freeSize, steppedSize));
        row(form, row++, label("appearance.font.weight", "Weight"), weight);
        row(form, row++, label("appearance.font.posture", "Style"), posture);
        row(form, row++, label("appearance.font.underline", "Underline"), underline);
        row(form, row++, label("appearance.font.strike", "Strikethrough"), strike);
        row(form, row++, label("appearance.font.character_spacing", "Character spacing"), characterSpacing);
        row(form, row++, label("appearance.font.line_height", "Line height"), lineHeight);
        row(form, row++, label("appearance.font.cjk_fallback", "CJK fallback"), cjkFallback);
        row(form, row, label("appearance.font.axes", "Variable axes"), axes);

        getChildren().addAll(new Label(label("appearance.font.title", "Font picker")), surfaceSearch, familyList,
                form, capability, sample);
        VBox.setVgrow(familyList, Priority.ALWAYS);
        if (!families.isEmpty()) familyList.getSelectionModel().selectFirst();
        refreshSample();
        refreshSurfaceSearch();
    }

    public void setChangeHandler(Consumer<Map<AppearanceProperty, String>> handler) {
        changeHandler = handler == null ? ignored -> { } : handler;
    }

    public void load(AppearanceStyle style) {
        load(style, cjkFallback.getText());
    }

    public void load(AppearanceStyle style, String globalCjkFallback) {
        updating = true;
        try {
            String family = style.get(AppearanceProperty.FONT_FAMILY).orElse(familyList.getSelectionModel().getSelectedItem());
            if (family != null) familyList.getSelectionModel().select(family);
            double size = style.number(AppearanceProperty.FONT_SIZE).orElse(14);
            steppedSize.getValueFactory().setValue(size);
            freeSize.setText(format(size));
            weight.setValue(style.get(AppearanceProperty.FONT_WEIGHT).map(Integer::parseInt).orElse(400));
            posture.setValue(style.get(AppearanceProperty.FONT_POSTURE).orElse("NORMAL"));
            underline.setValue(style.get(AppearanceProperty.UNDERLINE_STYLE).orElse("NONE"));
            strike.setValue(style.get(AppearanceProperty.STRIKETHROUGH_STYLE).orElse("NONE"));
            axes.setText(style.get(AppearanceProperty.VARIABLE_FONT_AXES).orElse(""));
            characterSpacing.setText(style.get(AppearanceProperty.CHARACTER_SPACING).orElse("0"));
            lineHeight.setText(style.get(AppearanceProperty.LINE_HEIGHT).orElse("1"));
            if (globalCjkFallback != null && !globalCjkFallback.isBlank()) cjkFallback.setText(globalCjkFallback);
        } finally {
            updating = false;
        }
        refreshSample();
        refreshSurfaceSearch();
    }

    public Map<AppearanceProperty, String> currentValues() {
        EnumMap<AppearanceProperty, String> values = new EnumMap<>(AppearanceProperty.class);
        String family = familyList.getSelectionModel().getSelectedItem();
        if (family != null) values.put(AppearanceProperty.FONT_FAMILY, family);
        values.put(AppearanceProperty.FONT_SIZE, format(steppedSize.getValue()));
        values.put(AppearanceProperty.FONT_WEIGHT, Integer.toString(weight.getValue()));
        values.put(AppearanceProperty.FONT_POSTURE, posture.getValue());
        values.put(AppearanceProperty.UNDERLINE_STYLE, underline.getValue());
        values.put(AppearanceProperty.STRIKETHROUGH_STYLE, strike.getValue());
        if (!axes.getText().isBlank()) values.put(AppearanceProperty.VARIABLE_FONT_AXES, axes.getText().trim());
        values.put(AppearanceProperty.CHARACTER_SPACING, validNumber(characterSpacing.getText(), 0));
        values.put(AppearanceProperty.LINE_HEIGHT, validNumber(lineHeight.getText(), 1));
        return Map.copyOf(values);
    }

    public String cjkFallback() { return cjkFallback.getText().trim(); }

    public SearchField searchField() { return surfaceSearch.searchField(); }

    public void installAppearanceRegistry(AppearanceRegistry registry, String targetPrefix) {
        registry.registerSubtree(this, targetPrefix == null ? "appearance.font-picker" : targetPrefix);
    }

    private void commitFreeSize() {
        if (updating) return;
        try {
            double value = Double.parseDouble(freeSize.getText().trim());
            if (!Double.isFinite(value) || value < 6 || value > 160) throw new NumberFormatException();
            steppedSize.getValueFactory().setValue(value);
            freeSize.getStyleClass().remove("invalid");
        } catch (NumberFormatException invalid) {
            if (!freeSize.getStyleClass().contains("invalid")) freeSize.getStyleClass().add("invalid");
        }
    }

    private void changed(AppearanceProperty... properties) {
        if (updating) return;
        refreshSample();
        Map<AppearanceProperty, String> all = currentValues();
        EnumMap<AppearanceProperty, String> changed = new EnumMap<>(AppearanceProperty.class);
        for (AppearanceProperty property : properties) {
            String value = all.get(property);
            if (value != null) changed.put(property, value);
            else if (property == AppearanceProperty.VARIABLE_FONT_AXES) changed.put(property, "");
        }
        changeHandler.accept(Map.copyOf(changed));
        refreshSurfaceSearch();
    }

    private void refreshSurfaceSearch() {
        if (disposed) return;
        String selectedFamily = familyList.getSelectionModel().getSelectedItem();
        filtered.setPredicate(family -> Objects.equals(family, selectedFamily)
                || surfaceSearch.matchesCandidate(label("appearance.font.family", "Font family") + " " + family));

        List<AppearanceSurfaceSearch.Entry> entries = new ArrayList<>();
        for (String family : families) {
            String display = label("appearance.font.family", "Font family") + " · " + family;
            entries.add(new AppearanceSurfaceSearch.Entry(display, display, () -> familyList.scrollTo(family)));
        }
        addCurrent(entries, "appearance.font.family", "Font family", selectedFamily);
        addCurrent(entries, "appearance.font.size", "Size", freeSize.getText());
        addCurrent(entries, "appearance.font.weight", "Weight", Objects.toString(weight.getValue(), ""));
        addCurrent(entries, "appearance.font.posture", "Style", posture.getValue());
        addCurrent(entries, "appearance.font.underline", "Underline", underline.getValue());
        addCurrent(entries, "appearance.font.strike", "Strikethrough", strike.getValue());
        addCurrent(entries, "appearance.font.character_spacing", "Character spacing", characterSpacing.getText());
        addCurrent(entries, "appearance.font.line_height", "Line height", lineHeight.getText());
        addCurrent(entries, "appearance.font.cjk_fallback", "CJK fallback", cjkFallback.getText());
        addCurrent(entries, "appearance.font.axes", "Variable axes", axes.getText());
        for (String option : posture.getItems()) addOption(entries, "appearance.font.posture", "Style", option);
        for (String option : underline.getItems()) addOption(entries, "appearance.font.underline", "Underline", option);
        for (String option : strike.getItems()) addOption(entries, "appearance.font.strike", "Strikethrough", option);
        surfaceSearch.setEntries(entries);
    }

    private void addCurrent(List<AppearanceSurfaceSearch.Entry> entries, String key, String fallback, String value) {
        if (value == null || value.isBlank()) return;
        String display = label(key, fallback) + " · " + value;
        entries.add(AppearanceSurfaceSearch.Entry.of(display, display));
    }

    private void addOption(List<AppearanceSurfaceSearch.Entry> entries, String key, String fallback, String value) {
        String localized = label("appearance.choice." + value,
                value.toLowerCase(Locale.ROOT).replace('_', ' '));
        String display = label(key, fallback) + " · " + localized;
        entries.add(AppearanceSurfaceSearch.Entry.of(display, display + " " + value));
    }

    private void commit(TextField field, AppearanceProperty property) {
        field.setOnAction(event -> changed(property));
        field.focusedProperty().addListener((observable, previous, focused) -> {
            if (!focused) changed(property);
        });
    }

    private void refreshSample() {
        String family = familyList.getSelectionModel().getSelectedItem();
        if (family == null) family = Font.getDefault().getFamily();
        double size = steppedSize.getValue();
        int numericWeight = weight.getValue() == null ? 400 : weight.getValue();
        FontWeight fontWeight = numericWeight >= 700 ? FontWeight.BOLD
                : numericWeight >= 550 ? FontWeight.SEMI_BOLD
                : numericWeight <= 300 ? FontWeight.LIGHT : FontWeight.NORMAL;
        FontPosture fontPosture = "NORMAL".equals(posture.getValue()) ? FontPosture.REGULAR : FontPosture.ITALIC;
        sample.setFont(Font.font(family, fontWeight, fontPosture, size));
        sample.setUnderline(!"NONE".equals(underline.getValue()));
        sample.setStyle("-fx-strikethrough:" + !"NONE".equals(strike.getValue()) + ";");
        try { sample.setLineSpacing(Math.max(0, Double.parseDouble(lineHeight.getText()) - 1) * size); }
        catch (NumberFormatException ignored) { sample.setLineSpacing(0); }
    }

    private static ComboBox<String> choice(String... values) {
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(values));
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private void localizeCombo(ComboBox<String> combo) {
        combo.setCellFactory(list -> localizedChoiceCell());
        combo.setButtonCell(localizedChoiceCell());
    }

    private ListCell<String> localizedChoiceCell() {
        return new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(label("appearance.choice." + item,
                        item.toLowerCase(Locale.ROOT).replace('_', ' ')));
            }
        };
    }

    private static void row(GridPane grid, int row, String label, javafx.scene.Node control) {
        Label title = new Label(label);
        grid.add(title, 0, row);
        grid.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
    }

    private String label(String key, String fallback) {
        String value = text.apply(key);
        return value == null || value.isBlank() || value.equals(key) ? fallback : value;
    }

    private static String validNumber(String text, double fallback) {
        try {
            double parsed = Double.parseDouble(text.trim());
            return Double.isFinite(parsed) ? Double.toString(parsed) : Double.toString(fallback);
        } catch (RuntimeException invalid) {
            return Double.toString(fallback);
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    @Override
    public void close() {
        if (disposed) return;
        disposed = true;
        surfaceSearch.searchField().searchSpecProperty().removeListener(searchSpecListener);
        surfaceSearch.searchField().validationProperty().removeListener(searchValidationListener);
        surfaceSearch.close();
    }
}
