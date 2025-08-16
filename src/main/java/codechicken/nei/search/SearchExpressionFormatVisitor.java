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

    @Override
    public String visitPrefixedExpression(SearchExpressionParser.PrefixedExpressionContext ctx) {
        final EnumChatFormatting format = getFormatting(ctx.prefix, null);
        return format + String.valueOf(ctx.prefix) + visitToken(ctx.token());
    }

    @Override
    public String visitToken(SearchExpressionParser.TokenContext ctx) {
        return getTokenCleanText(ctx, ctx.prefix);
    }

    @Override
    public String visitRegex(SearchExpressionParser.RegexContext ctx) {
        return visitChildren(ctx, ctx.prefix);
    }

    @Override
    public String visitQuoted(SearchExpressionParser.QuotedContext ctx) {
        return visitChildren(ctx, ctx.prefix);
    }

    @Override
    protected String defaultResult() {
        return "";
    }

    private String getTokenCleanText(SearchExpressionParser.TokenContext ctx, Character prefix) {
        String cleanText = null;
        final int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
        if (ctx.PLAIN_TEXT() != null) {
            cleanText = ctx.PLAIN_TEXT().getSymbol().getText();
            // Unescape spaces
            if (spaceModeEnabled == 1) {
                cleanText = ESCAPED_SPACE_PATTERN.matcher(cleanText).replaceAll(" ");
            }
            final EnumChatFormatting format = getFormatting(prefix, EnumChatFormatting.RESET);
            return format + cleanText;
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
            final TerminalNode node = (TerminalNode) child;
            final int type = node.getSymbol().getType();
            final String format = Optional.ofNullable(
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
            final ISearchParserProvider provider = searchParser.getProvider(prefix);
            if (provider != null) {
                return provider.getHighlightedColor();
            }
        }
        return defaultFormatting;
    }
}
