package codechicken.nei.api;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;

public interface IBookmarkContainerHandler {
    void pullBookmarkItemsFromContainer(GuiContainer guiContainer, ArrayList<ItemStack> realItems);
}
