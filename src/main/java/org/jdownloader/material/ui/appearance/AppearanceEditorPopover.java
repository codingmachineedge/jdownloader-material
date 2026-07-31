package org.jdownloader.material.ui.appearance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.jdownloader.material.appearance.AppearancePreset;
import org.jdownloader.material.appearance.AppearanceProfile;
import org.jdownloader.material.appearance.AppearanceProperty;
import org.jdownloader.material.appearance.AppearanceState;
import org.jdownloader.material.appearance.AppearanceStyle;
import org.jdownloader.material.appearance.AppearanceTargetId;
import org.jdownloader.material.appearance.ColorTranslator;
import org.jdownloader.material.appearance.ColorValue;
import org.jdownloader.material.appearance.Density;
import org.jdownloader.material.appearance.FontSource;
import org.jdownloader.material.appearance.ThemeMode;
import org.jdownloader.material.search.SearchSpec;
import org.jdownloader.material.ui.search.SearchField;

/** Non-modal appearance editor that remains anchored to the exact originating element. */
public final class AppearanceEditorPopover implements AutoCloseable {

    private static final double DEFAULT_WIDTH = 820;
    private static final double DEFAULT_HEIGHT = 720;

    private final AppearanceService service;
    private final AppearanceRegistry registry;
    private final Function<String, String> text;
    private final AppearanceSurfaceSearch surfaceSearch;
    private final Stage stage = new Stage(StageStyle.UNDECORATED);
    private final BorderPane root = new BorderPane();
    private final Label targetLabel = new Label();
    private final Label status = new Label();
    private final ComboBox<AppearanceState> state = new ComboBox<>();
    private final FontPicker fontPicker;
    private final InfiniteColorPicker colorPicker;
    private final InfiniteColorPicker globalColorPicker;
    private final ComboBox<AppearanceProperty> selectedColorProperty = new ComboBox<>();
    private final ComboBox<String> selectedGlobalColor = new ComboBox<>();
    private final ListView<AppearancePreset> presets = new ListView<>();
    private final List<Runnable> reloaders = new ArrayList<>();
    private final ChangeListener<SearchSpec> editorSearchListener =
            (observable, previous, current) -> refreshEditorSearch();
    private final InvalidationListener anchorListener = observable -> relocateLater();
    private final InvalidationListener windowListener = observable -> relocateLater();

    private Node anchor;
    private AppearanceTargetId targetId;
    private Window anchorWindow;
    private TabPane tabs;
    private boolean ownerInitialized;
    private boolean updating;
    private boolean closed;

    AppearanceEditorPopover(AppearanceService service, AppearanceRegistry registry, Function<String, String> text) {
        this.service = Objects.requireNonNull(service, "service");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.text = text == null ? Function.identity() : text;
        this.surfaceSearch = new AppearanceSurfaceSearch(this.text,
                "appearance.editor.search", "appearance-editor-surface-search");
        this.fontPicker = new FontPicker(List.of(), this.text);
        this.colorPicker = new InfiniteColorPicker(this.text);
        this.globalColorPicker = new InfiniteColorPicker(this.text);
        build();
    }

    public void show(Node nextAnchor, AppearanceTargetId nextTargetId) {
        if (closed) throw new IllegalStateException("Appearance editor is closed");
        Objects.requireNonNull(nextAnchor, "anchor");
        Objects.requireNonNull(nextTargetId, "targetId");
        detachAnchor();
        anchor = nextAnchor;
        targetId = nextTargetId;
        anchorWindow = anchor.getScene() == null ? null : anchor.getScene().getWindow();
        if (anchor.getScene() != null) {
            String appearanceCss = Objects.requireNonNull(getClass().getResource("/css/appearance.css")).toExternalForm();
            root.getScene().getStylesheets().setAll(anchor.getScene().getStylesheets());
            if (!root.getScene().getStylesheets().contains(appearanceCss)) {
                root.getScene().getStylesheets().add(appearanceCss);
            }
        }
        if (!ownerInitialized && anchorWindow != null) {
            stage.initOwner(anchorWindow);
            ownerInitialized = true;
        }
        attachAnchor();
        targetLabel.setText(label("appearance.editor.target", "Target") + ": " + targetId.value());
        state.setValue(AppearanceState.NORMAL);
        reload();
        if (!stage.isShowing()) stage.show();
        stage.toFront();
        relocateLater();
    }

