package org.jdownloader.material.search;

import java.time.Duration;
import java.util.EnumSet;

/** Plain-main coverage for the bounded RE2/J search contract. */
public final class SafeSearchSmoke {

    private static int assertions;

    private SafeSearchSmoke() {
    }

    public static void main(String[] args) {
        SafeSearchEvaluator evaluator = new SafeSearchEvaluator();
        validAndNoMatch(evaluator);
        invalidPattern(evaluator);
        unicodeAndMultiline(evaluator);
        zeroWidthAndCaptures(evaluator);
        adversarialPattern(evaluator);
        hardBounds();
        plainTextVersusRegex(evaluator);
        System.out.println("Safe search smoke passed: " + assertions + " assertions");
    }

    private static void validAndNoMatch(SafeSearchEvaluator evaluator) {
        SearchSpec spec = SearchSpec.regex("download-(\\d+)\\.zip");
        SearchEvaluation found = evaluator.evaluate(spec, "download-42.zip");
        require(found.valid(), "A valid RE2/J expression was rejected");
        require(found.matches().size() == 1, "The valid expression did not find exactly one match");
        require("42".equals(found.matches().getFirst().captures().getFirst().text()),
                "The numbered capture did not retain its text");

        SearchEvaluation absent = evaluator.evaluate(spec, "readme.txt");
        require(absent.valid(), "A no-match result was reported as invalid");
        require(absent.matches().isEmpty(), "A no-match sample produced a phantom result");
    }

    private static void invalidPattern(SafeSearchEvaluator evaluator) {
        SearchEvaluation invalid = evaluator.evaluate(SearchSpec.regex("[unfinished"), "anything");
        require(!invalid.valid(), "An invalid character class was accepted");
        require(invalid.validation().code() == SearchValidation.Code.INVALID_PATTERN,
                "An invalid pattern returned the wrong validation code");
        require(invalid.matches().isEmpty(), "An invalid pattern returned matches");
    }

    private static void unicodeAndMultiline(SafeSearchEvaluator evaluator) {
        String dish = "蝦餃 🥟";
        SearchEvaluation unicode = evaluator.evaluate(SearchSpec.plain(dish), "食點心：" + dish + "，正！");
        require(unicode.matches().size() == 1, "Unicode plain text did not match");
        SearchMatch dishMatch = unicode.matches().getFirst();
        require(dish.equals(dishMatch.text()), "Unicode match text changed");
        require(dishMatch.end() - dishMatch.start() == dish.length(),
                "Unicode ranges are not expressed as documented UTF-16 offsets");

        SearchEvaluation emoji = evaluator.evaluate(SearchSpec.regex("."), "🥟");
        require(emoji.matches().size() == 1, "RE2/J split one Unicode code point into two matches");
        require(emoji.matches().getFirst().start() == 0 && emoji.matches().getFirst().end() == 2,
                "Supplementary Unicode range did not use UTF-16 offsets");

        SearchEvaluation multiline = evaluator.evaluate(
                SearchSpec.regex("^second$", RegexFlag.MULTILINE), "first\nsecond\nthird");
        require(multiline.matches().size() == 1, "Multiline anchors did not match the middle line");
        require("second".equals(multiline.matches().getFirst().text()),
                "Multiline anchors selected the wrong text");

        SearchEvaluation dotAll = evaluator.evaluate(
                SearchSpec.regex("a.b", RegexFlag.DOT_ALL), "a\nb");
        require(dotAll.matches().size() == 1, "Dot-all did not include a newline");
    }

