package mezz.jei.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StringUtils;

import codechicken.nei.NEIClientConfig;
import mezz.jei.util.Translator;

public final class IngredientInformation {

    private IngredientInformation() {}

    public static <T> String getDisplayName(T ingredient, IIngredientHelper<T> ingredientHelper) {
        String displayName = ingredientHelper.getDisplayName(ingredient);
        return removeChatFormatting(displayName);
    }

    public static <T> List<String> getTooltipStrings(T ingredient, Set<String> toRemove) {
        boolean tooltipFlag = NEIClientConfig.getSearchAdvancedTooltips();
        List<String> tooltip;
        try {
            tooltip = ((ItemStack) ingredient).getTooltip(Minecraft.getMinecraft().thePlayer, tooltipFlag);
        } catch (NullPointerException ignored) {
            tooltip = Collections.emptyList();
        }
        List<String> cleanTooltip = new ArrayList<>(tooltip.size());
        for (String line : tooltip) {
            line = removeChatFormatting(line);
            line = Translator.toLowercaseWithLocale(line);
            for (String excludeWord : toRemove) {
                line = line.replace(excludeWord, "");
            }
            if (!StringUtils.isNullOrEmpty(line)) {
                cleanTooltip.add(line);
            }
        }
        return cleanTooltip;
    }

    private static String removeChatFormatting(String string) {
        String withoutFormattingCodes = EnumChatFormatting.getTextWithoutFormattingCodes(string);
        return (withoutFormattingCodes == null) ? "" : withoutFormattingCodes;
    }

    public static <V> List<String> getUniqueIdsWithWildcard(IIngredientHelper<V> ingredientHelper, V ingredient) {
        String uid = ingredientHelper.getUniqueId(ingredient);
        String uidWild = ingredientHelper.getWildcardId(ingredient);

        if (uid.equals(uidWild)) {
            return Collections.singletonList(uid);
        } else {
            return Arrays.asList(uid, uidWild);
        }
    }
}
