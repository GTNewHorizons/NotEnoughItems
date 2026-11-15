package codechicken.nei.search;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;

import codechicken.nei.ItemList.AllMultiItemFilter;
import codechicken.nei.ItemList.AnyMultiItemFilter;
import codechicken.nei.ItemList.NegatedItemFilter;
import codechicken.nei.ItemList.NothingItemFilter;
import codechicken.nei.SearchTokenParser;
import codechicken.nei.api.ItemFilter;

public class ItemFilterVisitor extends AbstractSearchExpressionVisitor<ItemFilter> {

    public ItemFilterVisitor(SearchTokenParser searchParser) {
        super(searchParser);
    }

    @Override
    public ItemFilter visitChildren(RuleNode node) {
        if (node instanceof ParserRuleContext) {
            return visitChildren((ParserRuleContext) node, null);
        } else {
            return defaultResult();
        }
    }

    // Should only be called for collecting ingredients permutations
    @Override
    public ItemFilter visitRecipeSearchExpression(SearchExpressionParser.RecipeSearchExpressionContext ctx) {
        if (ctx.recipeClauseExpression() != null) {
            final List<ItemFilter> anyFilters = new ArrayList<>();
            final List<ItemFilter> allFilters = new ArrayList<>();
            for (SearchExpressionParser.RecipeClauseExpressionContext clauseCtx : ctx.recipeClauseExpression()) {
                addIngredientPermutationItemFilter(clauseCtx.searchExpression(), anyFilters, allFilters);
            }
            return constructFilter(anyFilters, allFilters);
        }
        return defaultResult();
    }

    @Override
    public ItemFilter visitOrExpression(SearchExpressionParser.OrExpressionContext ctx) {
        return visitChildren(ctx, AnyMultiItemFilter::new);
    }

    @Override
    public ItemFilter visitSequenceExpression(SearchExpressionParser.SequenceExpressionContext ctx) {
        return visitChildren(ctx, AllMultiItemFilter::new);
    }

    @Override
    public ItemFilter visitNegateExpression(SearchExpressionParser.NegateExpressionContext ctx) {
        return new NegatedItemFilter(visitChildren(ctx));
    }

    @Override
    public ItemFilter visitPrefixedExpression(SearchExpressionParser.PrefixedExpressionContext ctx) {
        if (ctx.token() != null) {
            final String cleanPattern = SearchExpressionUtils.getCleanPattern(ctx.token());
            final SearchTokenParser.ISearchParserProvider provider = searchParser.getProvider(ctx.token().prefix);
            if (cleanPattern == null || provider == null) {
                return defaultResult();
            }
            return provider.getFilter(cleanPattern);
        }
        return defaultResult();
    }

    @Override
    public ItemFilter visitToken(SearchExpressionParser.TokenContext ctx) {
        final String cleanPattern = SearchExpressionUtils.getCleanPattern(ctx);
        if (cleanPattern != null) {
            return getAlwaysProvidersFilter(cleanPattern);
        }
        return defaultResult();
    }

    @Override
    protected ItemFilter defaultResult() {
        return new NothingItemFilter();
    }

    private void addIngredientPermutationItemFilter(SearchExpressionParser.SearchExpressionContext ctx,
            List<ItemFilter> anyFilters, List<ItemFilter> allFilters) {
        // Only for ingredients or default
        if (ctx.type == 0 || ctx.type == 3) {
            if (ctx.allRecipe) {
                allFilters.add(visit(ctx));
            } else {
                anyFilters.add(visit(ctx));
            }
        }
    }

    private ItemFilter constructFilter(List<ItemFilter> anyFilters, List<ItemFilter> allFilters) {
        if (anyFilters.isEmpty()) {
            return constructFilter(allFilters, AllMultiItemFilter::new);
        }
        if (allFilters.isEmpty()) {
            return constructFilter(anyFilters, AnyMultiItemFilter::new);
        }
        final List<ItemFilter> filters = new ArrayList<>();
        filters.add(constructFilter(allFilters, AllMultiItemFilter::new));
        filters.add(constructFilter(anyFilters, AnyMultiItemFilter::new));
        return constructFilter(filters, AllMultiItemFilter::new);
    }

    private ItemFilter visitChildren(ParserRuleContext node, Function<List<ItemFilter>, ItemFilter> filterConstructor) {
        if (node.children != null && !node.children.isEmpty()) {
            // By default return any found rule child filter (there should be only one)
            if (filterConstructor == null) {
                for (final ParseTree child : node.children) {
                    if (child instanceof RuleNode) {
                        return visit(child);
                    }
                }
                return defaultResult();
                // Otherwise create a filter out of rule childrens' filters
            } else {
                final List<ItemFilter> filters = new ArrayList<>();
                for (final ParseTree child : node.children) {
                    if (child instanceof RuleNode) {
                        filters.add(visit(child));
                    }
                }
                return constructFilter(filters, filterConstructor);
            }
        }
        return defaultResult();
    }

    private ItemFilter getAlwaysProvidersFilter(String searchText) {
        final List<ItemFilter> filters = searchParser.getAlwaysProvidersFilters(searchText);
        return constructFilter(filters, AnyMultiItemFilter::new);
    }

    private ItemFilter constructFilter(List<ItemFilter> filters, Function<List<ItemFilter>, ItemFilter> constructor) {
        if (!filters.isEmpty()) {
            // Propagate the result up
            if (filters.size() == 1) {
                return filters.get(0);
            }
            return constructor.apply(filters);
        } else {
            return defaultResult();
        }
    }

}
