package org.jdownloader.material.search;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded local evaluator backed by RE2/J's linear-time regular-expression engine.
 *
 * <p>All offsets are UTF-16 offsets so callers can apply them directly to
 * Java and JavaFX strings. RE2/J advances safely after zero-width matches; the
 * explicit result cap provides an additional hard bound.</p>
 */
public final class SafeSearchEvaluator {

    private final SearchLimits limits;

    public SafeSearchEvaluator() {
        this(SearchLimits.DEFAULT);
    }

    public SafeSearchEvaluator(SearchLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public SearchLimits limits() {
        return limits;
    }

    public SearchValidation validate(SearchSpec spec) {
        return prepare(Objects.requireNonNull(spec, "spec")).validation();
    }

    public SearchEvaluation evaluate(SearchSpec spec, CharSequence input) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(input, "input");

        Prepared prepared = prepare(spec);
        if (!prepared.validation().valid()) {
            return new SearchEvaluation(spec, prepared.validation(), List.of(), false);
        }
        if (input.length() > limits.maxInputChars()) {
            return new SearchEvaluation(spec,
                    SearchValidation.inputTooLong(input.length(), limits.maxInputChars()), List.of(), false);
        }
        if (spec.expression().isEmpty()) {
            return new SearchEvaluation(spec, SearchValidation.ok(), List.of(), false);
        }

        Matcher matcher = prepared.pattern().matcher(input);
        List<SearchMatch> matches = new ArrayList<>(Math.min(32, limits.maxMatches()));
        long resultChars = 0;
        boolean truncated = false;
        while (matcher.find()) {
            if (matches.size() >= limits.maxMatches()) {
                truncated = true;
                break;
            }
            long nextChars = matcher.end() - matcher.start();
            for (int index = 1; index <= prepared.captureGroups(); index++) {
                int start = matcher.start(index);
                if (start >= 0) nextChars += matcher.end(index) - start;
            }
            if (nextChars > limits.maxResultChars() - resultChars) {
                truncated = true;
                break;
            }
            List<CaptureGroup> captures = new ArrayList<>(prepared.captureGroups());
            for (int index = 1; index <= prepared.captureGroups(); index++) {
                int start = matcher.start(index);
                if (start < 0) {
                    captures.add(CaptureGroup.unmatched(index));
                } else {
                    captures.add(new CaptureGroup(index, start, matcher.end(index), matcher.group(index), true));
                }
            }
            matches.add(new SearchMatch(matcher.start(), matcher.end(), matcher.group(), captures));
            resultChars += nextChars;
        }
        return new SearchEvaluation(spec, SearchValidation.ok(), matches, truncated);
    }

    public boolean matches(SearchSpec spec, CharSequence input) {
        SearchEvaluation evaluation = evaluate(spec, input);
        return evaluation.valid() && !evaluation.matches().isEmpty();
    }

    /** Exportable RE2 expression preserving the selected mode and supported flags. */
    public String portablePattern(SearchSpec spec) {
        Objects.requireNonNull(spec, "spec");
        String expression = effectiveExpression(spec);
        String flags = spec.flagTokens();
        return flags.isEmpty() ? expression : "(?" + flags + ")" + expression;
    }

    private Prepared prepare(SearchSpec spec) {
        if (spec.expression().length() > limits.maxExpressionChars()) {
            return Prepared.invalid(SearchValidation.expressionTooLong(
                    spec.expression().length(), limits.maxExpressionChars()));
        }
        if (spec.expression().isEmpty()) {
            return new Prepared(Pattern.compile(""), 0, SearchValidation.ok());
        }
        try {
            Pattern pattern = Pattern.compile(effectiveExpression(spec), RegexFlag.re2Flags(spec.flags()));
            int groups = pattern.matcher("").groupCount();
            if (groups > limits.maxCaptureGroups()) {
                return Prepared.invalid(SearchValidation.tooManyCaptureGroups(groups, limits.maxCaptureGroups()));
            }
            return new Prepared(pattern, groups, SearchValidation.ok());
        } catch (PatternSyntaxException invalid) {
            return Prepared.invalid(SearchValidation.invalidPattern(invalid.getDescription(), invalid.getIndex()));
        } catch (IllegalArgumentException invalid) {
            return Prepared.invalid(SearchValidation.invalidPattern(invalid.getMessage(), -1));
        }
    }

    private static String effectiveExpression(SearchSpec spec) {
        return spec.mode() == SearchMode.PLAIN_TEXT ? Pattern.quote(spec.expression()) : spec.expression();
    }

    private record Prepared(Pattern pattern, int captureGroups, SearchValidation validation) {
        private static Prepared invalid(SearchValidation validation) {
            return new Prepared(null, 0, validation);
        }
    }
}
