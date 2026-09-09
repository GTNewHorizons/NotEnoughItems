package codechicken.nei.api;

import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.gui.inventory.GuiContainer;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.recipe.GuiOverlayButton.ItemOverlayState;
import codechicken.nei.recipe.IRecipeHandler;

public interface IOverlayHandler {

    void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean maxTransfer);

    default int transferRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, int multiplier) {
        return 0;
    }

    default boolean canFillCraftingGrid(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex) {
        return true;
    }

    default boolean craft(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, int multiplier) {
        return false;
    }

    default boolean canCraft(GuiContainer firstGui, IRecipeHandler handler, int recipeIndex) {
        return false;
    }

    default boolean requireShiftForOverlayRecipe() {
        return NEIClientConfig.requireShiftForOverlayRecipe();
    }

    default List<ItemOverlayState> presenceOverlay(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex) {
        return NEIClientUtils.presenceOverlay(
                recipe.getIngredientStacks(recipeIndex),
                firstGui.inventorySlots.inventorySlots.stream()
                        .filter(
                                s -> s != null && s.getStack() != null
                                        && s.getStack().stackSize > 0
                                        && s.isItemValid(s.getStack())
                                        && s.canTakeStack(firstGui.mc.thePlayer))
                        .map(s -> s.getStack().copy()).collect(Collectors.toList()));
    }
}
