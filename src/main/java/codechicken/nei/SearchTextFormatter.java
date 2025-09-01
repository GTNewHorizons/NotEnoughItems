package codechicken.nei;

import net.minecraft.util.EnumChatFormatting;

import codechicken.nei.FormattedTextField.TextFormatter;
import codechicken.nei.search.SearchExpressionFormatVisitor;
import codechicken.nei.search.SearchExpressionUtils;
import codechicken.nei.search.SearchToken;

public class SearchTextFormatter implements TextFormatter {

    protected final SearchTokenParser searchParser;
    protected String contextToken = "";

    public SearchTextFormatter(SearchTokenParser searchParser) {
        this.searchParser = searchParser;
    }

    public void setContextToken(String contextToken) {
        this.contextToken = contextToken;
    }

    @Override
    public String format(String text) {
        final int patternMode = NEIClientConfig.getIntSetting("inventory.search.patternMode");
        if (patternMode == 3) {
            final int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
            if (spaceModeEnabled == 1) {
                text = SearchTokenParser.SPACE_PATTERN.matcher(text).replaceAll("\\\\ ");
            }
            final SearchExpressionFormatVisitor visitor = new SearchExpressionFormatVisitor(searchParser);
            return SearchExpressionUtils.visitSearchExpression(text, visitor);
        } else {
            final StringBuilder formattedText = new StringBuilder();

            for (String[] orQuery : searchParser.splitByDelimiters(text, "|", false)) {
                final String filterText = orQuery[1];

                formattedText.append(SearchExpressionUtils.HIGHLIGHTS.OR + orQuery[0]);

                for (String[] contextPart : searchParser.splitByDelimiters(filterText, this.contextToken, false)) {

                    if (!contextPart[0].isEmpty()) {
                        formattedText.append(
                                SearchExpressionUtils.HIGHLIGHTS.RECIPE + contextPart[0] + EnumChatFormatting.RESET);
                    }

                    if (contextPart[1].isEmpty()) {
                        continue;
                    }

                    int startIndex = 0;

                    for (SearchToken token : searchParser.splitSearchText(contextPart[1])) {
                        formattedText.append(contextPart[1].substring(startIndex, token.start));
                        EnumChatFormatting tokenColor = EnumChatFormatting.RESET;

                        if (token.firstChar != null) {
                            tokenColor = searchParser.getProvider(token.firstChar).getHighlightedColor();
                        }

                        if (token.ignore != null) {
                            formattedText
                                    .append(SearchExpressionUtils.HIGHLIGHTS.NEGATE + String.valueOf(token.ignore));
                        }

                        if (token.firstChar != null) {
                            formattedText.append(tokenColor + String.valueOf(token.firstChar));
                        }

                        if (token.quotes) {
                            formattedText.append(SearchExpressionUtils.HIGHLIGHTS.QUOTED + "\"");
                        }

                        if (!token.rawText.isEmpty()) {
                            formattedText.append(tokenColor + token.rawText);
                        }

                        if (token.quotes) {
                            formattedText.append(SearchExpressionUtils.HIGHLIGHTS.QUOTED + "\"");
                        }

                        startIndex = token.end;
                    }

                    formattedText.append(contextPart[1].substring(startIndex, contextPart[1].length()));
                }

            }

            return formattedText.toString();
        }
    }
}
