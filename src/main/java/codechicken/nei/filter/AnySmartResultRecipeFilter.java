package codechicken.nei.filter;

import java.util.List;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.RecipeFilter;
import codechicken.nei.recipe.IRecipeHandler;

public class AnySmartResultRecipeFilter extends AnyRecipeFilter implements RecipeFilter {

    public AnySmartResultRecipeFilter(ItemFilter filter) {
        super(filter);
    }

    @Override
    public boolean matches(IRecipeHandler handler, List<PositionedStack> ingredients, PositionedStack result,
            List<PositionedStack> others) {
        if (result != null) {
            return anyMatch(result);
        } else {
            // GT recipes use others as a result
            return anyMatch(others);
        }
    }

}
