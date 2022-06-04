package codechicken.nei.recipe;

import codechicken.nei.NEICPH;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.api.INEIGuiAdapter;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;


public class CheatItemHandler extends INEIGuiAdapter
{

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mouseX, int mouseY, ItemStack draggedStack, int button)
    {
        final Slot overSlot = gui.getSlotAtPosition(mouseX, mouseY);

        if (overSlot != null && overSlot.isItemValid(draggedStack)) {

            if (NEIClientConfig.canCheatItem(draggedStack)) {
                int contents = overSlot.getHasStack() ? overSlot.getStack().stackSize : 0;
                final int add = button == 0 ? draggedStack.stackSize : 1;

                if (overSlot.getHasStack() && !NEIServerUtils.areStacksSameType(draggedStack, overSlot.getStack())) {
                    contents = 0;
                }

                final int total = Math.min(contents + add, Math.min(overSlot.getSlotStackLimit(), draggedStack.getMaxStackSize()));

                if (total > contents) {
                    NEIClientUtils.setSlotContents(overSlot.slotNumber, NEIServerUtils.copyStack(draggedStack, total), true);
                    NEICPH.sendGiveItem(NEIServerUtils.copyStack(draggedStack, total), false, false);
                    draggedStack.stackSize -= total - contents;
                }

            } else {
                draggedStack.stackSize = 0;
            }

            return true;
        }

        return false;
    }

}
    