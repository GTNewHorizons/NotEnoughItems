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
        List<ItemFilter> childrenFilters = ctx.sequenceExpression()
            .stream()
            .map(this::visit)
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
        List<ItemFilter> childrenFilters = ctx.unaryExpression()
            .stream()
            .map(this::visit)
            .collect(Collectors.toList());
        return new ItemList.AllMultiItemFilter(childrenFilters);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#unaryExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitUnaryExpression(SearchExpressionParser.UnaryExpressionContext ctx) {
        if (ctx.complexUnaryExpression() != null) {
            return visitComplexUnaryExpression(ctx.complexUnaryExpression());
        } else if (ctx.token() != null) {
            return visitToken(ctx.token());
        }
        return new ItemList.NothingItemFilter();
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#complexUnaryExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public ItemFilter visitComplexUnaryExpression(SearchExpressionParser.ComplexUnaryExpressionContext ctx) {
        if (ctx.orExpression() != null) {
            return visitOrExpression(ctx.orExpression());
        } else if (ctx.modnameExpression() != null) {
            return visitModnameExpression(ctx.modnameExpression());
        } else if (ctx.tooltipExpression() != null) {
            return visitTooltipExpression(ctx.tooltipExpression());
        } else if (ctx.identifierExpression() != null) {
            return visitIdentifierExpression(ctx.identifierExpression());
        } else if (ctx.oredictExpression() != null) {
            return visitOredictExpression(ctx.oredictExpression());
        } else if (ctx.subsetExpression() != null) {
            return visitSubsetExpression(ctx.subsetExpression());
        } else if (ctx.negateExpression() != null) {
            return visitNegateExpression(ctx.negateExpression());
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
        if (ctx.complexUnaryExpression() != null) {
            return new ItemList.NegatedItemFilter(visitComplexUnaryExpression(ctx.complexUnaryExpression()));
        } else if (ctx.smartToken() != null) {
            return new ItemList.NegatedItemFilter(visitSmartToken(ctx.smartToken()));
        }
        return new ItemList.NothingItemFilter();
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
        return new ItemList.NothingItemFilter();
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
        return new ItemList.NothingItemFilter();
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
            return new ItemList.NothingItemFilter();
        }
        return provider.getFilter(pattern);
    }

    private String getTokenCleanText(SearchExpressionParser.TokenContext ctx) {
        if (ctx.PLAIN_TEXT() != null) {
            return Pattern.quote(
                ctx.PLAIN_TEXT()
                    .getSymbol()
                    .getText()
                    .replaceAll("\\\\(.)", "$1"));
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
        } else if (ctx.REGEX() != null) {
            String regex = ctx.REGEX()
                .getSymbol()
                .getText();
            // Allows to avoid forcing '/' at the end of REGEX
            if (Pattern.compile("[^\\\\](?:\\\\\\\\)*/$")
                .matcher(regex)
                .find()) {
                cleanText = regex.substring(regex.indexOf('/') + 1, regex.length() - 1);
            } else {
                cleanText = regex.substring(regex.indexOf('/') + 1);
            }
            if (spaceModeEnabled == 1) {
                cleanText = cleanText.replaceAll("([^\\\\](?:\\\\\\\\)+)?\\\\ ", "$1 ");
            }
        } else if (ctx.QUOTED() != null) {
            String quoted = ctx.QUOTED()
                .getSymbol()
                .getText();
            // Allows to avoid forcing '\"' at the end of QUOTED
            if (Pattern.compile("[^\\\\]\"$")
                .matcher(quoted)
                .find()) {
                cleanText = quoted.substring(1, quoted.length() - 1);
            } else {
                cleanText = quoted.substring(1);
            }
            cleanText = Pattern.quote(cleanText)
                .replaceAll("\\\\\"", "\"");
            if (spaceModeEnabled == 1) {
                cleanText = cleanText.replaceAll("\\\\ ", " ");
            }
        }
        return cleanText;
    }
}
