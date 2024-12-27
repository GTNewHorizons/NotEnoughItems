package codechicken.nei;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.guihook.GuiContainerManager;

public class ItemPanel extends PanelWidget {

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

    public ItemHistoryPanel historyPanel;
    public Button toggleGroups;

    public static class ItemPanelSlot {

        public ItemStack item;
        public int slotIndex;

        public ItemPanelSlot(int idx, ItemStack stack) {
            slotIndex = idx;
            item = stack;
        }

        @Deprecated
        public ItemPanelSlot(int idx) {
            this(idx, ItemPanels.itemPanel.getGrid().getItem(idx));
        }
    }

    protected static class MaskMetadata {

        public int groupIndex = -1;
        public String displayName = "";
        public boolean extended = false;

        public Color bgColor = null;
        public Color color = null;

        public boolean left = false;
        public boolean top = false;
        public boolean right = false;
        public boolean bottom = false;

    }

    protected static class ItemPanelGrid extends ItemsGrid {

        public ArrayList<ItemStack> newItems;
        public ArrayList<ItemStack> rawItems;

        protected final HashMap<Integer, List<ItemStack>> groupItems = new HashMap<>();
        protected final HashMap<Integer, MaskMetadata> maskMetadata = new HashMap<>();
        protected boolean forceExpand = false;

        public void setItems(ArrayList<ItemStack> items) {
            newItems = items;
        }

        public void refresh(GuiContainer gui) {

            if (this.newItems != null) {
                this.groupItems.clear();
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
                                this.realItems.add(stack);
                                groups.add(groupIndex);
                            }

                            if (!this.groupItems.containsKey(groupIndex)) {
                                this.groupItems.put(groupIndex, new ArrayList<>());
                            }

                            this.groupItems.get(groupIndex).add(stack);
                        }
                    }

