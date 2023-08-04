package codechicken.nei.api;

import java.util.ArrayList;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

public interface IBookmarkContainerHandler {

    void pullBookmarkItemsFromContainer(GuiContainer guiContainer, ArrayList<ItemStack> realItems);
}
