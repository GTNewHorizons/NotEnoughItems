package codechicken.nei.search;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import codechicken.nei.ItemList;
import codechicken.nei.SearchTokenParser;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipe.AllIngredientsItemRecipeFilter;
import codechicken.nei.recipe.GuiRecipe.AllOthersItemRecipeFilter;
import codechicken.nei.recipe.GuiRecipe.AllResultItemRecipeFilter;
import codechicken.nei.recipe.GuiRecipe.IngredientsItemRecipeFilter;
import codechicken.nei.recipe.GuiRecipe.OthersItemRecipeFilter;
import codechicken.nei.recipe.GuiRecipe.ResultItemRecipeFilter;

public class RecipeFilterVisitor extends SearchExpressionParserBaseVisitor<IRecipeFilter> {

    private final ItemFilterVisitor itemFilterVisitor;

    public RecipeFilterVisitor(SearchTokenParser searchParser) {
        super();
        itemFilterVisitor = new ItemFilterVisitor(searchParser);
    }

    @Override
    public IRecipeFilter visitRecipeSearchExpression(SearchExpressionParser.RecipeSearchExpressionContext ctx) {
        if (ctx.searchExpression() != null) {
            List<IRecipeFilter> filters = new ArrayList<>();
            filters.add(getFilterByType(0, ctx, IngredientsItemRecipeFilter::new, AllIngredientsItemRecipeFilter::new));
            filters.add(getFilterByType(1, ctx, ResultItemRecipeFilter::new, AllResultItemRecipeFilter::new));
            filters.add(getFilterByType(2, ctx, OthersItemRecipeFilter::new, AllOthersItemRecipeFilter::new));
            return new GuiRecipe.AllMultiRecipeFilter(filters);
        }
        return defaultResult();
    }

    @Override
    protected IRecipeFilter defaultResult() {
        return new GuiRecipe.ItemRecipeFilter(new ItemList.EverythingItemFilter());
    }

    private IRecipeFilter getFilterByType(int type, SearchExpressionParser.RecipeSearchExpressionContext ctx,
            Function<ItemFilter, IRecipeFilter> createAnyFilter, Function<ItemFilter, IRecipeFilter> createAllFilter) {
        List<IRecipeFilter> filters = ctx.searchExpression().stream()
                .filter(searchExpressionCtx -> searchExpressionCtx.type == type).map(searchExpressionCtx -> {
                    ItemFilter itemFilter = itemFilterVisitor.visitSearchExpression(searchExpressionCtx);
                    if (searchExpressionCtx.allRecipe) {
                        return createAllFilter.apply(itemFilter);
                    } else {
                        return createAnyFilter.apply(itemFilter);
                    }
                }).collect(Collectors.toList());
        if (filters.isEmpty()) {
            return defaultResult();
        } else if (filters.size() == 1) {
            return filters.get(0);
        }
        return new GuiRecipe.AllMultiRecipeFilter(filters);
    }

}
