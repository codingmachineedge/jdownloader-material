package org.jdownloader.material.search;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, portable state for one search field.
 *
 * <p>The same expression is shared by the field and its builder. Switching
 * modes changes interpretation without maintaining a second hidden query.</p>
 */
public record SearchSpec(SearchMode mode, String expression, Set<RegexFlag> flags) {

    public SearchSpec {
        mode = Objects.requireNonNullElse(mode, SearchMode.PLAIN_TEXT);
        expression = expression == null ? "" : expression;
        flags = immutableFlags(flags);
    }

    /** New search fields are plain text and case-insensitive. */
    public static SearchSpec empty() {
        return new SearchSpec(SearchMode.PLAIN_TEXT, "", EnumSet.of(RegexFlag.CASE_INSENSITIVE));
    }

    public static SearchSpec plain(String expression) {
        return new SearchSpec(SearchMode.PLAIN_TEXT, expression,
                EnumSet.of(RegexFlag.CASE_INSENSITIVE));
    }

    public static SearchSpec regex(String expression, RegexFlag... flags) {
        EnumSet<RegexFlag> selected = EnumSet.noneOf(RegexFlag.class);
        if (flags != null) {
            for (RegexFlag flag : flags) if (flag != null) selected.add(flag);
        }
        return new SearchSpec(SearchMode.REGEX, expression, selected);
    }

    public SearchSpec withMode(SearchMode replacement) {
        return new SearchSpec(replacement, expression, flags);
    }

    public SearchSpec withExpression(String replacement) {
        return new SearchSpec(mode, replacement, flags);
    }

    public SearchSpec withFlags(Set<RegexFlag> replacement) {
        return new SearchSpec(mode, expression, replacement);
    }

    /** Stable compact representation used by export and diagnostics. */
    public String flagTokens() {
        StringBuilder tokens = new StringBuilder();
        for (RegexFlag flag : RegexFlag.values()) if (flags.contains(flag)) tokens.append(flag.token());
        return tokens.toString();
    }

    private static Set<RegexFlag> immutableFlags(Set<RegexFlag> source) {
        if (source == null || source.isEmpty()) return Set.of();
        EnumSet<RegexFlag> copy = EnumSet.noneOf(RegexFlag.class);
        for (RegexFlag flag : source) copy.add(Objects.requireNonNull(flag, "flag"));
        return Set.copyOf(copy);
    }
}
