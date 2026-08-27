package codechicken.nei;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class NEIPotionGuiHandlerTest {

    @Test
    void preservesGapsBetweenPotionBoxes() {
        assertTrue(NEIPotionGuiHandler.intersectsPotionEffects(76, 131, 1, 1, 76, 100, 2, 33));
        assertFalse(NEIPotionGuiHandler.intersectsPotionEffects(76, 132, 1, 1, 76, 100, 2, 33));
    }
}
