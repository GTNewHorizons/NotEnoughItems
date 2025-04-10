package codechicken.nei.recipe;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;

import codechicken.nei.LRUCache;
import codechicken.nei.LayoutManager;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IRecipeOverlayRenderer;
import codechicken.nei.recipe.GuiOverlayButton.ItemOverlayState;
import codechicken.nei.recipe.Recipe.RecipeId;

public class RecipeHandlerRef {

    private static final LRUCache<RecipeId, RecipeHandlerRef> recipeRefCache = new LRUCache<>(200);
    public final IRecipeHandler handler;
    public final int recipeIndex;

    private RecipeHandlerRef(IRecipeHandler handler, int recipeIndex) {
        this.handler = handler;
        this.recipeIndex = recipeIndex;
    }

    public static RecipeHandlerRef of(IRecipeHandler handler, int recipeIndex) {
        return new RecipeHandlerRef(handler, recipeIndex);
    }

    public static RecipeHandlerRef of(RecipeId recipeId) {

        if (recipeId.getResult() != null && !recipeId.getIngredients().isEmpty()) {
            return recipeRefCache.computeIfAbsent(recipeId, ri -> {
                final List<ICraftingHandler> handlers = GuiCraftingRecipe
                        .getCraftingHandlers("recipeId", recipeId.getResult(), recipeId);

                for (ICraftingHandler handler : handlers) {
                    int refIndex = SearchRecipeHandler.findFirst(
                            handler,
                            recipeIndex -> recipeId.equalsIngredients(handler.getIngredientStacks(recipeIndex)));

                    if (refIndex >= 0) {
                        return new RecipeHandlerRef(handler, refIndex);
                    }
                }

                return null;
            });
        }

        return null;
    }

    private GuiContainer prepareGui(GuiContainer firstGui) {

        if (firstGui instanceof GuiRecipe gui) {
            firstGui = gui.firstGui;
        }

        return firstGui != null && firstGui.inventorySlots != null ? firstGui : null;
    }

    public IOverlayHandler getOverlayHandler(GuiContainer gui) {
        final GuiContainer firstGui = prepareGui(gui);

        if (firstGui != null && this.handler.hasOverlay(firstGui, firstGui.inventorySlots, this.recipeIndex)) {
            return this.handler.getOverlayHandler(firstGui, this.recipeIndex);
        }

        return null;
    }

    public boolean canFillCraftingGrid(GuiContainer gui) {
        final IOverlayHandler overlayHandler = getOverlayHandler(gui);

        if (overlayHandler != null) {
            return overlayHandler.canFillCraftingGrid(prepareGui(gui), this.handler, this.recipeIndex);
        }

        return false;
    }

    public boolean canCraft(GuiContainer gui) {
        final IOverlayHandler overlayHandler = getOverlayHandler(gui);

        if (overlayHandler != null && allowedTransferAlghoritm(gui)) {
            return overlayHandler.canCraft(prepareGui(gui), this.handler, this.recipeIndex);
        }

        return false;
    }

    public List<ItemOverlayState> getPresenceOverlay(GuiContainer gui) {
        final IOverlayHandler overlayHandler = getOverlayHandler(gui);

        if (overlayHandler != null) {
            return overlayHandler.presenceOverlay(prepareGui(gui), this.handler, this.recipeIndex);
        }

        return Collections.emptyList();
    }

    public void fillCraftingGrid(GuiContainer gui, int multiplier) {
        final IOverlayHandler overlayHandler = getOverlayHandler(gui);

        if (overlayHandler != null) {
            final GuiContainer firstGui = prepareGui(gui);

            if (!firstGui.equals(firstGui.mc.currentScreen)) {
                firstGui.mc.displayGuiScreen(firstGui);
            }

            if (!allowedTransferAlghoritm(gui)) {
                overlayHandler.overlayRecipe(firstGui, this.handler, this.recipeIndex, multiplier != 1);
            } else {
                overlayHandler.transferRecipe(firstGui, this.handler, this.recipeIndex, multiplier);
            }

        }

    }

    public boolean craft(GuiContainer gui, int multiplier) {
        final GuiContainer firstGui = prepareGui(gui);
        final IOverlayHandler overlayHandler = getOverlayHandler(firstGui);

        if (overlayHandler != null && overlayHandler.canCraft(firstGui, this.handler, this.recipeIndex)) {

            if (!firstGui.equals(firstGui.mc.currentScreen)) {
                firstGui.mc.displayGuiScreen(firstGui);
            }

            return overlayHandler.craft(firstGui, this.handler, this.recipeIndex, multiplier);
        }

        return false;
    }

    public boolean allowedTransferAlghoritm(GuiContainer gui) {
        final IOverlayHandler overlayHandler = getOverlayHandler(gui);

        if (overlayHandler != null) {
            try {
                return !overlayHandler.getClass()
                        .getDeclaredMethod(
                                "transferRecipe",
                                GuiContainer.class,
                                IRecipeHandler.class,
                                int.class,
                                int.class)
                        .isDefault();
            } catch (NoSuchMethodException e) {}
        }

        return false;
    }

    public IRecipeOverlayRenderer getRecipeOverlayRenderer(GuiContainer gui) {
        final GuiContainer firstGui = prepareGui(gui);

        if (firstGui != null && this.handler.hasOverlay(firstGui, firstGui.inventorySlots, this.recipeIndex)) {
            return this.handler.getOverlayRenderer(firstGui, this.recipeIndex);
        }

        return null;
    }

    public boolean canUseOverlayRenderer(GuiContainer gui) {
        return getRecipeOverlayRenderer(gui) != null;
    }

    public void useOverlayRenderer(GuiContainer gui) {
        final IRecipeOverlayRenderer renderer = getRecipeOverlayRenderer(gui);

        if (renderer != null) {
            final GuiContainer firstGui = prepareGui(gui);

            if (!firstGui.equals(firstGui.mc.currentScreen)) {
                firstGui.mc.displayGuiScreen(firstGui);
            }

            LayoutManager.overlayRenderer = renderer;
        }
    }
}
