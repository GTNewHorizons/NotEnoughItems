package codechicken.nei.search;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import codechicken.nei.SearchTokenParser;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.filter.AllIngredientsRecipeFilter;
import codechicken.nei.filter.AllMultiRecipeFilter;
import codechicken.nei.filter.AllOthersRecipeFilter;
import codechicken.nei.filter.AllSmartResultRecipeFilter;
import codechicken.nei.filter.AnyIngredientsRecipeFilter;
import codechicken.nei.filter.AnyItemRecipeFilter;
import codechicken.nei.filter.AnyOthersRecipeFilter;
import codechicken.nei.filter.AnySmartResultRecipeFilter;
import codechicken.nei.filter.EverythingItemFilter;

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
        return new AnyItemRecipeFilter(new EverythingItemFilter());
    }

    private IRecipeFilter createRecipeFilter(SearchExpressionParser.SearchExpressionContext ctx) {
        if (ctx == null) {
            return defaultResult();
        }
        final ItemFilter itemFilter = itemFilterVisitor.visitSearchExpression(ctx);
        switch (ctx.type) {
            case 0:
                return getAllOrAnyFilter(
                        ctx.allRecipe,
                        itemFilter,
                        AnyIngredientsRecipeFilter::new,
                        AllIngredientsRecipeFilter::new);
            case 1:
                return getAllOrAnyFilter(
                        ctx.allRecipe,
                        itemFilter,
                        AnySmartResultRecipeFilter::new,
                        AllSmartResultRecipeFilter::new);
            case 2:
                return getAllOrAnyFilter(
                        ctx.allRecipe,
                        itemFilter,
                        AnyOthersRecipeFilter::new,
                        AllOthersRecipeFilter::new);
            // Doesn't support all by default
            case 3:
                return getAllOrAnyFilter(ctx.allRecipe, itemFilter, AnyItemRecipeFilter::new, AnyItemRecipeFilter::new);
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
