package codechicken.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemPanel.ItemPanelSlot;
import codechicken.nei.api.GuiInfo;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;

public abstract class PanelWidget extends Widget {

    protected static final int PADDING = 2;

    public ItemStack draggedStack;
    public int mouseDownSlot = -1;

    public Button pagePrev;
    public Button pageNext;
    public Label pageLabel;

    protected ItemsGrid grid;

    public ArrayList<ItemStack> getItems() {
        return grid.getItems();
    }

    public ItemsGrid getGrid() {
        return grid;
    }

    public void init() {
        pageLabel = new Label("0/0", true);

        pagePrev = new Button("Prev") {

            public boolean onButtonPress(boolean rightclick) {
                if (!rightclick) {
                    grid.shiftPage(-1);
                    return true;
                }
                return false;
            }

            @Override
            public String getRenderLabel() {
                return "<";
            }
        };
        pageNext = new Button("Next") {

            public boolean onButtonPress(boolean rightclick) {
                if (!rightclick) {
                    grid.shiftPage(1);
                    return true;
                }
                return false;
            }

            @Override
            public String getRenderLabel() {
                return ">";
            }
        };
    }

    public abstract String getLabelText();

    protected abstract String getPositioningSettingName();

    public abstract int getMarginLeft(GuiContainer gui);

    public abstract int getMarginTop(GuiContainer gui);

    public abstract int getWidth(GuiContainer gui);

    public abstract int getHeight(GuiContainer gui);

    public void resize(GuiContainer gui) {
        final Rectangle4i margin = new Rectangle4i(
                getMarginLeft(gui),
                getMarginTop(gui),
                getWidth(gui),
                getHeight(gui));

        final int minWidth = 5 * ItemsGrid.SLOT_SIZE;
        final int minHeight = 9 * ItemsGrid.SLOT_SIZE;

        final String settingName = getPositioningSettingName();
        int paddingLeft = (int) Math
                .ceil(margin.w * NEIClientConfig.getSetting(settingName + ".left").getIntValue() / 100000.0);
        int paddingTop = (int) Math
                .ceil(margin.h * NEIClientConfig.getSetting(settingName + ".top").getIntValue() / 100000.0);
        int paddingRight = (int) Math
                .ceil(margin.w * NEIClientConfig.getSetting(settingName + ".right").getIntValue() / 100000.0);
        int paddingBottom = (int) Math
                .ceil(margin.h * NEIClientConfig.getSetting(settingName + ".bottom").getIntValue() / 100000.0);

        int deltaHeight = Math.min(0, margin.h - paddingTop - paddingBottom - minHeight) / 2;

        paddingLeft = Math.min(paddingLeft, Math.max(0, margin.w - paddingRight - minWidth));
        paddingRight = Math.min(paddingRight, Math.max(0, margin.w - paddingLeft - minWidth));
        paddingTop = Math.min(margin.h - minHeight, Math.max(0, paddingTop + deltaHeight));
        paddingBottom = Math.min(margin.h - paddingTop - minHeight, Math.max(0, paddingBottom - deltaHeight));

        x = margin.x + paddingLeft;
        y = margin.y + paddingTop;
        w = margin.w - paddingLeft - paddingRight;
        h = margin.h - paddingTop - paddingBottom;

        final int header = resizeHeader(gui);
        final int footer = resizeFooter(gui);

        grid.setGridSize(x, y + header, w, h - header - footer);
        grid.refresh(gui);
    }

