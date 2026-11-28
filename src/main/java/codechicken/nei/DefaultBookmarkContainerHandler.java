package codechicken.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import codechicken.nei.api.IBookmarkContainerHandler;
import codechicken.nei.recipe.StackInfo;

public class DefaultBookmarkContainerHandler implements IBookmarkContainerHandler {

    @Override
    public void pullBookmarkItemsFromContainer(GuiContainer guiContainer, ArrayList<ItemStack> bookmarkItems) {
        final List<Slot> slots = guiContainer.inventorySlots.inventorySlots;
        final FastTransferManager manager = new FastTransferManager();

        for (ItemStack bookmarkItem : bookmarkItems) {
            for (int i = 0; i < slots.size() - 4 * 9; i++) {
                final Slot slot = slots.get(i);
                if (slot.getHasStack() && matches(bookmarkItem, slot.getStack())) {
                    final int transferAmount = Math.min(bookmarkItem.stackSize, slot.getStack().stackSize);
                    manager.transferItems(guiContainer, i, transferAmount);
                    bookmarkItem.stackSize -= transferAmount;
                }
            }
        }
    }

    /**
     * If either stack holds a fluid, both need the same NBT, so a filled fluid cell is not taken for an empty one.
     * Other items match as in crafting, so an item that got its NBT later is still pulled.
     */
    private static boolean matches(ItemStack bookmarkItem, ItemStack stack) {
        if (StackInfo.getFluid(bookmarkItem) != null || StackInfo.getFluid(stack) != null) {
            return bookmarkItem.isItemEqual(stack) && StackInfo.equalItemAndNBT(bookmarkItem, stack, true);
        }
        return NEIServerUtils.areStacksSameTypeCraftingWithNBT(bookmarkItem, stack);
    }
}
