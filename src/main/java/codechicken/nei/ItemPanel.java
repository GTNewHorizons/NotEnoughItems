package codechicken.nei;

import codechicken.nei.api.GuiInfo;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.ItemsGrid.ItemPanelSlot;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.StackInfo;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import java.util.ArrayList;

public class ItemPanel extends Widget
{

    public ItemStack draggedStack;
    public int mouseDownSlot = -1;

    public Button pagePrev;
    public Button pageNext;
    public Label pageLabel;

    //info fix for OpenComputers
    private int itemsPerPage = 0;

    protected static class ItemPanelGrid extends ItemsGrid
    {
        public ArrayList<ItemStack> newItems;

        public void setItems(ArrayList<ItemStack> items)
        {
            newItems = items;
            needRefresh = true;
        }

        public void refresh(GuiContainer gui)
        {

            if (newItems != null) {
                realItems = newItems;
                newItems = null;
            }

            super.refresh(gui);
        }

    }

    protected ItemsGrid grid = new ItemPanelGrid();

    public ArrayList<ItemStack> getItems()
    {
        return grid.getItems();
    }

    public static void updateItemList(ArrayList<ItemStack> newItems)
    {
        ((ItemPanelGrid) ItemPanels.itemPanel.getGrid()).setItems(newItems);
    }

    public ItemsGrid getGrid()
    {
        return grid;
    }

