package codechicken.nei.recipe.widget;

import java.util.List;

import net.minecraft.item.ItemStack;

public final class ViewState {

    public int recipeIndex = -1;
    public int groupSize = 0;

    // single state counter: member = allowed[cycle % size], permutation index = cycle / size
    public int cycle = 0;
    public int cycleticks = 0;
    public int pausedRecipeIndex = -1;
    public List<ItemStack> pinnedIngredients = null;
}
