package org.jdownloader.material.changelog;

import java.time.LocalDate;
import java.util.Objects;
import org.jdownloader.material.engine.LanguageMode;
import org.jdownloader.material.i18n.I18n;

/** One factual shipped release entry bundled with the application. */
public record ChangelogEntry(String version, LocalDate date, String category, String commit,
                             String english, String cantonese) {
    public ChangelogEntry {
        version = Objects.requireNonNullElse(version, "unknown").strip();
        date = Objects.requireNonNull(date, "date");
        category = Objects.requireNonNullElse(category, "Other").strip();
        commit = Objects.requireNonNullElse(commit, "").strip();
        english = Objects.requireNonNullElse(english, "No recorded changes.").strip();
        cantonese = Objects.requireNonNullElse(cantonese, "冇已記錄改動。").strip();
    }

    public String localized(LanguageMode mode) {
        return switch (mode) {
            case ENGLISH -> english;
            case HONG_KONG_CANTONESE -> cantonese;
            case BILINGUAL -> english + "\n" + cantonese;
        };
    }

    public String localized(I18n i18n) {
        return Objects.requireNonNull(i18n, "i18n")
                .presentFacts("changelog.entry." + category, english, cantonese);
    }

    public String searchable() {
        return String.join(" ", version, date.toString(), category, commit, english, cantonese);
    }
}
