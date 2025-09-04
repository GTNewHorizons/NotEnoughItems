package codechicken.nei.search;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import codechicken.nei.ItemList.EverythingItemFilter;
import codechicken.nei.SearchTokenParser;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.filter.AllMultiRecipeFilter;
import codechicken.nei.filter.AllOthersRecipeFilter;
import codechicken.nei.filter.AnyOthersRecipeFilter;
import codechicken.nei.filter.RecipeFilter;
import codechicken.nei.filter.RecipeFilter.FilterContext;

public class RecipeFilterVisitor extends AbstractSearchExpressionVisitor<IRecipeFilter> {

    private final ItemFilterVisitor itemFilterVisitor;

    public RecipeFilterVisitor(SearchTokenParser searchParser) {
        super(searchParser);
        itemFilterVisitor = new ItemFilterVisitor(searchParser);
    }

    @Override
    public IRecipeFilter visitRecipeSearchExpression(SearchExpressionParser.RecipeSearchExpressionContext ctx) {
        if (ctx.recipeClauseExpression() != null) {
            final List<IRecipeFilter> filters = new ArrayList<>();
            for (SearchExpressionParser.RecipeClauseExpressionContext clauseCtx : ctx.recipeClauseExpression()) {
                filters.add(createRecipeFilter(clauseCtx.searchExpression()));
            }
            return constructFilter(filters);
        }
        return defaultResult();
    }

    @Override
    protected IRecipeFilter defaultResult() {
        return new RecipeFilter(FilterContext.ANY, true, new EverythingItemFilter());
    }

    private IRecipeFilter createRecipeFilter(SearchExpressionParser.SearchExpressionContext ctx) {
        if (ctx == null) {
            return defaultResult();
        }
        final ItemFilter itemFilter = itemFilterVisitor.visitSearchExpression(ctx);
        switch (ctx.type) {
            case 0:
                return new RecipeFilter(FilterContext.INPUT, !ctx.allRecipe, itemFilter);
            case 1:
                return new RecipeFilter(FilterContext.OUTPUT, !ctx.allRecipe, itemFilter);
            case 2:
                return getAllOrAnyFilter(
                        ctx.allRecipe,
                        itemFilter,
                        AnyOthersRecipeFilter::new,
                        AllOthersRecipeFilter::new);
            // Doesn't support all by default
            case 3:
                return new RecipeFilter(FilterContext.ANY, !ctx.allRecipe, itemFilter);
            default:
                return defaultResult();
        }
    }

    private IRecipeFilter getAllOrAnyFilter(boolean allRecipe, ItemFilter itemFilter,
            Function<ItemFilter, IRecipeFilter> createAnyFilter, Function<ItemFilter, IRecipeFilter> createAllFilter) {
        if (allRecipe) {
            return createAllFilter.apply(itemFilter);
        } else {
            return createAnyFilter.apply(itemFilter);
        }
    }

    private IRecipeFilter constructFilter(List<IRecipeFilter> filters) {
        if (!filters.isEmpty()) {
            // Propagate the result up
            if (filters.size() == 1) {
                return filters.get(0);
            }
            return new AllMultiRecipeFilter(filters);
        } else {
            return defaultResult();
        }
    }

}