    protected int resizeHeader(GuiContainer gui) {
        final int BUTTON_SIZE = 16;
        int paddingLeft = 0;
        int paddingRight = 0;

        while (paddingLeft < w && GuiInfo.hideItemPanelSlot(
                gui,
                new Rectangle4i(x + paddingLeft, y, ItemsGrid.SLOT_SIZE, ItemsGrid.SLOT_SIZE))) {
            paddingLeft += ItemsGrid.SLOT_SIZE;
        }

        while (paddingRight < w && GuiInfo.hideItemPanelSlot(
                gui,
                new Rectangle4i(
                        x + w - paddingRight - ItemsGrid.SLOT_SIZE,
                        y,
                        ItemsGrid.SLOT_SIZE,
                        ItemsGrid.SLOT_SIZE))) {
            paddingRight += ItemsGrid.SLOT_SIZE;
        }

        if (paddingLeft + paddingRight >= w) {
            return 0;
        }

        pagePrev.w = pageNext.w = BUTTON_SIZE;
        pagePrev.h = pageNext.h = BUTTON_SIZE;
        pagePrev.y = pageNext.y = y;

        pagePrev.x = x + paddingLeft;
        pageNext.x = x + w - pageNext.w - paddingRight;

        pageLabel.x = x + paddingLeft + (w - paddingLeft - paddingRight) / 2;
        pageLabel.y = y + 5;
        pageLabel.text = getLabelText();

        return BUTTON_SIZE + 2;
    }

    protected abstract int resizeFooter(GuiContainer gui);

    public void setVisible() {
        if (grid.getPerPage() > 0) {
            LayoutManager.addWidget(pagePrev);
            LayoutManager.addWidget(pageNext);
            LayoutManager.addWidget(pageLabel);
            grid.setVisible();
        }
    }

    @Override
    public void update() {
        grid.update();
    }

    @Override
    public void draw(int mousex, int mousey) {
        grid.draw(mousex, mousey);
    }

    @Override
    public void postDraw(int mousex, int mousey) {
        if (draggedStack != null) {
            GuiContainerManager.drawItems.zLevel += 100;
            GuiContainerManager.drawItem(mousex - 8, mousey - 8, draggedStack);
            GuiContainerManager.drawItems.zLevel -= 100;
        }
    }

    @Override
    public void postDrawTooltips(int mx, int my, List<String> tooltip) {
        grid.postDrawTooltips(mx, my, tooltip);
    }

    @Override
    public void mouseDragged(int mousex, int mousey, int button, long heldTime) {
        if (mouseDownSlot >= 0 && draggedStack == null
                && NEIClientUtils.getHeldItem() == null
                && NEIClientConfig.hasSMPCounterPart()) {
            ItemPanelSlot mouseOverSlot = getSlotMouseOver(mousex, mousey);

            if (mouseOverSlot == null || mouseOverSlot.slotIndex != mouseDownSlot || heldTime > 500) {
                draggedStack = getDraggedStackWithQuantity(mouseDownSlot);
                mouseDownSlot = -1;
            }
        }
    }

    protected abstract ItemStack getDraggedStackWithQuantity(int mouseDownSlot);

