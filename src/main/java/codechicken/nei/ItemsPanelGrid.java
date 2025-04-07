package codechicken.nei;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.guihook.GuiContainerManager;

public class ItemsPanelGrid extends ItemsGrid<ItemsPanelGrid.ItemsPanelGridSlot, ItemsPanelGrid.MouseContext> {

    public static class ItemsPanelGridSlot extends ItemsGridSlot {

        protected static final float LINE_WIDTH = 0.75f;

        public int groupIndex = -1;
        public String displayName = "";
        public ItemStack bgItemStack;
        public int groupSize = 1;
        public boolean extended = false;

        public Color bgColor = null;

        public ItemsPanelGridSlot(int slotIndex, int itemIndex, ItemStack itemStack) {
            super(slotIndex, itemIndex, itemStack);
        }

        public ItemStack getBGItem() {
            return this.bgItemStack;
        }

        @Override
        public void beforeDraw(Rectangle4i rect, MouseContext mouseContext) {
            super.beforeDraw(rect, mouseContext);

            if (this.bgColor != null) {
                NEIClientUtils.drawRect(rect.x, rect.y, rect.w, rect.h, this.bgColor);
            }

        }

        @Override
        public void drawItem(Rectangle4i rect) {

            if (!this.extended && getBGItem() != null) {
                GuiContainerManager.drawItems.zLevel -= 10F;
                super.drawItem(getBGItem(), rect.offset(1, -1));
                GuiContainerManager.drawItems.zLevel += 10F;
                super.drawItem(getItemStack(), rect.offset(-2, 2));
            } else {
                super.drawItem(rect);
            }
        }

    }

    public ArrayList<ItemStack> newItems;
    public ArrayList<ItemStack> rawItems;
    protected List<ItemsPanelGridSlot> gridMask;

    protected final Map<Integer, Integer> itemToGroup = new HashMap<>();
    protected final Map<Integer, List<ItemStack>> groupItems = new HashMap<>();
    protected boolean forceExpand = false;

    public void setItems(ArrayList<ItemStack> items) {
        newItems = items;
    }

    public static void updateScale() {
        SLOT_SIZE = 2 + (int) Math.ceil(8f * NEIClientConfig.getIntSetting("inventory.itempanelScale") / 100) * 2;
    }

    @Override
    public void refresh(GuiContainer gui) {

        if (this.newItems != null) {
            this.groupItems.clear();
            this.itemToGroup.clear();
            this.forceExpand = false;

            if (!CollapsibleItems.isEmpty() && !this.newItems.isEmpty()) {
                final Set<Integer> groups = new HashSet<>();
                boolean outsideGroup = false;

                this.realItems = new ArrayList<>();
                this.rawItems = new ArrayList<>(this.newItems);

                for (ItemStack stack : this.newItems) {
                    final int groupIndex = CollapsibleItems.getGroupIndex(stack);

                    if (groupIndex == -1) {
                        this.realItems.add(stack);
                        outsideGroup = true;
                    } else {

                        if (!groups.contains(groupIndex) || CollapsibleItems.isExpanded(groupIndex)) {
                            this.itemToGroup.put(this.realItems.size(), groupIndex);
                            this.realItems.add(stack);
                            groups.add(groupIndex);
                        }

                        this.groupItems.computeIfAbsent(groupIndex, gi -> new ArrayList<>()).add(stack);
                    }
                }

                // automatically opens if there are elements from only one group
                if (!outsideGroup && groups.size() == 1) {
                    final int groupIndex = groups.iterator().next();
                    this.realItems = new ArrayList<>(this.newItems);
                    this.itemToGroup.clear();
                    this.forceExpand = true;

                    for (int itemIndex = 0; itemIndex < this.realItems.size(); itemIndex++) {
                        this.itemToGroup.put(itemIndex, groupIndex);
                    }
                }
            } else {
                this.realItems = new ArrayList<>(this.newItems);
            }

            this.newItems = null;
            onItemsChanged();
        }

        super.refresh(gui);
    }

