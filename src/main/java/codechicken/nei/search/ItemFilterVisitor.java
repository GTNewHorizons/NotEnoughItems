package codechicken.nei.search;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;

import codechicken.nei.ItemList.AllMultiItemFilter;
import codechicken.nei.ItemList.AnyMultiItemFilter;
import codechicken.nei.ItemList.NegatedItemFilter;
import codechicken.nei.ItemList.NothingItemFilter;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.SearchTokenParser;
import codechicken.nei.api.ItemFilter;

public class ItemFilterVisitor extends AbstractSearchExpressionVisitor<ItemFilter> {

    private static final Pattern REGEX_ESCAPED_SPACE_PATTERN = Pattern.compile("([^\\\\](?:\\\\\\\\)+)?\\\\ ");
    private static final Pattern PLAIN_TEXT_ESCAPED_PATTERN = Pattern.compile("\\\\(.)");
    private static final Pattern ESCAPED_QUOTE_PATTERN = Pattern.compile("\\\\\"");
    private static final Pattern ESCAPED_SPACE_PATTERN = Pattern.compile("\\\\ ");

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
            final String cleanText = getTokenCleanText(ctx.token());
            final SearchTokenParser.ISearchParserProvider provider = searchParser.getProvider(ctx.token().prefix);
            if (cleanText == null || provider == null) {
                return defaultResult();
            }
            return provider.getFilter(cleanText);
        }
        return defaultResult();
    }

    @Override
    public ItemFilter visitToken(SearchExpressionParser.TokenContext ctx) {
        final String cleanText = getTokenCleanText(ctx);
        if (cleanText != null) {
            return getAlwaysProvidersFilter(cleanText);
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

    private String getTokenCleanText(SearchExpressionParser.TokenContext ctx) {
        String cleanText = null;
        if (ctx.plainText() != null) {
            cleanText = Pattern.quote(
                    PLAIN_TEXT_ESCAPED_PATTERN.matcher(ctx.plainText().getText())
                            // Unescape everything in search expression
                            .replaceAll("$1"));
        } else if (ctx.regex() != null) {
            if (ctx.regex().REGEX_CONTENT() != null) {
                cleanText = ctx.regex().REGEX_CONTENT().getSymbol().getText();
                final int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
                // Unescape spaces
                if (spaceModeEnabled == 1) {
                    cleanText = REGEX_ESCAPED_SPACE_PATTERN.matcher(cleanText).replaceAll("$1 ");
                }
            }
        } else if (ctx.quoted() != null) {
            if (ctx.quoted().QUOTED_CONTENT() != null) {
                cleanText = ctx.quoted().QUOTED_CONTENT().getSymbol().getText();
                // Unescape quotes
                cleanText = ESCAPED_QUOTE_PATTERN.matcher(cleanText).replaceAll("\"");
                cleanText = Pattern.quote(cleanText);
                final int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
                // Unescape spaces
                if (spaceModeEnabled == 1) {
                    cleanText = ESCAPED_SPACE_PATTERN.matcher(cleanText).replaceAll(" ");
                }
            }
        }
        return cleanText;
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
