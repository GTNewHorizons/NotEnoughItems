package codechicken.nei.api;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.nei.recipe.IRecipeHandler;

/**
 * Implemented by the container of a machine that processes recipes over time. With such a container open, "Craft Items"
 * on a crafting chain does not craft: it hands the recipes of the chain to the machine, which loads the ingredients of
 * the one it can process next.
 */
public interface IRecipeQueue {

    /**
     * Loads the ingredients of the next recipe into the machine.
     *
     * @param recipes the recipes of the chain that still need crafting, from the bottom of the chain up
     * @return whether the machine took care of "Craft Items"; if not, NEI crafts the chain as usual
     */
    boolean queueRecipes(GuiContainer gui, List<QueuedRecipe> recipes);

    /**
     * Items the machine holds outside its slots, such as the craft it is making or the fluid in its tanks (as fluid
     * display stacks). Crafting chains count them as available, so they are neither missing nor queued again.
     */
    default List<ItemStack> getItemsOutsideSlots() {
        return Collections.emptyList();
    }

    /** A recipe of a crafting chain and how many crafts it still needs. */
    final class QueuedRecipe {

        public final IRecipeHandler handler;
        public final int recipeIndex;
        public final long crafts;

        public QueuedRecipe(IRecipeHandler handler, int recipeIndex, long crafts) {
            this.handler = handler;
            this.recipeIndex = recipeIndex;
            this.crafts = crafts;
        }
    }
}
