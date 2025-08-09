package codechicken.nei.search;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.RuleNode;

import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.SearchTokenParser;
import codechicken.nei.api.ItemFilter;

public class SearchExpressionFilterVisitor extends SearchExpressionParserBaseVisitor<ItemFilter> {

    private final SearchTokenParser parser;
    private static final char MODNAME_SYMBOL = '@';
    private static final char TOOLTIP_SYMBOL = '#';
    private static final char IDENTIFIER_SYMBOL = '&';
    private static final char OREDICT_SYMBOL = '$';
    private static final char SUBSET_SYMBOL = '%';

    private static final Pattern REGEX_ESCAPED_SPACE_PATTERN = Pattern.compile("([^\\\\](?:\\\\\\\\)+)?\\\\ ");
    private static final Pattern PLAIN_TEXT_ESCAPED_PATTERN = Pattern.compile("\\\\(.)");
    private static final Pattern ESCAPED_QUOTE_PATTERN = Pattern.compile("\\\\\"");
    private static final Pattern ESCAPED_SPACE_PATTERN = Pattern.compile("\\\\ ");

    private final boolean logSearchExceptions;

    public SearchExpressionFilterVisitor(SearchTokenParser parser, boolean logSearchExceptions) {
        super();
        this.parser = parser;
        this.logSearchExceptions = logSearchExceptions;
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
    public ItemFilter visitOrExpression(SearchExpressionParser.OrExpressionContext ctx) {
        return visitChildren(ctx, ItemList.AnyMultiItemFilter::new);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#sequenceExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitSequenceExpression(SearchExpressionParser.SequenceExpressionContext ctx) {
        return visitChildren(ctx, ItemList.AllMultiItemFilter::new);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#negateExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitNegateExpression(SearchExpressionParser.NegateExpressionContext ctx) {
        if (ctx.complexUnaryExpression() != null) {
            return new ItemList.NegatedItemFilter(visitComplexUnaryExpression(ctx.complexUnaryExpression()));
        } else if (ctx.smartToken() != null) {
            return new ItemList.NegatedItemFilter(visitSmartToken(ctx.smartToken()));
        }
        return defaultResult();
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#modnameExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitModnameExpression(SearchExpressionParser.ModnameExpressionContext ctx) {
        return getFilterForPrefixedExpression(MODNAME_SYMBOL, ctx.token());
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#tooltipExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitTooltipExpression(SearchExpressionParser.TooltipExpressionContext ctx) {
        return getFilterForPrefixedExpression(TOOLTIP_SYMBOL, ctx.token());
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#identifierExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitIdentifierExpression(SearchExpressionParser.IdentifierExpressionContext ctx) {
        return getFilterForPrefixedExpression(IDENTIFIER_SYMBOL, ctx.token());
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#oredictExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitOredictExpression(SearchExpressionParser.OredictExpressionContext ctx) {
        return getFilterForPrefixedExpression(OREDICT_SYMBOL, ctx.token());
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#subsetExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitSubsetExpression(SearchExpressionParser.SubsetExpressionContext ctx) {
        return getFilterForPrefixedExpression(SUBSET_SYMBOL, ctx.token());
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#token}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitToken(SearchExpressionParser.TokenContext ctx) {
        String cleanText = getTokenCleanText(ctx);
        if (cleanText != null) {
            Pattern pattern = getPattern(cleanText);
            if (pattern != null) {
                return new ItemList.PatternItemFilter(pattern);
            }
        }
        return defaultResult();
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#smartToken}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitSmartToken(SearchExpressionParser.SmartTokenContext ctx) {
        String cleanText = getTokenCleanText(ctx);
        if (cleanText != null) {
            Pattern pattern = getPattern(cleanText);
            if (pattern != null) {
                return new ItemList.PatternItemFilter(pattern);
            }
        }
        return defaultResult();
    }

    @Override
    protected ItemFilter defaultResult() {
        return new ItemList.EverythingItemFilter();
    }

    private Pattern getPattern(String cleanText) {
        if (cleanText == null) {
            return null;
        }
        try {
            Pattern pattern = Pattern
                    .compile(cleanText, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            return pattern;
        } catch (PatternSyntaxException e) {
            if (logSearchExceptions) {
                NEIClientConfig.logger.error("Invalid pattern syntax when parsing " + cleanText);
            }
            return null;
        }
    }

    private ItemFilter getFilterForPrefixedExpression(char prefix, SearchExpressionParser.TokenContext ctx) {
        Pattern pattern = getPattern(getTokenCleanText(ctx));
        SearchTokenParser.ISearchParserProvider provider = parser.getProviderForDefaultPrefix(prefix);
        if (pattern == null || provider == null) {
            return defaultResult();
        }
        return provider.getFilter(pattern);
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

}
