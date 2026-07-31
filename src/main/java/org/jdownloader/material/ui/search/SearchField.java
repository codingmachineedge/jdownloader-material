package org.jdownloader.material.ui.search;

import io.github.palexdev.materialfx.controls.MFXButton;
import java.util.EnumSet;
import java.util.Objects;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.i18n.I18n;
import org.jdownloader.material.search.RegexFlag;
import org.jdownloader.material.search.SafeSearchEvaluator;
import org.jdownloader.material.search.SearchEvaluation;
import org.jdownloader.material.search.SearchMode;
import org.jdownloader.material.search.SearchSpec;
import org.jdownloader.material.search.SearchValidation;
import org.jdownloader.material.ui.component.Mat;

/**
 * Reusable plain-text-first search field with its own independent regex state
 * and adjacent anchored builder affordance.
 */
public final class SearchField extends HBox implements AutoCloseable {

    public static final String DEFAULT_PROMPT_KEY = "search.field.prompt";
    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    private final I18n i18n;
    private final String promptKey;
    private final SafeSearchEvaluator evaluator;
    private final TextField input = new TextField();
    private final MFXButton builderButton;
    private final StringProperty expression = new SimpleStringProperty(this, "expression", "");
    private final ObjectProperty<SearchMode> mode =
            new SimpleObjectProperty<>(this, "mode", SearchMode.PLAIN_TEXT);
    private final ObservableSet<RegexFlag> flags =
            FXCollections.observableSet(EnumSet.of(RegexFlag.CASE_INSENSITIVE));
    private final ReadOnlyObjectWrapper<SearchSpec> spec =
            new ReadOnlyObjectWrapper<>(this, "searchSpec", SearchSpec.empty());
    private final ReadOnlyObjectWrapper<SearchValidation> validation =
            new ReadOnlyObjectWrapper<>(this, "validation", SearchValidation.ok());
    private final RegexBuilderPopover builder;
    private final ChangeListener<String> expressionListener = (observable, previous, current) -> refreshSpec();
    private final ChangeListener<SearchMode> modeListener = (observable, previous, current) -> refreshSpec();
    private final SetChangeListener<RegexFlag> flagListener = change -> refreshSpec();
    private final ChangeListener<LanguageMode> languageListener =
            (observable, previous, current) -> refreshLocalizedText();
    private final ChangeListener<Number> widthListener =
            (observable, previous, current) -> refreshLocalizedText();
    private boolean applyingSpec;
    private boolean disposed;

    public SearchField(I18n i18n) {
        this(i18n, DEFAULT_PROMPT_KEY, new SafeSearchEvaluator());
    }

    public SearchField(I18n i18n, String promptKey) {
        this(i18n, promptKey, new SafeSearchEvaluator());
    }

    public SearchField(I18n i18n, String promptKey, SafeSearchEvaluator evaluator) {
        this.i18n = Objects.requireNonNull(i18n, "i18n");
        this.promptKey = promptKey == null || promptKey.isBlank() ? DEFAULT_PROMPT_KEY : promptKey;
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");

        getStyleClass().add("regex-search-field");
        setAlignment(Pos.CENTER_LEFT);
        setMaxWidth(Double.MAX_VALUE);

        input.getStyleClass().add("regex-search-input");
        input.setMaxWidth(Double.MAX_VALUE);
        input.setTextFormatter(lengthLimit(evaluator.limits().maxExpressionChars()));
        HBox.setHgrow(input, Priority.ALWAYS);
        input.textProperty().bindBidirectional(expression);

        builderButton = Mat.icon("tune", null);
        builderButton.getStyleClass().add("regex-builder-button");
        builderButton.setOnAction(event -> toggleBuilder());
        getChildren().setAll(input, builderButton);

        expression.addListener(expressionListener);
        mode.addListener(modeListener);
        flags.addListener(flagListener);
        i18n.modeProperty().addListener(languageListener);
        widthProperty().addListener(widthListener);

        builder = new RegexBuilderPopover(this, i18n, evaluator);
        refreshLocalizedText();
        refreshSpec();
    }

