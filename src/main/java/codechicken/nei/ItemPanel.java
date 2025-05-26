package codechicken.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.ItemsPanelGrid.ItemsPanelGridSlot;
import codechicken.nei.util.NEIMouseUtils;

public class ItemPanel extends PanelWidget<ItemsPanelGrid> {

    /**
     * Backwards compat :-/
     */
    @SuppressWarnings("unused")
    @Deprecated
    private int itemsPerPage = 0;

    @Deprecated
    public static ArrayList<ItemStack> items = new ArrayList<>();

    @Deprecated
    public ArrayList<ItemStack> realItems = new ArrayList<>();

    public ItemStack getStackMouseOver(int mousex, int mousey) {
        // *looks angrily at AppleCore*
        return super.getStackMouseOver(mousex, mousey);
    }

    public ItemHistoryPanel historyPanel = new ItemHistoryPanel();
    public Button toggleGroups;

    @Deprecated
    public static class ItemPanelSlot extends ItemsPanelGridSlot {

        @Deprecated
        public ItemPanelSlot(int idx, ItemStack item) {
            this(idx, 0, item);
        }

        @Deprecated
        public ItemPanelSlot(int idx) {
            this(idx, 0, ItemPanels.itemPanel.getGrid().getItem(idx));
        }

        private ItemPanelSlot(int slotIndex, int itemIndex, ItemStack item) {
            super(slotIndex, itemIndex, item);
        }

        protected static ItemPanelSlot of(ItemsPanelGridSlot slot) {

            if (slot != null) {
                ItemPanelSlot customSlot = new ItemPanelSlot(slot.slotIndex, slot.itemIndex, slot.item);
                customSlot.groupIndex = slot.groupIndex;
                customSlot.displayName = slot.displayName;
                customSlot.bgItemStack = slot.bgItemStack;
                customSlot.groupSize = slot.groupSize;
                customSlot.extended = slot.extended;
                customSlot.bgColor = slot.bgColor;
                return customSlot;
            }

            return null;
        }
    }

    public ItemPanel() {
        this.grid = new ItemsPanelGrid();
    }

    public static void updateItemList(ArrayList<ItemStack> newItems) {
        ItemPanels.itemPanel.getGrid().setItems(newItems);
        ItemPanels.itemPanel.realItems = newItems;
    }

    @Override
    public void init() {
        super.init();

        toggleGroups = new Button("G") {

            @Override
            public String getButtonTip() {
                return NEIClientUtils.translate("itempanel.collapsed.button.tip");
            }

            @Override
            public boolean onButtonPress(boolean rightclick) {
                CollapsibleItems.toggleGroups(rightclick ? false : null);
                ItemList.updateFilter.restart();
                return true;
            }
        };

    }

    @Override
    public ItemPanelSlot getSlotMouseOver(int mousex, int mousey) {
        // use ItemPanelSlot for Backwards compat
        return ItemPanelSlot.of(this.grid.getSlotMouseOver(mousex, mousey));
    }

    public String getLabelText() {
        return String.format("%d/%d", getPage(), Math.max(1, getNumPages()));
    }

    protected String getPositioningSettingName() {
        return "world.panels.items";
    }

    public int getMarginLeft(GuiContainer gui) {
        return (gui.width + gui.xSize) / 2 + PADDING;
    }

    public int getMarginTop(GuiContainer gui) {
        return PADDING;
    }

    public int getWidth(GuiContainer gui) {
        return gui.width - (gui.xSize + gui.width) / 2 - PADDING * 2;
    }

    public int getHeight(GuiContainer gui) {
        return gui.height - getMarginTop(gui) - PADDING;
    }

    @Override
    protected int resizeHeader(GuiContainer gui) {
        int marginTop = super.resizeHeader(gui);

        toggleGroups.w = pageNext.w;
        toggleGroups.h = pageNext.h;
        toggleGroups.y = pageNext.y;
        toggleGroups.x = pageNext.x - toggleGroups.w - 2;

        return marginTop;
    }

