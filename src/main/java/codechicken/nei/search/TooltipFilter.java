package codechicken.nei.search;

import java.util.HashSet;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import codechicken.nei.ItemList;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.guihook.GuiContainerManager;

public class TooltipFilter implements ItemFilter {

    // lookup optimisation
    private static final ConcurrentHashMap<ItemStackKey, String> itemSearchNames = new ConcurrentHashMap<>();

    private static class ItemStackKey {

        public final ItemStack stack;

        public ItemStackKey(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int hashCode() {
            if (this.stack == null) return 1;
            int hashCode = 1;
            hashCode = 31 * hashCode + stack.stackSize;
            hashCode = 31 * hashCode + Item.getIdFromItem(stack.getItem());
            hashCode = 31 * hashCode + stack.getItemDamage();
            hashCode = 31 * hashCode + (!stack.hasTagCompound() ? 0 : stack.getTagCompound().hashCode());
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof ItemStackKey)) return false;
            return ItemStack.areItemStacksEqual(this.stack, ((ItemStackKey) o).stack);
        }
    }

    private final Pattern pattern;

    public TooltipFilter(Pattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public boolean matches(ItemStack itemStack) {
        return pattern.matcher(getSearchTooltip(itemStack)).find();
    }

    public static void populateSearchMap() {
        /* Create a snapshot of the current keys in the cache */
        HashSet<ItemStackKey> oldItems = new HashSet<>(itemSearchNames.keySet());

        for (ItemStack stack : ItemList.items) {
            oldItems.remove(new ItemStackKey(stack));
        }

        itemSearchNames.keySet().removeAll(oldItems);
    }

    protected static String getSearchTooltip(ItemStack stack) {
        return itemSearchNames.computeIfAbsent(new ItemStackKey(stack), key -> getTooltip(key.stack));
    }

    private static String getTooltip(ItemStack itemstack) {
        final List<String> list = GuiContainerManager.itemDisplayNameMultiline(itemstack, null, true);
        final StringJoiner sb = new StringJoiner("\n");
        final int size = list.size();

        for (int i = 1; i < size; i++) {
            sb.add(list.get(i));
        }

        return EnumChatFormatting.getTextWithoutFormattingCodes(sb.toString());
    }

}
