package codechicken.nei;

import static codechicken.lib.gui.GuiDraw.drawRect;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import codechicken.nei.ItemPanel.ItemPanelSlot;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.StackInfo;

public class ItemHistoryPanel extends Widget {

    protected int mouseDownSlot = -1;
    protected ItemsGrid grid;

    public ItemHistoryPanel() {
        grid = new ItemsGrid();
    }

    public void draw(int mousex, int mousey) {

        if (NEIClientConfig.getIntSetting("inventory.history.splittingMode") == 0) {
            drawRect(x, y, w, h, NEIClientConfig.getSetting("inventory.history.historyColor").getHexValue());
        } else {
            drawSplittingArea(x, y, w, h, NEIClientConfig.getSetting("inventory.history.historyColor").getHexValue());
        }

        grid.draw(mousex, mousey);
    }

    public void update() {
        grid.update();
    }

    public void addItem(ItemStack stack) {
        if (stack != null) {
            ItemStack is = StackInfo.loadFromNBT(StackInfo.itemStackToNBT(stack), 0);

            grid.realItems.removeIf(historyStack -> StackInfo.equalItemAndNBT(historyStack, stack, true));
            grid.realItems.add(0, is);

            if (grid.realItems.size() > (grid.rows * grid.columns)) {
                grid.realItems.remove(grid.rows * grid.columns);
            }

            grid.onItemsChanged();
        }
    }

    public void resize(GuiContainer gui) {
        grid.setGridSize(x, y, w, h);
        grid.refresh(gui);
    }

    @Override
    public ItemStack getStackMouseOver(int mousex, int mousey) {
        ItemPanelSlot slot = getSlotMouseOver(mousex, mousey);
        return slot == null ? null : slot.item;
    }

    public ItemPanelSlot getSlotMouseOver(int mousex, int mousey) {
        return grid.getSlotMouseOver(mousex, mousey);
    }

    private void drawSplittingArea(int x, int y, int width, int height, int color) {

        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;

        GL11.glPushMatrix();

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_LINE_STIPPLE);
        GL11.glColor4f(red, green, blue, alpha);
        GL11.glLineWidth(2F);
        GL11.glLineStipple(2, (short) 0x00FF);

        GL11.glBegin(GL11.GL_LINE_LOOP);

        GL11.glVertex2i(x, y);
        GL11.glVertex2i(x + width, y);
        GL11.glVertex2i(x + width, y + height);
        GL11.glVertex2i(x, y + height);

        GL11.glEnd();

        GL11.glLineStipple(1, (short) 0xFFFF);
        GL11.glLineWidth(1F);
        GL11.glDisable(GL11.GL_LINE_STIPPLE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1F, 1F, 1F, 1F);

        GL11.glPopMatrix();

    }

    @Override
    public void mouseDragged(int mousex, int mousey, int button, long heldTime) {

        if (mouseDownSlot >= 0 && ItemPanels.itemPanel.draggedStack == null
                && NEIClientUtils.getHeldItem() == null
                && NEIClientConfig.hasSMPCounterPart()) {

            ItemPanelSlot mouseOverSlot = getSlotMouseOver(mousex, mousey);

            if (mouseOverSlot == null || mouseOverSlot.slotIndex != mouseDownSlot || heldTime > 500) {
                ItemPanels.itemPanel.draggedStack = getDraggedStackWithQuantity(mouseDownSlot);
                mouseDownSlot = -1;
            }
        }

    }

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
                    ItemPanels.itemPanel.draggedStack = getDraggedStackWithQuantity(hoverSlot.slotIndex);
                }

            } else {
                mouseDownSlot = hoverSlot.slotIndex;
            }

            return true;
        }

        return false;
    }

    @Override
    public void mouseUp(int mousex, int mousey, int button) {
        ItemPanelSlot hoverSlot = getSlotMouseOver(mousex, mousey);

        if (hoverSlot != null && hoverSlot.slotIndex == mouseDownSlot && ItemPanels.itemPanel.draggedStack == null) {
            ItemStack item = hoverSlot.item.copy();

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

}
