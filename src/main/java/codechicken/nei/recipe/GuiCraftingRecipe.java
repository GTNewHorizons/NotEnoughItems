package codechicken.nei.recipe;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.FavoriteRecipes;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.bookmark.BookmarksGridSlot;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.stackinfo.GTFluidStackStringifyHandler;

public class GuiCraftingRecipe extends GuiRecipe<ICraftingHandler> {

    public static ArrayList<ICraftingHandler> craftinghandlers = new ArrayList<>();
    public static ArrayList<ICraftingHandler> serialCraftingHandlers = new ArrayList<>();
    private static Set<String> existingHandlers = new HashSet<>();

    public static boolean openRecipeGui(String outputId, Object... results) {
        return createRecipeGui(outputId, true, results) != null;
    }

    public static GuiRecipe<?> createRecipeGui(String outputId, boolean open, Object... results) {
        final Minecraft mc = NEIClientUtils.mc();
        final RecipeId recipeId;

        if ("item".equals(outputId)) {
            for (int i = 0; i < results.length; i++) {
                results[i] = normalizeItemStack((ItemStack) results[i]);
            }
            recipeId = getRecipeId(mc.currentScreen, (ItemStack) results[0]);
        } else if ("recipeId".equals(outputId)) {
            recipeId = (RecipeId) results[1];
        } else {
            recipeId = getCurrentRecipeId(mc.currentScreen);
        }

        final ArrayList<ICraftingHandler> handlers = getCraftingHandlers(outputId, results);

        if (!handlers.isEmpty()) {
            final GuiCraftingRecipe gui = new GuiCraftingRecipe(handlers);

            if (open) {

                if (NEIClientConfig.showHistoryPanelWidget() && "item".equals(outputId)
                        && results[0] instanceof ItemStack stack) {
                    ItemPanels.itemPanel.historyPanel.addItem(stack);
                }

                mc.displayGuiScreen(gui);
            }

            gui.openTargetRecipe(recipeId);
            return gui;
        }

        return null;
    }

    public static ArrayList<ICraftingHandler> getCraftingHandlers(String outputId, Object... results) {
        ArrayList<ICraftingHandler> craftinghandlers = GuiCraftingRecipe.craftinghandlers;
        ArrayList<ICraftingHandler> serialCraftingHandlers = GuiCraftingRecipe.serialCraftingHandlers;
        UnaryOperator<ICraftingHandler> recipeHandlerFunction;

        if ("recipeId".equals(outputId)) {
            ItemStack stack = (ItemStack) results[0];
            RecipeId recipeId = (RecipeId) results[1];
            craftinghandlers = filterByHandlerName(craftinghandlers, recipeId.getHandleName());
            serialCraftingHandlers = filterByHandlerName(serialCraftingHandlers, recipeId.getHandleName());
            recipeHandlerFunction = h -> h.getRecipeHandler("item", stack);
        } else {
            recipeHandlerFunction = h -> h.getRecipeHandler(outputId, results);
        }

        final RecipeHandlerQuery<ICraftingHandler> recipeQuery = new RecipeHandlerQuery<>(
                recipeHandlerFunction,
                craftinghandlers,
                serialCraftingHandlers,
                "Error while looking up crafting recipe",
                "outputId: " + outputId,
                "results: " + Arrays.toString(results));

        return recipeQuery.runWithProfiling(NEIClientUtils.translate("recipe.concurrent.crafting"));
    }

    private static ArrayList<ICraftingHandler> filterByHandlerName(ArrayList<ICraftingHandler> craftinghandlers,
            String handlerName) {
        return craftinghandlers.stream().filter(h -> getHandlerName(h).equals(handlerName))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String getHandlerName(ICraftingHandler handler) {
        return GuiRecipeTab.getHandlerInfo(handler).getHandlerName();
    }

    private static ItemStack normalizeItemStack(ItemStack stack) {
        GTFluidStackStringifyHandler.replaceAE2FCFluidDrop = true;
        stack = StackInfo.loadFromNBT(StackInfo.itemStackToNBT(stack));
        GTFluidStackStringifyHandler.replaceAE2FCFluidDrop = false;
        return stack;
    }

    public static RecipeId getRecipeId(GuiScreen gui, ItemStack stackover) {

        if (gui instanceof GuiRecipe guiRecipe) {
            final Recipe focusedRecipe = guiRecipe.getFocusedRecipe();

            if (focusedRecipe != null) {
                return focusedRecipe.getRecipeId();
            }
        }

        final Point mouseover = GuiDraw.getMousePosition();
        final BookmarksGridSlot panelSlot = ItemPanels.bookmarkPanel.getSlotMouseOver(mouseover.x, mouseover.y);

        if (panelSlot != null) {
            return panelSlot.isIngredient() || panelSlot.getRecipeId() == null ? FavoriteRecipes.getFavorite(stackover)
                    : panelSlot.getRecipeId();
        }

        ItemsGridSlot itemSlot = ItemPanels.itemPanel.getSlotMouseOver(mouseover.x, mouseover.y);

        if (itemSlot == null) {
            itemSlot = ItemPanels.itemPanel.historyPanel.getSlotMouseOver(mouseover.x, mouseover.y);
        }

        if (itemSlot == null) {
            itemSlot = ItemPanels.itemPanel.craftablesPanel.getSlotMouseOver(mouseover.x, mouseover.y);
        }

        if (itemSlot != null) {
            return itemSlot.getRecipeId();
        }

        return null;
    }

    private GuiCraftingRecipe(ArrayList<ICraftingHandler> handlers) {
        super(NEIClientUtils.mc().currentScreen);
        this.currenthandlers = handlers;
    }

    public static void registerRecipeHandler(ICraftingHandler handler) {
        final String handlerId = handler.getHandlerId();

        if (existingHandlers.contains(handlerId)) {
            return;
        }

        synchronized (existingHandlers) {
            existingHandlers.add(handlerId);
        }

        if (NEIClientConfig.serialHandlers.contains(handlerId)) {
            synchronized (serialCraftingHandlers) {
                serialCraftingHandlers.add(handler);
            }
        } else {
            synchronized (craftinghandlers) {
                craftinghandlers.add(handler);
            }
        }

    }

    @Override
    public ArrayList<ICraftingHandler> getCurrentRecipeHandlers() {
        return currenthandlers;
    }
}
