package codechicken.nei.search;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.RuleNode;

import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.SearchTokenParser;
import codechicken.nei.api.ItemFilter;

public class SearchExpressionFilterVisitor extends SearchExpressionParserBaseVisitor<ItemFilter> {

    private final SearchTokenParser searchParser;

    private static final Pattern REGEX_ESCAPED_SPACE_PATTERN = Pattern.compile("([^\\\\](?:\\\\\\\\)+)?\\\\ ");
    private static final Pattern PLAIN_TEXT_ESCAPED_PATTERN = Pattern.compile("\\\\(.)");
    private static final Pattern ESCAPED_QUOTE_PATTERN = Pattern.compile("\\\\\"");
    private static final Pattern ESCAPED_SPACE_PATTERN = Pattern.compile("\\\\ ");

    public SearchExpressionFilterVisitor(SearchTokenParser searchParser) {
        super();
        this.searchParser = searchParser;
    }

    @Override
    public ItemFilter visitChildren(RuleNode node) {
        if (node instanceof ParserRuleContext) {
            return visitChildren((ParserRuleContext) node, null);
        } else {
            return defaultResult();
        }
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#orExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public ItemFilter visitOrExpression(SearchExpressionParser.OrExpressionContext ctx) {
        return visitChildren(ctx, ItemList.AnyMultiItemFilter::new);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#sequenceExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public ItemFilter visitSequenceExpression(SearchExpressionParser.SequenceExpressionContext ctx) {
        return visitChildren(ctx, ItemList.AllMultiItemFilter::new);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#negateExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public ItemFilter visitNegateExpression(SearchExpressionParser.NegateExpressionContext ctx) {
        return new ItemList.NegatedItemFilter(visitChildren(ctx));
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#modnameExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public ItemFilter visitPrefixedExpression(SearchExpressionParser.PrefixedExpressionContext ctx) {
        if (ctx.token() != null) {
            String cleanText = getTokenCleanText(ctx.token());
            SearchTokenParser.ISearchParserProvider provider = searchParser.getProvider(ctx.token().prefix);
            if (cleanText == null || provider == null) {
                return defaultResult();
            }
            return provider.getFilter(cleanText);
        }
        return defaultResult();
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#token}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public ItemFilter visitToken(SearchExpressionParser.TokenContext ctx) {
        String cleanText = getTokenCleanText(ctx);
        if (cleanText != null) {
            return getAlwaysProvidersFilter(cleanText);
        }
        return defaultResult();
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#smartToken}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public ItemFilter visitSmartToken(SearchExpressionParser.SmartTokenContext ctx) {
        String cleanText = getTokenCleanText(ctx);
        if (cleanText != null) {
            return getAlwaysProvidersFilter(cleanText);
        }
        return defaultResult();
    }

    @Override
    protected ItemFilter defaultResult() {
        return new ItemList.NothingItemFilter();
    }

    private String getTokenCleanText(SearchExpressionParser.TokenContext ctx) {
        if (ctx.PLAIN_TEXT() != null) {
            return Pattern.quote(
                    PLAIN_TEXT_ESCAPED_PATTERN.matcher(ctx.PLAIN_TEXT().getSymbol().getText())
                            // Unescape everything in search expression
                            .replaceAll("$1"));
        } else if (ctx.smartToken() != null) {
            return getTokenCleanText(ctx.smartToken());
        }
        return null;
    }

    private String getTokenCleanText(SearchExpressionParser.SmartTokenContext ctx) {
        String cleanText = null;
        int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
        if (ctx.DASH() != null) {
            cleanText = "-";
        } else if (ctx.regex() != null) {
            if (ctx.regex().REGEX_CONTENT() != null) {
                cleanText = ctx.regex().REGEX_CONTENT().getSymbol().getText();
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
                return node.children.stream().flatMap(child -> {
                    if (child instanceof RuleNode) {
                        return Stream.of(visit(child));
                    }
                    return Stream.empty();
                }).findAny().orElse(defaultResult());
                // Otherwise create a filter out of rule childrens' filters
            } else {
                List<ItemFilter> filters = node.children.stream().flatMap(child -> {
                    if (child instanceof RuleNode) {
                        return Stream.of(visit(child));
                    }
                    return Stream.empty();
                }).collect(Collectors.toList());
                if (!filters.isEmpty()) {
                    return filterConstructor.apply(filters);
                }
            }
        }
        return defaultResult();
    }

    private ItemFilter getAlwaysProvidersFilter(String searchText) {
        return new ItemList.AnyMultiItemFilter(searchParser.getAlwaysProvidersFilters(searchText));
    }

}
