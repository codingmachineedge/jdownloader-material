package org.jdownloader.material.search;

/** Hard resource limits applied before and during local search evaluation. */
public record SearchLimits(int maxExpressionChars, int maxInputChars,
                           int maxMatches, int maxCaptureGroups, int maxResultChars) {

    public static final SearchLimits DEFAULT = new SearchLimits(4_096, 65_536, 1_000, 100, 262_144);

    public SearchLimits(int maxExpressionChars, int maxInputChars,
                        int maxMatches, int maxCaptureGroups) {
        this(maxExpressionChars, maxInputChars, maxMatches, maxCaptureGroups,
                Math.max(maxInputChars, Math.min(262_144, maxInputChars * 4)));
    }

    public SearchLimits {
        if (maxExpressionChars < 1) throw new IllegalArgumentException("maxExpressionChars must be positive");
        if (maxInputChars < 1) throw new IllegalArgumentException("maxInputChars must be positive");
        if (maxMatches < 1) throw new IllegalArgumentException("maxMatches must be positive");
        if (maxCaptureGroups < 0) throw new IllegalArgumentException("maxCaptureGroups must not be negative");
        if (maxResultChars < 1) throw new IllegalArgumentException("maxResultChars must be positive");
    }
}
