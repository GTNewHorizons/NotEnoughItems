package codechicken.nei.filter;

import java.util.List;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.recipe.IRecipeHandler;

public class AllSmartResultRecipeFilter extends AllRecipeFilter implements IRecipeFilter {

    public AllSmartResultRecipeFilter(ItemFilter filter) {
        super(filter);
    }

    @Override
    public boolean matches(IRecipeHandler handler, List<PositionedStack> ingredients, PositionedStack result,
            List<PositionedStack> others) {
        if (result != null) {
            return allMatch(result);
        } else {
            // GT recipes use others as a result
            return allMatch(others);
        }
    }

}
