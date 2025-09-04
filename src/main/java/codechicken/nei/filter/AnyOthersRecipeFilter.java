package codechicken.nei.filter;

import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.recipe.IRecipeHandler;

public class AnyOthersRecipeFilter implements IRecipeFilter {

    private final ItemFilter filter;

    public AnyOthersRecipeFilter(ItemFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean matches(IRecipeHandler handler, int recipeIndex) {

        for (PositionedStack pStack : handler.getOtherStacks(recipeIndex)) {
            if (match(pStack)) {
                return true;
            }
        }

        return false;
    }

    protected boolean match(PositionedStack pStack) {
        if (pStack == null) {
            return false;
        }

        for (ItemStack stack : pStack.items) {
            if (filter.matches(stack)) {
                return true;
            }
        }

        return false;
    }

}
