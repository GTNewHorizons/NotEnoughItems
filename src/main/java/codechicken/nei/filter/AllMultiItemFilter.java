package codechicken.nei.filter;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.item.ItemStack;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.api.ItemFilter;

public class AllMultiItemFilter implements ItemFilter {

    public final List<ItemFilter> filters;

    public AllMultiItemFilter(List<ItemFilter> filters) {
        this.filters = filters;
    }

    public AllMultiItemFilter(ItemFilter... filters) {
        this(new LinkedList<>(Arrays.asList(filters)));
    }

    public AllMultiItemFilter() {
        this(new LinkedList<>());
    }

    @Override
    public boolean matches(ItemStack item) {
        for (ItemFilter filter : filters) try {
            if (filter != null && !filter.matches(item)) return false;
        } catch (Exception e) {
            NEIClientConfig.logger
                    .error("Exception filtering " + item + " with " + filter + " (" + e.getMessage() + ")", e);
        }

        return true;
    }
}
