package codechicken.nei;

import java.awt.Point;
import java.util.ArrayList;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.api.GuiInfo;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;

public abstract class PanelWidget<T extends ItemsGrid<? extends ItemsGridSlot, ? extends ItemsGrid.MouseContext>>
        extends Widget implements IContainerTooltipHandler {

    protected static final int PADDING = 2;

    public ItemStack draggedStack;
    public int mouseDownSlot = -1;

    public Button pagePrev;
    public Button pageNext;
    public Label pageLabel;

    protected T grid;

    public ArrayList<ItemStack> getItems() {
        return this.grid.getItems();
    }

    public T getGrid() {
        return this.grid;
    }

    public void init() {
        pageLabel = new Label("0/0", true);

        pagePrev = new Button("<") {

            public boolean onButtonPress(boolean rightclick) {

                if (rightclick) {
                    grid.setPage(0);
                } else {
                    grid.shiftPage(-1);
                }

                return true;
            }

            @Override
            public void draw(int mousex, int mousey) {
                this.state = grid.getNumPages() <= 1 ? 2 : 0;
                super.draw(mousex, mousey);
            }
        };
        pageNext = new Button(">") {

            public boolean onButtonPress(boolean rightclick) {

                if (rightclick) {
                    grid.setPage(grid.getNumPages() - 1);
                } else {
                    grid.shiftPage(1);
                }

                return true;
            }

            @Override
            public void draw(int mousex, int mousey) {
                this.state = grid.getNumPages() <= 1 ? 2 : 0;
                super.draw(mousex, mousey);
            }
        };
    }

    public abstract String getLabelText();

    public abstract Rectangle4i calculateBounds();

    public void resize(GuiContainer gui) {
        final Rectangle4i bounds = calculateBounds();

        x = bounds.x;
        y = bounds.y;
        w = bounds.w;
        h = bounds.h;

        final int header = resizeHeader(gui);
        final int footer = resizeFooter(gui);

        this.grid.setGridSize(x, y + header, w, h - header - footer);
        this.grid.refresh(gui);
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
        if (this.grid.getPerPage() > 0 && !this.grid.isEmpty()) {
            LayoutManager.addWidget(pagePrev);
            LayoutManager.addWidget(pageNext);
            LayoutManager.addWidget(pageLabel);
        }
    }

    @Override
    public void update() {
        this.grid.update();
    }

    @Override
    public void draw(int mousex, int mousey) {
        this.grid.draw(mousex, mousey);
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
    public void mouseDragged(int mousex, int mousey, int button, long heldTime) {
        if (this.grid.getSlotBySlotIndex(mouseDownSlot) == null) {
            mouseDownSlot = -1;
        }

        if (mouseDownSlot >= 0 && draggedStack == null
                && NEIClientUtils.getHeldItem() == null
                && NEIClientConfig.hasSMPCounterPart()) {
            ItemsGridSlot mouseOverSlot = this.grid.getSlotMouseOver(mousex, mousey);

            if (mouseOverSlot == null || mouseOverSlot.slotIndex != mouseDownSlot || heldTime > 500) {
                draggedStack = getDraggedStackWithQuantity(this.grid.getSlotBySlotIndex(mouseDownSlot).getItemStack());
                mouseDownSlot = -1;
            }
        }
    }

    protected abstract ItemStack getDraggedStackWithQuantity(ItemStack itemStack);

    @Override
    public boolean handleClick(int mousex, int mousey, int button) {
        if (handleClickExt(mousex, mousey, button)) return true;

        ItemsGridSlot hoverSlot = this.grid.getSlotMouseOver(mousex, mousey);
        if (hoverSlot != null) {

            if (button == 2) {
                draggedStack = getDraggedStackWithQuantity(hoverSlot.getItemStack());
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
            ItemPanels.bookmarkPanel.addItem(ItemPanels.itemPanel.draggedStack);
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
        final ItemsGridSlot hoverSlot = this.grid.getSlotMouseOver(mousex, mousey);

        if (hoverSlot != null && hoverSlot.slotIndex == mouseDownSlot && draggedStack == null) {

            if (NEIController.manager.window instanceof GuiRecipe || NEIClientUtils.shiftKey()
                    || !NEIClientConfig.canCheatItem(hoverSlot.getItemStack())) {

                if (button == 0) {
                    GuiCraftingRecipe.openRecipeGui("item", hoverSlot.getItemStack().copy());
                } else if (button == 1) {
                    GuiUsageRecipe.openRecipeGui("item", hoverSlot.getItemStack().copy());
                }

            } else {
                NEIClientUtils.cheatItem(getDraggedStackWithQuantity(hoverSlot.getItemStack()), button, -1);
            }

        }

        mouseDownSlot = -1;
    }

    @Override
    public boolean onMouseWheel(int i, int mousex, int mousey) {
        if (!contains(mousex, mousey)) return false;

        this.grid.shiftPage(-i);
        return true;
    }

    @Override
    public boolean handleKeyPress(int keyID, char keyChar) {
        final Point mouse = GuiDraw.getMousePosition();
        if (!contains(mouse.x, mouse.y)) return false;

        if (NEIClientConfig.isKeyHashDown("gui.next")) {
            this.grid.shiftPage(1);
            return true;
        }

        if (NEIClientConfig.isKeyHashDown("gui.prev")) {
            this.grid.shiftPage(-1);
            return true;
        }

        return false;
    }

    @Override
    public ItemStack getStackMouseOver(int mousex, int mousey) {
        final ItemsGridSlot slot = this.grid.getSlotMouseOver(mousex, mousey);
        return slot == null ? null : slot.getItemStack();
    }

    public ItemStack getStackMouseOverWithQuantity(int mousex, int mousey) {
        final ItemStack hoverSlot = getStackMouseOver(mousex, mousey);
        return hoverSlot != null ? getDraggedStackWithQuantity(hoverSlot) : null;
    }

    abstract ItemsGridSlot getSlotMouseOver(int mousex, int mousey);

    public int getPage() {
        return this.grid.getPage();
    }

    public int getNumPages() {
        return this.grid.getNumPages();
    }

    @Override
    public boolean contains(int px, int py) {
        return this.grid.contains(px, py);
    }

}
