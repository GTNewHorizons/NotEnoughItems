package codechicken.nei.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.RecipeFilter;
import codechicken.nei.recipe.IRecipeHandler;

public class AllMultiRecipeFilter implements RecipeFilter {

    public final List<RecipeFilter> filters;

    public AllMultiRecipeFilter(List<RecipeFilter> filters) {
        this.filters = filters;
    }

    public AllMultiRecipeFilter(RecipeFilter filters) {
        this(Arrays.asList(filters));
    }

    public AllMultiRecipeFilter() {
        this(new ArrayList<>());
    }

    @Override
    public boolean matches(IRecipeHandler handler, List<PositionedStack> ingredients, PositionedStack result,
            List<PositionedStack> others) {
        for (RecipeFilter filter : filters) {
            try {
                if (filter != null && !filter.matches(handler, ingredients, result, others)) return false;
            } catch (Exception e) {
                NEIClientConfig.logger.error("Exception filtering " + handler + " with " + filter, e);
            }
        }
        return true;

    }

}