    protected int resizeFooter(GuiContainer gui) {
        if (!NEIClientConfig.showItemQuantityWidget() && NEIClientConfig.isSearchWidgetCentered()
                && !NEIClientConfig.showHistoryPanelWidget()) {
            return 0;
        }

        if (NEIClientConfig.showHistoryPanelWidget()) {
            historyPanel.x = x;
            historyPanel.w = w;
            historyPanel.h = 8 + ItemsGrid.SLOT_SIZE * NEIClientConfig.getIntSetting("inventory.history.useRows");

            if (NEIClientConfig.showItemQuantityWidget() || !NEIClientConfig.isSearchWidgetCentered()) {
                historyPanel.y = LayoutManager.quantity.y - historyPanel.h - PanelWidget.PADDING;
                return LayoutManager.quantity.h + historyPanel.h + PanelWidget.PADDING * 2;
            } else {
                historyPanel.y = y + h - historyPanel.h;
                return historyPanel.h + PanelWidget.PADDING;
            }
        }

        return LayoutManager.quantity.h + PanelWidget.PADDING;
    }

    @Override
    public void setVisible() {
        super.setVisible();

        if (grid.getPerPage() > 0) {
            if (!CollapsibleItems.isEmpty() && !grid.isEmpty()) {
                LayoutManager.addWidget(toggleGroups);
            }

            if (NEIClientConfig.showHistoryPanelWidget() && (!grid.isEmpty() || !historyPanel.isEmpty())) {
                LayoutManager.addWidget(historyPanel);
            }
        }
    }

    @Override
    public void resize(GuiContainer gui) {
        super.resize(gui);
        historyPanel.resize(gui);
    }

    protected ItemStack getDraggedStackWithQuantity(ItemStack itemStack) {
        return ItemQuantityField.prepareStackWithQuantity(itemStack, 0);
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack itemstack, int mousex, int mousey,
            List<String> currenttip) {

        if (!this.grid.forceExpand && !currenttip.isEmpty()) {
            final ItemsPanelGridSlot hoverSlot = this.grid.getSlotMouseOver(mousex, mousey);

            if (hoverSlot != null && hoverSlot.groupIndex >= 0
                    && NEIClientConfig.getBooleanSetting("inventory.collapsibleItems.customName")
                    && !hoverSlot.extended
                    && !"".equals(hoverSlot.displayName)) {
                currenttip.clear();
                currenttip.add(hoverSlot.displayName + GuiDraw.TOOLTIP_LINESPACE);
            }
        }

        return super.handleItemTooltip(gui, itemstack, mousex, mousey, currenttip);
    }

    @Override
    public boolean handleClick(int mousex, int mousey, int button) {

        if (button == 0 && !this.grid.forceExpand && NEIClientUtils.altKey()) {
            final ItemsPanelGridSlot hoverSlot = this.grid.getSlotMouseOver(mousex, mousey);

            if (hoverSlot != null && hoverSlot.groupIndex >= 0) {
                CollapsibleItems.setExpanded(hoverSlot.groupIndex, !hoverSlot.extended);
                this.grid.setItems(this.grid.rawItems);
                NEIClientUtils.playClickSound();
                return true;
            }
        }

        return super.handleClick(mousex, mousey, button);
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {
        final ItemsPanelGridSlot hoverSlot = this.grid.getSlotMouseOver(mousex, mousey);

        if (!this.grid.forceExpand && hoverSlot != null && hoverSlot.groupIndex >= 0 && hoverSlot.groupSize > 1) {
            final String message = hoverSlot.extended ? "itempanel.collapsed.hint.collapse"
                    : "itempanel.collapsed.hint.expand";
            hotkeys.put(
                    NEIClientUtils.getKeyName(NEIClientUtils.ALT_HASH, NEIMouseUtils.MOUSE_BTN_LMB),
                    NEIClientUtils.translate(message, hoverSlot.groupSize));
        }

        return hotkeys;
    }

}
