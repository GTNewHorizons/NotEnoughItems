package codechicken.nei.recipe;

import java.util.Comparator;
import java.util.List;

import net.minecraft.item.ItemStack;

import codechicken.nei.FavoriteRecipes;
import codechicken.nei.ItemsTooltipLineHandler;
import codechicken.nei.NEIClientUtils;

public class AcceptsFollowingTooltipLineHandler extends ItemsTooltipLineHandler {

    protected static final int DEFAULT_MAX_ROWS = 4;

    public Object tooltipGUID;

    protected AcceptsFollowingTooltipLineHandler(Object tooltipGUID, List<ItemStack> items, ItemStack activeStack,
            int maxRows) {
        super(NEIClientUtils.translate("recipe.accepts"), items, false, maxRows);
        this.tooltipGUID = tooltipGUID;
        setActiveStack(activeStack);
        setAmountRenderer(new FavoriteAmountRenderer());
    }

    public static AcceptsFollowingTooltipLineHandler of(Object tooltipGUID, List<ItemStack> items,
            ItemStack activeStack) {
        return of(tooltipGUID, items, activeStack, DEFAULT_MAX_ROWS);
    }

    public static AcceptsFollowingTooltipLineHandler of(Object tooltipGUID, List<ItemStack> items,
            ItemStack activeStack, int maxRows) {

        if (items.size() > 1) {
            items.sort(Comparator.comparing(FavoriteRecipes::containsManual).reversed());
            return new AcceptsFollowingTooltipLineHandler(tooltipGUID, items, activeStack, maxRows);
        }

        return null;
    }

    protected static class FavoriteAmountRenderer extends NoAmountRenderer {

        @Override
        public void draw(int x, int y, ItemStack stack, long amount) {
            super.draw(x, y, stack, amount);

            if (FavoriteRecipes.containsManual(stack)) {
                NEIClientUtils.drawNEIOverlayText("F", x, y);
            }
        }
    }

}
