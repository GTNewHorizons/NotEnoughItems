package codechicken.nei.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.junit.jupiter.api.Test;

import codechicken.lib.vec.Rectangle4i;

public class GuiInfoTest {

    @Test
    void checksProvidedHandlerSnapshot() {
        GuiContainer gui = mock(GuiContainer.class);
        INEIGuiHandler handler = mock(INEIGuiHandler.class);
        when(handler.hideItemPanelSlot(gui, 1, 2, 3, 4)).thenReturn(true);

        assertTrue(GuiInfo.hideItemPanelSlot(gui, new Rectangle4i(1, 2, 3, 4), new INEIGuiHandler[] { handler }));
    }

    @Test
    void buttonEdgeContactDoesNotOverlap() {
        GuiContainer gui = mock(GuiContainer.class);
        gui.buttonList = Collections.singletonList(new GuiButton(0, 10, 10, 10, 10, ""));

        assertTrue(GuiInfo.hideItemPanelSlot(gui, new Rectangle4i(19, 19, 1, 1), new INEIGuiHandler[0]));
        assertFalse(GuiInfo.hideItemPanelSlot(gui, new Rectangle4i(20, 10, 1, 1), new INEIGuiHandler[0]));
    }
}
