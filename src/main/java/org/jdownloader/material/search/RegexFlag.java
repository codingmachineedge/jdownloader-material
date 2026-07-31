package org.jdownloader.material.search;

import com.google.re2j.Pattern;
import java.util.Set;

/** RE2/J flags deliberately exposed by the desktop regex builder. */
public enum RegexFlag {
    CASE_INSENSITIVE('i', Pattern.CASE_INSENSITIVE, "search.regex.flag.case_insensitive"),
    MULTILINE('m', Pattern.MULTILINE, "search.regex.flag.multiline"),
    DOT_ALL('s', Pattern.DOTALL, "search.regex.flag.dot_all");

    private final char token;
    private final int re2Flag;
    private final String labelKey;

    RegexFlag(char token, int re2Flag, String labelKey) {
        this.token = token;
        this.re2Flag = re2Flag;
        this.labelKey = labelKey;
    }

    public char token() {
        return token;
    }

    public String labelKey() {
        return labelKey;
    }

    static int re2Flags(Set<RegexFlag> flags) {
        int combined = 0;
        if (flags != null) {
            for (RegexFlag flag : flags) combined |= flag.re2Flag;
        }
        return combined;
    }
}
