package codechicken.nei.recipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import codechicken.nei.recipe.Recipe.RecipeId;

class CraftRampThrottleTest {

    @Test
    @DisplayName("first craft of a recipe is instant")
    void firstCraftInstant() {
        CraftRampThrottle throttle = new CraftRampThrottle();
        RecipeId id = mock(RecipeId.class);
        assertEquals(0L, throttle.nextDelayMs(id));
    }

    @Test
    @DisplayName("same recipe decays geometrically and clamps at floor")
    void rampDecaysToFloor() {
        CraftRampThrottle throttle = new CraftRampThrottle();
        RecipeId id = mock(RecipeId.class);

        assertEquals(0L, throttle.nextDelayMs(id));                 // craft 1: instant
        assertEquals(500L, throttle.nextDelayMs(id));               // craft 2: START
        assertEquals(425L, throttle.nextDelayMs(id));               // 500 * 0.85
        assertEquals(361L, throttle.nextDelayMs(id));               // round(425 * 0.85)

        long last = 361L;
        for (int i = 0; i < 50; i++) {
            last = throttle.nextDelayMs(id);
        }
        assertEquals(CraftRampThrottle.FLOOR_DELAY_MS, last);       // clamped at floor
    }

    @Test
    @DisplayName("changing recipe resets the ramp to instant")
    void recipeChangeResets() {
        CraftRampThrottle throttle = new CraftRampThrottle();
        RecipeId a = mock(RecipeId.class);
        RecipeId b = mock(RecipeId.class);

        assertEquals(0L, throttle.nextDelayMs(a));                  // a craft 1
        assertEquals(500L, throttle.nextDelayMs(a));                // a craft 2
        assertEquals(0L, throttle.nextDelayMs(b));                  // switch -> instant
        assertEquals(500L, throttle.nextDelayMs(b));                // b craft 2
        assertEquals(0L, throttle.nextDelayMs(a));                  // switch back -> instant again
    }
}
