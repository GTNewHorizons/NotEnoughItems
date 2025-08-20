package codechicken.nei.search;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import codechicken.nei.SearchTokenParser;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.RecipeFilter;
import codechicken.nei.filter.AllIngredientsRecipeFilter;
import codechicken.nei.filter.AllMultiRecipeFilter;
import codechicken.nei.filter.AllOthersRecipeFilter;
import codechicken.nei.filter.AllResultRecipeFilter;
import codechicken.nei.filter.AnyIngredientsRecipeFilter;
import codechicken.nei.filter.AnyItemRecipeFilter;
import codechicken.nei.filter.AnyOthersRecipeFilter;
import codechicken.nei.filter.AnyResultRecipeFilter;
import codechicken.nei.filter.EverythingItemFilter;

public class RecipeFilterVisitor extends SearchExpressionParserBaseVisitor<RecipeFilter> {

    private final ItemFilterVisitor itemFilterVisitor;

    public RecipeFilterVisitor(SearchTokenParser searchParser) {
        super();
        itemFilterVisitor = new ItemFilterVisitor(searchParser);
    }

    @Override
    public RecipeFilter visitRecipeSearchExpression(SearchExpressionParser.RecipeSearchExpressionContext ctx) {
        if (ctx.searchExpression() != null) {
            final List<RecipeFilter> filters = new ArrayList<>();
            filters.add(getFilterByType(0, ctx, AnyIngredientsRecipeFilter::new, AllIngredientsRecipeFilter::new));
            filters.add(getFilterByType(1, ctx, AnyResultRecipeFilter::new, AllResultRecipeFilter::new));
            filters.add(getFilterByType(2, ctx, AnyOthersRecipeFilter::new, AllOthersRecipeFilter::new));
            return new AllMultiRecipeFilter(filters);
        }
        return defaultResult();
    }

    @Override
    protected RecipeFilter defaultResult() {
        return new AnyItemRecipeFilter(new EverythingItemFilter());
    }

    private RecipeFilter getFilterByType(int type, SearchExpressionParser.RecipeSearchExpressionContext ctx,
            Function<ItemFilter, RecipeFilter> createAnyFilter, Function<ItemFilter, RecipeFilter> createAllFilter) {
        final List<RecipeFilter> filters = new ArrayList<>();
        for (final SearchExpressionParser.SearchExpressionContext searchExpressionCtx : ctx.searchExpression()) {
            if (searchExpressionCtx.type == type) {
                final ItemFilter itemFilter = itemFilterVisitor.visitSearchExpression(searchExpressionCtx);
                if (searchExpressionCtx.allRecipe) {
                    filters.add(createAllFilter.apply(itemFilter));
                } else {
                    filters.add(createAnyFilter.apply(itemFilter));
                }
            }
        }
        if (filters.isEmpty()) {
            return defaultResult();
        } else if (filters.size() == 1) {
            return filters.get(0);
        }
        return new AllMultiRecipeFilter(filters);
    }

}
