package codechicken.nei.recipe;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.api.IRecipeOverlayRenderer;

/**
 * Do not implement this. Implement one of it's subinterfaces, either {@link ICraftingHandler} or {@link IUsageHandler}
 */
public interface IRecipeHandler {

    /**
     * For legacy compatibility reasons, this method's default implementation returns the class name.
     *
     * @return A unique identifier for this handler, used for finding duplicate handlers as well as GUI tab
     *         {@link HandlerInfo}.
     */
    default String getHandlerId() {
        return getClass().getName();
    }

    /**
     *
     * @return The name of this inventory. To be displayed at the top of the viewing container.
     */
    String getRecipeName();

    /**
     * For legacy compatibility reasons, this method's default implementation returns the recipe name. This is also the
     * expected behavior
     *
     * @return The name of this inventory. To be displayed in the tooltip in the tab at the top of the viewing
     *         container.
     */
    default String getRecipeTabName() {
        return getRecipeName();
    }

    default int getRecipeHeight(int recipe) {
        return 0;
    }

    /**
     *
     * @return The number of recipes that this handler contains.
     */
    int numRecipes();

    /**
     * Draw the background of this recipe handler (basically the slot layout image).
     *
     * @param recipe The recipe index to draw at this position.
     */
    void drawBackground(int recipe);

    /**
     * Draw the foreground of this recipe handler (for things like progress bars).
     *
     * @param recipe The recipe index to draw at this position.
     */
    void drawForeground(int recipe);

    /**
     *
     * @param recipe The recipe index to get items for.
     * @return A list of the ingredient {@link PositionedStack}s in this recipe relative to the top left corner of your
     *         recipe drawing space.
     */
    List<PositionedStack> getIngredientStacks(int recipe);

    /**
     * Callers should read {@link #getResultStacks(int)} and {@link #getAuxiliaryStacks(int)} instead.
     *
     * @param recipe The recipe index to get items for.
     * @return The fuel or catalysts when {@link #getResultStack(int)} is present, otherwise all the results of the
     *         recipe. Relative to the top left corner of your recipe drawing space.
     */
    default List<PositionedStack> getOtherStacks(int recipe) {
        return Collections.emptyList();
    }

    /**
     * Callers should read {@link #getResultStacks(int)} instead.
     *
     * @param recipe The recipe index to get the result for.
     * @return The single result, or {@code null} when the recipe has several results and returns them from
     *         {@link #getOtherStacks(int)}. Relative to the top left corner of your recipe drawing space.
     */
    default PositionedStack getResultStack(int recipe) {
        return null;
    }

    /**
     * Built from {@link #getResultStack(int)} and {@link #getOtherStacks(int)}. Don't override it, return the results
     * from those methods so mods that still read them see the same results.
     *
     * @return The results: the result stack when present, otherwise the other stacks.
     */
    default List<PositionedStack> getResultStacks(int recipe) {
        final PositionedStack result = getResultStack(recipe);
        return result != null ? Collections.singletonList(result) : getOtherStacks(recipe);
    }

    /**
     * Override it only when the recipe has several results and also fuel or catalysts: the results then go to
     * {@link #getOtherStacks(int)} and the catalysts can't be returned from the old methods.
     *
     * @return The stacks that are neither ingredients nor results (fuel, catalysts): the other stacks when a result
     *         stack is present.
     */
    default List<PositionedStack> getAuxiliaryStacks(int recipe) {
        return getResultStack(recipe) != null ? getOtherStacks(recipe) : Collections.emptyList();
    }

    /**
     * @param recipe The recipe index to get the group id for.
     * @return An id shared by every recipe in this recipe's group, or {@code null} if this recipe isn't grouped.
     */
    default Object getRecipeGroupId(int recipe) {
        return null;
    }

    /**
     * A tick function called for updating progress bars and cycling damage items.
     */
    void onUpdate();

    /**
     *
     * @param recipe    The recipe index to check for.
     * @param gui       The GUI to overlay.
     * @param container The container of the GUI.
     * @return true if this recipe can render an overlay for the specified type of inventory GUI.
     */
    boolean hasOverlay(GuiContainer gui, Container container, int recipe);

    /**
     *
     * @param recipe The recipe index to get the overlay renderer for.
     * @return An instance of {@link IRecipeOverlayRenderer} to be used for rendering the overlay of this specific
     *         recipe.
     */
    IRecipeOverlayRenderer getOverlayRenderer(GuiContainer gui, int recipe);

    /**
     *
     * @param recipe The recipe index to get the overlay renderer for.
     * @return An instance of {@link IOverlayHandler} to be used for rendering the overlay of this specific recipe.
     */
    IOverlayHandler getOverlayHandler(GuiContainer gui, int recipe);

    /**
     * Simply works with the {@link codechicken.nei.api.DefaultOverlayRenderer} If the current container has been
     * registered with this identifier, the question mark appears and an overlay guide can be drawn.
     *
     * @return The overlay identifier of this recipe type.
     */
    default String getOverlayIdentifier() {
        return null;
    }

    /**
     *
     * @param gui        An instance of the currentscreen
     * @param currenttip The current tooltip, will contain item name and info
     * @param recipe     The recipe index being handled
     * @return The modified tooltip. DO NOT return null
     */
    List<String> handleTooltip(GuiRecipe<?> gui, List<String> currenttip, int recipe);

    /**
     *
     * @param gui        An instance of the currentscreen
     * @param stack      The itemstack currently under the mouse
     * @param currenttip The current tooltip, will contain item name and info
     * @param recipe     The recipe index being handled
     * @return The modified tooltip. DO NOT return null
     */
    List<String> handleItemTooltip(GuiRecipe<?> gui, ItemStack stack, List<String> currenttip, int recipe);

    /**
     *
     * @param gui     An instance of the currentscreen
     * @param keyChar The character representing the keyPress
     * @param keyCode The KeyCode as defined in {@link Keyboard}
     * @return true to terminate further processing of this event.
     */
    boolean keyTyped(GuiRecipe<?> gui, char keyChar, int keyCode, int recipe);

    /**
     *
     * @param gui    An instance of the currentscreen
     * @param button The button index being pressed, {0 = Left Click, 1 = Right Click, 2 = Middle Click}
     * @return true to terminate further processing of this event.
     */
    boolean mouseClicked(GuiRecipe<?> gui, int button, int recipe);

    /**
     * For legacy compatibility reasons, this method has a do-nothing default implementation.
     *
     * @param gui    An instance of the currentscreen
     * @param scroll The scroll direction, {> 0 = Up, < 0 = Down}
     * @return true to terminate further processing of this event.
     */
    default boolean mouseScrolled(GuiRecipe<?> gui, int scroll, int recipe) {
        return false;
    }

    /**
     * @deprecated
     * @return The number of recipes that can fit on a page in the viewer (1 or 2)
     */
    default int recipiesPerPage() {
        return 2;
    }
}
