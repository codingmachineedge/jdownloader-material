package org.jdownloader.material.ui.search;

import com.google.re2j.Pattern;
import io.github.palexdev.materialfx.controls.MFXButton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.SetChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.AccessibleRole;
import javafx.scene.control.CheckBox;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.PopupWindow;
import javafx.stage.Screen;
import javafx.stage.Window;
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.search.CaptureGroup;
import org.jdownloader.material.search.RegexFlag;
import org.jdownloader.material.search.SafeSearchEvaluator;
import org.jdownloader.material.search.SearchEvaluation;
import org.jdownloader.material.search.SearchMatch;
import org.jdownloader.material.search.SearchMode;
import org.jdownloader.material.search.SearchSpec;
import org.jdownloader.material.search.SearchValidation;
import org.jdownloader.material.ui.component.Mat;

/**
 * Anchored, non-modal RE2/J builder bound directly to one {@link SearchField}.
 */
public final class RegexBuilderPopover implements AutoCloseable {

    private enum Guide {
        LITERAL("search.regex.guide.literal"),
        CHARACTER_CLASS("search.regex.guide.character_class"),
        ANCHOR("search.regex.guide.anchor"),
        GROUP("search.regex.guide.group"),
        ALTERNATION("search.regex.guide.alternation"),
        QUANTIFIER("search.regex.guide.quantifier");

        private final String key;

        Guide(String key) {
            this.key = key;
        }
    }

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private static final double GAP = 8;
    private static final double SCREEN_MARGIN = 16;

    private final SearchField owner;
    private final I18n i18n;
    private final SafeSearchEvaluator evaluator;
    private final Popup popup = new Popup();
    private final VBox root = new VBox(10);
    private final ScrollPane scroll = new ScrollPane(root);
    private final Label title = Mat.label("", "subtitle", "regex-builder-title");
    private final Label dialect = Mat.label("", "row-desc", "regex-builder-dialect");
    private final Label expressionLabel = Mat.label("", "row-title");
    private final Label flagsLabel = Mat.label("", "row-title");
    private final Label sampleLabel = Mat.label("", "row-title");
    private final Label resultsLabel = Mat.label("", "row-title");
    private final Label validation = Mat.label("", "row-desc", "regex-validation");
    private final Label status = Mat.label("", "row-desc", "regex-status");
    private final ToggleButton plainMode = new ToggleButton();
    private final ToggleButton regexMode = new ToggleButton();
    private final TextArea expressionEditor = new TextArea();
    private final TextArea sample = new TextArea();
    private final ListView<SearchMatch> matches = new ListView<>();
    private final TextArea captures = new TextArea();
    private final Map<RegexFlag, CheckBox> flagChecks = new EnumMap<>(RegexFlag.class);
    private final Map<Guide, MFXButton> guideButtons = new EnumMap<>(Guide.class);
    private final MFXButton copy = Mat.outlined("", "paste");
    private final MFXButton export = Mat.outlined("", "download");
    private final MFXButton close = Mat.text("", "close");
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final ChangeListener<SearchSpec> specListener = (observable, previous, current) -> {
        syncModeAndFlags(current);
        refreshEvaluation();
    };
    private final ChangeListener<String> sampleListener = (observable, previous, current) -> refreshEvaluation();
    private final ChangeListener<SearchMatch> matchListener = (observable, previous, current) -> refreshCaptures(current);
    private final ChangeListener<LanguageMode> languageListener =
            (observable, previous, current) -> refreshLocalizedText();
    private final SetChangeListener<RegexFlag> ownerFlagListener = change -> syncFlagChecks();
    private final InvalidationListener anchorTracker = observable -> scheduleReposition();
    private Node anchor;
    private Window anchorWindow;
    private boolean syncing;
    private boolean repositionScheduled;
    private boolean disposed;

