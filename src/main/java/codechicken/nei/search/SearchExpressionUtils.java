package codechicken.nei.search;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.util.EnumChatFormatting;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

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

        RESET(EnumChatFormatting.RESET, -1),
        OR(EnumChatFormatting.GRAY, SearchExpressionParser.OR),
        BRACKETS(EnumChatFormatting.GRAY, SearchExpressionParser.LEFT_BRACKET, SearchExpressionParser.RIGHT_BRACKET),
        NEGATE(EnumChatFormatting.BLUE, SearchExpressionParser.DASH),
        REGEX(EnumChatFormatting.AQUA, SearchExpressionParser.REGEX_LEFT, SearchExpressionParser.REGEX_RIGHT),
        QUOTED(EnumChatFormatting.GOLD, SearchExpressionParser.QUOTE_LEFT, SearchExpressionParser.QUOTE_RIGHT),
        MODNAME(EnumChatFormatting.LIGHT_PURPLE, SearchExpressionParser.MODNAME_PREFIX),
        TOOLTIP(EnumChatFormatting.YELLOW, SearchExpressionParser.TOOLTIP_PREFIX),
        IDENTIFIER(EnumChatFormatting.GOLD, SearchExpressionParser.IDENTIFIER_PREFIX),
        OREDICT(EnumChatFormatting.AQUA, SearchExpressionParser.OREDICT_PREFIX),
        SUBSET(EnumChatFormatting.DARK_PURPLE, SearchExpressionParser.SUBSET_PREFIX);

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

    public static final <T> T visitSearchExpression(String text, SearchExpressionParserBaseVisitor<T> visitor) {
        final CharStream inputStream = CharStreams.fromString(text);
        final SearchExpressionErrorListener errorListener = new SearchExpressionErrorListener(true);
        final SearchExpressionLexer lexer = new SearchExpressionLexer(inputStream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(errorListener);
        final CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        final SearchExpressionParser parser = new SearchExpressionParser(tokenStream);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);
        return visitor.visitSearchExpression(parser.searchExpression());
    }
}
