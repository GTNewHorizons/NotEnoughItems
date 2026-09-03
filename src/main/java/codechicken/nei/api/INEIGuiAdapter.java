package codechicken.nei.api;

import java.util.regex.Pattern;

import net.minecraft.util.EnumChatFormatting;

/**
 * Lets you just override those things you want to
 */
public class INEIGuiAdapter implements INEIGuiHandler {

    static final Pattern SPECIAL_REGEX_CHARS = Pattern.compile("[{}()\\[\\].+*?^$\\\\|]");

    protected String formattingText(String displayName) {
        return SPECIAL_REGEX_CHARS.matcher(EnumChatFormatting.getTextWithoutFormattingCodes(displayName))
                .replaceAll("\\\\$0");
    }
}
