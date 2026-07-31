package org.jdownloader.material.search;

import java.util.List;
import java.util.Objects;

/** Complete bounded evaluation result. */
public record SearchEvaluation(SearchSpec spec, SearchValidation validation,
                               List<SearchMatch> matches, boolean truncated) {

    public SearchEvaluation {
        spec = Objects.requireNonNull(spec, "spec");
        validation = Objects.requireNonNull(validation, "validation");
        matches = List.copyOf(matches == null ? List.of() : matches);
        if (!validation.valid() && (!matches.isEmpty() || truncated)) {
            throw new IllegalArgumentException("An invalid evaluation cannot contain matches");
        }
    }

    public boolean valid() {
        return validation.valid();
    }
}
