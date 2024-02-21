package codechicken.nei.recipe;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import codechicken.nei.ItemPanels;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IRecipeOverlayRenderer;

class ItemsHistoryHandler implements ICraftingHandler, IUsageHandler {

    @Override
    public ICraftingHandler getRecipeHandler(String outputId, Object... results) {

        if (NEIClientConfig.showHistoryPanelWidget() && "item".equals(outputId) && results[0] instanceof ItemStack) {
            ItemPanels.itemPanel.historyPanel.addItem((ItemStack) results[0]);
        }

        return null;
    }

    @Override
    public IUsageHandler getUsageHandler(String inputId, Object... ingredients) {

        if (NEIClientConfig.showHistoryPanelWidget() && "item".equals(inputId) && ingredients[0] instanceof ItemStack) {
            ItemPanels.itemPanel.historyPanel.addItem((ItemStack) ingredients[0]);
        }

        return null;
    }

    @Override
    public void onUpdate() {

    }

    @Override
    public boolean hasOverlay(GuiContainer gui, Container container, int recipe) {
        return false;
    }

    @Override
    public IRecipeOverlayRenderer getOverlayRenderer(GuiContainer gui, int recipe) {
        return null;
    }

    @Override
    public IOverlayHandler getOverlayHandler(GuiContainer gui, int recipe) {
        return null;
    }

    @Override
    public int recipiesPerPage() {
        return 0;
    }

    @Override
    public List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip, int recipe) {
        return null;
    }

    @Override
    public List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> currenttip, int recipe) {
        return null;
    }

    @Override
    public boolean keyTyped(GuiRecipe gui, char keyChar, int keyCode, int recipe) {
        return false;
    }

    @Override
    public boolean mouseClicked(GuiRecipe gui, int button, int recipe) {
        return true;
    }

    @Override
    public String getRecipeName() {
        return null;
    }

    @Override
    public int numRecipes() {
        return 0;
    }

    @Override
    public void drawBackground(int recipe) {

    }

    @Override
    public void drawForeground(int recipe) {

    }

    @Override
    public List<PositionedStack> getIngredientStacks(int recipe) {
        return null;
    }

    @Override
    public List<PositionedStack> getOtherStacks(int recipeType) {
        return null;
    }

    @Override
    public PositionedStack getResultStack(int recipe) {
        return null;
    }

}
