package codechicken.nei;

import java.util.List;
import java.util.StringJoiner;

import net.minecraft.util.EnumChatFormatting;

import codechicken.nei.FormattedTextField.TextFormatter;
import codechicken.nei.SearchTokenParser.SearchToken;
import codechicken.nei.search.SearchExpressionFormatVisitor;
import codechicken.nei.search.SearchExpressionUtils;

public class SearchTextFormatter implements TextFormatter {

    protected final SearchTokenParser searchParser;

    public SearchTextFormatter(SearchTokenParser searchParser) {
        this.searchParser = searchParser;
    }

    public String format(String text) {
        int patternMode = NEIClientConfig.getIntSetting("inventory.search.patternMode");
        if (patternMode == 3) {
            int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
            if (spaceModeEnabled == 1) {

                text = text.replaceAll(" ", "\\\\ ");
            }
            final SearchExpressionFormatVisitor visitor = new SearchExpressionFormatVisitor(this.searchParser);
            return SearchExpressionUtils.visitSearchExpression(text, visitor);
        } else {
            final String[] parts = (text + "| ").split("\\|");
            StringJoiner formattedText = new StringJoiner(EnumChatFormatting.GRAY + "|");

            for (int i = 0; i < parts.length - 1; i++) {
                final String filterText = parts[i];
                final List<SearchToken> tokens = searchParser.splitSearchText(filterText);
                StringBuilder formattedPart = new StringBuilder();
                int startIndex = 0;

                for (SearchToken token : tokens) {
                    formattedPart.append(filterText.substring(startIndex, token.start));
                    EnumChatFormatting tokenColor = EnumChatFormatting.RESET;

                    if (token.firstChar != null) {
                        tokenColor = searchParser.getProvider(token.firstChar)
                            .getHighlightedColor();
                    }

                    if (token.ignore) {
                        formattedPart.append(EnumChatFormatting.BLUE + "-");
                    }

                    if (token.firstChar != null) {
                        formattedPart.append(tokenColor + String.valueOf(token.firstChar));
                    }

                    if (token.quotes) {
                        formattedPart.append(EnumChatFormatting.GOLD + "\"");
                    }

                    if (!token.rawText.isEmpty()) {
                        formattedPart.append(tokenColor + token.rawText);
                    }

                    if (token.quotes) {
                        formattedPart.append(EnumChatFormatting.GOLD + "\"");
                    }

                    startIndex = token.end;
                }

                formattedPart.append(filterText.substring(startIndex, filterText.length()));
                formattedText.add(formattedPart);
            }

            return formattedText.toString();
        }
    }
}