    @Override
    protected void onGridChanged() {
        this.gridMask = null;
        super.onGridChanged();
    }

    @Override
    public List<ItemsPanelGridSlot> getMask() {

        if (this.gridMask == null) {
            this.gridMask = new ArrayList<>();
            int itemIndex = page * perPage;

            for (int slotIndex = 0; slotIndex < rows * columns && itemIndex < size(); slotIndex++) {
                if (!isInvalidSlot(slotIndex)) {
                    this.gridMask.add(new ItemsPanelGridSlot(slotIndex, itemIndex, getItem(itemIndex)));
                    itemIndex++;
                }
            }

            calculateGroupBorders(this.gridMask);
        }

        return this.gridMask;
    }

    @Override
    protected MouseContext getMouseContext(int mousex, int mousey) {
        final ItemsPanelGridSlot hovered = getSlotMouseOver(mousex, mousey);

        if (hovered != null) {
            return new MouseContext(
                    hovered.slotIndex,
                    hovered.slotIndex / this.columns,
                    hovered.slotIndex % this.columns);
        }

        return null;
    }

    protected void calculateGroupBorders(List<ItemsPanelGridSlot> gridMask) {

        if (gridMask.isEmpty() || this.groupItems.isEmpty()) {
            return;
        }

        final Color collapsedBGColor = new Color(
                NEIClientConfig.getSetting("inventory.collapsibleItems.collapsedColor").getHexValue(),
                true);
        final Color expandedBGColor = new Color(
                NEIClientConfig.getSetting("inventory.collapsibleItems.expandedColor").getHexValue(),
                true);
        final Color collapsedBorderColor = darkerColor(collapsedBGColor);
        final Color expandedBorderColor = darkerColor(expandedBGColor);
        final Map<Integer, Integer> borderGrid = new HashMap<>();

        for (ItemsPanelGridSlot item : gridMask) {
            borderGrid.put(item.slotIndex, this.itemToGroup.getOrDefault(item.itemIndex, -1));
        }

        for (ItemsPanelGridSlot item : gridMask) {
            final int groupIndex = borderGrid.get(item.slotIndex);
            final List<ItemStack> groupItems = this.groupItems.get(groupIndex);

            if (groupItems == null || groupItems.size() == 1) {
                continue;
            }

            int column = item.slotIndex % this.columns;
            int row = item.slotIndex / this.columns;
            int prevSlotIndex = (row - 1) * this.columns + column;
            int nextSlotIndex = (row + 1) * this.columns + column;

            item.groupIndex = groupIndex;
            item.groupSize = groupItems.size();
            item.bgItemStack = groupItems.get(groupItems.size() - 1);
            item.displayName = CollapsibleItems.getDisplayName(groupIndex);
            item.extended = this.forceExpand || CollapsibleItems.isExpanded(groupIndex);
            item.bgColor = item.extended ? expandedBGColor : collapsedBGColor;

            item.borderColor = item.extended ? expandedBorderColor : collapsedBorderColor;
            item.borderLeft = column == 0 || borderGrid.getOrDefault(item.slotIndex - 1, -1) != groupIndex;
            item.borderRight = column + 1 == this.columns
                    || borderGrid.getOrDefault(item.slotIndex + 1, -1) != groupIndex;
            item.borderTop = borderGrid.getOrDefault(prevSlotIndex, -1) != groupIndex;
            item.borderBottom = borderGrid.getOrDefault(nextSlotIndex, -1) != groupIndex;
        }

    }

    private static Color darkerColor(Color color) {
        return new Color(
                color.getRed(),
                color.getGreen(),
                color.getBlue(),
                Math.min((int) (color.getAlpha() + (255f / 5) * 2), 255));
    }

    @Override
    public String getMessageOnEmpty() {
        return ItemList.loadFinished ? null : NEIClientUtils.translate("itempanel.loading");
    }

}
