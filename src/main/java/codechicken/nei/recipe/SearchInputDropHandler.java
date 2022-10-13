package codechicken.nei.recipe;

import static codechicken.nei.LayoutManager.searchField;

import codechicken.nei.api.INEIGuiAdapter;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public class SearchInputDropHandler extends INEIGuiAdapter {

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mouseX, int mouseY, ItemStack draggedStack, int button) {

        if (searchField.isVisible() && searchField.contains(mouseX, mouseY)) {
            final FluidStack fluidStack = StackInfo.getFluid(draggedStack);

            if (fluidStack != null) {
                searchField.setText(formattingText(fluidStack.getLocalizedName()));
            } else {
                searchField.setText(formattingText(draggedStack.getDisplayName()));
            }

            return true;
        }

        return false;
    }
}
