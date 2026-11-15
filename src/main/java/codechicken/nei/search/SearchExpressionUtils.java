package codechicken.nei.search;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import net.minecraft.util.EnumChatFormatting;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.SearchTokenParser;

public class SearchExpressionUtils {

    static final Pattern REGEX_ESCAPED_SPACE_PATTERN = Pattern.compile("([^\\\\](?:\\\\\\\\)+)?\\\\ ");
    private static final Pattern ESCAPED_SYMBOL_PATTERN = Pattern.compile("\\\\(.)");
    private static final Pattern ESCAPED_QUOTE_PATTERN = Pattern.compile("\\\\\"");
    static final Pattern ESCAPED_SPACE_PATTERN = Pattern.compile("\\\\ ");

    private static final Map<Integer, EnumChatFormatting> HIGHLIGHT_MAP = new HashMap<>();
    static {
        for (HIGHLIGHTS elem : HIGHLIGHTS.values()) {
            for (int parserType : elem.parserTypes) {
                HIGHLIGHT_MAP.put(parserType, elem.f);
            }
        }
    }

    public static enum HIGHLIGHTS {

        RECIPE(EnumChatFormatting.GREEN, SearchExpressionParser.RECIPE_INGREDIENTS,
                SearchExpressionParser.RECIPE_RESULT, SearchExpressionParser.RECIPE_OTHERS,
                SearchExpressionParser.RECIPE_TEXT),
        OR(EnumChatFormatting.GRAY, SearchExpressionParser.OR),
        BRACKETS(EnumChatFormatting.GRAY, SearchExpressionParser.LEFT_BRACKET, SearchExpressionParser.RIGHT_BRACKET),
        NEGATE(EnumChatFormatting.BLUE, SearchExpressionParser.DASH),
        REGEX(EnumChatFormatting.AQUA, SearchExpressionParser.REGEX_LEFT, SearchExpressionParser.REGEX_RIGHT),
        QUOTED(EnumChatFormatting.GOLD, SearchExpressionParser.QUOTE_LEFT, SearchExpressionParser.QUOTE_RIGHT);

        public final EnumChatFormatting f;
        public final int[] parserTypes;

        private HIGHLIGHTS(EnumChatFormatting format, int... parserTypes) {
            this.f = format;
            this.parserTypes = parserTypes;
        }

        @Override
        public String toString() {
            return this.f.toString();
        }
    }

    public static EnumChatFormatting getHighlight(Integer parserType) {
        return HIGHLIGHT_MAP.get(parserType);
    }

    public static <T> T visitSearchExpression(String text, AbstractSearchExpressionVisitor<T> visitor) {
        SearchExpressionParser parser = createSearchExpressionParser(text, visitor.getSearchParser(), visitor);
        SearchExpressionParser.RecipeSearchExpressionContext recipeCtx = parser.recipeSearchExpression();
        // Manually check whether it's not a recipe expression, because parser would parse it either way
        if (recipeCtx.recipeClauseExpression() != null && recipeCtx.recipeClauseExpression().size() == 1
                && recipeCtx.recipeClauseExpression(0).searchExpression().type == 3) {
            return visitor.visitSearchExpression(recipeCtx.recipeClauseExpression(0).searchExpression());
        }
        return visitor.fail();
    }

    public static <T> T visitRecipeSearchExpression(String text, AbstractSearchExpressionVisitor<T> visitor) {
        SearchExpressionParser parser = createSearchExpressionParser(text, visitor.getSearchParser(), visitor);
        return visitor.visitRecipeSearchExpression(parser.recipeSearchExpression());
    }

    public static String getCleanPattern(SearchExpressionParser.TokenContext ctx) {
        String cleanPattern = null;
        if (ctx.plainText() != null) {
            cleanPattern = Pattern.quote(
                    ESCAPED_SYMBOL_PATTERN.matcher(ctx.plainText().getText())
                            // Unescape everything in search expression
                            .replaceAll("$1"));
        } else if (ctx.regex() != null) {
            if (ctx.regex().REGEX_CONTENT() != null) {
                cleanPattern = ctx.regex().REGEX_CONTENT().getSymbol().getText();
                final int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
                // Unescape spaces
                if (spaceModeEnabled == 1) {
                    cleanPattern = REGEX_ESCAPED_SPACE_PATTERN.matcher(cleanPattern).replaceAll("$1 ");
                }
            }
        } else if (ctx.quoted() != null) {
            if (ctx.quoted().QUOTED_CONTENT() != null) {
                cleanPattern = ctx.quoted().QUOTED_CONTENT().getSymbol().getText();
                // Unescape quotes
                cleanPattern = ESCAPED_QUOTE_PATTERN.matcher(cleanPattern).replaceAll("\"");
                cleanPattern = Pattern.quote(cleanPattern);
                final int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
                // Unescape spaces
                if (spaceModeEnabled == 1) {
                    cleanPattern = ESCAPED_SPACE_PATTERN.matcher(cleanPattern).replaceAll(" ");
                }
            }
        }
        return cleanPattern;
    }

    private static <T> SearchExpressionParser createSearchExpressionParser(String text, SearchTokenParser searchParser,
            SearchExpressionParserBaseVisitor<T> visitor) {
        final boolean doLogExceptions = NEIClientConfig.getBooleanSetting("inventory.search.logSearchExceptions");
        final CharStream inputStream = CharStreams.fromString(text);
        final SearchExpressionErrorListener errorListener = new SearchExpressionErrorListener();
        final SearchExpressionLexer lexer = new SearchExpressionLexer(inputStream, searchParser);
        lexer.removeErrorListeners();
        if (doLogExceptions) {
            lexer.addErrorListener(errorListener);
        }
        final CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        final SearchExpressionParser parser = new SearchExpressionParser(tokenStream);
        parser.removeErrorListeners();
        if (doLogExceptions) {
            parser.addErrorListener(errorListener);
        }
        return parser;
    }

}
