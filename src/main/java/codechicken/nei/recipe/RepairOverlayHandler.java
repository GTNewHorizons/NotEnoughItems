package codechicken.nei.recipe;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import codechicken.nei.PositionedStack;

public class RepairOverlayHandler extends DefaultOverlayHandler {

    @Override
    public boolean canCraft(GuiContainer firstGui, IRecipeHandler handler, int recipeIndex) {
        return false;
    }

    @Override
    public Slot[][] mapIngredSlots(GuiContainer gui, List<PositionedStack> ingredients) {
        final List<Slot> slots = gui.inventorySlots.inventorySlots;

        return new Slot[][] { { slots.get(0) }, { slots.get(1) } };
    }

}
