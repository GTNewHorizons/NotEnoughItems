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

public class SearchExpressionFormatVisitor extends SearchExpressionParserBaseVisitor<String> {

    private static final Pattern REGEX_ESCAPED_SPACE_PATTERN = Pattern.compile("([^\\\\](?:\\\\\\\\)+)?\\\\ ");
    private static final Pattern ESCAPED_SPACE_PATTERN = Pattern.compile("\\\\ ");

    @Override
    public String visitChildren(RuleNode node) {
        if (node instanceof ParserRuleContext) {
            return visitChildren((ParserRuleContext) node, null);
        } else {
            return defaultResult();
        }
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#token}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public String visitToken(SearchExpressionParser.TokenContext ctx) {
        return getTokenCleanText(ctx, ctx.parentType);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#smartToken}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public String visitSmartToken(SearchExpressionParser.SmartTokenContext ctx) {
        return getTokenCleanText(ctx, ctx.parentType);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#regex}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public String visitRegex(SearchExpressionParser.RegexContext ctx) {
        return visitChildren(ctx, ctx.parentType);
    }

    /**
     * Visit a parse tree produced by {@link SearchExpressionParser#quoted}.
     *
     * @param ctx the parse tree
     * @return the visitor result
     */
    @Override
    public String visitQuoted(SearchExpressionParser.QuotedContext ctx) {
        return visitChildren(ctx, ctx.parentType);
    }

    @Override
    protected String defaultResult() {
        return "";
    }

    private String getTokenCleanText(SearchExpressionParser.TokenContext ctx, Integer parentType) {
        if (ctx.PLAIN_TEXT() != null) {
            String cleanText = ctx.PLAIN_TEXT().getSymbol().getText();
            int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
            // Unescape spaces
            if (spaceModeEnabled == 1) {
                cleanText = ESCAPED_SPACE_PATTERN.matcher(cleanText).replaceAll(" ");
            }
            EnumChatFormatting format = EnumChatFormatting.RESET;
            if (parentType != null) {
                format = SearchExpressionUtils.getHighlight(parentType);
            }
            return format + cleanText;
        } else if (ctx.smartToken() != null) {
            return visitSmartToken(ctx.smartToken());
        }
        return null;
    }

    private String getTokenCleanText(SearchExpressionParser.SmartTokenContext ctx, Integer parentType) {
        String cleanText = null;
        int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
        if (ctx.DASH() != null) {
            EnumChatFormatting format = EnumChatFormatting.RESET;
            if (parentType != null) {
                format = SearchExpressionUtils.getHighlight(parentType);
            }
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

    private String formatChild(ParseTree child, Integer parentType) {
        if (child instanceof TerminalNode) {
            TerminalNode node = (TerminalNode) child;
            int type = node.getSymbol().getType();
            String format = Optional.ofNullable(
                    // check if highlight is defined for the token
                    Optional.ofNullable(SearchExpressionUtils.getHighlight(type))
                            // check if highlight is defined for the parent token
                            .orElse(SearchExpressionUtils.getHighlight(parentType))
            // use default highlight otherwise
            ).map(EnumChatFormatting::toString).orElse("");

            return format + node.getSymbol().getText();
        } else {
            return visit(child);
        }
    }

    private String visitChildren(ParserRuleContext node, Integer parentType) {
        if (node.children != null && !node.children.isEmpty()) {
            return node.children.stream().map(child -> formatChild(child, parentType)).collect(Collectors.joining());
        }
        return defaultResult();
    }
}
