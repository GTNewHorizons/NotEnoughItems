package codechicken.nei.recipe;

import codechicken.nei.ItemPanel.ItemPanelSlot;
import codechicken.nei.ItemPanels;
import codechicken.nei.api.INEIGuiAdapter;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

public class FillFluidContainerHandler extends INEIGuiAdapter
{

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mouseX, int mouseY, ItemStack draggedStack, int button)
    {

        if (button == 2) {
            return false;
        }

        ItemPanelSlot mouseOverSlot = ItemPanels.itemPanel.getSlotMouseOver(mouseX, mouseY);

        if (mouseOverSlot == null) {
            mouseOverSlot = ItemPanels.bookmarkPanel.getSlotMouseOver(mouseX, mouseY);
        }

        if (mouseOverSlot != null && draggedStack.getItem() instanceof IFluidContainerItem) {
            FluidStack fluidStack = StackInfo.getFluid(mouseOverSlot.item);

            if (fluidStack != null) {
                final int stackSize = draggedStack.stackSize;

                fluidStack = fluidStack.copy();

                if (fluidStack.amount == 0) {
                    fluidStack.amount = 1000;
                }

                if (mouseOverSlot.item.stackSize > 1) {
                    fluidStack.amount *= mouseOverSlot.item.stackSize;
                }

                draggedStack.stackSize = 1;
                ((IFluidContainerItem) draggedStack.getItem()).fill(draggedStack, fluidStack, true);
                draggedStack.stackSize = stackSize;

                if (button == 1 && ((IFluidContainerItem) draggedStack.getItem()).getFluid(draggedStack) != null) {
                    ItemPanels.bookmarkPanel.addOrRemoveItem(draggedStack.copy());
                }

            }

            return true;
        }

        return false;
    }

}
