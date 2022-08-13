package codechicken.nei.recipe;

import codechicken.nei.ItemPanels;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.PositionedStack;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import java.util.List;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

public class RecipeItemInputHandler implements IContainerInputHandler {

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {

        if (NEIClientConfig.isKeyHashDown("gui.overlay_hide")) {
            return hideOverlayRecipe();
        }

        ItemStack stackover = GuiContainerManager.getStackMouseOver(gui);

        if (stackover == null) {
            return false;
        }

        stackover = stackover.copy();

        if (NEIClientConfig.isKeyHashDown("gui.overlay")) {
            return openOverlayRecipe(gui, stackover, false);
        }

        if (NEIClientConfig.isKeyHashDown("gui.overlay_use")) {
            return openOverlayRecipe(gui, stackover, true);
        }

        if (NEIClientConfig.isKeyHashDown("gui.recipe")) {
            return GuiCraftingRecipe.openRecipeGui("item", stackover);
        }

        if (NEIClientConfig.isKeyHashDown("gui.usage")) {
            return GuiUsageRecipe.openRecipeGui("item", stackover);
        }

        if (NEIClientConfig.isKeyHashDown("gui.bookmark")) {
            return saveRecipeInBookmark(gui, stackover, false, false);
        }

        if (NEIClientConfig.isKeyHashDown("gui.bookmark_recipe")) {
            return saveRecipeInBookmark(gui, stackover, true, false);
        }

        if (NEIClientConfig.isKeyHashDown("gui.bookmark_count")) {
            return saveRecipeInBookmark(gui, stackover, false, true);
        }

        if (NEIClientConfig.isKeyHashDown("gui.bookmark_recipe_count")) {
            return saveRecipeInBookmark(gui, stackover, true, true);
        }

        return false;
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        if (!(gui instanceof GuiRecipe)
                || ItemPanels.itemPanel.contains(mousex, mousey)
                || ItemPanels.bookmarkPanel.contains(mousex, mousey)) return false;

        ItemStack stackover = GuiContainerManager.getStackMouseOver(gui);

        if (stackover != null) {
            if (button == 0) {
                return GuiCraftingRecipe.openRecipeGui("item", stackover.copy());
            } else if (button == 1) {
                return GuiUsageRecipe.openRecipeGui("item", stackover.copy());
            }
        }

        return false;
    }

    private boolean hideOverlayRecipe() {

        if (LayoutManager.overlayRenderer != null) {
            LayoutManager.overlayRenderer = null;
            return true;
        }

        return false;
    }

    private boolean openOverlayRecipe(GuiContainer gui, ItemStack stack, boolean shift) {

        if (gui instanceof GuiRecipe) {
            return false;
        }

        return GuiCraftingRecipe.openRecipeGui("item", true, shift, stack);
    }

    private boolean saveRecipeInBookmark(
            GuiContainer gui, ItemStack stack, boolean saveIngredients, boolean saveStackSize) {

        if (stack != null) {
            List<PositionedStack> ingredients = null;
            String handlerName = "";

            if (gui instanceof GuiRecipe) {
                ingredients = ((GuiRecipe<?>) gui).getFocusedRecipeIngredients();
                handlerName = ((GuiRecipe<?>) gui).getHandlerName();
            }

            ItemPanels.bookmarkPanel.addOrRemoveItem(stack, handlerName, ingredients, saveIngredients, saveStackSize);
            return true;
        }

        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyID) {}

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyID) {
        return false;
    }

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {}
}
