package codechicken.nei.recipe;

import java.util.Objects;

import codechicken.nei.recipe.Recipe.RecipeId;

/**
 * Per-craft delay ramp for auto-crafting. First craft of a recipe is instant,
 * then the delay decays geometrically toward a floor. Changing the recipe
 * resets the ramp. Pure logic; does not sleep.
 */
public class CraftRampThrottle {

    public static final long START_DELAY_MS = 500L; // delay before the 2nd craft
    public static final long FLOOR_DELAY_MS = 50L;  // fastest allowed (cap)
    public static final double DECAY = 0.85D;

    private RecipeId current;
    private long delayMs;

    /** Delay to wait before the next craft of {@code id}; 0 for the first / after a change. */
    public long nextDelayMs(RecipeId id) {
        if (!Objects.equals(id, this.current)) {
            this.current = id;
            this.delayMs = START_DELAY_MS;
            return 0L;
        }

        final long delay = this.delayMs;
        this.delayMs = Math.max(FLOOR_DELAY_MS, Math.round(this.delayMs * DECAY));
        return delay;
    }
}
