package codechicken.nei.recipe;

import static codechicken.lib.gui.GuiDraw.getMousePosition;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import codechicken.nei.ItemPanel.ItemPanelSlot;
import codechicken.nei.ItemPanels;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.stackinfo.GTFluidStackStringifyHandler;

public class GuiCraftingRecipe extends GuiRecipe<ICraftingHandler> {

    public static ArrayList<ICraftingHandler> craftinghandlers = new ArrayList<>();
    public static ArrayList<ICraftingHandler> serialCraftingHandlers = new ArrayList<>();

    public static boolean openRecipeGui(String outputId, Object... results) {
        return createRecipeGui(outputId, true, results) != null;
    }

    public static GuiRecipe<?> createRecipeGui(String outputId, boolean open, Object... results) {
        final Minecraft mc = NEIClientUtils.mc();
        final BookmarkRecipeId recipeId;

        if ("item".equals(outputId)) {
            results = Arrays.asList(results).stream().map(rslt -> normalizeItemStack((ItemStack) rslt)).toArray();
            recipeId = getRecipeId(mc.currentScreen, (ItemStack) results[0]);
        } else if ("recipeId".equals(outputId)) {
            recipeId = (BookmarkRecipeId) results[1];
        } else {
            recipeId = getCurrentRecipe(mc.currentScreen);
        }

        final ArrayList<ICraftingHandler> handlers = getCraftingHandlers(outputId, results);

        if (!handlers.isEmpty()) {
            final GuiCraftingRecipe gui = new GuiCraftingRecipe(handlers, "recipeId".equals(outputId));

            if (open) {
                mc.displayGuiScreen(gui);
            }

            gui.openTargetRecipe(recipeId);
            return gui;
        }

        return null;
    }

    public static boolean overlayRecipe(ItemStack stack, BookmarkRecipeId recipeId, final Boolean shift) {

        if (stack == null || recipeId == null) {
            return false;
        }

        final ArrayList<ICraftingHandler> handlers = getCraftingHandlers("recipeId", stack, recipeId);

        if (!handlers.isEmpty()) {
            final GuiCraftingRecipe gui = new GuiCraftingRecipe(handlers, true);
            final int recipe = gui.openTargetRecipe(recipeId);

            if (recipe != -1) {
                gui.overlayRecipe(recipe, shift);
                return true;
            }

        }

        return false;
    }

    public static ArrayList<ICraftingHandler> getCraftingHandlers(String outputId, Object... results) {
        ArrayList<ICraftingHandler> craftinghandlers = GuiCraftingRecipe.craftinghandlers;
        ArrayList<ICraftingHandler> serialCraftingHandlers = GuiCraftingRecipe.serialCraftingHandlers;
        Function<ICraftingHandler, ICraftingHandler> recipeHandlerFunction;

        if ("recipeId".equals(outputId)) {
            ItemStack stack = (ItemStack) results[0];
            BookmarkRecipeId recipeId = (BookmarkRecipeId) results[1];
            craftinghandlers = filterByHandlerName(craftinghandlers, recipeId.handlerName);
            serialCraftingHandlers = filterByHandlerName(serialCraftingHandlers, recipeId.handlerName);
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

        return recipeQuery.runWithProfiling("recipe.concurrent.crafting");
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

    private GuiCraftingRecipe(ArrayList<ICraftingHandler> handlers, boolean limitToOneRecipe) {
        super(NEIClientUtils.mc().currentScreen);
        this.limitToOneRecipe = limitToOneRecipe;
        this.currenthandlers = handlers;
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