    public void close() {
        if (closed) return;
        closed = true;
        detachAnchor();
        surfaceSearch.searchField().searchSpecProperty().removeListener(editorSearchListener);
        surfaceSearch.close();
        fontPicker.close();
        colorPicker.close();
        globalColorPicker.close();
        registry.detachScene(root.getScene());
        stage.close();
    }

    private void build() {
        stage.initModality(Modality.NONE);
        stage.setAlwaysOnTop(false);
        stage.setWidth(DEFAULT_WIDTH);
        stage.setHeight(DEFAULT_HEIGHT);

        root.getStyleClass().add("appearance-editor");
        root.setAccessibleRole(AccessibleRole.DIALOG);
        root.setAccessibleText(label("appearance.editor.title", "Appearance editor"));

        Label title = new Label(label("appearance.editor.title", "Appearance editor"));
        title.getStyleClass().add("appearance-editor-title");
        targetLabel.getStyleClass().add("appearance-target-label");
        targetLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(targetLabel, Priority.ALWAYS);
        state.getItems().setAll(AppearanceState.values());
        localizeCombo(state, value -> choiceLabel(value.name()));
        state.setAccessibleText(label("appearance.editor.state", "Interaction state"));
        state.valueProperty().addListener((observable, previous, current) -> reload());
        Button close = new Button("×");
        close.getStyleClass().add("appearance-editor-close");
        close.setAccessibleText(label("appearance.editor.close", "Close appearance editor"));
        close.setOnAction(event -> stage.hide());
        VBox titles = new VBox(2, title, targetLabel);
        HBox header = new HBox(10, titles, state, close);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("appearance-editor-header");
        HBox.setHgrow(titles, Priority.ALWAYS);
        surfaceSearch.searchField().searchSpecProperty().addListener(editorSearchListener);
        root.setTop(new VBox(header, surfaceSearch));

        tabs = new TabPane(
                tab("appearance.tab.global", "Global", globalTab()),
                tab("appearance.tab.typography", "Typography", typographyTab()),
                tab("appearance.tab.colors", "Colors", colorsTab()),
                tab("appearance.tab.layout", "Layout", propertyPane(EnumSet.of(
                        AppearanceProperty.Category.GEOMETRY, AppearanceProperty.Category.SPACING))),
                tab("appearance.tab.icon", "Icon", propertyPane(EnumSet.of(AppearanceProperty.Category.ICON))),
                tab("appearance.tab.presets", "Presets", presetsTab()));
        tabs.getStyleClass().add("appearance-editor-tabs");
        root.setCenter(tabs);

        status.getStyleClass().add("appearance-editor-status");
        status.setWrapText(true);
        status.setMinHeight(28);
        root.setBottom(status);

        Scene editorScene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        String css = Objects.requireNonNull(getClass().getResource("/css/appearance.css")).toExternalForm();
        editorScene.getStylesheets().add(css);
        editorScene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.hide();
                event.consume();
            }
        });
        stage.setScene(editorScene);
        registry.attachScene(editorScene);
        registry.registerSubtree(root, "appearance.editor");
        fontPicker.installAppearanceRegistry(registry, "appearance.editor.font-picker");
        colorPicker.installAppearanceRegistry(registry, "appearance.editor.color-picker");
        globalColorPicker.installAppearanceRegistry(registry, "appearance.editor.global-color-picker");
        stage.setOnHidden(event -> {
            detachAnchor();
            Node returnTarget = anchor;
            if (returnTarget != null && returnTarget.getScene() != null) Platform.runLater(returnTarget::requestFocus);
        });
    }

    SearchField searchField() {
        return surfaceSearch.searchField();
    }

    private Node globalTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(14));

        ComboBox<ThemeMode> theme = new ComboBox<>(FXCollections.observableArrayList(ThemeMode.values()));
        ComboBox<Density> density = new ComboBox<>(FXCollections.observableArrayList(Density.values()));
        TextField family = new TextField();
        ComboBox<FontSource> source = new ComboBox<>(FXCollections.observableArrayList(FontSource.values()));
        localizeCombo(theme, value -> choiceLabel(value.name()));
        localizeCombo(density, value -> choiceLabel(value.name()));
        localizeCombo(source, value -> choiceLabel(value.name()));
        TextField scale = new TextField();
        ComboBox<Integer> weight = new ComboBox<>();
        for (int value = 100; value <= 1_000; value += 50) weight.getItems().add(value);
        TextField fallback = new TextField();

        theme.valueProperty().addListener((observable, previous, current) -> {
            if (!updating && current != null) service.updateGlobal(profile -> profile.setTheme(current));
        });
        density.valueProperty().addListener((observable, previous, current) -> {
            if (!updating && current != null) service.updateGlobal(profile -> profile.setDensity(current));
        });
        source.valueProperty().addListener((observable, previous, current) -> {
            if (!updating && current != null && !family.getText().isBlank()) {
                service.updateGlobal(profile -> profile.setFontFamily(family.getText(), current));
            }
        });
        weight.valueProperty().addListener((observable, previous, current) -> {
            if (!updating && current != null) service.updateGlobal(profile -> profile.setFontWeight(current));
        });
        commit(family, () -> service.updateGlobal(profile -> profile.setFontFamily(family.getText(), source.getValue())));
        commit(scale, () -> service.updateGlobal(profile -> profile.setFontSizeScale(Double.parseDouble(scale.getText().trim()))));
        commit(fallback, () -> service.updateGlobal(profile -> profile.setCjkFallback(fallback.getText())));

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        formRow(form, 0, label("appearance.global.theme", "Theme"), theme);
        formRow(form, 1, label("appearance.global.density", "Density"), density);
        formRow(form, 2, label("appearance.global.font_family", "Font family"), family);
        formRow(form, 3, label("appearance.global.font_source", "Font source"), source);
        formRow(form, 4, label("appearance.global.font_scale", "Font size scale"), scale);
        formRow(form, 5, label("appearance.global.font_weight", "Font weight"), weight);
        formRow(form, 6, label("appearance.global.cjk_fallback", "CJK-safe fallback"), fallback);

        selectedGlobalColor.getItems().setAll("seed", "accent");
        localizeCombo(selectedGlobalColor, value -> choiceLabel(value.toUpperCase(Locale.ROOT)));
        selectedGlobalColor.setValue("accent");
        selectedGlobalColor.valueProperty().addListener((observable, previous, current) -> loadGlobalColor());
        globalColorPicker.valueProperty().addListener((observable, previous, current) -> {
            if (updating || current == null) return;
            service.updateGlobal(profile -> {
                if ("seed".equals(selectedGlobalColor.getValue())) profile.setSeedColor(current);
                else profile.setAccentColor(current);
            });
        });

        reloaders.add(() -> {
            AppearanceProfile profile = service.profile();
            theme.setValue(profile.theme());
            density.setValue(profile.density());
            family.setText(profile.fontFamily());
            source.setValue(profile.fontSource());
            scale.setText(Double.toString(profile.fontSizeScale()));
            weight.setValue(profile.fontWeight());
            fallback.setText(profile.cjkFallback());
            loadGlobalColor();
        });

        box.getChildren().addAll(section("appearance.global.title", "Global profile"), form,
                new Separator(), selectedGlobalColor, globalColorPicker);
        return scroll(box);
    }

    private Node typographyTab() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(10));
        fontPicker.setChangeHandler(values -> {
            if (updating || targetId == null) return;
            service.update(targetId, selectedState(), style -> values.forEach(style::set));
            String fallback = fontPicker.cjkFallback();
            if (!fallback.isBlank()) service.updateGlobal(profile -> profile.setCjkFallback(fallback));
        });
        reloaders.add(() -> fontPicker.load(explicitStyle(), service.profile().cjkFallback()));
        Node advanced = propertyPane(EnumSet.of(AppearanceProperty.Category.TYPOGRAPHY),
                EnumSet.of(AppearanceProperty.FONT_FAMILY, AppearanceProperty.FONT_SIZE,
                        AppearanceProperty.FONT_WEIGHT, AppearanceProperty.FONT_POSTURE,
                        AppearanceProperty.UNDERLINE_STYLE, AppearanceProperty.STRIKETHROUGH_STYLE,
                        AppearanceProperty.VARIABLE_FONT_AXES, AppearanceProperty.CHARACTER_SPACING,
                        AppearanceProperty.LINE_HEIGHT));
        content.getChildren().addAll(fontPicker, new Separator(),
                section("appearance.typography.advanced", "Advanced typography"), advanced);
        return scroll(content);
    }

    private Node colorsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        List<AppearanceProperty> colors = java.util.Arrays.stream(AppearanceProperty.values())
                .filter(property -> property.kind() == AppearanceProperty.Kind.COLOR).toList();
        selectedColorProperty.getItems().setAll(colors);
        selectedColorProperty.setCellFactory(list -> propertyCell());
        selectedColorProperty.setButtonCell(propertyCell());
        selectedColorProperty.setValue(AppearanceProperty.TEXT_COLOR);
        Label capability = new Label();
        capability.getStyleClass().add("appearance-capability");
        capability.setWrapText(true);
        selectedColorProperty.valueProperty().addListener((observable, previous, current) -> {
            loadSelectedColor();
            capability.setText(current != null && !current.javaFxSupported()
                    ? label("appearance.unsupported", "Stored, but this JavaFX control cannot render it directly.") : "");
        });
        colorPicker.valueProperty().addListener((observable, previous, current) -> {
            if (updating || current == null || targetId == null || selectedColorProperty.getValue() == null) return;
            service.update(targetId, selectedState(), style -> style.set(selectedColorProperty.getValue(), current));
        });
        Button reset = new Button(label("appearance.reset.property", "Reset property"));
        reset.setOnAction(event -> {
            if (targetId != null && selectedColorProperty.getValue() != null) {
                service.resetProperty(targetId, selectedState(), selectedColorProperty.getValue());
                reload();
            }
        });
        reloaders.add(this::loadSelectedColor);
        box.getChildren().addAll(section("appearance.colors.title", "Target color"),
                new HBox(8, selectedColorProperty, reset), capability, colorPicker);
        return scroll(box);
    }

    private Node presetsTab() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        presets.setPrefHeight(220);
        presets.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(AppearancePreset preset, boolean empty) {
                super.updateItem(preset, empty);
                setText(empty || preset == null ? null : presetLabel(preset));
            }
        });
        Button apply = new Button(label("appearance.preset.apply", "Apply preset"));
        apply.setOnAction(event -> {
            AppearancePreset preset = presets.getSelectionModel().getSelectedItem();
            if (preset != null && targetId != null) {
                service.applyPreset(preset, targetId);
                reload();
            }
        });
        Button delete = new Button(label("appearance.preset.delete", "Delete user preset"));
        delete.setOnAction(event -> {
            AppearancePreset preset = presets.getSelectionModel().getSelectedItem();
            if (preset != null && !preset.builtIn()) {
                confirm("appearance.confirm.delete_preset.title", "Delete appearance preset?",
                        "appearance.confirm.delete_preset.body", "This removes the named preset.", preset.name(), () -> {
                            service.removeUserPreset(preset.id());
                            reloadPresets();
                        });
            }
        });
        presets.getSelectionModel().selectedItemProperty().addListener((observable, previous, current) ->
                delete.setDisable(current == null || current.builtIn()));

        TextField name = new TextField();
        name.setPromptText(label("appearance.preset.name", "Preset name"));
        Button save = new Button(label("appearance.preset.save", "Save current as preset"));
        save.setOnAction(event -> {
            if (name.getText() == null || name.getText().isBlank()) return;
            String id = AppearanceTargetId.segment(name.getText()) + "-" + Long.toUnsignedString(System.nanoTime(), 36);
            AppearancePreset preset = service.profile().snapshotPreset(id, name.getText(), explicitStyle());
            service.addUserPreset(preset);
            name.clear();
            reloadPresets();
            presets.getSelectionModel().select(preset);
        });

        Button resetTarget = new Button(label("appearance.reset.target", "Reset target"));
        resetTarget.setOnAction(event -> {
            if (targetId == null) return;
            AppearanceTargetId resetting = targetId;
            confirm("appearance.confirm.reset_target.title", "Reset this target?",
                    "appearance.confirm.reset_target.body",
                    "All appearance overrides for this target will return to inherited values.",
                    resetting.value(), () -> { service.resetTarget(resetting); reload(); });
        });
        Button resetGlobal = new Button(label("appearance.reset.global", "Reset all appearance"));
        resetGlobal.setOnAction(event -> confirm(
                "appearance.confirm.reset_global.title", "Reset all appearance?",
                "appearance.confirm.reset_global.body",
                "Global appearance and all target overrides will return to defaults. Named presets are retained.",
                "", () -> { service.resetGlobalAppearance(); reload(); }));

        Button export = new Button(label("appearance.export", "Export profile"));
        export.setOnAction(event -> exportProfile());
        Button importButton = new Button(label("appearance.import", "Import profile"));
        importButton.setOnAction(event -> importProfile());

        HBox nameRow = new HBox(8, name, save);
        HBox.setHgrow(name, Priority.ALWAYS);
        box.getChildren().addAll(section("appearance.presets.title", "Named presets"), presets,
                new HBox(8, apply, delete), nameRow, new Separator(),
                new HBox(8, resetTarget, resetGlobal), new HBox(8, export, importButton));
        reloaders.add(this::reloadPresets);
        return scroll(box);
    }

    private Node propertyPane(EnumSet<AppearanceProperty.Category> categories) {
        return propertyPane(categories, EnumSet.noneOf(AppearanceProperty.class));
    }

    private Node propertyPane(EnumSet<AppearanceProperty.Category> categories,
                              EnumSet<AppearanceProperty> excluded) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("appearance-property-grid");
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(8));
        int row = 0;
        for (AppearanceProperty property : AppearanceProperty.values()) {
            if (!categories.contains(property.category()) || excluded.contains(property)
                    || property.kind() == AppearanceProperty.Kind.COLOR) continue;
            addPropertyRow(grid, row++, property);
        }
        return grid;
    }

    private void addPropertyRow(GridPane grid, int row, AppearanceProperty property) {
        Label name = new Label(propertyLabel(property));
        name.setWrapText(true);
        Node editor;
        Runnable loader;
        if (property.kind() == AppearanceProperty.Kind.BOOLEAN) {
            ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList("", "true", "false"));
            localizeCombo(box, value -> value == null || value.isBlank()
                    ? label("appearance.inherit", "Inherit") : choiceLabel(value.toUpperCase(Locale.ROOT)));
            box.setPromptText(label("appearance.inherit", "Inherit"));
            box.valueProperty().addListener((observable, previous, current) -> {
                if (!updating) store(property, current);
            });
            editor = box;
            loader = () -> box.setValue(explicitStyle().get(property).orElse(""));
        } else if (property.kind() == AppearanceProperty.Kind.CHOICE) {
            List<String> values = new ArrayList<>();
            values.add("");
            values.addAll(property.choices());
            ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(values));
            localizeCombo(box, value -> value == null || value.isBlank()
                    ? label("appearance.inherit", "Inherit") : choiceLabel(value));
            box.setPromptText(label("appearance.inherit", "Inherit"));
            box.valueProperty().addListener((observable, previous, current) -> {
                if (!updating) store(property, current);
            });
            editor = box;
            loader = () -> box.setValue(explicitStyle().get(property).orElse(""));
        } else {
            TextField field = new TextField();
            field.setPromptText(label("appearance.inherit", "Inherit"));
            commit(field, () -> store(property, field.getText()));
            editor = field;
            loader = () -> field.setText(explicitStyle().get(property).orElse(""));
        }
        editor.setAccessibleText(propertyLabel(property));
        name.setLabelFor(editor);
        if (editor instanceof javafx.scene.control.Control control) control.setMaxWidth(Double.MAX_VALUE);
        Button reset = new Button("↺");
        reset.setAccessibleText(label("appearance.reset.property", "Reset property") + ": " + propertyLabel(property));
        reset.setOnAction(event -> {
            if (targetId != null) {
                service.resetProperty(targetId, selectedState(), property);
                reload();
            }
        });
        VBox valueBox = new VBox(3, editor);
        if (!property.javaFxSupported()) {
            Label explanation = new Label(label("appearance.unsupported",
                    "Stored, but this JavaFX control cannot render it directly."));
            explanation.getStyleClass().add("appearance-capability");
            explanation.setWrapText(true);
            valueBox.getChildren().add(explanation);
        }
        grid.add(name, 0, row);
        grid.add(valueBox, 1, row);
        grid.add(reset, 2, row);
        GridPane.setHgrow(valueBox, Priority.ALWAYS);
        reloaders.add(loader);
    }

    private void store(AppearanceProperty property, String raw) {
        if (targetId == null) return;
        try {
            if (raw == null || raw.isBlank()) service.resetProperty(targetId, selectedState(), property);
            else service.update(targetId, selectedState(), style -> style.set(property, raw));
            status.setText("");
        } catch (RuntimeException invalid) {
            status.setText(label("appearance.invalid", "Invalid appearance value") + ": " + invalid.getMessage());
        }
    }

    private void reload() {
        if (targetId == null) return;
        updating = true;
        try {
            reloaders.forEach(Runnable::run);
            status.setText("");
        } finally {
            updating = false;
        }
        refreshEditorSearch();
    }

    private void refreshEditorSearch() {
        if (closed || tabs == null) return;
        AppearanceProfile profile = service.profile();
        AppearanceStyle explicit = explicitStyle();
        List<AppearanceSurfaceSearch.Entry> entries = new ArrayList<>();
        addSearchEntry(entries, label("appearance.editor.target", "Target"),
                targetId == null ? "" : targetId.value(), null);
        addSearchEntry(entries, label("appearance.editor.state", "Interaction state"),
                choiceLabel(selectedState().name()), null);

        for (int index = 0; index < tabs.getTabs().size(); index++) {
            int targetTab = index;
            Tab tab = tabs.getTabs().get(index);
            entries.add(new AppearanceSurfaceSearch.Entry(tab.getText(), tab.getText(),
                    () -> tabs.getSelectionModel().select(targetTab)));
        }

        addSearchEntry(entries, label("appearance.global.theme", "Theme"), choiceLabel(profile.theme().name()), 0);
        addSearchEntry(entries, label("appearance.global.density", "Density"), choiceLabel(profile.density().name()), 0);
        addSearchEntry(entries, label("appearance.global.font_family", "Font family"), profile.fontFamily(), 0);
        addSearchEntry(entries, label("appearance.global.font_source", "Font source"),
                choiceLabel(profile.fontSource().name()), 0);
        addSearchEntry(entries, label("appearance.global.font_scale", "Font size scale"),
                Double.toString(profile.fontSizeScale()), 0);
        addSearchEntry(entries, label("appearance.global.font_weight", "Font weight"),
                Integer.toString(profile.fontWeight()), 0);
        addSearchEntry(entries, label("appearance.global.cjk_fallback", "CJK-safe fallback"),
                profile.cjkFallback(), 0);
        addSearchEntry(entries, choiceLabel("SEED"), profile.seedColor().toHex8(), 0);
        addSearchEntry(entries, choiceLabel("ACCENT"), profile.accentColor().toHex8(), 0);

        for (AppearanceProperty property : AppearanceProperty.values()) {
            int targetTab = switch (property.category()) {
                case TYPOGRAPHY -> 1;
                case COLORS -> 2;
                case GEOMETRY, SPACING -> 3;
                case ICON -> 4;
            };
            String current = explicit.get(property).orElse(label("appearance.inherit", "Inherit"));
            addSearchEntry(entries, propertyLabel(property), current, targetTab);
            for (String option : property.choices()) {
                addSearchEntry(entries, propertyLabel(property), choiceLabel(option), targetTab);
            }
        }
        for (AppearancePreset preset : service.presets()) {
            addSearchEntry(entries, label("appearance.presets.title", "Named presets"), presetLabel(preset), 5);
        }
        surfaceSearch.setEntries(entries);
    }

    private void addSearchEntry(List<AppearanceSurfaceSearch.Entry> entries, String label,
                                String value, Integer targetTab) {
        String display = value == null || value.isBlank() ? label : label + " · " + value;
        Runnable activation = targetTab == null ? null : () -> tabs.getSelectionModel().select(targetTab);
        entries.add(new AppearanceSurfaceSearch.Entry(display, display, activation));
    }

    private void loadSelectedColor() {
        AppearanceProperty property = selectedColorProperty.getValue();
        if (property == null || targetId == null) return;
        ColorValue fallback = property == AppearanceProperty.BACKGROUND_COLOR
                || property == AppearanceProperty.HIGHLIGHT_COLOR ? ColorTranslator.fromHex("#00000000")
                : ColorTranslator.fromHex("#191C20");
        colorPicker.setValue(explicitStyle().color(property).orElse(fallback));
    }

    private void loadGlobalColor() {
        AppearanceProfile profile = service.profile();
        globalColorPicker.setValue("seed".equals(selectedGlobalColor.getValue())
                ? profile.seedColor() : profile.accentColor());
    }

    private void reloadPresets() {
        AppearancePreset selected = presets.getSelectionModel().getSelectedItem();
        presets.getItems().setAll(service.presets());
        if (selected != null) presets.getItems().stream().filter(value -> value.id().equals(selected.id()))
                .findFirst().ifPresent(value -> presets.getSelectionModel().select(value));
        if (presets.getSelectionModel().getSelectedItem() == null && !presets.getItems().isEmpty()) {
            presets.getSelectionModel().selectFirst();
        }
    }

    private AppearanceStyle explicitStyle() {
        return targetId == null ? new AppearanceStyle() : service.explicitStyleFor(targetId, selectedState());
    }

    private AppearanceState selectedState() {
        return state.getValue() == null ? AppearanceState.NORMAL : state.getValue();
    }

    private void exportProfile() {
        FileChooser chooser = profileChooser(label("appearance.export", "Export profile"), true);
        java.io.File selected = chooser.showSaveDialog(stage);
        if (selected == null) return;
        try {
            service.exportTo(selected.toPath());
            status.setText(label("appearance.exported", "Appearance profile exported") + ": " + selected);
        } catch (IOException error) {
            status.setText(label("appearance.export_failed", "Could not export appearance profile") + ": " + error.getMessage());
        }
    }

    private void importProfile() {
        FileChooser chooser = profileChooser(label("appearance.import", "Import profile"), false);
        java.io.File selected = chooser.showOpenDialog(stage);
        if (selected == null) return;
        confirm("appearance.confirm.import.title", "Replace appearance with this profile?",
                "appearance.confirm.import.body",
                "The imported profile replaces current global and per-element appearance values.",
                selected.getAbsolutePath(), () -> {
                    try {
                        service.importFrom(selected.toPath());
                        reload();
                        status.setText(label("appearance.imported", "Appearance profile imported") + ": " + selected);
                    } catch (IOException error) {
                        // Informational failure remains inline/non-blocking in the editor.
                        status.setText(label("appearance.import_failed", "Could not import appearance profile")
                                + ": " + error.getMessage());
                    }
                });
    }

    private void confirm(String titleKey, String titleFallback, String bodyKey, String bodyFallback,
                         String factualDetail, Runnable confirmed) {
        AppearanceConfirmationDialog.show(root, text, titleKey, titleFallback,
                bodyKey, bodyFallback, factualDetail, confirmed);
    }

    private FileChooser profileChooser(String title, boolean export) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                label("appearance.profile_files", "Appearance profiles"), "*.jdmappearance"));
        if (export) chooser.setInitialFileName("jdownloader-material.jdmappearance");
        return chooser;
    }

    private void attachAnchor() {
        if (anchor == null) return;
        anchor.boundsInLocalProperty().addListener(anchorListener);
        anchor.localToSceneTransformProperty().addListener(anchorListener);
        if (anchorWindow != null) {
            anchorWindow.xProperty().addListener(windowListener);
            anchorWindow.yProperty().addListener(windowListener);
            anchorWindow.widthProperty().addListener(windowListener);
            anchorWindow.heightProperty().addListener(windowListener);
        }
    }

    private void detachAnchor() {
        if (anchor != null) {
            anchor.boundsInLocalProperty().removeListener(anchorListener);
            anchor.localToSceneTransformProperty().removeListener(anchorListener);
        }
        if (anchorWindow != null) {
            anchorWindow.xProperty().removeListener(windowListener);
            anchorWindow.yProperty().removeListener(windowListener);
            anchorWindow.widthProperty().removeListener(windowListener);
            anchorWindow.heightProperty().removeListener(windowListener);
        }
        anchorWindow = null;
    }

    private void relocateLater() {
        if (!stage.isShowing()) return;
        Platform.runLater(this::relocate);
    }

    private void relocate() {
        if (anchor == null || anchor.getScene() == null) return;
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds == null) return;
        double width = Math.max(DEFAULT_WIDTH, stage.getWidth());
        double height = Math.max(DEFAULT_HEIGHT, stage.getHeight());
        Screen screen = Screen.getScreensForRectangle(bounds.getMinX(), bounds.getMinY(),
                Math.max(1, bounds.getWidth()), Math.max(1, bounds.getHeight())).stream()
                .findFirst().orElse(Screen.getPrimary());
        javafx.geometry.Rectangle2D visual = screen.getVisualBounds();
        double x = bounds.getMaxX() + 8;
        if (x + width > visual.getMaxX()) x = bounds.getMinX() - width - 8;
        x = clamp(x, visual.getMinX(), visual.getMaxX() - width);
        double y = clamp(bounds.getMinY(), visual.getMinY(), visual.getMaxY() - height);
        stage.setX(x);
        stage.setY(y);
    }

    private void commit(TextField field, Runnable action) {
        field.setOnAction(event -> runGuarded(action));
        field.focusedProperty().addListener((observable, previous, focused) -> {
            if (!focused && !updating) runGuarded(action);
        });
    }

    private void runGuarded(Runnable action) {
        if (updating) return;
        try {
            action.run();
            status.setText("");
        } catch (RuntimeException invalid) {
            status.setText(label("appearance.invalid", "Invalid appearance value") + ": " + invalid.getMessage());
        }
    }

    private Tab tab(String key, String fallback, Node content) {
        Tab tab = new Tab(label(key, fallback), content);
        tab.setClosable(false);
        return tab;
    }

    private ScrollPane scroll(Node content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.getStyleClass().add("appearance-scroll");
        return pane;
    }

    private Label section(String key, String fallback) {
        Label label = new Label(label(key, fallback));
        label.getStyleClass().add("appearance-section-title");
        return label;
    }

    private ListCell<AppearanceProperty> propertyCell() {
        return new ListCell<>() {
            @Override protected void updateItem(AppearanceProperty property, boolean empty) {
                super.updateItem(property, empty);
                setText(empty || property == null ? null : propertyLabel(property));
            }
        };
    }

    private String propertyLabel(AppearanceProperty property) {
        return label("appearance.property." + property.name(), humanize(property.name()));
    }

    private String choiceLabel(String value) {
        return label("appearance.choice." + value, humanize(value));
    }

    private String presetLabel(AppearancePreset preset) {
        if (!preset.builtIn()) return preset.name();
        return label("appearance.preset." + preset.id().replace('-', '_'), preset.name())
                + " · " + label("appearance.preset.built_in", "built-in");
    }

    private <T> void localizeCombo(ComboBox<T> combo, Function<T, String> renderer) {
        combo.setCellFactory(list -> localizedCell(renderer));
        combo.setButtonCell(localizedCell(renderer));
    }

    private <T> ListCell<T> localizedCell(Function<T, String> renderer) {
        return new ListCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : renderer.apply(item));
            }
        };
    }

    private String label(String key, String fallback) {
        String value = text.apply(key);
        return value == null || value.isBlank() || value.equals(key) ? fallback : value;
    }

    private static String humanize(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static void formRow(GridPane grid, int row, String label, Node control) {
        Label title = new Label(label);
        title.setWrapText(true);
        grid.add(title, 0, row);
        grid.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
        if (control instanceof javafx.scene.control.Control value) value.setMaxWidth(Double.MAX_VALUE);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (maximum < minimum) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
