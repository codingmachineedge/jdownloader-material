package org.jdownloader.material.model;

/** Durable queue priority for a direct download link. */
public enum DownloadPriority {
    HIGHEST("Highest", 4),
    HIGH("High", 3),
    NORMAL("Normal", 2),
    LOW("Low", 1),
    LOWEST("Lowest", 0);

    private final String label;
    private final int weight;

    DownloadPriority(String label, int weight) {
        this.label = label;
        this.weight = weight;
    }

    public String label() { return label; }
    public int weight() { return weight; }
    @Override public String toString() { return label; }
}