    @Override
    public boolean handleClick(int mousex, int mousey, int button) {

        if (handleClickExt(mousex, mousey, button)) return true;

        if (NEIClientUtils.getHeldItem() != null) {

            if (!grid.contains(mousex, mousey)) {
                return false;
            }

            if (NEIClientConfig.canPerformAction("delete") && NEIClientConfig.canPerformAction("item")) {
                if (button == 1) {
                    NEIClientUtils.decreaseSlotStack(-999);
                } else {
                    NEIClientUtils.deleteHeldItem();
                }
            } else {
                NEIClientUtils.dropHeldItem();
            }

            return true;
        }

        ItemPanelSlot hoverSlot = getSlotMouseOver(mousex, mousey);
        if (hoverSlot != null) {

            if (button == 2) {

                if (hoverSlot.item != null) {
                    draggedStack = getDraggedStackWithQuantity(hoverSlot.slotIndex);
                }

            } else {
                mouseDownSlot = hoverSlot.slotIndex;
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean handleClickExt(int mouseX, int mouseY, int button) {

        if (ItemPanels.itemPanel.draggedStack != null && ItemPanels.bookmarkPanel.contains(mouseX, mouseY)) {
            ItemPanels.bookmarkPanel.addOrRemoveItem(ItemPanels.itemPanel.draggedStack, null, null, false, true);
            ItemPanels.itemPanel.draggedStack = null;
            return true;
        }

        if (ItemPanels.itemPanel.draggedStack != null) {
            return ItemPanels.itemPanel.handleDraggedClick(mouseX, mouseY, button);
        }

        if (ItemPanels.bookmarkPanel.draggedStack != null) {
            return ItemPanels.bookmarkPanel.handleDraggedClick(mouseX, mouseY, button);
        }

        if (NEIClientUtils.getHeldItem() != null) {
            final ItemStack draggedStack = NEIClientUtils.getHeldItem().copy();
            return handleGUIContainerClick(draggedStack, mouseX, mouseY, button);
        }

        return false;
    }

    protected boolean handleDraggedClick(int mouseX, int mouseY, int button) {
        if (draggedStack == null) {
            return false;
        }

        if (handleGUIContainerClick(draggedStack, mouseX, mouseY, button)) {

            if (draggedStack.stackSize == 0) {
                draggedStack = null;
            }

            return true;
        }

        final GuiContainer gui = NEIClientUtils.getGuiContainer();
        if (mouseX < gui.guiLeft || mouseY < gui.guiTop
                || mouseX >= gui.guiLeft + gui.xSize
                || mouseY >= gui.guiTop + gui.ySize) {
            draggedStack = null;
        }

        return true;
    }

    protected boolean handleGUIContainerClick(final ItemStack draggedStack, int mouseX, int mouseY, int button) {
        final GuiContainer gui = NEIClientUtils.getGuiContainer();
        boolean handled = false;

        try {
            GuiInfo.readLock.lock();
            for (INEIGuiHandler handler : GuiInfo.guiHandlers) {
                if (handler.handleDragNDrop(gui, mouseX, mouseY, draggedStack, button)) {
                    handled = true;
                    break;
                }
            }
        } finally {
            GuiInfo.readLock.unlock();
        }

        return handled;
    }

    @Override
    public void mouseUp(int mousex, int mousey, int button) {
        ItemPanelSlot hoverSlot = getSlotMouseOver(mousex, mousey);

        if (hoverSlot != null && hoverSlot.slotIndex == mouseDownSlot && draggedStack == null) {
            ItemStack item = hoverSlot.item;

            if (NEIController.manager.window instanceof GuiRecipe || !NEIClientConfig.canCheatItem(item)) {

                if (button == 0) {
                    GuiCraftingRecipe.openRecipeGui("item", item);
                } else if (button == 1) {
                    GuiUsageRecipe.openRecipeGui("item", item);
                }

                mouseDownSlot = -1;
                return;
            }

            NEIClientUtils.cheatItem(getDraggedStackWithQuantity(hoverSlot.slotIndex), button, -1);
        }

        mouseDownSlot = -1;
    }

    @Override
    public boolean onMouseWheel(int i, int mousex, int mousey) {
        if (!contains(mousex, mousey)) return false;

        grid.shiftPage(-i);
        return true;
    }

    @Override
    public boolean handleKeyPress(int keyID, char keyChar) {

        if (NEIClientConfig.isKeyHashDown("gui.next")) {
            grid.shiftPage(1);
            return true;
        }

        if (NEIClientConfig.isKeyHashDown("gui.prev")) {
            grid.shiftPage(-1);
            return true;
        }

        return false;
    }

    @Override
    public ItemStack getStackMouseOver(int mousex, int mousey) {
        ItemPanelSlot slot = getSlotMouseOver(mousex, mousey);
        return slot == null ? null : slot.item;
    }

    public ItemPanelSlot getSlotMouseOver(int mousex, int mousey) {
        return grid.getSlotMouseOver(mousex, mousey);
    }

    public int getPage() {
        return grid.getPage();
    }

    public int getNumPages() {
        return grid.getNumPages();
    }

    @Override
    public boolean contains(int px, int py) {
        return grid.contains(px, py);
    }
}
