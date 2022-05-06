package codechicken.nei.recipe;

import codechicken.nei.ItemPanel.ItemPanelSlot;
import codechicken.nei.ItemPanels;
import codechicken.nei.api.INEIGuiAdapter;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
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

        if (draggedStack.getItem() instanceof IFluidContainerItem || FluidContainerRegistry.isContainer(draggedStack)) {
            ItemPanelSlot mouseOverSlot = ItemPanels.itemPanel.getSlotMouseOver(mouseX, mouseY);

            if (mouseOverSlot == null) {
                mouseOverSlot = ItemPanels.bookmarkPanel.getSlotMouseOver(mouseX, mouseY);
            }

            if (mouseOverSlot != null) {
                FluidStack fluidStack = StackInfo.getFluid(mouseOverSlot.item);

                if (fluidStack != null) {
                    ItemStack container = fillContainer(draggedStack, fluidStack);

                    if (container != null) {
                        container.stackSize = draggedStack.stackSize;

                        if (ItemPanels.itemPanel.draggedStack != null) {
                            ItemPanels.itemPanel.draggedStack = container;
                        } else {
                            ItemPanels.bookmarkPanel.draggedStack = container;
                        }

                    }
                    
                    if (button == 1 && StackInfo.getFluid(container != null? container: draggedStack) != null) {
                        ItemPanels.bookmarkPanel.addOrRemoveItem((container != null? container: draggedStack).copy());
                    }

                }

                return true;
            }

        }

        return false;
    }

    protected ItemStack fillContainer(ItemStack draggedStack, FluidStack fluidStack)
    {
        fluidStack = fluidStack.copy();
        draggedStack = draggedStack.copy();
        draggedStack.stackSize = 1;

        if (fluidStack.amount == 0) {
            fluidStack.amount = 1000;
        }

        if (FluidContainerRegistry.isContainer(draggedStack)) {

            if (FluidContainerRegistry.getContainerCapacity(draggedStack) > 0) {
                draggedStack = FluidContainerRegistry.drainFluidContainer(draggedStack);
            }

            return FluidContainerRegistry.fillFluidContainer(fluidStack, draggedStack);
        } else if (draggedStack.getItem() instanceof IFluidContainerItem) {
            IFluidContainerItem item = (IFluidContainerItem) draggedStack.getItem();

            if (item.getCapacity(draggedStack) > 0) {
                item.drain(draggedStack, item.getCapacity(draggedStack), true);
            }

            if (item.fill(draggedStack, fluidStack, true) > 0) {
                return draggedStack;
            }

        }

        return null;
    }

}
