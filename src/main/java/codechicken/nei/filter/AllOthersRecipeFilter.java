package codechicken.nei.filter;

import java.util.List;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.recipe.IRecipeHandler;

public class AllOthersRecipeFilter extends AllRecipeFilter implements IRecipeFilter {

    public AllOthersRecipeFilter(ItemFilter filter) {
        super(filter);
    }

    @Override
    public boolean matches(IRecipeHandler handler, List<PositionedStack> ingredients, PositionedStack result,
            List<PositionedStack> others) {

        return allMatch(others);
    }

}
