package codechicken.nei.filter;

import java.util.List;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.RecipeFilter;
import codechicken.nei.recipe.IRecipeHandler;

public class AnyItemRecipeFilter extends AnyRecipeFilter implements RecipeFilter {

    public AnyItemRecipeFilter(ItemFilter filter) {
        super(filter);
    }

    @Override
    public boolean matches(IRecipeHandler handler, List<PositionedStack> ingredients, PositionedStack result,
            List<PositionedStack> others) {
        return anyMatch(ingredients) || anyMatch(result) || anyMatch(others);
    }

}
