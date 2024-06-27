package codechicken.nei;

import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.util.EnumChatFormatting;

import codechicken.nei.FormattedTextField.TextFormatter;

public class SearchTextFormatter implements TextFormatter {

    protected final SearchTokenParser searchParser;

    public SearchTextFormatter(SearchTokenParser searchParser) {
        this.searchParser = searchParser;
    }

    public String format(String text) {
        final String[] parts = text.split("\\|");
        final Pattern splitPattern = searchParser.getSplitPattern();
        StringJoiner formattedText = new StringJoiner(EnumChatFormatting.GRAY + "|");

        for (String filterText : parts) {
            Matcher filterMatcher = splitPattern.matcher(filterText);
            StringBuilder formattedPart = new StringBuilder();
            int startIndex = 0;

            while (filterMatcher.find()) {
                boolean ignore = "-".equals(filterMatcher.group(2));
                String firstChar = filterMatcher.group(3);
                String token = filterMatcher.group(4);
                boolean quotes = token.length() > 1 && token.startsWith("\"") && token.endsWith("\"");

                if (quotes) {
                    token = token.substring(1, token.length() - 1);
                }

                formattedPart.append(filterText.substring(startIndex, filterMatcher.start()));
                EnumChatFormatting tokenColor = EnumChatFormatting.RESET;

                if (!firstChar.isEmpty()) {
                    tokenColor = searchParser.getProvider(firstChar.charAt(0)).getHighlightedColor();
                }

                if (ignore) {
                    formattedPart.append(EnumChatFormatting.BLUE + "-");
                }

                if (!firstChar.isEmpty()) {
                    formattedPart.append(tokenColor + firstChar);
                }

                if (quotes) {
                    formattedPart.append(EnumChatFormatting.GOLD + "\"");
                }

                if (!token.isEmpty()) {
                    formattedPart.append(tokenColor + token);
                }

                if (quotes) {
                    formattedPart.append(EnumChatFormatting.GOLD + "\"");
                }

                startIndex = filterMatcher.end();
            }

            formattedPart.append(filterText.substring(startIndex, filterText.length()));
            formattedText.add(formattedPart);
        }

        if (text.endsWith("|")) {
            formattedText.add("");
        }

        return formattedText.toString();
    }
}