    public TextField input() {
        return input;
    }

    public MFXButton builderButton() {
        return builderButton;
    }

    public RegexBuilderPopover builderPopover() {
        return builder;
    }

    public StringProperty expressionProperty() {
        return expression;
    }

    public ObjectProperty<SearchMode> modeProperty() {
        return mode;
    }

    public ObservableSet<RegexFlag> flags() {
        return flags;
    }

    public ReadOnlyObjectProperty<SearchSpec> searchSpecProperty() {
        return spec.getReadOnlyProperty();
    }

    public SearchSpec searchSpec() {
        return spec.get();
    }

    public ReadOnlyObjectProperty<SearchValidation> validationProperty() {
        return validation.getReadOnlyProperty();
    }

    public SearchValidation validation() {
        return validation.get();
    }

    public SafeSearchEvaluator evaluator() {
        return evaluator;
    }

    public SearchEvaluation evaluate(CharSequence input) {
        return evaluator.evaluate(searchSpec(), input);
    }

    public void setSearchSpec(SearchSpec replacement) {
        Objects.requireNonNull(replacement, "replacement");
        applyingSpec = true;
        try {
            expression.set(replacement.expression());
            mode.set(replacement.mode());
            flags.clear();
            flags.addAll(replacement.flags());
        } finally {
            applyingSpec = false;
        }
        refreshSpec();
    }

    public void showBuilder() {
        if (!disposed) builder.show(builderButton);
    }

    public void hideBuilder() {
        builder.hide();
    }

    private void toggleBuilder() {
        if (builder.isShowing()) builder.hide();
        else showBuilder();
    }

    private void refreshSpec() {
        if (applyingSpec || disposed) return;
        if (mode.get() == null) {
            applyingSpec = true;
            try {
                mode.set(SearchMode.PLAIN_TEXT);
            } finally {
                applyingSpec = false;
            }
        }
        SearchSpec next = new SearchSpec(mode.get(), expression.get(), flags);
        spec.set(next);
        SearchValidation nextValidation = evaluator.validate(next);
        validation.set(nextValidation);
        pseudoClassStateChanged(INVALID, !nextValidation.valid());
        refreshAccessibleHelp();
    }

    private void refreshLocalizedText() {
        String fullPrompt = i18n.text(promptKey);
        boolean compactBilingual = i18n.modeProperty().get() == LanguageMode.BILINGUAL
                && getWidth() > 0 && getWidth() < 300;
        String visiblePrompt = compactBilingual ? i18n.text(DEFAULT_PROMPT_KEY) : fullPrompt;
        input.setPromptText(visiblePrompt);
        input.setAccessibleText(fullPrompt);
        if (input.getTooltip() == null) Mat.tip(input, fullPrompt);
        else input.getTooltip().setText(fullPrompt);
        String builderLabel = i18n.text("search.regex.open_builder");
        builderButton.setAccessibleText(builderLabel);
        builderButton.setAccessibleHelp(i18n.text("search.regex.open_builder.help"));
        if (builderButton.getTooltip() == null) Mat.tip(builderButton, builderLabel);
        else builderButton.getTooltip().setText(builderLabel);
        refreshAccessibleHelp();
    }

    private void refreshAccessibleHelp() {
        if (input == null || i18n == null) return;
        String modeKey = mode.get() == SearchMode.REGEX ? "search.mode.regex" : "search.mode.plain";
        input.setAccessibleHelp(i18n.text("search.field.help", i18n.text(modeKey)));
    }

    private static TextFormatter<String> lengthLimit(int maximum) {
        return new TextFormatter<>(change -> change.getControlNewText().length() <= maximum ? change : null);
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        builder.dispose();
        expression.removeListener(expressionListener);
        mode.removeListener(modeListener);
        flags.removeListener(flagListener);
        i18n.modeProperty().removeListener(languageListener);
        widthProperty().removeListener(widthListener);
        input.textProperty().unbindBidirectional(expression);
    }

    @Override
    public void close() {
        dispose();
    }
}
