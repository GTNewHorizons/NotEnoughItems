package codechicken.nei.filter;

import net.minecraft.item.ItemStack;

import codechicken.nei.api.ItemFilter;

public class NegatedItemFilter implements ItemFilter {

    private final ItemFilter filter;

    public NegatedItemFilter(ItemFilter filter) {
        this.filter = filter;
    }

    @Override
    public boolean matches(ItemStack item) {
        return this.filter == null || !this.filter.matches(item);
    }
}
