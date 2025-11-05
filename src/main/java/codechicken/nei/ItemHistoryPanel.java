package codechicken.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.ItemsGrid.MouseContext;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.StackInfo;

public class ItemHistoryPanel extends AbstractSubpanel<ItemsGrid<ItemHistoryPanel.HistoryGridSlot, MouseContext>> {

    public static class HistoryGridSlot extends ItemsGridSlot {

        public HistoryGridSlot(int slotIndex, int itemIndex, ItemStack item) {
            super(slotIndex, itemIndex, item);
        }

        @Override
        public RecipeId getRecipeId() {
            return FavoriteRecipes.getFavorite(this.item);
        }
    }

    public ItemHistoryPanel() {
        this.grid = new ItemsGrid<>() {

            protected List<HistoryGridSlot> gridMask;

            @Override
            protected void onGridChanged() {
                this.gridMask = null;
                super.onGridChanged();
            }

            @Override
            public List<HistoryGridSlot> getMask() {

                if (this.gridMask == null) {
                    this.gridMask = new ArrayList<>();
                    int itemIndex = 0;
                    for (int slotIndex = 0; slotIndex < this.rows * this.columns && itemIndex < size(); slotIndex++) {
                        if (!isInvalidSlot(slotIndex)) {
                            this.gridMask.add(new HistoryGridSlot(slotIndex, itemIndex, getItem(itemIndex)));
                            itemIndex++;
                        }
                    }

                    ItemHistoryPanel.this.updateLinePadding();
                }

                return this.gridMask;
            }

            @Override
            protected MouseContext getMouseContext(int mousex, int mousey) {
                final HistoryGridSlot hovered = getSlotMouseOver(mousex, mousey);

                if (hovered != null) {
                    return new MouseContext(
                            hovered.slotIndex,
                            hovered.slotIndex / this.columns,
                            hovered.slotIndex % this.columns);
                }

                return null;
            }

        };
    }

    @Override
    protected ItemStack getDraggedStackWithQuantity(ItemStack itemStack) {
        return ItemQuantityField.prepareStackWithQuantity(itemStack, 0);
    }

    @Override
    public void draw(int mousex, int mousey) {
        if (this.grid.size() > 0) {
            super.draw(mousex, mousey);
        }
    }

    @Override
    public int setPanelWidth(int width) {
        final int columns = width / ItemsGrid.SLOT_SIZE;
        final int useRows = NEIClientConfig.getIntSetting("inventory.history.useRows");
        final int rows = (int) Math.min(Math.ceil(this.grid.size() * 1f / columns), useRows);

        this.w = width;
        this.h = 8 + ItemsGrid.SLOT_SIZE * Math.max(rows, 1);

        return rows;
    }

    public void update() {
        this.splittingLineColor = NEIClientConfig.getSetting("inventory.history.color").getHexValue();
        super.update();
    }

    public void addItem(ItemStack stack) {
        if (stack != null) {
            ItemStack is = StackInfo.withAmount(stack, 0);

            this.grid.realItems.removeIf(historyStack -> StackInfo.equalItemAndNBT(historyStack, stack, true));
            this.grid.realItems.add(0, is);

            if (this.grid.realItems.size() > Math.max(50, this.grid.rows * this.grid.columns)) {
                this.grid.realItems.remove(this.grid.realItems.size() - 1);
            }

            this.grid.onItemsChanged();
        }
    }

}
