package codechicken.nei;

import codechicken.nei.api.ItemFilter;
import net.minecraft.item.ItemStack;

public class DoubleFilteredFilter implements ItemFilter {
    private ItemFilter filter;
    private ItemList.PatternItemFilter patternFilter;
    
    public DoubleFilteredFilter(ItemFilter filter, String itemFilter) {
        this.filter = filter; 
        this.patternFilter = new ItemList.PatternItemFilter(SearchField.getPattern(itemFilter));
    }

    @Override
    public boolean matches(ItemStack item) {
        if(filter.matches(item)) {
            return patternFilter.matches(item);
        }
        return false;
    }
}
