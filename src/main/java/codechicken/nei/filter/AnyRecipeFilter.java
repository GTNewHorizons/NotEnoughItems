package codechicken.nei.filter;

import java.util.List;

import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.ItemFilter;

public abstract class AnyRecipeFilter {

    private final ItemFilter filter;

    protected AnyRecipeFilter(ItemFilter filter) {
        this.filter = filter;
    }

    protected boolean anyMatch(List<PositionedStack> items) {
        for (PositionedStack pStack : items) {
            if (anyMatch(pStack)) {
                return true;
            }
        }
        return false;
    }

    protected boolean anyMatch(PositionedStack pStack) {
        if (pStack == null) {
            return false;
        }

        for (ItemStack stack : pStack.items) {
            if (filter.matches(stack)) {
                return true;
            }
        }

        return false;
    }

}
