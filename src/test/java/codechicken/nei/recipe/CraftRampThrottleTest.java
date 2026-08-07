package codechicken.nei.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import codechicken.nei.recipe.CraftRampThrottle.Tick;
import codechicken.nei.recipe.Recipe.RecipeId;

class CraftRampThrottleTest {

    private static Tick rampToFloor(CraftRampThrottle throttle, RecipeId id) {
        Tick tick = null;
        for (int i = 0; i < 100; i++) {
            tick = throttle.next(id);
        }
        return tick;
    }

    @Test
    @DisplayName("first craft of a recipe uses the start delay and is single")
    void firstCraftUsesStartDelay() {
        CraftRampThrottle throttle = new CraftRampThrottle();
        RecipeId id = mock(RecipeId.class);

        Tick tick = throttle.next(id);
        assertEquals(CraftRampThrottle.START_DELAY_MS, tick.delayMs);
        assertEquals(1, tick.crafts);
    }

    @Test
    @DisplayName("same recipe decays monotonically and clamps at floor")
    void rampDecaysToFloor() {
        CraftRampThrottle throttle = new CraftRampThrottle();
        RecipeId id = mock(RecipeId.class);

        long prev = Long.MAX_VALUE;
        for (int i = 0; i < 5; i++) {
            long delay = throttle.next(id).delayMs;
            assertTrue(delay <= prev, "delay should not increase while ramping");
            assertTrue(delay >= CraftRampThrottle.FLOOR_DELAY_MS, "delay should not drop below floor");
            prev = delay;
        }

        assertEquals(CraftRampThrottle.FLOOR_DELAY_MS, rampToFloor(throttle, id).delayMs);
    }

    @Test
    @DisplayName("at floor speed, crafts happen in bulk")
    void bulkAtFloorSpeed() {
        CraftRampThrottle throttle = new CraftRampThrottle();
        RecipeId id = mock(RecipeId.class);

        Tick tick = rampToFloor(throttle, id);
        assertEquals(CraftRampThrottle.FLOOR_DELAY_MS, tick.delayMs);
        assertEquals(CraftRampThrottle.BULK_CRAFTS, tick.crafts);
    }

    @Test
    @DisplayName("crafts stay single while still ramping")
    void singleWhileRamping() {
        CraftRampThrottle throttle = new CraftRampThrottle();
        RecipeId id = mock(RecipeId.class);

        assertEquals(1, throttle.next(id).crafts);
        assertEquals(1, throttle.next(id).crafts);
    }

    @Test
    @DisplayName("recipe change reduces momentum but does not fully reset")
    void recipeChangeReducesMomentum() {
        CraftRampThrottle throttle = new CraftRampThrottle();
        RecipeId a = mock(RecipeId.class);
        RecipeId b = mock(RecipeId.class);

        // Ramp A to full speed (floor).
        assertEquals(CraftRampThrottle.FLOOR_DELAY_MS, rampToFloor(throttle, a).delayMs);

        // Switching to B slows down, but keeps momentum: slower than floor, faster than a cold start.
        long afterSwitch = throttle.next(b).delayMs;
        assertTrue(afterSwitch > CraftRampThrottle.FLOOR_DELAY_MS, "switch should reduce momentum");
        assertTrue(afterSwitch < CraftRampThrottle.START_DELAY_MS, "switch should not fully reset");
    }
}
