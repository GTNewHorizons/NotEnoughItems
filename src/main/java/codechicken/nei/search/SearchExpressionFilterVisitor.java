package codechicken.nei.search;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.ErrorNode;

import codechicken.nei.ItemList;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.SearchTokenParser;
import codechicken.nei.api.ItemFilter;

public class SearchExpressionFilterVisitor extends SearchExpressionBaseVisitor<ItemFilter> {

    private final SearchTokenParser parser;
    private static final char MODNAME_SYMBOL = '@';
    private static final char TOOLTIP_SYMBOL = '#';
    private static final char IDENTIFIER_SYMBOL = '&';
    private static final char OREDICT_SYMBOL = '$';
    private static final char SUBSET_SYMBOL = '%';
    private final boolean logSearchExceptions;

    public SearchExpressionFilterVisitor(SearchTokenParser parser, boolean logSearchExceptions) {
        super();
        this.parser = parser;
        this.logSearchExceptions = logSearchExceptions;
    }

    @Override
    public ItemFilter visitErrorNode(ErrorNode node) {
        return new ItemList.EverythingItemFilter();
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#searchExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitSearchExpression(SearchExpressionParser.SearchExpressionContext ctx) {
        return visitOrExpression(ctx.orExpression());
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#orExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitOrExpression(SearchExpressionParser.OrExpressionContext ctx) {
        List<ItemFilter> childrenFilters = ctx.sequenceExpression().stream().map(this::visit)
                .collect(Collectors.toList());
        return new ItemList.AnyMultiItemFilter(childrenFilters);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#sequenceExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitSequenceExpression(SearchExpressionParser.SequenceExpressionContext ctx) {
        List<ItemFilter> childrenFilters = ctx.unaryExpression().stream().map(this::visit).collect(Collectors.toList());
        return new ItemList.AllMultiItemFilter(childrenFilters);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#unaryExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitUnaryExpression(SearchExpressionParser.UnaryExpressionContext ctx) {
        if (ctx.orExpression() != null) {
            return this.visitOrExpression(ctx.orExpression());
        } else if (ctx.token() != null) {
            return this.visitToken(ctx.token());
        } else if (ctx.modnameExpression() != null) {
            return this.visitModnameExpression(ctx.modnameExpression());
        } else if (ctx.tooltipExpression() != null) {
            return this.visitTooltipExpression(ctx.tooltipExpression());
        } else if (ctx.identifierExpression() != null) {
            return this.visitIdentifierExpression(ctx.identifierExpression());
        } else if (ctx.oredictExpression() != null) {
            return this.visitOredictExpression(ctx.oredictExpression());
        } else if (ctx.subsetExpression() != null) {
            return this.visitSubsetExpression(ctx.subsetExpression());
        } else if (ctx.negateExpression() != null) {
            return this.visitNegateExpression(ctx.negateExpression());
        }
        return new ItemList.NothingItemFilter();
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#negateExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitNegateExpression(SearchExpressionParser.NegateExpressionContext ctx) {
        return new ItemList.NegatedItemFilter(this.visitUnaryExpression(ctx.unaryExpression()));
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
        Pattern pattern = getPattern(ctx);
        if (pattern != null) {
            return new ItemList.PatternItemFilter(pattern);
        }
        return new ItemList.NothingItemFilter();
    }

    private Pattern getPattern(SearchExpressionParser.TokenContext ctx) {
        String cleanText = getTokenCleanText(ctx);
        if (cleanText == null) {
            return null;
        }
        try {
            Pattern pattern = Pattern
                    .compile(cleanText, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            return pattern;
        } catch (PatternSyntaxException e) {
            if (logSearchExceptions) {
                NEIClientConfig.logger.error("Invalid pattern syntax when parsing " + cleanText, e);
            }
            return null;
        }
    }

    private ItemFilter getFilterForPrefixedExpression(char prefix, SearchExpressionParser.TokenContext ctx) {
        Pattern pattern = getPattern(ctx);
        if (pattern == null) {
            return new ItemList.NothingItemFilter();
        }

        return parser.getProvider(prefix).getFilter(pattern);
    }

    private String getTokenCleanText(SearchExpressionParser.TokenContext ctx) {
        String cleanText = null;
        int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
        if (ctx.PLAIN_TEXT() != null) {
            cleanText = Pattern.quote(ctx.PLAIN_TEXT().getSymbol().getText().replaceAll("\\\\(.)", "$1"));
        } else if (ctx.REGEX() != null) {
            String regex = ctx.REGEX().getSymbol().getText();
            cleanText = regex.substring(regex.indexOf('/') + 1, regex.length() - 1);
            if (spaceModeEnabled == 1) {
                cleanText = cleanText.replaceAll("((?:\\\\\\\\)*)\\\\ ", "$1 ");
            }
        } else if (ctx.QUOTED() != null) {
            String quoted = ctx.QUOTED().getSymbol().getText();
            cleanText = Pattern.quote(quoted.substring(1, quoted.length() - 1)).replaceAll("\\\\\"", "\"");
            if (spaceModeEnabled == 1) {
                cleanText = cleanText.replaceAll("\\\\ ", " ");
            }
        }
        return cleanText;
    }
}