                    // automatically opens if there are elements from only one group
                    if (!outsideGroup && groups.size() == 1) {
                        this.realItems = new ArrayList<>(this.newItems);
                        this.forceExpand = true;
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
        protected List<Integer> getMask() {
            final boolean updateBorders = this.gridMask == null;
            final List<Integer> mask = super.getMask();

            if (updateBorders) {
                calculateGroupBorders(mask);
            }

            return mask;
        }

        protected void calculateGroupBorders(List<Integer> mask) {
            this.maskMetadata.clear();

            if (mask.isEmpty() || this.groupItems.isEmpty()) {
                return;
            }

            final HashMap<Integer, Integer> maskGroup = new HashMap<>();
            final Color collapsedColor = new Color(
                    NEIClientConfig.getSetting("inventory.collapsibleItems.collapsedColor").getHexValue(),
                    true);
            final Color expandedColor = new Color(
                    NEIClientConfig.getSetting("inventory.collapsibleItems.expandedColor").getHexValue(),
                    true);

            for (int slotIndex = 0; slotIndex < mask.size(); slotIndex++) {
                if (mask.get(slotIndex) == null) {
                    continue;
                }

                int idx = mask.get(slotIndex);
                maskGroup.put(idx, CollapsibleItems.getGroupIndex(getItem(idx)));
            }

            for (int slotIndex = 0; slotIndex < mask.size(); slotIndex++) {
                if (mask.get(slotIndex) == null) {
                    continue;
                }

                int idx = mask.get(slotIndex);
                int groupIndex = maskGroup.get(idx);

                if (groupIndex == -1 || this.groupItems.get(groupIndex).size() == 1) {
                    continue;
                }

                int column = slotIndex % this.columns;
                int row = slotIndex / this.columns;
                int prevSlotIndex = (row - 1) * this.columns + column;
                int nextSlotIndex = (row + 1) * this.columns + column;
                MaskMetadata metadata = new MaskMetadata();

                metadata.groupIndex = groupIndex;
                metadata.displayName = CollapsibleItems.getDisplayName(groupIndex);
                metadata.extended = this.forceExpand || CollapsibleItems.isExpanded(groupIndex);
                metadata.bgColor = metadata.extended ? expandedColor : collapsedColor;
                metadata.color = darkerColor(metadata.bgColor);
                metadata.left = column == 0 || idx == 0
                        || mask.get(slotIndex - 1) == null
                        || maskGroup.getOrDefault(idx - 1, -1) != groupIndex;
                metadata.right = column == this.columns - 1 || maskGroup.getOrDefault(idx + 1, -1) != groupIndex;

                if (prevSlotIndex >= 0) {
                    Integer previdx = mask.get(prevSlotIndex);
                    metadata.top = previdx == null || maskGroup.getOrDefault(previdx, -1) != groupIndex;
                } else {
                    metadata.top = true;
                }

                if (nextSlotIndex < mask.size()) {
                    Integer nextidx = mask.get(nextSlotIndex);
                    metadata.bottom = nextidx == null || maskGroup.getOrDefault(nextidx, -1) != groupIndex;
                } else {
                    metadata.bottom = true;
                }

                this.maskMetadata.put(idx, metadata);
            }
        }

        @Override
        protected void beforeDrawItems(int mousex, int mousey, @Nullable ItemPanelSlot focused) {
            final List<Integer> mask = getMask();

            super.beforeDrawItems(mousex, mousey, focused);

            for (int i = 0; i < mask.size(); i++) {
                if (mask.get(i) != null) {
                    drawBorder(mask.get(i), getSlotRect(i));
                }
            }
        }

        protected void drawBorder(int slotIdx, Rectangle4i rect) {
            final MaskMetadata metadata = this.maskMetadata.get(slotIdx);

            if (metadata != null) {
                final float LINE_WIDTH = 0.75f;

                drawRect(rect.x, rect.y, rect.w, rect.h, metadata.bgColor);

                if (metadata.left) {
                    float leftBottom = metadata.bottom ? -LINE_WIDTH : 0;
                    drawRect(rect.x, rect.y, LINE_WIDTH, rect.h + leftBottom, metadata.color);
                }

                if (metadata.right) {
                    float rightTop = metadata.top ? LINE_WIDTH : 0;
                    drawRect(rect.x + rect.w, rect.y - rightTop, LINE_WIDTH, rect.h + rightTop, metadata.color);
                }

                if (metadata.top) {
                    drawRect(rect.x, rect.y - LINE_WIDTH, rect.w, LINE_WIDTH, metadata.color);
                }

                if (metadata.bottom) {
                    drawRect(rect.x, rect.y + rect.h - LINE_WIDTH, rect.w, LINE_WIDTH, metadata.color);
                }
            }
        }

        private static Color darkerColor(Color color) {
            return new Color(
                    color.getRed(),
                    color.getGreen(),
                    color.getBlue(),
                    Math.min((int) (color.getAlpha() + (255f / 5) * 2), 255));
        }

        private static void drawRect(double left, double top, double width, double height, Color color) {
            Tessellator tessellator = Tessellator.instance;

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.glBlendFunc(770, 771, 1, 0);
            GL11.glColor4f(
                    color.getRed() / 255f,
                    color.getGreen() / 255f,
                    color.getBlue() / 255f,
                    color.getAlpha() / 255f);
            tessellator.startDrawingQuads();
            tessellator.addVertex(left, top + height, 0.0D);
            tessellator.addVertex(left + width, top + height, 0.0D);
            tessellator.addVertex(left + width, top, 0.0D);
            tessellator.addVertex(left, top, 0.0D);
            tessellator.draw();
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_BLEND);
        }

