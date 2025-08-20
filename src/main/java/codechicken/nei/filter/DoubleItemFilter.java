package codechicken.nei.filter;

import net.minecraft.item.ItemStack;

import codechicken.nei.SearchField;
import codechicken.nei.api.ItemFilter;

public class DoubleItemFilter implements ItemFilter {

    private final ItemFilter filter;
    private final PatternItemFilter patternFilter;

    public DoubleItemFilter(ItemFilter filter, String itemFilter) {
        this.filter = filter;
        this.patternFilter = new PatternItemFilter(SearchField.getPattern(itemFilter));
    }

    @Override
    public boolean matches(ItemStack item) {
        if (filter.matches(item)) {
            return patternFilter.matches(item);
        }
        return false;
    }
}
