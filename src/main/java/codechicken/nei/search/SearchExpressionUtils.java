package codechicken.nei.search;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class SearchExpressionUtils {
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