        @Override
        protected void drawItem(Rectangle4i rect, int slotIdx) {
            final MaskMetadata metadata = this.maskMetadata.get(slotIdx);

            if (metadata != null && !metadata.extended) {
                final List<ItemStack> groupItems = this.groupItems.get(metadata.groupIndex);

                GuiContainerManager.drawItems.zLevel -= 10F;
                GuiContainerManager.drawItem(rect.x + 1, rect.y - 1, groupItems.get(groupItems.size() - 1), true, "");
                GuiContainerManager.drawItems.zLevel += 10F;

                GuiContainerManager.drawItem(rect.x - 1, rect.y + 1, getItem(slotIdx), true, "");
            } else {
                super.drawItem(rect, slotIdx);
            }
        }

        @Override
        public String getMessageOnEmpty() {
            return ItemList.loadFinished ? null : NEIClientUtils.translate("itempanel.loading");
        }
    }

    public ItemPanel() {
        grid = new ItemPanelGrid();
    }

    public static void updateItemList(ArrayList<ItemStack> newItems) {
        ((ItemPanelGrid) ItemPanels.itemPanel.getGrid()).setItems(newItems);
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

        historyPanel = new ItemHistoryPanel();
    }

    @Deprecated
    public void scroll(int i) {
        grid.shiftPage(i);
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
            historyPanel.h = ItemsGrid.SLOT_SIZE * NEIClientConfig.getIntSetting("inventory.history.useRows");

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

    protected ItemStack getDraggedStackWithQuantity(int mouseDownSlot) {
        ItemStack stack = grid.getItem(mouseDownSlot);

        if (stack != null) {
            int amount = NEIClientConfig.showItemQuantityWidget() ? NEIClientConfig.getItemQuantity() : 0;

            if (amount == 0) {
                amount = stack.getMaxStackSize();
            }

            return NEIServerUtils.copyStack(stack, amount);
        }

        return null;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack itemstack, int mousex, int mousey,
            List<String> currenttip) {
        final ItemPanelGrid panelGrid = ((ItemPanelGrid) this.grid);

        if (!panelGrid.forceExpand && !NEIClientConfig.isHidden() && !currenttip.isEmpty()) {
            final ItemPanelSlot hoverSlot = getSlotMouseOver(mousex, mousey);

            if (hoverSlot != null) {
                final MaskMetadata metadata = panelGrid.maskMetadata.get(hoverSlot.slotIndex);

                if (metadata != null) {
                    final List<ItemStack> items = panelGrid.groupItems.get(metadata.groupIndex);

                    if (NEIClientConfig.getBooleanSetting("inventory.collapsibleItems.customName") && !metadata.extended
                            && !"".equals(metadata.displayName)) {
                        currenttip.clear();
                        currenttip.add(metadata.displayName + GuiDraw.TOOLTIP_LINESPACE);
                    }

                    if (items != null && items.size() > 1) {
                        String message = metadata.extended ? "itempanel.collapsed.hint.collapse"
                                : "itempanel.collapsed.hint.expand";
                        currenttip.add(
                                1,
                                EnumChatFormatting.GRAY + NEIClientUtils.translate(message, items.size())
                                        + EnumChatFormatting.RESET);
                    }
                }
            }
        }

        return super.handleItemTooltip(gui, itemstack, mousex, mousey, currenttip);
    }

    @Override
    public boolean handleClick(int mousex, int mousey, int button) {
        final ItemPanelGrid panelGrid = ((ItemPanelGrid) this.grid);

        if (NEIClientUtils.altKey() && button == 0 && !panelGrid.forceExpand) {
            final ItemPanelSlot hoverSlot = grid.getSlotMouseOver(mousex, mousey);

            if (hoverSlot != null) {
                final MaskMetadata metadata = panelGrid.maskMetadata.get(hoverSlot.slotIndex);

                if (metadata != null) {
                    CollapsibleItems.setExpanded(metadata.groupIndex, !metadata.extended);
                    panelGrid.setItems(panelGrid.rawItems);
                    return true;
                }
            }
        }

        return super.handleClick(mousex, mousey, button);
    }

}
