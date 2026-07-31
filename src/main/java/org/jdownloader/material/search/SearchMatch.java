package org.jdownloader.material.search;

import java.util.List;

/** One non-overlapping match and its numbered capture groups. */
public record SearchMatch(int start, int end, String text, List<CaptureGroup> captures) {

    public SearchMatch {
        if (start < 0 || end < start) throw new IllegalArgumentException("Invalid match range");
        text = text == null ? "" : text;
        captures = List.copyOf(captures == null ? List.of() : captures);
    }

    public boolean zeroWidth() {
        return start == end;
    }
}
