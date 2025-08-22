package codechicken.nei.search;

import codechicken.nei.SearchTokenParser;

public abstract class AbstractSearchExpressionVisitor<T> extends SearchExpressionParserBaseVisitor<T> {

    protected final SearchTokenParser searchParser;

    protected AbstractSearchExpressionVisitor(SearchTokenParser searchParser) {
        this.searchParser = searchParser;
    }

    public SearchTokenParser getSearchParser() {
        return searchParser;
    }

}
