package codechicken.nei.recipe;

import static codechicken.nei.LayoutManager.searchField;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.nei.SearchField;
import codechicken.nei.api.INEIGuiAdapter;

public class SearchInputDropHandler extends INEIGuiAdapter {

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mouseX, int mouseY, ItemStack draggedStack, int button) {

        if (searchField.isVisible() && searchField.contains(mouseX, mouseY)) {
            searchField.setText(SearchField.getEscapedSearchText(draggedStack));
            return true;
        }

        return false;
    }
}
