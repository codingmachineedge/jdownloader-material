package org.jdownloader.material.dimsum;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.jdownloader.material.engine.Settings;

/** Exactly-one-draw-per-launch 1% policy, independently testable from JavaFX. */
public final class DimSumSurpriseService {
    public static final int CHANCE_DENOMINATOR = 100;
    private static final List<DimSumDish> DISHES = List.of(
            new DimSumDish("har-gow", "Shrimp dumpling", "蝦餃", "/dimsum/har-gow.png"),
            new DimSumDish("siu-mai", "Siu mai", "燒賣", "/dimsum/siu-mai.png"),
            new DimSumDish("char-siu-bao", "Char siu bao", "叉燒包", "/dimsum/char-siu-bao.png"),
            new DimSumDish("egg-tart", "Egg tart", "蛋撻", "/dimsum/egg-tart.png"));

    private final Settings settings;
    private final RandomGenerator random;
    private boolean evaluated;

    public DimSumSurpriseService(Settings settings) {
        this(settings, RandomGenerator.getDefault());
    }

    public DimSumSurpriseService(Settings settings, RandomGenerator random) {
        this.settings = settings;
        this.random = random;
    }

    /**
     * Returns a dish only after first run and only on a clean, idle startup.
     * Calling it again in the same launch never draws again.
     */
    public Optional<DimSumDish> choose(boolean startupError, boolean updating, boolean taskInProgress) {
        if (evaluated) return Optional.empty();
        evaluated = true;
        if (!settings.firstRunCompletedProperty().get()) {
            settings.firstRunCompletedProperty().set(true);
            return Optional.empty();
        }
        if (!settings.dimSumSurpriseEnabledProperty().get() || settings.quietHoursProperty().get()
                || startupError || updating || taskInProgress) return Optional.empty();
        if (random.nextInt(CHANCE_DENOMINATOR) != 0) return Optional.empty();
        return Optional.of(DISHES.get(random.nextInt(DISHES.size())));
    }

    public static List<DimSumDish> catalog() { return DISHES; }
}
