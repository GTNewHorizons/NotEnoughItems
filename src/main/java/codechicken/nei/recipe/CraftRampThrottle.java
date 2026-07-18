package codechicken.nei.recipe;

import java.util.HashMap;
import java.util.Map;

import codechicken.nei.recipe.Recipe.RecipeId;

/**
 * Per-craft delay ramp for auto-crafting. First craft of a recipe is instant,
 * then the delay decays geometrically toward a floor. Each recipe keeps its own
 * ramp for the run, so switching away and back resumes rather than restarts. At
 * floor (max) speed crafts are batched. Pure logic; does not sleep.
 */
public class CraftRampThrottle {

    public static final long START_DELAY_MS = 500L; // delay before the 2nd craft
    public static final long FLOOR_DELAY_MS = 50L;  // fastest allowed (cap)
    public static final double DECAY = 0.85D;
    public static final int BULK_CRAFTS = 8;        // crafts per tick once maxed out

    /** Delay to wait before a craft, and how many crafts that tick covers. */
    public static final class Tick {

        public final long delayMs;
        public final int crafts;

        Tick(long delayMs, int crafts) {
            this.delayMs = delayMs;
            this.crafts = crafts;
        }
    }

    private final Map<RecipeId, Long> delays = new HashMap<>();

    /** Next tick for {@code id}: instant single craft the first time, then a decaying delay. */
    public Tick next(RecipeId id) {
        final Long stored = this.delays.get(id);

        if (stored == null) {
            this.delays.put(id, START_DELAY_MS);
            return new Tick(0L, 1);
        }

        final long delay = stored;
        this.delays.put(id, Math.max(FLOOR_DELAY_MS, Math.round(delay * DECAY)));
        return new Tick(delay, delay == FLOOR_DELAY_MS ? BULK_CRAFTS : 1);
    }
}
