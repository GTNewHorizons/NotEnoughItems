package codechicken.nei.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import codechicken.nei.recipe.CraftRampThrottle.Tick;
import codechicken.nei.recipe.Recipe.RecipeId;

class CraftRampThrottleTest {

    @Test
    @DisplayName("first craft of a recipe is instant and single")
    void firstCraftInstant() {
        CraftRampThrottle throttle = new CraftRampThrottle();
        RecipeId id = mock(RecipeId.class);

        Tick tick = throttle.next(id);
        assertEquals(0L, tick.delayMs);
        assertEquals(1, tick.crafts);
    }

    @Test
    @DisplayName("same recipe decays geometrically and clamps at floor")
    void rampDecaysToFloor() {
        CraftRampThrottle throttle = new CraftRampThrottle();
        RecipeId id = mock(RecipeId.class);

        assertEquals(0L, throttle.next(id).delayMs);                 // craft 1: instant
        assertEquals(500L, throttle.next(id).delayMs);               // craft 2: START
        assertEquals(425L, throttle.next(id).delayMs);               // 500 * 0.85
        assertEquals(361L, throttle.next(id).delayMs);               // round(425 * 0.85)

        Tick last = null;
        for (int i = 0; i < 50; i++) {
            last = throttle.next(id);
        }
        assertEquals(CraftRampThrottle.FLOOR_DELAY_MS, last.delayMs); // clamped at floor
    }

    @Test
    @DisplayName("at floor speed, crafts happen in bulk")
    void bulkAtFloorSpeed() {
        CraftRampThrottle throttle = new CraftRampThrottle();
        RecipeId id = mock(RecipeId.class);

        Tick tick = throttle.next(id);
        for (int i = 0; i < 50; i++) {
            tick = throttle.next(id);
        }

        assertEquals(CraftRampThrottle.FLOOR_DELAY_MS, tick.delayMs);
        assertEquals(CraftRampThrottle.BULK_CRAFTS, tick.crafts);     // batched once maxed out
    }

    @Test
    @DisplayName("crafts stay single while still ramping")
    void singleWhileRamping() {
        CraftRampThrottle throttle = new CraftRampThrottle();
        RecipeId id = mock(RecipeId.class);

        assertEquals(1, throttle.next(id).crafts);                   // instant
        assertEquals(1, throttle.next(id).crafts);                   // 500
        assertEquals(1, throttle.next(id).crafts);                   // 425
    }

    @Test
    @DisplayName("each recipe keeps its own ramp; returning resumes, not resets")
    void perRecipeMemoryResumes() {
        CraftRampThrottle throttle = new CraftRampThrottle();
        RecipeId a = mock(RecipeId.class);
        RecipeId b = mock(RecipeId.class);

        assertEquals(0L, throttle.next(a).delayMs);                  // a craft 1
        assertEquals(500L, throttle.next(a).delayMs);                // a craft 2
        assertEquals(0L, throttle.next(b).delayMs);                  // b starts fresh
        assertEquals(425L, throttle.next(a).delayMs);                // a resumes where it left off
        assertEquals(500L, throttle.next(b).delayMs);                // b resumes its own ramp
    }
}
