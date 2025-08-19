package codechicken.nei.filter;

import java.util.List;

import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.ItemFilter;

public abstract class AllRecipeFilter {

    private final ItemFilter filter;

    protected AllRecipeFilter(ItemFilter filter) {
        this.filter = filter;
    }

    protected boolean allMatch(List<PositionedStack> items) {
        for (PositionedStack pStack : items) {
            if (!allMatch(pStack)) {
                return false;
            }
        }
        return true;
    }

    protected boolean allMatch(PositionedStack pStack) {
        if (pStack == null) {
            return false;
        }

        for (ItemStack stack : pStack.items) {
            if (!filter.matches(stack)) {
                return false;
            }
        }

        return true;
    }

}
