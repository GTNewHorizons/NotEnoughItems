package codechicken.nei.recipe;

import java.util.Comparator;
import java.util.List;

import net.minecraft.item.ItemStack;

import codechicken.nei.FavoriteRecipes;
import codechicken.nei.ItemsTooltipLineHandler;
import codechicken.nei.NEIClientUtils;

public class AcceptsFollowingTooltipLineHandler extends ItemsTooltipLineHandler {

    protected static final int DEFAULT_MAX_ROWS = 5;

    public Object tooltipGUID;

    public AcceptsFollowingTooltipLineHandler(Object tooltipGUID, List<ItemStack> items, ItemStack activeStack,
            int maxRows) {
        super(NEIClientUtils.translate("recipe.accepts"), items, false, maxRows);
        this.tooltipGUID = tooltipGUID;
        setActiveStack(activeStack);
    }

    public static AcceptsFollowingTooltipLineHandler of(Object tooltipGUID, List<ItemStack> items,
            ItemStack activeStack) {
        return of(tooltipGUID, items, activeStack, DEFAULT_MAX_ROWS);
    }

    public static AcceptsFollowingTooltipLineHandler of(Object tooltipGUID, List<ItemStack> items,
            ItemStack activeStack, int maxRows) {

        if (items.size() > 1) {
            items.sort(Comparator.comparing(FavoriteRecipes::contains).reversed());
            return new AcceptsFollowingTooltipLineHandler(tooltipGUID, items, activeStack, maxRows);
        }

        return null;
    }

    @Override
    protected void drawItem(int x, int y, ItemStack drawStack, String stackSize) {
        super.drawItem(x, y, drawStack, stackSize);

        if (FavoriteRecipes.contains(drawStack)) {
            NEIClientUtils.drawNEIOverlayText("F", x, y);
        }
    }

}
