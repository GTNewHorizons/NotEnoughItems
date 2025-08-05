package codechicken.nei.search;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import codechicken.nei.NEIClientConfig;

public class SearchExpressionErrorListener extends BaseErrorListener {

    private final boolean logSearchExceptions;

    public SearchExpressionErrorListener(boolean logSearchExceptions) {
        super();
        this.logSearchExceptions = logSearchExceptions;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
            String msg, RecognitionException e) {
        if (logSearchExceptions) {
            NEIClientConfig.logger.error(
                    "NEI Search Pattern exception on line " + line + " char " + charPositionInLine + ": " + msg,
                    e);
        }
    }

}
