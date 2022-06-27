package codechicken.nei.recipe;

import codechicken.nei.ItemPanels;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

public class GuiCraftingRecipe extends GuiRecipe {
    public static boolean openRecipeGui(String outputId, Object... results) {
        return openRecipeGui(outputId, false, results);
    }

    public static boolean openRecipeGui(String outputId, Boolean overlay, Object... results) {
        RecipeHandlerQuery<ICraftingHandler> recipeQuery = new RecipeHandlerQuery<>(
                h -> h.getRecipeHandler(outputId, results), craftinghandlers, serialCraftingHandlers);
        ArrayList<ICraftingHandler> handlers = recipeQuery.run("recipe.concurrent.crafting");
        if (handlers.isEmpty()) return false;

        Minecraft mc = NEIClientUtils.mc();

        BookmarkRecipeId recipeId =
                "item".equals(outputId) ? getRecipeId(mc.currentScreen, (ItemStack) results[0]) : null;
        GuiCraftingRecipe gui = new GuiCraftingRecipe(handlers, recipeId);

        mc.displayGuiScreen(gui);

        if (NEIClientConfig.saveCurrentRecipeInBookmarksEnabled())
            if (!NEIClientUtils.shiftKey() || overlay) {
                gui.openTargetRecipe(gui.recipeId);
            }

        if (overlay) {
            if (!NEIClientConfig.saveCurrentRecipeInBookmarksEnabled() || gui.recipeId == null) {
                mc.displayGuiScreen(mc.currentScreen);
                return false;
            }
            gui.overlayRecipe(gui.recipeId.position);
        }

        return true;
    }

    protected static BookmarkRecipeId getRecipeId(GuiScreen gui, ItemStack stackover) {

        if (gui instanceof GuiRecipe) {
            final List<PositionedStack> ingredients = ((GuiRecipe) gui).getFocusedRecipeIngredients();
            final String handlerName = ((GuiRecipe) gui).getHandlerName();

            if (ingredients != null && !ingredients.isEmpty()) {
                return new BookmarkRecipeId(handlerName, ingredients);
            }
        }

        return ItemPanels.bookmarkPanel.getBookmarkRecipeId(stackover);
    }

    private GuiCraftingRecipe(ArrayList<ICraftingHandler> handlers, BookmarkRecipeId recipeId) {
        super(NEIClientUtils.mc().currentScreen);
        this.currenthandlers = handlers;
        this.recipeId = recipeId;
    }

    public static void registerRecipeHandler(ICraftingHandler handler) {
        final String handlerId = handler.getHandlerId();
        if (craftinghandlers.stream().anyMatch(h -> h.getHandlerId().equals(handlerId))
                || serialCraftingHandlers.stream()
                        .anyMatch(h -> h.getHandlerId().equals(handlerId))) return;

        if (NEIClientConfig.serialHandlers.contains(handlerId)) serialCraftingHandlers.add(handler);
        else craftinghandlers.add(handler);
    }

    public ArrayList<? extends IRecipeHandler> getCurrentRecipeHandlers() {
        return currenthandlers;
    }

    public ArrayList<ICraftingHandler> currenthandlers;

    public static ArrayList<ICraftingHandler> craftinghandlers = new ArrayList<>();
    public static ArrayList<ICraftingHandler> serialCraftingHandlers = new ArrayList<>();
}
