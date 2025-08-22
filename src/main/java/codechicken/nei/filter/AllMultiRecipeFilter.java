package codechicken.nei.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.recipe.IRecipeHandler;

public class AllMultiRecipeFilter implements IRecipeFilter {

    public final List<IRecipeFilter> filters;

    public AllMultiRecipeFilter(List<IRecipeFilter> filters) {
        this.filters = filters;
    }

    public AllMultiRecipeFilter(IRecipeFilter filters) {
        this(Arrays.asList(filters));
    }

    public AllMultiRecipeFilter() {
        this(new ArrayList<>());
    }

    @Override
    public boolean matches(IRecipeHandler handler, List<PositionedStack> ingredients, PositionedStack result,
            List<PositionedStack> others) {
        for (IRecipeFilter filter : filters) {
            try {
                if (filter != null && !filter.matches(handler, ingredients, result, others)) return false;
            } catch (Exception e) {
                NEIClientConfig.logger.error("Exception filtering " + handler + " with " + filter, e);
            }
        }
        return true;

    }

}
