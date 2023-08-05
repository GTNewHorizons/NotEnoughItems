package codechicken.nei.recipe;

import static codechicken.lib.gui.GuiDraw.getMousePosition;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import codechicken.nei.ItemPanel.ItemPanelSlot;
import codechicken.nei.ItemPanels;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;

public class GuiCraftingRecipe extends GuiRecipe<ICraftingHandler> {

    public static ArrayList<ICraftingHandler> craftinghandlers = new ArrayList<>();
    public static ArrayList<ICraftingHandler> serialCraftingHandlers = new ArrayList<>();

    public static boolean openRecipeGui(String outputId, Object... results) {
        return openRecipeGui(outputId, false, false, results);
    }

    public static boolean openRecipeGui(String outputId, final Boolean overlay, final Boolean shift,
            Object... results) {
        return createRecipeGui(outputId, overlay, shift, true, results) != null;
    }

    public static GuiRecipe<?> createRecipeGui(String outputId, final Boolean overlay, final Boolean shift,
            final boolean open, Object... results) {
        final Minecraft mc = NEIClientUtils.mc();

        final BookmarkRecipeId recipeId = "item".equals(outputId)
                ? getRecipeId(mc.currentScreen, (ItemStack) results[0])
                : getCurrentRecipe(mc.currentScreen);

        if (overlay && recipeId == null) return null;

        final ArrayList<ICraftingHandler> handlers = getCraftingHandlers(outputId, results);

        if (!handlers.isEmpty()) {
            GuiCraftingRecipe gui = new GuiCraftingRecipe(handlers, recipeId);

            if (open) {
                mc.displayGuiScreen(gui);
            }

            if (recipeId != null) {
                gui.openTargetRecipe(gui.recipeId);
            }

            if (overlay) {
                gui.overlayRecipe(gui.recipeId.position, shift);
            }

            return gui;
        }

        return null;
    }

    public static ArrayList<ICraftingHandler> getCraftingHandlers(String outputId, Object... results) {
        final RecipeHandlerQuery<ICraftingHandler> recipeQuery = new RecipeHandlerQuery<>(
                h -> h.getRecipeHandler(outputId, results),
                craftinghandlers,
                serialCraftingHandlers,
                "Error while looking up crafting recipe",
                "outputId: " + outputId,
                "results: " + Arrays.toString(results));

        return recipeQuery.runWithProfiling("recipe.concurrent.crafting");
    }

    protected static BookmarkRecipeId getRecipeId(GuiScreen gui, ItemStack stackover) {

        if (gui instanceof GuiRecipe) {
            final List<PositionedStack> ingredients = ((GuiRecipe<?>) gui).getFocusedRecipeIngredients();
            final String handlerName = ((GuiRecipe<?>) gui).getHandlerName();

            if (ingredients != null && !ingredients.isEmpty()) {
                return new BookmarkRecipeId(handlerName, ingredients);
            }
        }

        final Point mouseover = getMousePosition();
        ItemPanelSlot panelSlot = ItemPanels.bookmarkPanel.getSlotMouseOver(mouseover.x, mouseover.y);

        if (panelSlot != null) {
            return ItemPanels.bookmarkPanel.getBookmarkRecipeId(panelSlot.slotIndex);
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
                || serialCraftingHandlers.stream().anyMatch(h -> h.getHandlerId().equals(handlerId)))
            return;

        if (NEIClientConfig.serialHandlers.contains(handlerId)) serialCraftingHandlers.add(handler);
        else craftinghandlers.add(handler);
    }

    @Override
    public ArrayList<ICraftingHandler> getCurrentRecipeHandlers() {
        return currenthandlers;
    }
}
