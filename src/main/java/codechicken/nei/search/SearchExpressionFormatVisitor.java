package codechicken.nei.search;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.EnumChatFormatting;

import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.SearchTokenParser;

public class SearchExpressionFormatVisitor extends SearchExpressionParserBaseVisitor<String> {

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
        HIGHLIGHT_MAP.put(SearchExpressionParser.REGEX_LEFT, EnumChatFormatting.AQUA);
        HIGHLIGHT_MAP.put(SearchExpressionParser.REGEX_RIGHT, EnumChatFormatting.AQUA);
        HIGHLIGHT_MAP.put(SearchExpressionParser.QUOTE_LEFT, EnumChatFormatting.GOLD);
        HIGHLIGHT_MAP.put(SearchExpressionParser.QUOTE_RIGHT, EnumChatFormatting.GOLD);
        HIGHLIGHT_MAP.put(SearchExpressionParser.MODNAME_PREFIX, EnumChatFormatting.LIGHT_PURPLE);
        HIGHLIGHT_MAP.put(SearchExpressionParser.TOOLTIP_PREFIX, EnumChatFormatting.YELLOW);
        HIGHLIGHT_MAP.put(SearchExpressionParser.IDENTIFIER_PREFIX, EnumChatFormatting.GOLD);
        HIGHLIGHT_MAP.put(SearchExpressionParser.OREDICT_PREFIX, EnumChatFormatting.AQUA);
        HIGHLIGHT_MAP.put(SearchExpressionParser.SUBSET_PREFIX, EnumChatFormatting.DARK_PURPLE);
    }

    @Override
    public String visitErrorNode(ErrorNode node) {
        return "";
    }

    @Override
    public String visitChildren(RuleNode node) {
        return visitChildren(node, null);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#token}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public String visitToken(SearchExpressionParser.TokenContext ctx) {
        return getTokenCleanText(ctx, ctx.format);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#smartToken}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public String visitSmartToken(SearchExpressionParser.SmartTokenContext ctx) {
        return getTokenCleanText(ctx, ctx.format);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#regex}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public String visitRegex(SearchExpressionParser.RegexContext ctx) {
        return visitChildren(ctx, ctx.format);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#quoted}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    public String visitQuoted(SearchExpressionParser.QuotedContext ctx) {
        return visitChildren(ctx, ctx.format);
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
            return visitSmartToken(ctx.smartToken());
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
        } else if (ctx.regex() != null) {
            cleanText = visitRegex(ctx.regex());
            if (spaceModeEnabled == 1) {
                cleanText = cleanText.replaceAll("([^\\\\](?:\\\\\\\\)+)?\\\\ ", "$1 ");
            }
        } else if (ctx.quoted() != null) {
            cleanText = visitQuoted(ctx.quoted());
            if (spaceModeEnabled == 1) {
                cleanText = cleanText.replaceAll("\\\\ ", " ");
            }
        }
        return cleanText;
    }

    private String formatChild(ParseTree child, EnumChatFormatting defaultFormat) {
        if (child instanceof TerminalNode) {
            TerminalNode node = (TerminalNode) child;
            int type = node.getSymbol()
                .getType();
            EnumChatFormatting format = HIGHLIGHT_MAP.get(type);
            if (format != null) {
                return format + node.getSymbol()
                    .getText();
            } else {
                if (defaultFormat != null) {
                    return defaultFormat + node.getSymbol()
                        .getText();
                } else {
                    return node.getSymbol()
                        .getText();
                }
            }
        } else {
            return visit(child);
        }
    }

    private String visitChildren(RuleNode node, EnumChatFormatting defaultFormat) {
        int childCount = node.getChildCount();
        if (childCount == 0) {
            return "";
        }
        if (childCount == 1) {
            return formatChild(node.getChild(0), defaultFormat);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < childCount; i++) {
            ParseTree child = node.getChild(i);
            builder.append(formatChild(child, defaultFormat));
        }
        return builder.toString();
    }
}
