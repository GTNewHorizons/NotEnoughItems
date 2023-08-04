package codechicken.nei;

import java.util.ArrayList;
import java.util.LinkedList;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import codechicken.nei.api.IBookmarkContainerHandler;

public class DefaultBookmarkContainerHandler implements IBookmarkContainerHandler {

    @Override
    public void pullBookmarkItemsFromContainer(GuiContainer guiContainer, ArrayList<ItemStack> realItems) {
        FastTransferManager manager = new FastTransferManager();
        Container openContainer = guiContainer.inventorySlots;
        LinkedList<ItemStack> stacks = manager.saveContainer(openContainer);
        for (ItemStack bookmarkItem : realItems) {
            int bookmarkItemSize = bookmarkItem.stackSize;
            int maxItemSize = bookmarkItem.getMaxStackSize();
            for (int i = 0; i < stacks.size() - 4 * 9; i++) { // Last 36 slots are player inventory
                ItemStack containerItem = stacks.get(i);
                if (containerItem == null) continue;
                if (bookmarkItem.isItemEqual(containerItem)) {
                    if (bookmarkItemSize <= 0) break;
                    if (bookmarkItemSize > maxItemSize && containerItem.stackSize == maxItemSize) { // Move full stack
                        manager.transferItems(guiContainer, stacks.indexOf(containerItem), 64);
                        bookmarkItemSize -= maxItemSize;
                        continue;
                    }
                    if (bookmarkItemSize >= containerItem.stackSize && containerItem.stackSize != maxItemSize) { // Move
                                                                                                                 // partial
                                                                                                                 // stack
                        manager.transferItems(guiContainer, stacks.indexOf(containerItem), containerItem.stackSize);
                        bookmarkItemSize -= containerItem.stackSize;
                        continue;
                    }
                    if (bookmarkItemSize < containerItem.stackSize) { // Move rest
                        manager.transferItems(guiContainer, stacks.indexOf(containerItem), bookmarkItemSize);
                        bookmarkItemSize = 0;
                    }
                }
            }
        }
    }
}
