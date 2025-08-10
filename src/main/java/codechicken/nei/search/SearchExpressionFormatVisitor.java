package codechicken.nei.search;

import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.minecraft.util.EnumChatFormatting;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.SearchTokenParser;
import codechicken.nei.SearchTokenParser.ISearchParserProvider;

public class SearchExpressionFormatVisitor extends SearchExpressionParserBaseVisitor<String> {

    private static final Pattern REGEX_ESCAPED_SPACE_PATTERN = Pattern.compile("([^\\\\](?:\\\\\\\\)+)?\\\\ ");
    private static final Pattern ESCAPED_SPACE_PATTERN = Pattern.compile("\\\\ ");

    private final SearchTokenParser searchParser;

    public SearchExpressionFormatVisitor(SearchTokenParser searchParser) {
        this.searchParser = searchParser;
    }

    @Override
    public String visitChildren(RuleNode node) {
        if (node instanceof ParserRuleContext) {
            return visitChildren((ParserRuleContext) node, null);
        } else {
            return defaultResult();
        }
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#prefixedExpression}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public String visitPrefixedExpression(SearchExpressionParser.PrefixedExpressionContext ctx) {
        EnumChatFormatting format = getFormatting(ctx.prefix, null);
        return format + String.valueOf(ctx.prefix) + visitToken(ctx.token());
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#token}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public String visitToken(SearchExpressionParser.TokenContext ctx) {
        return getTokenCleanText(ctx, ctx.prefix);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#smartToken}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public String visitSmartToken(SearchExpressionParser.SmartTokenContext ctx) {
        return getTokenCleanText(ctx, ctx.prefix);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#regex}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public String visitRegex(SearchExpressionParser.RegexContext ctx) {
        return visitChildren(ctx, ctx.prefix);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#quoted}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public String visitQuoted(SearchExpressionParser.QuotedContext ctx) {
        return visitChildren(ctx, ctx.prefix);
    }

    @Override
    protected String defaultResult() {
        return "";
    }

    private String getTokenCleanText(SearchExpressionParser.TokenContext ctx, Character prefix) {
        if (ctx.PLAIN_TEXT() != null) {
            String cleanText = ctx.PLAIN_TEXT().getSymbol().getText();
            int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
            // Unescape spaces
            if (spaceModeEnabled == 1) {
                cleanText = ESCAPED_SPACE_PATTERN.matcher(cleanText).replaceAll(" ");
            }
            EnumChatFormatting format = getFormatting(prefix, EnumChatFormatting.RESET);
            return format + cleanText;
        } else if (ctx.smartToken() != null) {
            return visitSmartToken(ctx.smartToken());
        }
        return null;
    }

    private String getTokenCleanText(SearchExpressionParser.SmartTokenContext ctx, Character prefix) {
        String cleanText = null;
        int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
        if (ctx.DASH() != null) {
            EnumChatFormatting format = getFormatting(prefix, EnumChatFormatting.RESET);
            cleanText = format + "-";
        } else if (ctx.regex() != null) {
            cleanText = visitRegex(ctx.regex());
            if (spaceModeEnabled == 1) {
                cleanText = REGEX_ESCAPED_SPACE_PATTERN.matcher(cleanText).replaceAll("$1 ");
            }
        } else if (ctx.quoted() != null) {
            cleanText = visitQuoted(ctx.quoted());
            if (spaceModeEnabled == 1) {
                cleanText = ESCAPED_SPACE_PATTERN.matcher(cleanText).replaceAll(" ");
            }
        }
        return cleanText;
    }

    private String formatChild(ParseTree child, Character prefix) {
        if (child instanceof TerminalNode) {
            TerminalNode node = (TerminalNode) child;
            int type = node.getSymbol().getType();
            String format = Optional.ofNullable(
                    // check if highlight is defined for the token
                    Optional.ofNullable(SearchExpressionUtils.getHighlight(type))
                            // check if highlight is defined for the prefix
                            .orElse(getFormatting(prefix, null))
            // use default highlight otherwise
            ).map(EnumChatFormatting::toString).orElse("");

            return format + node.getSymbol().getText();
        } else {
            return visit(child);
        }
    }

    private String visitChildren(ParserRuleContext node, Character prefix) {
        if (node.children != null && !node.children.isEmpty()) {
            return node.children.stream().map(child -> formatChild(child, prefix)).collect(Collectors.joining());
        }
        return defaultResult();
    }

    private EnumChatFormatting getFormatting(Character prefix, EnumChatFormatting defaultFormatting) {
        if (prefix != null) {
            if (prefix == '\0') {
                return EnumChatFormatting.RESET;
            }
            ISearchParserProvider provider = searchParser.getProvider(prefix);
            if (provider != null) {
                return provider.getHighlightedColor();
            }
        }
        return defaultFormatting;
    }
}
