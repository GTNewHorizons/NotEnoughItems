package codechicken.nei;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.api.GuiInfo;
import codechicken.nei.api.ShortcutInputHandler;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.recipe.GuiRecipe;

public abstract class AbstractSubpanel<T extends ItemsGrid<? extends ItemsGridSlot, ? extends ItemsGrid.MouseContext>>
        extends Widget implements IContainerTooltipHandler {

    final DrawableResource DASH = new DrawableBuilder("nei:textures/dash.png", 0, 0, 6, 1).setTextureSize(6, 1).build();

    protected int mouseDownItemIndex = -1;
    protected int splittingLineColor = 0xffffffff;
    protected int linePaddingStart = 0;
    protected T grid;

    public boolean isEmpty() {
        return this.grid.isEmpty();
    }

    public void draw(int mousex, int mousey) {
        drawSplittingLine();
        this.grid.draw(mousex, mousey);
    }

    public void update() {
        this.grid.update();
    }

    public abstract int setPanelWidth(int width);

    public void resize(GuiContainer gui) {
        this.grid.setGridSize(x, y + 4, w, h - 8);
        this.grid.refresh(gui);
    }

    @Override
    public ItemStack getStackMouseOver(int mousex, int mousey) {
        final ItemsGridSlot slot = getSlotMouseOver(mousex, mousey);
        return slot == null ? null : slot.getItemStack();
    }

    public ItemsGridSlot getSlotMouseOver(int mousex, int mousey) {
        return this.grid.getSlotMouseOver(mousex, mousey);
    }

    protected void updateLinePadding() {
        GuiContainer guiContainer = NEIClientUtils.getGuiContainer();
        this.linePaddingStart = 0;

        while (GuiInfo
                .hideItemPanelSlot(guiContainer, new Rectangle4i(this.x + this.linePaddingStart, this.y - 1, 10, 2))) {
            this.linePaddingStart += 10;
        }

    }

    protected void drawSplittingLine() {
        float alpha = (this.splittingLineColor >> 24 & 255) / 255.0F;
        float red = (this.splittingLineColor >> 16 & 255) / 255.0F;
        float green = (this.splittingLineColor >> 8 & 255) / 255.0F;
        float blue = (this.splittingLineColor & 255) / 255.0F;

        int width = this.grid.columns * ItemsGrid.SLOT_SIZE - this.linePaddingStart;
        int repeat = width / 6;
        int shiftX = this.x + this.linePaddingStart + (this.w - width) / 2;

        GL11.glColor4f(red, green, blue, alpha);
        for (int i = 0; i < repeat; i++) {
            DASH.draw(shiftX + i * 6, this.y);
        }
        GL11.glColor4f(1, 1, 1, 1);
    }

    @Override
    public void mouseDragged(int mousex, int mousey, int button, long heldTime) {

        if (this.mouseDownItemIndex >= 0 && ItemPanels.itemPanel.draggedStack == null
                && NEIClientUtils.getHeldItem() == null
                && NEIClientConfig.hasSMPCounterPart()) {

            final ItemsGridSlot mouseOverSlot = getSlotMouseOver(mousex, mousey);

            if (mouseOverSlot == null || mouseOverSlot.itemIndex != this.mouseDownItemIndex || heldTime > 500) {
                ItemPanels.itemPanel.draggedStack = getDraggedStackWithQuantity(
                        this.grid.getItem(this.mouseDownItemIndex));
                this.mouseDownItemIndex = -1;
            }
        }

    }

    @Override
    public boolean handleClick(int mousex, int mousey, int button) {
        if (handleClickExt(mousex, mousey, button)) return true;

        final ItemsGridSlot hoverSlot = this.grid.getSlotMouseOver(mousex, mousey);
        if (hoverSlot != null) {

            if (button == 2) {
                ItemPanels.itemPanel.draggedStack = getDraggedStackWithQuantity(hoverSlot.getItemStack());
            } else {
                mouseDownItemIndex = hoverSlot.itemIndex;
            }

            return true;
        }

        return false;
    }

    protected abstract ItemStack getDraggedStackWithQuantity(ItemStack itemStack);

    public ItemStack getStackMouseOverWithQuantity(int mousex, int mousey) {
        final ItemStack hoverSlot = getStackMouseOver(mousex, mousey);
        return hoverSlot != null ? getDraggedStackWithQuantity(hoverSlot) : null;
    }

    @Override
    public void mouseUp(int mousex, int mousey, int button) {
        final ItemsGridSlot hoverSlot = getSlotMouseOver(mousex, mousey);

        if (hoverSlot != null && hoverSlot.itemIndex == this.mouseDownItemIndex
                && ItemPanels.itemPanel.draggedStack == null) {

            if (NEIController.manager.window instanceof GuiRecipe || NEIClientUtils.shiftKey()
                    || !NEIClientConfig.canCheatItem(hoverSlot.getItemStack())) {
                ShortcutInputHandler.handleMouseClick(hoverSlot.getItemStack().copy());
            } else {
                NEIClientUtils.cheatItem(getDraggedStackWithQuantity(hoverSlot.getItemStack()), button, -1);
            }

        }

        this.mouseDownItemIndex = -1;
    }

}
