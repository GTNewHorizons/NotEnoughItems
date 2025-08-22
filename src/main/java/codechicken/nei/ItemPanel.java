package codechicken.nei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
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
    public ItemCraftablesPanel craftablesPanel = new ItemCraftablesPanel();
    protected Button toggleGroups;

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
            public String getRenderLabel() {
                return NEIClientUtils.translate("itempanel.collapsed.button");
            }

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

    public Rectangle4i calculateBounds() {
        final GuiContainer gui = NEIClientUtils.getGuiContainer();
        final int width = (gui.width - gui.xSize) / 2 - PADDING * 2;
        final Rectangle4i bounds = new Rectangle4i(
                (gui.width + gui.xSize) / 2 + PADDING,
                PADDING,
                (gui.width - 176) / 2 - PADDING * 2,
                gui.height - PADDING * 2);

        int paddingLeft = (int) Math
                .ceil(bounds.w * NEIClientConfig.getSetting("world.panels.items.left").getIntValue() / 100000.0);
        int paddingTop = (int) Math
                .ceil(bounds.h * NEIClientConfig.getSetting("world.panels.items.top").getIntValue() / 100000.0);
        int paddingRight = (int) Math
                .ceil(bounds.w * NEIClientConfig.getSetting("world.panels.items.right").getIntValue() / 100000.0);
        int paddingBottom = (int) Math
                .ceil(bounds.h * NEIClientConfig.getSetting("world.panels.items.bottom").getIntValue() / 100000.0);

        bounds.h = Math.max(ItemsGrid.SLOT_SIZE, bounds.h - paddingTop - paddingBottom);
        bounds.y = bounds.y + Math.min(paddingTop, bounds.h - ItemsGrid.SLOT_SIZE);

        bounds.w = Math.max(ItemsGrid.SLOT_SIZE, Math.min(bounds.w - paddingLeft - paddingRight, width - paddingRight));
        bounds.x = bounds.x + Math.max(0, width - bounds.w - paddingRight);

        return bounds;
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
        int footerY = y + h;
        int footerHeight = 0;

        if (grid.getPerPage() == 0) {
            return 0;
        }

        if (NEIClientConfig.showItemQuantityWidget() || !NEIClientConfig.isSearchWidgetCentered()) {
            footerY = LayoutManager.quantity.y;
            footerHeight = LayoutManager.quantity.h + PanelWidget.PADDING;
        }

        if (NEIClientConfig.showHistoryPanelWidget()) {
            historyPanel.x = x;

            if (historyPanel.setPanelWidth(w) != 0) {
                footerY = historyPanel.y = footerY - historyPanel.h;
                footerHeight += historyPanel.h;
            }

        }

        if (NEIClientConfig.showCraftablesPanelWidget()) {
            craftablesPanel.x = x;

            if (craftablesPanel.setPanelWidth(w) != 0) {
                footerY = craftablesPanel.y = footerY - craftablesPanel.h;
                footerHeight += craftablesPanel.h;
            }
        }

        return footerHeight;
    }

    @Override
    public void setVisible() {
        super.setVisible();

        if (grid.getPerPage() > 0) {

            if (!CollapsibleItems.isEmpty() && !grid.isEmpty()) {
                LayoutManager.addWidget(toggleGroups);
            }

            if (NEIClientConfig.showCraftablesPanelWidget()) {
                LayoutManager.addWidget(craftablesPanel);
            }

            if (NEIClientConfig.showHistoryPanelWidget()) {
                LayoutManager.addWidget(historyPanel);
            }

        }
    }

    @Override
    public void resize(GuiContainer gui) {
        super.resize(gui);
        this.historyPanel.resize(gui);
        this.craftablesPanel.resize(gui);
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
