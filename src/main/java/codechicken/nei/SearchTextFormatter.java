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

    protected String getContextToken() {
        return this.contextToken;
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
            return SearchExpressionUtils.visitRecipeSearchExpression(text, visitor);
        } else {
            final StringBuilder formattedText = new StringBuilder();
            final String contextToken = getContextToken();

            for (String[] orQuery : searchParser.splitByDelimiters(text, "|", false)) {
                final String filterText = orQuery[1];

                formattedText.append(SearchExpressionUtils.HIGHLIGHTS.OR + orQuery[0]);

                for (String[] contextPart : searchParser.splitByDelimiters(filterText, contextToken, false)) {

                    if (!contextPart[0].isEmpty()) {
                        formattedText.append(
                                SearchExpressionUtils.HIGHLIGHTS.RECIPE + contextPart[0].substring(0, 1)
                                        + EnumChatFormatting.RESET);
                        contextPart[1] = contextPart[0].substring(1) + contextPart[1];
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

                        if (patternMode == 1 && !token.quotes && isRegexToken(token.rawText)) {
                            formattedText.append(SearchExpressionUtils.HIGHLIGHTS.REGEX + token.rawText.substring(0, 2))
                                    .append(tokenColor + token.rawText.substring(2, token.rawText.length() - 1))
                                    .append(SearchExpressionUtils.HIGHLIGHTS.REGEX + "/");
                        } else if (!token.rawText.isEmpty()) {
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

    private static boolean isRegexToken(String search) {
        return search.length() >= 3 && search.regionMatches(true, 0, "r/", 0, 2) && search.endsWith("/");
    }
}