    public RegexBuilderPopover(SearchField owner, I18n i18n, SafeSearchEvaluator evaluator) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.i18n = Objects.requireNonNull(i18n, "i18n");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        buildContent();
        installBindings();
        configurePopup();
        refreshLocalizedText();
        syncModeAndFlags(owner.searchSpec());
        refreshEvaluation();
    }

    private void buildContent() {
        root.getStyleClass().add("regex-builder-popover");
        root.setAccessibleRole(AccessibleRole.PARENT);
        root.setPadding(new Insets(16));
        root.setPrefWidth(560);
        root.setMinWidth(420);

        close.getStyleClass().add("regex-builder-close");
        close.setOnAction(event -> hide());
        HBox header = new HBox(8, title, Mat.hSpacer(), close);
        header.setAlignment(Pos.CENTER_LEFT);

        plainMode.setToggleGroup(modeGroup);
        regexMode.setToggleGroup(modeGroup);
        plainMode.getStyleClass().add("regex-mode-button");
        regexMode.getStyleClass().add("regex-mode-button");
        plainMode.setOnAction(event -> {
            if (plainMode.isSelected()) owner.modeProperty().set(SearchMode.PLAIN_TEXT);
        });
        regexMode.setOnAction(event -> {
            if (regexMode.isSelected()) owner.modeProperty().set(SearchMode.REGEX);
        });
        HBox modes = new HBox(8, plainMode, regexMode);
        modes.getStyleClass().add("regex-mode-selector");

        expressionEditor.getStyleClass().add("regex-expression-editor");
        expressionEditor.setPrefRowCount(3);
        expressionEditor.setWrapText(false);
        expressionEditor.setMaxWidth(Double.MAX_VALUE);
        expressionEditor.setTextFormatter(lengthLimit(evaluator.limits().maxExpressionChars()));
        expressionLabel.setLabelFor(expressionEditor);

        HBox guides = new HBox(6);
        guides.getStyleClass().add("regex-guide-buttons");
        for (Guide guide : Guide.values()) {
            MFXButton button = Mat.tonal("", null);
            button.getStyleClass().add("regex-guide-button");
            button.setOnAction(event -> insert(guide));
            guideButtons.put(guide, button);
            guides.getChildren().add(button);
        }

        HBox flagRow = new HBox(12);
        flagRow.getStyleClass().add("regex-flag-row");
        for (RegexFlag flag : RegexFlag.values()) {
            CheckBox check = new CheckBox();
            check.getStyleClass().add("regex-flag");
            check.selectedProperty().addListener((observable, previous, selected) -> {
                if (syncing) return;
                if (selected) owner.flags().add(flag);
                else owner.flags().remove(flag);
            });
            flagChecks.put(flag, check);
            flagRow.getChildren().add(check);
        }

        sample.getStyleClass().add("regex-sample-editor");
        sample.setPrefRowCount(5);
        sample.setWrapText(true);
        sample.setMaxWidth(Double.MAX_VALUE);
        sample.setTextFormatter(lengthLimit(evaluator.limits().maxInputChars()));
        sampleLabel.setLabelFor(sample);

        validation.setWrapText(true);
        status.setWrapText(true);

        matches.getStyleClass().add("regex-match-list");
        matches.setItems(FXCollections.observableArrayList());
        matches.setPrefHeight(180);
        matches.setCellFactory(view -> new MatchCell());

        captures.getStyleClass().add("regex-capture-details");
        captures.setEditable(false);
        captures.setWrapText(true);
        captures.setPrefRowCount(4);
        captures.setFocusTraversable(true);

        copy.getStyleClass().add("regex-copy-button");
        copy.setOnAction(event -> copyPattern());
        export.getStyleClass().add("regex-export-button");
        export.setOnAction(event -> exportPattern());
        HBox actions = new HBox(8, copy, export, Mat.hSpacer());
        actions.setAlignment(Pos.CENTER_LEFT);

        root.getChildren().setAll(header, dialect, modes, expressionLabel, expressionEditor,
                guides, flagsLabel, flagRow, sampleLabel, sample, validation,
                resultsLabel, matches, captures, actions, status);
        VBox.setVgrow(matches, Priority.ALWAYS);

        scroll.getStyleClass().add("regex-builder-scroll");
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(560);
        scroll.setPrefViewportHeight(640);
        scroll.setMinViewportWidth(420);
    }

    private void installBindings() {
        expressionEditor.textProperty().bindBidirectional(owner.expressionProperty());
        owner.searchSpecProperty().addListener(specListener);
        owner.flags().addListener(ownerFlagListener);
        sample.textProperty().addListener(sampleListener);
        matches.getSelectionModel().selectedItemProperty().addListener(matchListener);
        i18n.modeProperty().addListener(languageListener);
    }

    private void configurePopup() {
        popup.getContent().setAll(scroll);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setConsumeAutoHidingEvents(false);
        popup.setAutoFix(false);
        popup.setAnchorLocation(PopupWindow.AnchorLocation.WINDOW_TOP_LEFT);
        popup.setOnHidden(event -> {
            detachAnchorTracking();
            Node returnTarget = anchor;
            anchor = null;
            if (returnTarget != null && returnTarget.isVisible() && returnTarget.getScene() != null
                    && returnTarget.getScene().getWindow().isFocused()
                    && (returnTarget.getScene().getFocusOwner() == null
                    || returnTarget.getScene().getFocusOwner() == returnTarget)) {
                Platform.runLater(returnTarget::requestFocus);
            }
        });
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    public void show(Node requestedAnchor) {
        if (disposed) return;
        Objects.requireNonNull(requestedAnchor, "requestedAnchor");
        if (requestedAnchor.getScene() == null || requestedAnchor.getScene().getWindow() == null) return;
        if (popup.isShowing()) hide();
        anchor = requestedAnchor;
        anchorWindow = requestedAnchor.getScene().getWindow();
        popup.getScene().getStylesheets().setAll(requestedAnchor.getScene().getStylesheets());
        attachAnchorTracking();
        Bounds bounds = requestedAnchor.localToScreen(requestedAnchor.getBoundsInLocal());
        if (bounds == null) {
            detachAnchorTracking();
            anchor = null;
            return;
        }
        popup.show(requestedAnchor, bounds.getMinX(), bounds.getMaxY() + GAP);
        Platform.runLater(() -> {
            reposition();
            expressionEditor.requestFocus();
            expressionEditor.positionCaret(expressionEditor.getLength());
        });
    }

    public void hide() {
        if (popup.isShowing()) popup.hide();
    }

    private void attachAnchorTracking() {
        if (anchor == null || anchorWindow == null) return;
        anchor.localToSceneTransformProperty().addListener(anchorTracker);
        anchor.boundsInLocalProperty().addListener(anchorTracker);
        anchor.visibleProperty().addListener(anchorTracker);
        anchor.sceneProperty().addListener(anchorTracker);
        anchorWindow.xProperty().addListener(anchorTracker);
        anchorWindow.yProperty().addListener(anchorTracker);
        anchorWindow.widthProperty().addListener(anchorTracker);
        anchorWindow.heightProperty().addListener(anchorTracker);
    }

    private void detachAnchorTracking() {
        if (anchor != null) {
            anchor.localToSceneTransformProperty().removeListener(anchorTracker);
            anchor.boundsInLocalProperty().removeListener(anchorTracker);
            anchor.visibleProperty().removeListener(anchorTracker);
            anchor.sceneProperty().removeListener(anchorTracker);
        }
        if (anchorWindow != null) {
            anchorWindow.xProperty().removeListener(anchorTracker);
            anchorWindow.yProperty().removeListener(anchorTracker);
            anchorWindow.widthProperty().removeListener(anchorTracker);
            anchorWindow.heightProperty().removeListener(anchorTracker);
        }
        anchorWindow = null;
    }

    private void scheduleReposition() {
        if (repositionScheduled || !popup.isShowing()) return;
        repositionScheduled = true;
        Platform.runLater(() -> {
            repositionScheduled = false;
            reposition();
        });
    }

    private void reposition() {
        if (!popup.isShowing() || anchor == null || !anchor.isVisible() || anchor.getScene() == null) {
            hide();
            return;
        }
        Bounds anchorBounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (anchorBounds == null) {
            hide();
            return;
        }
        Screen screen = Screen.getScreensForRectangle(anchorBounds.getMinX(), anchorBounds.getMinY(),
                Math.max(1, anchorBounds.getWidth()), Math.max(1, anchorBounds.getHeight())).stream()
                .findFirst().orElse(Screen.getPrimary());
        Rectangle2D visual = screen.getVisualBounds();
        scroll.setPrefViewportHeight(Math.max(280, Math.min(640, visual.getHeight() - SCREEN_MARGIN * 2)));
        root.applyCss();
        root.autosize();
        double width = popup.getWidth() > 0 ? popup.getWidth() : scroll.prefWidth(-1);
        double height = popup.getHeight() > 0 ? popup.getHeight() : scroll.prefHeight(width);
        double x = clamp(anchorBounds.getMinX(), visual.getMinX() + SCREEN_MARGIN,
                visual.getMaxX() - width - SCREEN_MARGIN);
        double below = anchorBounds.getMaxY() + GAP;
        double above = anchorBounds.getMinY() - height - GAP;
        double y = below + height <= visual.getMaxY() - SCREEN_MARGIN ? below
                : Math.max(visual.getMinY() + SCREEN_MARGIN, above);
        popup.setAnchorX(x);
        popup.setAnchorY(y);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (maximum < minimum) return minimum;
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static TextFormatter<String> lengthLimit(int maximum) {
        return new TextFormatter<>(change -> change.getControlNewText().length() <= maximum ? change : null);
    }

    private void syncModeAndFlags(SearchSpec current) {
        syncing = true;
        try {
            plainMode.setSelected(current.mode() == SearchMode.PLAIN_TEXT);
            regexMode.setSelected(current.mode() == SearchMode.REGEX);
            syncFlagChecks();
        } finally {
            syncing = false;
        }
    }

    private void syncFlagChecks() {
        boolean wasSyncing = syncing;
        syncing = true;
        try {
            for (Map.Entry<RegexFlag, CheckBox> entry : flagChecks.entrySet()) {
                entry.getValue().setSelected(owner.flags().contains(entry.getKey()));
            }
        } finally {
            syncing = wasSyncing;
        }
    }

    private void insert(Guide guide) {
        owner.modeProperty().set(SearchMode.REGEX);
        IndexRange selection = expressionEditor.getSelection();
        String selected = expressionEditor.getSelectedText();
        String replacement;
        int caretOffset;
        switch (guide) {
            case LITERAL -> {
                replacement = selected.isEmpty() ? "\\Q\\E" : Pattern.quote(selected);
                caretOffset = selected.isEmpty() ? 2 : replacement.length();
            }
            case CHARACTER_CLASS -> {
                replacement = selected.isEmpty() ? "[A-Za-z0-9]" : "[" + escapeCharacterClass(selected) + "]";
                caretOffset = replacement.length();
            }
            case ANCHOR -> {
                replacement = "^" + selected + "$";
                caretOffset = selected.isEmpty() ? 1 : replacement.length();
            }
            case GROUP -> {
                replacement = "(" + selected + ")";
                caretOffset = selected.isEmpty() ? 1 : replacement.length();
            }
            case ALTERNATION -> {
                replacement = selected.isEmpty() ? "(?:a|b)" : "(?:" + selected + "|)";
                caretOffset = selected.isEmpty() ? replacement.length() : replacement.length() - 1;
            }
            case QUANTIFIER -> {
                replacement = selected.isEmpty() ? ".{1,3}" : "(?:" + selected + "){1,3}";
                caretOffset = replacement.length();
            }
            default -> throw new IllegalStateException("Unknown guide " + guide);
        }
        expressionEditor.replaceText(selection.getStart(), selection.getEnd(), replacement);
        expressionEditor.positionCaret(selection.getStart() + caretOffset);
        expressionEditor.requestFocus();
    }

    private static String escapeCharacterClass(String source) {
        return source.replace("\\", "\\\\").replace("]", "\\]")
                .replace("^", "\\^").replace("-", "\\-");
    }

    private void refreshEvaluation() {
        SearchEvaluation evaluation = evaluator.evaluate(owner.searchSpec(), sample.getText());
        validation.setText(validationMessage(evaluation.validation()));
        validation.pseudoClassStateChanged(INVALID, !evaluation.valid());
        root.pseudoClassStateChanged(INVALID, !evaluation.valid());
        export.setDisable(!owner.validation().valid() || owner.searchSpec().expression().isEmpty());
        matches.getItems().setAll(evaluation.matches());
        if (!evaluation.matches().isEmpty()) matches.getSelectionModel().selectFirst();
        else refreshCaptures(null);

        if (!evaluation.valid()) {
            status.setText(i18n.text("search.regex.status.invalid"));
        } else if (owner.searchSpec().expression().isEmpty()) {
            status.setText(i18n.text("search.regex.status.ready"));
        } else if (evaluation.matches().isEmpty()) {
            status.setText(i18n.text("search.regex.status.no_matches"));
        } else if (evaluation.truncated()) {
            status.setText(i18n.text("search.regex.status.truncated", evaluation.matches().size()));
        } else {
            status.setText(i18n.text("search.regex.status.matches", evaluation.matches().size()));
        }
    }

    private String validationMessage(SearchValidation result) {
        return switch (result.code()) {
            case VALID -> i18n.text("search.regex.validation.valid");
            case EXPRESSION_TOO_LONG -> i18n.text("search.regex.validation.expression_too_long",
                    result.actual(), result.limit());
            case INPUT_TOO_LONG -> i18n.text("search.regex.validation.input_too_long",
                    result.actual(), result.limit());
            case TOO_MANY_CAPTURE_GROUPS -> i18n.text("search.regex.validation.too_many_groups",
                    result.actual(), result.limit());
            case INVALID_PATTERN -> result.position() >= 0
                    ? i18n.text("search.regex.validation.invalid_at", result.position(), result.detail())
                    : i18n.text("search.regex.validation.invalid", result.detail());
        };
    }

    private void refreshCaptures(SearchMatch match) {
        if (match == null) {
            captures.setText(i18n.text("search.regex.captures.none"));
            return;
        }
        StringBuilder text = new StringBuilder(i18n.text("search.regex.captures.match",
                match.start(), match.end(), preview(match.text())));
        if (match.captures().isEmpty()) {
            text.append(System.lineSeparator()).append(i18n.text("search.regex.captures.none"));
        } else {
            for (CaptureGroup group : match.captures()) {
                text.append(System.lineSeparator());
                if (group.matched()) {
                    text.append(i18n.text("search.regex.captures.group", group.index(),
                            group.start(), group.end(), preview(group.text())));
                } else {
                    text.append(i18n.text("search.regex.captures.unmatched", group.index()));
                }
            }
        }
        captures.setText(text.toString());
        captures.positionCaret(0);
    }

    private String formatMatch(SearchMatch match) {
        return i18n.text("search.regex.match.item", match.start(), match.end(),
                preview(match.text()), match.captures().size());
    }

    private static String preview(String value) {
        String flattened = value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
        int points = flattened.codePointCount(0, flattened.length());
        if (points <= 80) return flattened;
        int end = flattened.offsetByCodePoints(0, 77);
        return flattened.substring(0, end) + "…";
    }

    private void copyPattern() {
        ClipboardContent content = new ClipboardContent();
        content.putString(evaluator.portablePattern(owner.searchSpec()));
        Clipboard.getSystemClipboard().setContent(content);
        status.setText(i18n.text("search.regex.status.copied"));
    }

    private void exportPattern() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n.text("search.regex.export.title"));
        chooser.setInitialFileName("search-pattern.re2");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                i18n.text("search.regex.export.filter"), "*.re2", "*.txt"));
        java.io.File selected = chooser.showSaveDialog(anchorWindow);
        if (selected == null) return;
        String lineSeparator = System.lineSeparator();
        String document = "# JDownloader Material RE2/J search v1" + lineSeparator
                + "# mode: " + owner.searchSpec().mode().name() + lineSeparator
                + "# flags: " + owner.searchSpec().flagTokens() + lineSeparator
                + evaluator.portablePattern(owner.searchSpec()) + lineSeparator;
        try {
            Files.writeString(selected.toPath(), document, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            status.setText(i18n.text("search.regex.status.exported", selected.getAbsolutePath()));
        } catch (IOException error) {
            String detail = error.getMessage() == null || error.getMessage().isBlank()
                    ? error.getClass().getSimpleName() : error.getMessage();
            status.setText(i18n.text("search.regex.status.export_failed", detail));
        }
    }

    private void refreshLocalizedText() {
        title.setText(i18n.text("search.regex.title"));
        root.setAccessibleText(title.getText());
        dialect.setText(i18n.text("search.regex.dialect"));
        expressionLabel.setText(i18n.text("search.regex.expression"));
        flagsLabel.setText(i18n.text("search.regex.flags"));
        sampleLabel.setText(i18n.text("search.regex.sample"));
        resultsLabel.setText(i18n.text("search.regex.results"));
        plainMode.setText(i18n.text("search.mode.plain"));
        regexMode.setText(i18n.text("search.mode.regex"));
        expressionEditor.setPromptText(i18n.text("search.regex.expression.prompt"));
        expressionEditor.setAccessibleText(i18n.text("search.regex.expression"));
        sample.setPromptText(i18n.text("search.regex.sample.prompt"));
        sample.setAccessibleText(i18n.text("search.regex.sample"));
        matches.setAccessibleText(i18n.text("search.regex.results"));
        captures.setAccessibleText(i18n.text("search.regex.captures"));
        for (Map.Entry<RegexFlag, CheckBox> entry : flagChecks.entrySet()) {
            String label = i18n.text(entry.getKey().labelKey());
            entry.getValue().setText(label);
            entry.getValue().setAccessibleText(label);
        }
        for (Map.Entry<Guide, MFXButton> entry : guideButtons.entrySet()) {
            String label = i18n.text(entry.getKey().key);
            entry.getValue().setText(label);
            entry.getValue().setAccessibleText(label);
        }
        copy.setText(i18n.text("search.regex.copy"));
        copy.setAccessibleText(copy.getText());
        export.setText(i18n.text("search.regex.export"));
        export.setAccessibleText(export.getText());
        close.setText(i18n.text("search.regex.close"));
        close.setAccessibleText(close.getText());
        matches.refresh();
        refreshCaptures(matches.getSelectionModel().getSelectedItem());
        refreshEvaluation();
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        hide();
        detachAnchorTracking();
        expressionEditor.textProperty().unbindBidirectional(owner.expressionProperty());
        owner.searchSpecProperty().removeListener(specListener);
        owner.flags().removeListener(ownerFlagListener);
        sample.textProperty().removeListener(sampleListener);
        matches.getSelectionModel().selectedItemProperty().removeListener(matchListener);
        i18n.modeProperty().removeListener(languageListener);
        popup.getContent().clear();
    }

    @Override
    public void close() {
        dispose();
    }

    private final class MatchCell extends ListCell<SearchMatch> {
        @Override
        protected void updateItem(SearchMatch item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : formatMatch(item));
            setAccessibleText(empty || item == null ? null : formatMatch(item));
        }
    }
}
