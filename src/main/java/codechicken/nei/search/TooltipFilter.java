package codechicken.nei.search;

import java.util.List;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import codechicken.nei.ItemStackMap;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.api.ItemFilter;

public class TooltipFilter implements ItemFilter {

    private static final ItemStackMap<String> itemSearchNames = new ItemStackMap<>();

    private final Pattern pattern;

    public TooltipFilter(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean matches(ItemStack itemStack) {
        return pattern.matcher(getSearchTooltip(itemStack)).find();
    }

    public static void clearCache() {
        itemSearchNames.clear();
    }

    public static void putItem(ItemStack stack) {
        String tooltip = getTooltip(stack.copy());
        synchronized (itemSearchNames) {
            itemSearchNames.put(stack, tooltip);
        }
    }

    public static String getSearchTooltip(ItemStack stack) {
        String tooltip = itemSearchNames.get(stack);

        if (tooltip == null) {
            tooltip = getTooltip(stack.copy());

            synchronized (itemSearchNames) {
                itemSearchNames.put(stack, tooltip);
            }
        }

        return tooltip;
    }

    private static String getTooltip(ItemStack itemstack) {

        try {
            List<String> namelist = itemstack.getTooltip(NEIClientUtils.mc().thePlayer, false);

            if (namelist.size() > 1) {
                return EnumChatFormatting
                        .getTextWithoutFormattingCodes(String.join("\n", namelist.subList(1, namelist.size())));
            }

        } catch (Throwable ignored) {}

        return "";
    }

}
