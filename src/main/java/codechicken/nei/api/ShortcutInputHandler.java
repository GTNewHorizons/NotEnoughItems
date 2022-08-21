package codechicken.nei.api;

import codechicken.nei.ItemPanels;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import java.util.List;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Mouse;

public class ShortcutInputHandler {

    public static boolean handleKeyEvent(ItemStack stackover) {

        if (NEIClientConfig.isKeyHashDown("gui.overlay_hide")) {
            return hideOverlayRecipe();
        }

        if (stackover == null) {
            return false;
        }

        stackover = stackover.copy();

        if (NEIClientConfig.isKeyHashDown("gui.overlay")) {
            return openOverlayRecipe(stackover, false);
        }

        if (NEIClientConfig.isKeyHashDown("gui.overlay_use")) {
            return openOverlayRecipe(stackover, true);
        }

        if (NEIClientConfig.isKeyHashDown("gui.recipe")) {
            return GuiCraftingRecipe.openRecipeGui("item", stackover);
        }

        if (NEIClientConfig.isKeyHashDown("gui.usage")) {
            return GuiUsageRecipe.openRecipeGui("item", stackover);
        }

        if (NEIClientConfig.isKeyHashDown("gui.bookmark")) {
            return saveRecipeInBookmark(stackover, false, false);
        }

        if (NEIClientConfig.isKeyHashDown("gui.bookmark_recipe")) {
            return saveRecipeInBookmark(stackover, true, false);
        }

        if (NEIClientConfig.isKeyHashDown("gui.bookmark_count")) {
            return saveRecipeInBookmark(stackover, false, true);
        }

        if (NEIClientConfig.isKeyHashDown("gui.bookmark_recipe_count")) {
            return saveRecipeInBookmark(stackover, true, true);
        }

        return false;
    }

    public static boolean handleMouseClick(ItemStack stackover) {

        if (stackover != null) {
            final int button = Mouse.getEventButton();
            stackover = stackover.copy();

            if (button == 0) {
                return GuiCraftingRecipe.openRecipeGui("item", stackover);
            } else if (button == 1) {
                return GuiUsageRecipe.openRecipeGui("item", stackover);
            }
        }

        return false;
    }

    private static boolean hideOverlayRecipe() {

        if (LayoutManager.overlayRenderer != null) {
            LayoutManager.overlayRenderer = null;
            return true;
        }

        return false;
    }

    private static boolean openOverlayRecipe(ItemStack stack, boolean shift) {
        final GuiContainer gui = NEIClientUtils.getGuiContainer();

        if (gui == null || gui instanceof GuiRecipe) {
            return false;
        }

        return GuiCraftingRecipe.openRecipeGui("item", true, shift, stack);
    }

    private static boolean saveRecipeInBookmark(ItemStack stack, boolean saveIngredients, boolean saveStackSize) {

        if (stack != null) {
            final GuiContainer gui = NEIClientUtils.getGuiContainer();
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
}
