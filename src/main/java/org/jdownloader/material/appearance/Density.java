package org.jdownloader.material.appearance;

/** Persisted UI density. Scale is used by controls that opt into the appearance service. */
public enum Density {
    COMPACT(0.86),
    STANDARD(1.0),
    COMFORTABLE(1.14);

    private final double scale;

    Density(double scale) {
        this.scale = scale;
    }

    public double scale() {
        return scale;
    }
}
