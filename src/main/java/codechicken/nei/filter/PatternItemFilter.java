package codechicken.nei.filter;

import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import codechicken.nei.api.ItemFilter;

public class PatternItemFilter implements ItemFilter {

    public final Pattern pattern;

    public PatternItemFilter(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean matches(ItemStack item) {
        String displayName = EnumChatFormatting.getTextWithoutFormattingCodes(item.getDisplayName());

        if (displayName != null && !displayName.isEmpty() && pattern.matcher(displayName).find()) {
            return true;
        }

        if (item.hasDisplayName()) {
            displayName = EnumChatFormatting
                    .getTextWithoutFormattingCodes(item.getItem().getItemStackDisplayName(item));

            return displayName != null && !displayName.isEmpty() && pattern.matcher(displayName).find();
        }

        return false;
    }
}
