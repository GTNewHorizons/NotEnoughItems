package codechicken.nei;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import net.minecraft.client.renderer.InventoryEffectRenderer;

import org.junit.jupiter.api.Test;

public class NEIPotionGuiHandlerTest {

    @Test
    void preservesGapsBetweenPotionBoxes() {
        assertTrue(NEIPotionGuiHandler.intersectsPotionEffects(131, 1, 100, 2, 33));
        assertFalse(NEIPotionGuiHandler.intersectsPotionEffects(132, 1, 100, 2, 33));
    }

    @Test
    void skipsConfigLookupOutsidePotionColumn() {
        InventoryEffectRenderer gui = mock(InventoryEffectRenderer.class);
        gui.guiLeft = 200;

        assertFalse(new NEIPotionGuiHandler().hideItemPanelSlot(gui, 216, 100, 1, 1));
    }
}
