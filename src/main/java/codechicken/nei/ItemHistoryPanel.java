package codechicken.nei;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.ItemsGrid.MouseContext;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.RecipeTooltipLineHandler;
import codechicken.nei.recipe.StackInfo;

public class ItemHistoryPanel extends Widget implements IContainerTooltipHandler {

    protected RecipeTooltipLineHandler recipeTooltipLineHandler = null;
    protected int mouseDownItemIndex = -1;

    protected ItemsGrid<ItemsGridSlot, MouseContext> grid = new ItemsGrid<>() {

        protected List<ItemsGridSlot> gridMask;

        @Override
        protected void onGridChanged() {
            this.gridMask = null;
            super.onGridChanged();
        }

        @Override
        public List<ItemsGridSlot> getMask() {

            if (this.gridMask == null) {
                this.gridMask = new ArrayList<>();
                for (int slotIndex = 0; slotIndex < Math.min(size(), this.rows * this.columns); slotIndex++) {
                    this.gridMask.add(new ItemsGridSlot(slotIndex, slotIndex, getItem(slotIndex)));
                }
            }

            return this.gridMask;
        }

        @Override
        protected MouseContext getMouseContext(int mousex, int mousey) {
            final ItemsGridSlot hovered = getSlotMouseOver(mousex, mousey);

            if (hovered != null) {
                return new MouseContext(
                        hovered.slotIndex,
                        hovered.slotIndex / this.columns,
                        hovered.slotIndex % this.columns);
            }

            return null;
        }

    };

    public boolean isEmpty() {
        return grid.isEmpty();
    }

    public void draw(int mousex, int mousey) {

        if (NEIClientConfig.getIntSetting("inventory.history.splittingMode") == 0) {
            GuiDraw.drawRect(x, y, w, h, NEIClientConfig.getSetting("inventory.history.historyColor").getHexValue());
        } else {
            drawSplittingArea(x, y, w, h, NEIClientConfig.getSetting("inventory.history.historyColor").getHexValue());
        }

        this.grid.draw(mousex, mousey);
    }

    public void update() {
        this.grid.update();
    }

    public void addItem(ItemStack stack) {
        if (stack != null) {
            ItemStack is = StackInfo.loadFromNBT(StackInfo.itemStackToNBT(stack), 0);

            this.grid.realItems.removeIf(historyStack -> StackInfo.equalItemAndNBT(historyStack, stack, true));
            this.grid.realItems.add(0, is);

            if (this.grid.realItems.size() > Math.max(50, this.grid.rows * this.grid.columns)) {
                this.grid.realItems.remove(this.grid.realItems.size() - 1);
            }

            this.grid.onItemsChanged();
        }
    }

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
        return grid.getSlotMouseOver(mousex, mousey);
    }

    private void drawSplittingArea(int x, int y, int width, int height, int color) {

        float alpha = (color >> 24 & 255) / 255.0F;
        float red = (color >> 16 & 255) / 255.0F;
        float green = (color >> 8 & 255) / 255.0F;
        float blue = (color & 255) / 255.0F;

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

    protected ItemStack getDraggedStackWithQuantity(ItemStack itemStack) {
        return ItemQuantityField.prepareStackWithQuantity(itemStack, 0);
    }

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

                if (button == 0) {
                    GuiCraftingRecipe.openRecipeGui("item", hoverSlot.getItemStack().copy());
                } else if (button == 1) {
                    GuiUsageRecipe.openRecipeGui("item", hoverSlot.getItemStack().copy());
                }

            } else {
                NEIClientUtils.cheatItem(getDraggedStackWithQuantity(hoverSlot.getItemStack()), button, -1);
            }

        }

        this.mouseDownItemIndex = -1;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack itemstack, int mousex, int mousey,
            List<String> currenttip) {

        if (itemstack != null && contains(mousex, mousey) && NEIClientConfig.getFavoriteRecipeTooltips()) {
            final RecipeId recipeId = FavoriteRecipes.getFavorite(itemstack);

            if (recipeId == null) {
                recipeTooltipLineHandler = null;
            } else if (recipeTooltipLineHandler == null || !recipeTooltipLineHandler.getRecipeId().equals(recipeId)) {
                recipeTooltipLineHandler = new RecipeTooltipLineHandler(recipeId);
            }

            if (recipeTooltipLineHandler != null) {
                currenttip.add(GuiDraw.TOOLTIP_HANDLER + GuiDraw.getTipLineId(recipeTooltipLineHandler));
            }
        } else {
            recipeTooltipLineHandler = null;
        }

        return currenttip;
    }

}
