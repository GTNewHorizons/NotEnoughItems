package codechicken.nei.search;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import codechicken.nei.ItemList;
import codechicken.nei.ItemStackMap;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.guihook.GuiContainerManager;

public class TooltipFilter implements ItemFilter {

    private static final ItemStackMap<String> itemSearchNames = new ItemStackMap<>();
    private static final ReentrantLock lock = new ReentrantLock();

    private final Pattern pattern;

    public TooltipFilter(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean matches(ItemStack itemStack) {
        return pattern.matcher(getSearchTooltip(itemStack)).find();
    }

    public static void populateSearchMap() {
        itemSearchNames.clear();
        new Thread(
                () -> ItemList.items.parallelStream().forEach(TooltipFilter::getSearchTooltip),
                "NEI populate Tooltip Filter").start();
    }

    protected static String getSearchTooltip(ItemStack stack) {
        lock.lock();

        try {
            String tooltip = itemSearchNames.get(stack);

            if (tooltip == null) {
                tooltip = getTooltip(stack.copy());
                itemSearchNames.put(stack, tooltip);
            }

            return tooltip;
        } catch (Throwable th) {
            th.printStackTrace();
            return "";
        } finally {
            lock.unlock();
        }
    }

    private static String getTooltip(ItemStack itemstack) {
        final List<String> list = GuiContainerManager.itemDisplayNameMultiline(itemstack, null, true);
        return EnumChatFormatting.getTextWithoutFormattingCodes(String.join("\n", list.subList(1, list.size())));
    }

}
