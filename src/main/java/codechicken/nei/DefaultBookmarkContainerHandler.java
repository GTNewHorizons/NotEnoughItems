package codechicken.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import codechicken.nei.api.IBookmarkContainerHandler;

public class DefaultBookmarkContainerHandler implements IBookmarkContainerHandler {

    @Override
    public void pullBookmarkItemsFromContainer(GuiContainer guiContainer, ArrayList<ItemStack> bookmarkItems) {
        final List<Slot> slots = guiContainer.inventorySlots.inventorySlots;
        final FastTransferManager manager = new FastTransferManager();

        for (ItemStack bookmarkItem : bookmarkItems) {
            for (int i = 0; i < slots.size() - 4 * 9; i++) {
                final Slot slot = slots.get(i);
                if (slot.getHasStack() && bookmarkItem.isItemEqual(slot.getStack())) {
                    final int transferAmount = Math.min(bookmarkItem.stackSize, slot.getStack().stackSize);
                    manager.transferItems(guiContainer, i, transferAmount);
                    bookmarkItem.stackSize -= transferAmount;
                }
            }
        }
    }
}
