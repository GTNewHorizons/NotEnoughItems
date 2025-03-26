package codechicken.nei.api;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public interface IBookmarkContainerHandler {

    default List<ItemStack> getStorageStacks(GuiContainer guiContainer) {
        final List<Slot> slots = guiContainer.inventorySlots.inventorySlots;
        final List<ItemStack> stacks = new ArrayList<>();

        for (int i = 0; i < slots.size() - 4 * 9; i++) {
            if (slots.get(i).getHasStack()) {
                stacks.add(slots.get(i).getStack());
            }
        }

        return stacks;
    }

    void pullBookmarkItemsFromContainer(GuiContainer guiContainer, ArrayList<ItemStack> realItems);
}
