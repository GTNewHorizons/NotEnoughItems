package codechicken.nei.api;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;

public interface IGuiContainerOverlay
{
    GuiContainer getFirstScreen();

    default GuiScreen getFirstScreenGeneral() {
        return getFirstScreen();
    }
}
