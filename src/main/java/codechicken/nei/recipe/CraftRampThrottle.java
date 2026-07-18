package codechicken.nei.recipe;

import java.util.Objects;

import codechicken.nei.recipe.Recipe.RecipeId;

/**
 * Per-craft delay ramp for auto-crafting. Crafting a recipe repeatedly speeds up
 * (delay decays geometrically toward a floor). Momentum is global: switching to
 * a different recipe reduces speed by a penalty but does not reset it, so going
 * back to a recipe resumes with most of its momentum. At floor (max) speed
 * crafts are batched. Pure logic; does not sleep.
 */
public class CraftRampThrottle {

    public static final long START_DELAY_MS = 300L; // delay before the 1st craft
    public static final long FLOOR_DELAY_MS = 100L; // fastest allowed (cap)
    public static final double DECAY = 0.88D;       // per-craft speedup
    public static final double SWITCH_PENALTY = 2.0D; // momentum lost on recipe change
    public static final int BULK_CRAFTS = 4;        // crafts per tick once maxed out

    /** Delay to wait before a craft, and how many crafts that tick covers. */
    public static final class Tick {

        public final long delayMs;
        public final int crafts;

        Tick(long delayMs, int crafts) {
            this.delayMs = delayMs;
            this.crafts = crafts;
        }
    }

    private RecipeId current;
    private long delayMs = START_DELAY_MS;

    /** Next tick for {@code id}: decays on repeat, slows (but keeps momentum) on a recipe change. */
    public Tick next(RecipeId id) {
        if (!Objects.equals(id, this.current)) {
            if (this.current != null) {
                this.delayMs = Math.min(START_DELAY_MS, Math.round(this.delayMs * SWITCH_PENALTY));
            }
            this.current = id;
        }

        final long delay = this.delayMs;
        this.delayMs = Math.max(FLOOR_DELAY_MS, Math.round(delay * DECAY));
        return new Tick(delay, delay <= FLOOR_DELAY_MS ? BULK_CRAFTS : 1);
    }
}
