package codechicken.nei.recipe;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.ItemPanels;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IRecipeOverlayRenderer;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RecipeItemInputHandler implements IContainerInputHandler {
    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
        ItemStack stackover = GuiContainerManager.getStackMouseOver(gui);
        if (stackover == null) return false;

        if (keyCode == NEIClientConfig.getKeyBinding("gui.recipe"))
            return GuiCraftingRecipe.openRecipeGui("item", stackover.copy());

        if (keyCode == NEIClientConfig.getKeyBinding("gui.usage"))
            return GuiUsageRecipe.openRecipeGui("item", stackover.copy());

        if (keyCode == NEIClientConfig.getKeyBinding("gui.bookmark")) {
            NEIClientConfig.logger.debug("Adding or removing {} from bookmarks", stackover.getDisplayName());
            List<PositionedStack> ingredients = null;
            String handlerName = "";

            if (gui instanceof GuiRecipe && NEIClientConfig.saveCurrentRecipeInBookmarksEnabled()) {
                ingredients = ((GuiRecipe) gui).getFocusedRecipeIngredients();
                handlerName = ((GuiRecipe) gui).getHandlerName();
            }

            ItemPanels.bookmarkPanel.addOrRemoveItem(stackover.copy(), handlerName, ingredients);
        }

        if (keyCode == NEIClientConfig.getKeyBinding("gui.overlay")) {
            if (NEIClientUtils.controlKey()) {
                LayoutManager.overlayRenderer = null;
                return true;
            }
            final Point mousePosition = GuiDraw.getMousePosition();
            if (ItemPanels.bookmarkPanel.getStackMouseOver(mousePosition.x, mousePosition.y) == null) return false;
            final BookmarkRecipeId recipeId = ItemPanels.bookmarkPanel.getBookmarkRecipeId(stackover);
            if (recipeId == null || recipeId.handlerName == null || recipeId.ingredients == null || recipeId.ingredients.isEmpty())
                return false;
            final ArrayList<ICraftingHandler> handlers = GuiCraftingRecipe.getHandlers("item", recipeId.handlerName, stackover.copy());
            if (handlers == null || handlers.isEmpty()) return false;
            final ICraftingHandler handler = handlers.get(0);
            if (recipeId.position == -1) {
                for (int i = 0; i < handler.numRecipes(); i++) {
                    if (recipeId.equalsIngredients(handler.getIngredientStacks(i))) {
                        recipeId.position = i;
                        break;
                    }
                }
            }
            if (recipeId.position == -1) return false;
            final IRecipeOverlayRenderer renderer = handler.getOverlayRenderer(gui, recipeId.position);
            final IOverlayHandler overlayHandler = handler.getOverlayHandler(gui, recipeId.position);
            final boolean shift = NEIClientUtils.shiftKey();
            if (renderer == null || shift) {
                overlayHandler.overlayRecipe(gui, handler, recipeId.position, shift);
            } else {
                LayoutManager.overlayRenderer = renderer;
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        ItemStack stackover = GuiContainerManager.getStackMouseOver(gui);

        if (stackover == null) return false;

        if (!(gui instanceof GuiRecipe)) return false;

        //disabled open recipe gui if hold shift (player have move recipe)
        if (button == 0 && ItemPanels.bookmarkPanel.getStackMouseOver(mousex, mousey) != null && NEIClientUtils.shiftKey()) {
            return false;
        }

        if (button == 0) return GuiCraftingRecipe.openRecipeGui("item", stackover.copy());

        if (button == 1) return GuiUsageRecipe.openRecipeGui("item", stackover.copy());

        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyID) {
    }

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
    }

    @Override
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {
    }

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyID) {
        return false;
    }

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
    }

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {
    }
}
