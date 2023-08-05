package codechicken.nei;

import java.util.ArrayList;
import java.util.LinkedList;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.nei.api.IBookmarkContainerHandler;

public class DefaultBookmarkContainerHandler implements IBookmarkContainerHandler {

    @Override
    public void pullBookmarkItemsFromContainer(GuiContainer guiContainer, ArrayList<ItemStack> bookmarkItems) {
        FastTransferManager manager = new FastTransferManager();
        LinkedList<ItemStack> containerStacks = manager.saveContainer(guiContainer.inventorySlots);

        for (ItemStack bookmarkItem : bookmarkItems) {

            int bookmarkSizeBackup = bookmarkItem.stackSize;

            for (int i = 0; i < containerStacks.size() - 4 * 9; i++) { // Last 36 slots are player inventory
                ItemStack containerItem = containerStacks.get(i);

                if (containerItem == null) {
                    continue;
                }

                if (bookmarkItem.isItemEqual(containerItem)) {
                    if (bookmarkItem.stackSize <= 0) {
                        break;
                    }

                    int transferAmount = Math.min(bookmarkItem.stackSize, containerItem.stackSize);

                    manager.transferItems(guiContainer, containerStacks.indexOf(containerItem), transferAmount);
                    bookmarkItem.stackSize -= transferAmount;

                    if (bookmarkItem.stackSize == 0) {
                        break;
                    }
                }
            }
            bookmarkItem.stackSize = bookmarkSizeBackup;
        }
    }
}
