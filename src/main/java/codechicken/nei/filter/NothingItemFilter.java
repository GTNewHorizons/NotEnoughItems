package codechicken.nei.filter;

import net.minecraft.item.ItemStack;

import codechicken.nei.api.ItemFilter;

public class NothingItemFilter implements ItemFilter {

    @Override
    public boolean matches(ItemStack item) {
        return false;
    }
}
