package org.jdownloader.material.dimsum;

import java.util.Objects;
import org.jdownloader.material.engine.LanguageMode;

/** Bundled, correctly named local dish image. */
public record DimSumDish(String id, String englishName, String cantoneseName, String resourcePath) {
    public DimSumDish {
        id = Objects.requireNonNull(id, "id");
        englishName = Objects.requireNonNull(englishName, "englishName");
        cantoneseName = Objects.requireNonNull(cantoneseName, "cantoneseName");
        resourcePath = Objects.requireNonNull(resourcePath, "resourcePath");
    }

    public String name(LanguageMode mode) {
        return switch (mode) {
            case ENGLISH -> englishName;
            case HONG_KONG_CANTONESE -> cantoneseName;
            case BILINGUAL -> englishName + " · " + cantoneseName;
        };
    }

    public String bilingualName() { return englishName + " · " + cantoneseName; }
}
