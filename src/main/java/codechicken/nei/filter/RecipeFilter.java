package codechicken.nei.filter;

import java.util.List;

import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.recipe.IRecipeHandler;

public class RecipeFilter implements IRecipeFilter {

    public enum FilterContext {

        ANY,
        INPUT,
        OUTPUT;

        public static FilterContext fromChar(char c) {
            switch (c) {
                case '<':
                    return INPUT;
                case '>':
                    return OUTPUT;
                default:
                    return ANY;
            }
        }
    }

    private final ItemFilter filter;
    private final FilterContext context;
    private final boolean anyMatch;

    public RecipeFilter(FilterContext context, boolean anyMatch, ItemFilter filter) {
        this.context = context;
        this.anyMatch = anyMatch;
        this.filter = filter;
    }

    @Override
    public boolean matches(IRecipeHandler handler, int recipeIndex) {

        if ((this.context == FilterContext.ANY || this.context == FilterContext.INPUT)
                && matchPositionedStack(handler.getIngredientStacks(recipeIndex), this.anyMatch)) {
            return this.anyMatch;
        }

        if (this.context == FilterContext.ANY || this.context == FilterContext.OUTPUT) {
            final PositionedStack result = handler.getResultStack(recipeIndex);

            if (result != null && matchPositionedStack(result) == this.anyMatch) {
                return this.anyMatch;
            }

            if (matchPositionedStack(handler.getOtherStacks(recipeIndex), this.anyMatch)) {
                return this.anyMatch;
            }

        }

        return !this.anyMatch;
    }

    private boolean matchPositionedStack(List<PositionedStack> items, boolean dir) {

        for (PositionedStack pStack : items) {
            if (matchPositionedStack(pStack) == dir) {
                return true;
            }
        }

        return false;
    }

    private boolean matchPositionedStack(PositionedStack pStack) {
        if (pStack == null) return false;

        for (ItemStack stack : pStack.items) {
            if (this.filter.matches(stack)) {
                return true;
            }
        }

        return false;
    }

}
