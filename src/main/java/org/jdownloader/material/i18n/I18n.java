package org.jdownloader.material.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import javafx.beans.binding.StringBinding;
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
    private final ResourceBundle english = ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH);
    private final ResourceBundle cantonese = ResourceBundle.getBundle(BUNDLE, Locale.forLanguageTag("yue-HK"));

    public I18n(ObjectProperty<LanguageMode> mode) {
        this.mode = mode;
    }

    public ObjectProperty<LanguageMode> modeProperty() {
        return mode;
    }

    public String text(String key, Object... arguments) {
        String englishCopy = format(english, key, arguments);
        return switch (mode.get()) {
            case ENGLISH -> englishCopy;
            case HONG_KONG_CANTONESE -> format(cantonese, key, arguments);
            case BILINGUAL -> bilingual(englishCopy, format(cantonese, key, arguments), key);
        };
    }

    public StringBinding bind(String key) {
        return new StringBinding() {
            { super.bind(mode); }
            @Override protected String computeValue() { return text(key); }
        };
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