    private static void zeroWidthAndCaptures(SafeSearchEvaluator evaluator) {
        SearchEvaluation zeroWidth = evaluator.evaluate(SearchSpec.regex("^|$"), "ab");
        require(zeroWidth.valid(), "A zero-width expression was rejected");
        require(zeroWidth.matches().size() == 2, "Zero-width matching did not advance safely");
        require(zeroWidth.matches().get(0).zeroWidth() && zeroWidth.matches().get(1).zeroWidth(),
                "Anchor matches were not recorded as zero-width");
        require(zeroWidth.matches().get(0).start() == 0 && zeroWidth.matches().get(1).start() == 2,
                "Zero-width match offsets are wrong");

        SearchEvaluation captures = evaluator.evaluate(SearchSpec.regex("(a)(b)?"), "a ab");
        require(captures.matches().size() == 2, "Optional capture sample did not find both matches");
        CaptureGroup absent = captures.matches().get(0).captures().get(1);
        require(!absent.matched() && absent.start() == -1 && absent.end() == -1,
                "An unmatched optional group did not retain the sentinel range");
        CaptureGroup present = captures.matches().get(1).captures().get(1);
        require(present.matched() && "b".equals(present.text()),
                "A participating optional group was lost");
    }

    private static void adversarialPattern(SafeSearchEvaluator evaluator) {
        String input = "a".repeat(65_000) + "!";
        long started = System.nanoTime();
        SearchEvaluation evaluation = evaluator.evaluate(SearchSpec.regex("(a+)+$"), input);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        require(evaluation.valid(), "The supported adversarial expression was rejected");
        require(evaluation.matches().isEmpty(), "The adversarial no-match sample returned a match");
        require(elapsed.compareTo(Duration.ofSeconds(5)) < 0,
                "Adversarial evaluation exceeded the linear-time smoke budget: " + elapsed);
    }

    private static void hardBounds() {
        SafeSearchEvaluator defaults = new SafeSearchEvaluator();
        SearchEvaluation expressionTooLong = defaults.evaluate(
                SearchSpec.regex("x".repeat(SearchLimits.DEFAULT.maxExpressionChars() + 1)), "x");
        require(expressionTooLong.validation().code() == SearchValidation.Code.EXPRESSION_TOO_LONG,
                "Expression length bound was not enforced");

        SearchEvaluation inputTooLong = defaults.evaluate(SearchSpec.plain("x"),
                "x".repeat(SearchLimits.DEFAULT.maxInputChars() + 1));
        require(inputTooLong.validation().code() == SearchValidation.Code.INPUT_TOO_LONG,
                "Input length bound was not enforced");

        SafeSearchEvaluator tight = new SafeSearchEvaluator(new SearchLimits(32, 64, 2, 2));
        SearchEvaluation truncated = tight.evaluate(SearchSpec.regex("a"), "aaaa");
        require(truncated.matches().size() == 2 && truncated.truncated(),
                "Match result cap did not report truncation");

        SearchEvaluation tooManyGroups = tight.evaluate(SearchSpec.regex("(a)(b)(c)"), "abc");
        require(tooManyGroups.validation().code() == SearchValidation.Code.TOO_MANY_CAPTURE_GROUPS,
                "Capture-group bound was not enforced");

        SafeSearchEvaluator tinyOutput = new SafeSearchEvaluator(new SearchLimits(32, 64, 10, 2, 3));
        SearchEvaluation outputBound = tinyOutput.evaluate(SearchSpec.regex("(a)"), "aaaa");
        require(outputBound.matches().size() == 1 && outputBound.truncated(),
                "Captured-result character bound was not enforced");
    }

    private static void plainTextVersusRegex(SafeSearchEvaluator evaluator) {
        String sample = "a.b axb";
        SearchEvaluation plain = evaluator.evaluate(SearchSpec.plain("a.b"), sample);
        SearchEvaluation regex = evaluator.evaluate(SearchSpec.regex("a.b"), sample);
        require(plain.matches().size() == 1 && "a.b".equals(plain.matches().getFirst().text()),
                "Plain-text mode interpreted metacharacters");
        require(regex.matches().size() == 2, "Regex mode did not interpret the dot metacharacter");

        SearchSpec defensive = new SearchSpec(SearchMode.REGEX, "hello",
                EnumSet.of(RegexFlag.CASE_INSENSITIVE));
        require(evaluator.matches(defensive, "HELLO"), "Case-insensitive flag was not applied");
        require(evaluator.portablePattern(defensive).startsWith("(?i)"),
                "Portable export did not preserve flags");
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new IllegalStateException(message);
    }
}
