package codechicken.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.nei.api.IBookmarkContainerHandler;

public class DefaultBookmarkContainerHandler implements IBookmarkContainerHandler {

    @Override
    public void pullBookmarkItemsFromContainer(GuiContainer guiContainer, ArrayList<ItemStack> bookmarkItems) {
        final FastTransferManager manager = new FastTransferManager();
        final List<ItemStack> containerStacks = getStorageStacks(guiContainer);

        for (ItemStack bookmarkItem : bookmarkItems) {
            for (int i = 0; i < containerStacks.size() && bookmarkItem.stackSize > 0; i++) {
                final ItemStack containerItem = containerStacks.get(i);

                if (bookmarkItem.isItemEqual(containerItem)) {
                    final int transferAmount = Math.min(bookmarkItem.stackSize, containerItem.stackSize);
                    manager.transferItems(guiContainer, i, transferAmount);
                    bookmarkItem.stackSize -= transferAmount;
                }
            }
        }
    }
}
