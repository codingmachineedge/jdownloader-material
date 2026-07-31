package org.jdownloader.material.search;

/** One numbered capture. Ranges are UTF-16 offsets, matching JavaFX text APIs. */
public record CaptureGroup(int index, int start, int end, String text, boolean matched) {

    public CaptureGroup {
        if (index < 1) throw new IllegalArgumentException("Capture group indices begin at one");
        text = text == null ? "" : text;
        if (matched) {
            if (start < 0 || end < start) throw new IllegalArgumentException("Invalid capture range");
        } else if (start != -1 || end != -1) {
            throw new IllegalArgumentException("An unmatched capture must use the range -1..-1");
        }
    }

    public static CaptureGroup unmatched(int index) {
        return new CaptureGroup(index, -1, -1, "", false);
    }
}
