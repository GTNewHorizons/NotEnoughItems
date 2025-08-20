package codechicken.nei.filter;

import java.util.LinkedList;
import java.util.List;

import net.minecraft.item.ItemStack;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.api.ItemFilter;

public class AnyMultiItemFilter implements ItemFilter {

    public final List<ItemFilter> filters;

    public AnyMultiItemFilter(List<ItemFilter> filters) {
        this.filters = filters;
    }

    public AnyMultiItemFilter() {
        this(new LinkedList<>());
    }

    @Override
    public boolean matches(ItemStack item) {
        for (ItemFilter filter : filters) try {
            if (filter != null && filter.matches(item)) return true;
        } catch (Exception e) {
            NEIClientConfig.logger
                    .error("Exception filtering " + item + " with " + filter + " (" + e.getMessage() + ")", e);
        }

        return false;
    }
}
