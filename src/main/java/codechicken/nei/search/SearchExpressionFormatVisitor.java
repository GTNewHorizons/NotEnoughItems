package codechicken.nei.search;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import net.minecraft.util.EnumChatFormatting;

import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.SearchTokenParser;

public class SearchExpressionFormatVisitor extends SearchExpressionBaseVisitor<String> {

    private final SearchTokenParser parser;
    private static final char MODNAME_SYMBOL = '@';
    private static final char TOOLTIP_SYMBOL = '#';
    private static final char IDENTIFIER_SYMBOL = '&';
    private static final char OREDICT_SYMBOL = '$';
    private static final char SUBSET_SYMBOL = '%';
    private static final char DASH = '-';
    private final Map<Integer, EnumChatFormatting> HIGHLIGHT_MAP;

    public SearchExpressionFormatVisitor(SearchTokenParser parser) {
        super();
        this.parser = parser;
        this.HIGHLIGHT_MAP = new HashMap<>();
        HIGHLIGHT_MAP.put(SearchExpressionParser.OR, EnumChatFormatting.GRAY);
        HIGHLIGHT_MAP.put(SearchExpressionParser.LEFT_BRACKET, EnumChatFormatting.GRAY);
        HIGHLIGHT_MAP.put(SearchExpressionParser.RIGHT_BRACKET, EnumChatFormatting.GRAY);
        HIGHLIGHT_MAP.put(SearchExpressionParser.DASH, EnumChatFormatting.BLUE);
    }

    @Override
    public String visitErrorNode(ErrorNode node) {
        return "";
    }

    @Override
    public String visitChildren(RuleNode node) {
        int childCount = node.getChildCount();
        if (childCount == 0) {
            return "";
        }
        if (childCount == 1) {
            return formatChild(node.getChild(0));
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < childCount; i++) {
            ParseTree child = node.getChild(i);
            builder.append(formatChild(child));
        }
        return builder.toString();
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#modnameExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public String visitModnameExpression(SearchExpressionParser.ModnameExpressionContext ctx) {
        return formatPrefixedExpression(MODNAME_SYMBOL, ctx.token());
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#tooltipExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public String visitTooltipExpression(SearchExpressionParser.TooltipExpressionContext ctx) {
        return formatPrefixedExpression(TOOLTIP_SYMBOL, ctx.token());
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#identifierExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public String visitIdentifierExpression(SearchExpressionParser.IdentifierExpressionContext ctx) {
        return formatPrefixedExpression(IDENTIFIER_SYMBOL, ctx.token());
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#oredictExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public String visitOredictExpression(SearchExpressionParser.OredictExpressionContext ctx) {
        return formatPrefixedExpression(OREDICT_SYMBOL, ctx.token());
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#subsetExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public String visitSubsetExpression(SearchExpressionParser.SubsetExpressionContext ctx) {
        return formatPrefixedExpression(SUBSET_SYMBOL, ctx.token());
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#token}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public String visitToken(SearchExpressionParser.TokenContext ctx) {
        return getTokenCleanText(ctx);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#smartToken}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public String visitSmartToken(SearchExpressionParser.SmartTokenContext ctx) {
        return getTokenCleanText(ctx);
    }

    private String formatPrefixedExpression(char prefix, SearchExpressionParser.TokenContext ctx) {
        SearchTokenParser.ISearchParserProvider provider = parser.getProviderForDefaultPrefix(prefix);
        if (provider == null) {
            return "";
        }
        String cleanText = getTokenCleanText(ctx, provider.getHighlightedColor());
        if (cleanText == null) {
            return "";
        }
        return provider.getHighlightedColor() + String.valueOf(prefix) + cleanText;
    }

    private String getTokenCleanText(SearchExpressionParser.TokenContext ctx) {
        return getTokenCleanText(ctx, null);
    }

    private String getTokenCleanText(SearchExpressionParser.SmartTokenContext ctx) {
        return getTokenCleanText(ctx, null);
    }

    private String getTokenCleanText(SearchExpressionParser.TokenContext ctx, EnumChatFormatting format) {
        if (format == null) {
            format = EnumChatFormatting.RESET;
        }
        if (ctx.PLAIN_TEXT() != null) {
            String cleanText = ctx.PLAIN_TEXT()
                .getSymbol()
                .getText();
            int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
            if (spaceModeEnabled == 1) {
                cleanText = cleanText.replaceAll("\\\\ ", " ");
            }
            return format + cleanText;
        } else if (ctx.smartToken() != null) {
            return getTokenCleanText(ctx.smartToken(), format);
        }
        return null;
    }

    private String getTokenCleanText(SearchExpressionParser.SmartTokenContext ctx, EnumChatFormatting format) {
        if (format == null) {
            format = EnumChatFormatting.RESET;
        }
        String cleanText = null;
        int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
        if (ctx.DASH() != null) {
            cleanText = format + "-";
        } else if (ctx.REGEX() != null) {
            String regex = ctx.REGEX()
                .getSymbol()
                .getText();
            // Allows to avoid forcing '/' at the end of REGEX
            int regexStartLength = regex.indexOf('/') + 1;
            if (Pattern.compile("[^\\\\](?:\\\\\\\\)*/$")
                .matcher(regex)
                .find()) {
                cleanText = EnumChatFormatting.AQUA + regex.substring(0, regexStartLength)
                    + format
                    + regex.substring(regexStartLength, regex.length() - 1)
                    + EnumChatFormatting.AQUA
                    + "/";
            } else {
                cleanText = EnumChatFormatting.AQUA + regex.substring(0, regexStartLength)
                    + format
                    + regex.substring(regexStartLength);
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
                cleanText = EnumChatFormatting.GOLD + "\""
                    + format
                    + quoted.substring(1, quoted.length() - 1)
                    + EnumChatFormatting.GOLD
                    + "\"";
            } else {
                cleanText = EnumChatFormatting.GOLD + "\"" + format + quoted.substring(1);
            }
            if (spaceModeEnabled == 1) {
                cleanText = cleanText.replaceAll("\\\\ ", " ");
            }
        }
        return cleanText;
    }

    private String formatChild(ParseTree child) {
        if (child instanceof TerminalNode) {
            TerminalNode node = (TerminalNode) child;
            int type = node.getSymbol()
                .getType();
            EnumChatFormatting format = HIGHLIGHT_MAP.get(type);
            if (format != null) {
                return format + node.getSymbol()
                    .getText();
            } else {
                return node.getSymbol()
                    .getText();
            }
        } else {
            return visit(child);
        }
    }
}
