package codechicken.nei.search;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.EnumChatFormatting;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.SearchTokenParser;

public class SearchExpressionUtils {

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
                SearchExpressionParser.RECIPE_RESULT, SearchExpressionParser.RECIPE_OTHERS),
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
