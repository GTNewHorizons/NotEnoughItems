package codechicken.nei.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.recipe.IRecipeHandler;

public class AnyMultiRecipeFilter implements IRecipeFilter {

    public final List<IRecipeFilter> filters;

    public AnyMultiRecipeFilter(List<IRecipeFilter> filters) {
        this.filters = filters;
    }

    public AnyMultiRecipeFilter(IRecipeFilter filters) {
        this(Arrays.asList(filters));
    }

    public AnyMultiRecipeFilter() {
        this(new ArrayList<>());
    }

    @Override
    public boolean matches(IRecipeHandler handler, int recipeIndex) {
        for (IRecipeFilter filter : filters) {
            try {
                if (filter != null && filter.matches(handler, recipeIndex)) return true;
            } catch (Exception e) {
                NEIClientConfig.logger.error("Exception filtering " + handler + " with " + filter, e);
            }
        }

        return false;
    }

}
