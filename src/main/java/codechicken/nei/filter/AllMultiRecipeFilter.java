package codechicken.nei.filter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import codechicken.nei.NEIClientUtils;
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
    public boolean matches(IRecipeHandler handler, int recipeIndex) {
        for (IRecipeFilter filter : filters) {
            try {
                if (filter != null && !filter.matches(handler, recipeIndex)) return false;
            } catch (Exception e) {
                NEIClientUtils.errorOnce(
                        "filter:" + handler.getHandlerId() + ":" + filter.getClass().getName(),
                        "Exception filtering " + handler.getHandlerId() + " with " + filter,
                        e);
            }
        }
        return true;

    }

}
