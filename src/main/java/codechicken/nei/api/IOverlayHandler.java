package codechicken.nei.api;

import net.minecraft.client.gui.inventory.GuiContainer;

import codechicken.nei.recipe.IRecipeHandler;

public interface IOverlayHandler {

    void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean shift);
}
