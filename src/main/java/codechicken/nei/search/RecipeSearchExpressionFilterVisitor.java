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

public class RecipeSearchExpressionFilterVisitor extends SearchExpressionParserBaseVisitor<IRecipeFilter> {

    private final SearchExpressionFilterVisitor searchExpressionVisitor;

    public RecipeSearchExpressionFilterVisitor(SearchTokenParser searchParser) {
        super();
        searchExpressionVisitor = new SearchExpressionFilterVisitor(searchParser);
    }

    @Override
    public IRecipeFilter visitRecipeSearchExpression(SearchExpressionParser.RecipeSearchExpressionContext ctx) {
        if (ctx.searchExpression() != null) {
            List<IRecipeFilter> filters = new ArrayList<>();
            System.out.println("left");
            filters.add(getFilterByType(0, ctx, IngredientsItemRecipeFilter::new, AllIngredientsItemRecipeFilter::new));
            System.out.println("right");
            filters.add(getFilterByType(1, ctx, ResultItemRecipeFilter::new, AllResultItemRecipeFilter::new));
            System.out.println("others");
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
        IRecipeFilter filter = ctx.searchExpression().stream()
                .filter(searchExpressionCtx -> searchExpressionCtx.type == type).map(searchExpressionCtx -> {
                    ItemFilter itemFilter = searchExpressionVisitor.visitSearchExpression(searchExpressionCtx);
                    if (searchExpressionCtx.allRecipe) {
                        return createAllFilter.apply(itemFilter);
                    } else {
                        return createAnyFilter.apply(itemFilter);
                    }
                }).findFirst().orElse(defaultResult());
        // System.out.println(printFilterContents(filter));
        return filter;

    }

    private static String printFilterContents(ItemFilter filter) {
        if (filter instanceof SearchTokenParser.IsRegisteredItemFilter) {
            return printFilterContents(((SearchTokenParser.IsRegisteredItemFilter) filter).filter);
        }
        if (filter instanceof ItemList.AnyMultiItemFilter) {
            return "ANY: (" + ((ItemList.AnyMultiItemFilter) filter).filters.stream()
                    .map(RecipeSearchExpressionFilterVisitor::printFilterContents).collect(Collectors.joining(","))
                    + ")";
        }
        if (filter instanceof ItemList.AllMultiItemFilter) {
            return "ALL: (" + ((ItemList.AllMultiItemFilter) filter).filters.stream()
                    .map(RecipeSearchExpressionFilterVisitor::printFilterContents).collect(Collectors.joining(","))
                    + ")";
        }
        if (filter instanceof ItemList.PatternItemFilter) {
            return "pattern(" + ((ItemList.PatternItemFilter) filter).pattern + ")";
        }
        return filter.toString();
    }

}
