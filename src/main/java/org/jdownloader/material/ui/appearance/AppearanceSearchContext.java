package org.jdownloader.material.ui.appearance;

import java.util.Objects;
import java.util.function.Function;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.i18n.I18n;

/** Adapts the appearance subsystem's live translation function for SearchField. */
final class AppearanceSearchContext {

    private static final String ENGLISH_PROBE = "Appearance search probe";
    private static final String CANTONESE_PROBE = "外觀搜尋探針";
    private static final String[] ENGLISH_SUFFIXES = {
            "", " · ready", " · running neatly", " · no gremlins invited",
            " · powered by tiny dim-sum-sized gears"};
    private static final String[] CANTONESE_SUFFIXES = {
            "", " · 穩陣", " · 幾醒神", " · 今次冇走雞", " · 飲茶級醒神，粒粒有料"};

    private final Function<String, String> source;
    private final SimpleObjectProperty<LanguageMode> mode =
            new SimpleObjectProperty<>(LanguageMode.ENGLISH);
    private final SimpleIntegerProperty englishFunny = new SimpleIntegerProperty(1);
    private final SimpleIntegerProperty cantoneseFunny = new SimpleIntegerProperty(1);
    private final I18n i18n = new I18n(mode, englishFunny, cantoneseFunny);

    AppearanceSearchContext(Function<String, String> source) {
        this.source = source == null ? Function.identity() : source;
        refresh();
    }

    I18n i18n() {
        return i18n;
    }

    void refresh() {
        String probe;
        try {
            probe = Objects.toString(source.apply("appearance.search.probe"), "");
        } catch (RuntimeException unavailable) {
            probe = "";
        }
        boolean english = probe.contains(ENGLISH_PROBE);
        boolean cantonese = probe.contains(CANTONESE_PROBE);
        LanguageMode detected = english && cantonese ? LanguageMode.BILINGUAL
                : cantonese ? LanguageMode.HONG_KONG_CANTONESE : LanguageMode.ENGLISH;
        int nextEnglish = level(probe, ENGLISH_SUFFIXES);
        int nextCantonese = level(probe, CANTONESE_SUFFIXES);
        boolean voiceChanged = englishFunny.get() != nextEnglish || cantoneseFunny.get() != nextCantonese;
        englishFunny.set(nextEnglish);
        cantoneseFunny.set(nextCantonese);
        if (mode.get() != detected) {
            mode.set(detected);
        } else if (voiceChanged) {
            // SearchField observes language mode; bounce it so funny-level copy refreshes too.
            mode.set(detected == LanguageMode.ENGLISH ? LanguageMode.BILINGUAL : LanguageMode.ENGLISH);
            mode.set(detected);
        }
    }

    private static int level(String probe, String[] suffixes) {
        for (int index = suffixes.length - 1; index > 0; index--) {
            if (probe.contains(suffixes[index])) return index + 1;
        }
        return 1;
    }
}