    public void init() {
        pageLabel = new Label("(0/0)", true);

        pagePrev = new Button("Prev")
        {
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
        pageNext = new Button("Next")
        {
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

    public int getMarginLeft(GuiContainer gui)
    {
        if (gui instanceof GuiContainerCreative && NEIClientUtils.getGamemode() == 2) {
            //then activate creative+ first render creative gui and oly then creative+
            return (gui.width + 176) / 2  + 2;
        }

        return (gui.width + gui.xSize) / 2  + 2;
    }

    public int getMarginTop(GuiContainer gui)
    {
        return 2;
    }

    public int getWidth(GuiContainer gui)
    {
        return LayoutManager.getSideWidth(gui) - getMarginLeft(gui);
    }

    public int getHeight(GuiContainer gui)
    {
        // - 19 = 2 + 16 + 1 -> input in bottom
        return gui.height - getMarginTop(gui) - 19;
    }

    public String getLabelText()
    {
        return "(" + getPage() + "/" + Math.max(1, getNumPages()) + ")";
    }

    public void resize(GuiContainer gui)
    {
        final int marginLeft = getMarginLeft(gui);
        final int marginTop = getMarginTop(gui);
        final int width = getWidth(gui);
        final int height = getHeight(gui);
        final int buttonHeight = 16;
        final int buttonWidth = 16;

        pagePrev.w = pageNext.w = buttonWidth;
        pagePrev.h = pageNext.h = buttonHeight;
        pagePrev.y = pageNext.y = marginTop;

        pagePrev.x = marginLeft;
        pageNext.x = marginLeft + width - buttonWidth;

        pageLabel.x = marginLeft + width / 2;
        pageLabel.y = marginTop + 5;
        pageLabel.text = getLabelText();


        y = marginTop + buttonHeight + 2;
        x = marginLeft;
        w = width;
        h = height - buttonHeight - 2;

        grid.setGridSize(x, y, w, h);
        grid.refresh(gui);

    }

    public void setVisible()
    {
        LayoutManager.addWidget(pagePrev);
        LayoutManager.addWidget(pageNext);
        LayoutManager.addWidget(pageLabel);
    }

    @Override
    public void draw(int mousex, int mousey)
    {
        grid.draw(mousex, mousey);
    }

    @Override
    public void postDraw(int mousex, int mousey) {
        if (draggedStack != null) {
            GuiContainerManager.drawItems.zLevel += 100;
            GuiContainerManager.drawItem(mousex - 8, mousey - 8, draggedStack, true);
            GuiContainerManager.drawItems.zLevel -= 100;
        }
    }

    @Override
    public void mouseDragged(int mousex, int mousey, int button, long heldTime) {
        if (mouseDownSlot >= 0 && draggedStack == null && NEIClientUtils.getHeldItem() == null &&
            NEIClientConfig.hasSMPCounterPart() && !GuiInfo.hasCustomSlots(NEIClientUtils.getGuiContainer())) {
            ItemPanelSlot mouseOverSlot = getSlotMouseOver(mousex, mousey);

            if (mouseOverSlot == null || mouseOverSlot.slotIndex != mouseDownSlot || heldTime > 500) {
                draggedStack = getDraggedStackWithQuantity(mouseDownSlot);
            }

        }
    }

    protected ItemStack getDraggedStackWithQuantity(int mouseDownSlot)
    {
        ItemStack stack = grid.getItem(mouseDownSlot);

        if (stack != null) {
            int amount = NEIClientConfig.getItemQuantity();

            if (amount == 0) {
                amount = stack.getMaxStackSize();
            }

            return NEIServerUtils.copyStack(stack, amount);
        }

        return null;
    }

    @Override
    public boolean handleClick(int mousex, int mousey, int button)
    {

        if (handleClickExt(mousex, mousey, button))
            return true;

        if (NEIClientUtils.getHeldItem() != null) {

            if (!grid.contains(mousex, mousey)) {
                return false;
            }

            if (NEIClientConfig.canPerformAction("delete") && NEIClientConfig.canPerformAction("item"))
                if (button == 1)
                    NEIClientUtils.decreaseSlotStack(-999);
                else
                    NEIClientUtils.deleteHeldItem();
            else
                NEIClientUtils.dropHeldItem();

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
    public boolean handleClickExt(int mouseX, int mouseY, int button)
    {
        if (ItemPanels.itemPanel.draggedStack != null) {
            return ItemPanels.itemPanel.handleDraggedClick(mouseX, mouseY, button);
        }

        if (ItemPanels.bookmarkPanel.draggedStack != null) {
            return ItemPanels.bookmarkPanel.handleDraggedClick(mouseX, mouseY, button);
        }

        return false;
    }

    protected boolean handleDraggedClick(int mouseX, int mouseY, int button)
    {
        if (draggedStack == null) {
            return false;
        }

        if (button == 1 && handleFillFluidContainerClick(mouseX, mouseY)) {
            return true;
        }

        if (handleGUIContainerClick(mouseX, mouseY, button)) {
            return true;
        }

        if (handleCheatItemClick(mouseX, mouseY, button)) {
            return true;
        }

        final GuiContainer gui = NEIClientUtils.getGuiContainer();
        if (mouseX < gui.guiLeft || mouseY < gui.guiTop || mouseX >= gui.guiLeft + gui.xSize || mouseY >= gui.guiTop + gui.ySize) {
            draggedStack = null;
        }

        return true;
    }

    protected boolean handleFillFluidContainerClick(int mouseX, int mouseY)
    {
        ItemPanelSlot mouseOverSlot = ItemPanels.itemPanel.getSlotMouseOver(mouseX, mouseY);

        if (mouseOverSlot == null) {
            mouseOverSlot = ItemPanels.bookmarkPanel.getSlotMouseOver(mouseX, mouseY);
        }

        if (mouseOverSlot != null && draggedStack.getItem() instanceof IFluidContainerItem) {
            FluidStack fluidStack = StackInfo.getFluid(mouseOverSlot.item);

            if (fluidStack != null) {
                final int stackSize = draggedStack.stackSize;

                fluidStack = fluidStack.copy();

                if (fluidStack.amount == 0) {
                    fluidStack.amount = 1000;
                }

                fluidStack.amount *= mouseOverSlot.item.stackSize;

                draggedStack.stackSize = 1;
                ((IFluidContainerItem) draggedStack.getItem()).fill(draggedStack, fluidStack, true);
                draggedStack.stackSize = stackSize;
            }

            return true;
        }

        return false;
    }

    protected boolean handleGUIContainerClick(int mouseX, int mouseY, int button)
    {
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

        if (draggedStack.stackSize == 0) {
            draggedStack = null;
        }

        return handled;
    }

    protected boolean handleCheatItemClick(int mouseX, int mouseY, int button)
    {
        final GuiContainer gui = NEIClientUtils.getGuiContainer();
        final Slot overSlot = gui.getSlotAtPosition(mouseX, mouseY);

        if (overSlot != null && overSlot.isItemValid(draggedStack)) {

            if (NEIClientConfig.canCheatItem(draggedStack)) {
                int contents = overSlot.getHasStack() ? overSlot.getStack().stackSize : 0;
                final int add = button == 0 ? draggedStack.stackSize : 1;

                if (overSlot.getHasStack() && !NEIServerUtils.areStacksSameType(draggedStack, overSlot.getStack())) {
                    contents = 0;
                }        

                final int total = Math.min(contents + add, Math.min(overSlot.getSlotStackLimit(), draggedStack.getMaxStackSize()));

                if (total > contents) {
                    NEIClientUtils.setSlotContents(overSlot.slotNumber, NEIServerUtils.copyStack(draggedStack, total), true);
                    NEICPH.sendGiveItem(NEIServerUtils.copyStack(draggedStack, total), false, false);
                    draggedStack.stackSize -= total - contents;
                }

                if (draggedStack.stackSize == 0) {
                    draggedStack = null;
                }

            } else {
                draggedStack = null;
            }

            return true;
        }

        return false;
    }

    @Override
    public void mouseUp(int mousex, int mousey, int button) {
        ItemPanelSlot hoverSlot = getSlotMouseOver(mousex, mousey);

        if (hoverSlot != null && hoverSlot.slotIndex == mouseDownSlot && draggedStack == null) {
            ItemStack item = hoverSlot.item;

            if (NEIController.manager.window instanceof GuiRecipe || !NEIClientConfig.canCheatItem(item)) {

                if (button == 0)
                    GuiCraftingRecipe.openRecipeGui("item", item);
                else if (button == 1)
                    GuiUsageRecipe.openRecipeGui("item", item);

                draggedStack = null;
                mouseDownSlot = -1;
                return;
            }

            NEIClientUtils.cheatItem(getDraggedStackWithQuantity(hoverSlot.slotIndex), button, -1);
        }

        mouseDownSlot = -1;
    }

    @Override
    public boolean onMouseWheel(int i, int mousex, int mousey) {
        if (!contains(mousex, mousey))
            return false;

        grid.shiftPage(-i);
        return true;
    }

    @Override
    public boolean handleKeyPress(int keyID, char keyChar) {

        if (keyID == NEIClientConfig.getKeyBinding("gui.next")) {
            grid.shiftPage(1);
            return true;
        }

        if (keyID == NEIClientConfig.getKeyBinding("gui.prev")) {
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

    public ItemPanelSlot getSlotMouseOver(int mousex, int mousey)
    {
        return grid.getSlotMouseOver(mousex, mousey);
    }

    public int getPage() {
        return grid.page + 1;
    }

    public int getNumPages() {
        return grid.numPages;
    }

    @Override
    public boolean contains(int px, int py)
    {
        return grid.contains(px, py);
    }

}
