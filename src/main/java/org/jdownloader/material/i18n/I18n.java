package org.jdownloader.material.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.ObjectProperty;
import org.jdownloader.material.engine.LanguageMode;

/**
 * Small UTF-8 resource-bundle facade for the three presentation modes.
 * Bilingual copy is composed here so views never need to concatenate English
 * and Cantonese by hand.
 */
public final class I18n {

    private static final String BUNDLE = "i18n.messages";
    private final ObjectProperty<LanguageMode> mode;
    private final IntegerProperty englishFunnyLevel;
    private final IntegerProperty cantoneseFunnyLevel;
    private final ResourceBundle english = ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH);
    private final ResourceBundle cantonese = ResourceBundle.getBundle(BUNDLE, Locale.forLanguageTag("yue-HK"));

    public I18n(ObjectProperty<LanguageMode> mode) {
        this(mode, new SimpleIntegerProperty(1), new SimpleIntegerProperty(1));
    }

    public I18n(ObjectProperty<LanguageMode> mode, IntegerProperty englishFunnyLevel,
                IntegerProperty cantoneseFunnyLevel) {
        this.mode = mode;
        this.englishFunnyLevel = englishFunnyLevel;
        this.cantoneseFunnyLevel = cantoneseFunnyLevel;
    }

    public ObjectProperty<LanguageMode> modeProperty() {
        return mode;
    }

    public String text(String key, Object... arguments) {
        String englishFact = format(english, key, arguments);
        String cantoneseFact = format(cantonese, key, arguments);
        return presentFacts(key, englishFact, cantoneseFact);
    }

    /** Styles already-localized factual copy with the active independent voice levels. */
    public String presentFacts(String semanticKey, String englishFact, String cantoneseFact) {
        String key = semanticKey == null ? "message" : semanticKey;
        englishFact = englishFact == null ? "" : englishFact;
        cantoneseFact = cantoneseFact == null ? englishFact : cantoneseFact;
        String englishCopy = voiced(englishFact, key, englishFunnyLevel.get(), false);
        return switch (mode.get()) {
            case ENGLISH -> englishCopy;
            case HONG_KONG_CANTONESE -> voiced(cantoneseFact, key, cantoneseFunnyLevel.get(), true);
            // International abbreviations and other byte-identical labels are
            // language-neutral facts. Rendering them twice adds crowding but
            // no Cantonese information, particularly in narrow table headers.
            case BILINGUAL -> englishFact.equals(cantoneseFact) ? englishFact
                    : bilingual(englishCopy, voiced(cantoneseFact, key,
                    cantoneseFunnyLevel.get(), true), key);
        };
    }

    public StringBinding bind(String key) {
        return new StringBinding() {
            { super.bind(mode, englishFunnyLevel, cantoneseFunnyLevel); }
            @Override protected String computeValue() { return text(key); }
        };
    }

    /** Independent persisted voice controls; level changes never alter message facts. */
    private static String voiced(String factualCopy, String key, int requestedLevel, boolean cantonese) {
        int level = Math.max(1, Math.min(5, requestedLevel));
        if (factualCopy.isBlank() || level == 1 || key.startsWith("language.option.")) return factualCopy;
        String[] englishSuffix = {"", " · ready", " · running neatly", " · no gremlins invited",
                " · powered by tiny dim-sum-sized gears"};
        String[] cantoneseSuffix = {"", " · 穩陣", " · 幾醒神", " · 今次冇走雞",
                " · 飲茶級醒神，粒粒有料"};
        return factualCopy + (cantonese ? cantoneseSuffix[level - 1] : englishSuffix[level - 1]);
    }

    public String languageName(LanguageMode language) {
        // A picker is easier to scan when each option names itself, rather
        // than being translated again by the mode currently being selected.
        return format(english, "language.option." + language.name());
    }

    private static String bilingual(String englishCopy, String cantoneseCopy, String key) {
        return englishCopy.equals(cantoneseCopy)
                ? englishCopy
                : englishCopy + bilingualSeparator(key) + cantoneseCopy;
    }

    private static String bilingualSeparator(String key) {
        return key.startsWith("desc.") || key.startsWith("empty.") || key.startsWith("about.")
                || key.startsWith("nav.") || key.startsWith("app.")
                || key.startsWith("status.addlinks.") || key.startsWith("status.backup.")
                || key.startsWith("properties.hint")
                || key.startsWith("state.")
                ? "\n" : " · ";
    }

    private static String format(ResourceBundle bundle, String key, Object... arguments) {
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException missing) {
            pattern = key;
        }
        return arguments == null || arguments.length == 0 ? pattern : MessageFormat.format(pattern, arguments);
    }
}
