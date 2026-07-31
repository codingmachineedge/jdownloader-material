package org.jdownloader.material.search;

/** Structured validation result kept free of presentation-language strings. */
public record SearchValidation(Code code, String detail, int position, int actual, int limit) {

    public enum Code {
        VALID,
        EXPRESSION_TOO_LONG,
        INPUT_TOO_LONG,
        INVALID_PATTERN,
        TOO_MANY_CAPTURE_GROUPS
    }

    public SearchValidation {
        if (code == null) throw new IllegalArgumentException("code is required");
        detail = detail == null ? "" : detail;
    }

    public static SearchValidation ok() {
        return new SearchValidation(Code.VALID, "", -1, 0, 0);
    }

    public static SearchValidation expressionTooLong(int actual, int limit) {
        return new SearchValidation(Code.EXPRESSION_TOO_LONG, "", -1, actual, limit);
    }

    public static SearchValidation inputTooLong(int actual, int limit) {
        return new SearchValidation(Code.INPUT_TOO_LONG, "", -1, actual, limit);
    }

    public static SearchValidation invalidPattern(String detail, int position) {
        return new SearchValidation(Code.INVALID_PATTERN, detail, position, 0, 0);
    }

    public static SearchValidation tooManyCaptureGroups(int actual, int limit) {
        return new SearchValidation(Code.TOO_MANY_CAPTURE_GROUPS, "", -1, actual, limit);
    }

    public boolean valid() {
        return code == Code.VALID;
    }
}
