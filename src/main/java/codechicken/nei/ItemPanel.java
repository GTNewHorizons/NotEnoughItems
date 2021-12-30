package codechicken.nei;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.api.GuiInfo;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;

import static codechicken.lib.gui.GuiDraw.drawRect;
import static codechicken.nei.NEIClientConfig.canPerformAction;

public class ItemPanel extends Widget {
    /**
     *  Backwards compat :-/
     */
    public static ArrayList<ItemStack> items = new ArrayList<>();

    /**
     * Should not be externally modified, use updateItemList
     */
    public ArrayList<ItemStack> realItems = new ArrayList<>();
    /**
     * Swapped into visible items on update
     */
    protected ArrayList<ItemStack> _items = realItems;

    public ArrayList<ItemStack> getItems() {
        return realItems;
    }

    public static void updateItemList(ArrayList<ItemStack> newItems) {
        ItemPanels.itemPanel._items = newItems;
    }

    public class ItemPanelSlot
    {
        public ItemStack item;
        public int slotIndex;

        public ItemPanelSlot(int index) {
            item = realItems.get(index);
            slotIndex = index;
        }
    }

    public ItemStack draggedStack;
    public int mouseDownSlot = -1;

    public Button prev;
    public Button next;
    public Label pageLabel;


    private int marginLeft;
    private int marginTop;
    private int rows;
    private int columns;

    private boolean[] validSlotMap;
    private int firstIndex;
    private int itemsPerPage;

    private int page;
    private int numPages;



    public void init() {
        pageLabel = new Label("(0/0)", true);

        prev = new Button("Prev")
        {
            public boolean onButtonPress(boolean rightclick) {
                if (!rightclick) {
                    scroll(-1);
                    return true;
                }
                return false;
            }

            @Override
            public String getRenderLabel() {
                return "<";
            }
        };
        next = new Button("Next")
        {
            public boolean onButtonPress(boolean rightclick) {
                if (!rightclick) {
                    scroll(1);
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

    public int getX(GuiContainer gui) {
        return (gui.xSize + gui.width) / 2 + 3;
    }

    public int getMarginLeft() {
        return x + (w % 18) / 2;
    }

    public int getMarginTop() {
        return y + (h % 18) / 2;
    }

    public int getButtonTop() {
        return 2;
    }

    public int getNextX(GuiContainer gui) {
        return gui.width - prev.w - 2;
    }

    public int getPrevX(GuiContainer gui) {
        return (gui.xSize + gui.width) / 2 + 2;
    }

    public int getPageX(GuiContainer gui) {
        return gui.guiLeft * 3 / 2 + gui.xSize + 1;
    }

    public int getHeight(GuiContainer gui) {
        return gui.height - 15 - y;
    }

    public int getWidth(GuiContainer gui) {
        return LayoutManager.getSideWidth(gui) - x;
    }

    public String getLabelText() {
        return pageLabel.text = "(" + getPage() + "/" + getNumPages() + ")";
    }

    protected void setItems() {
        realItems = _items;

        // Backwards compat
        ItemPanels.itemPanel._items = _items;
    }

    public void resize(GuiContainer gui) {
        setItems();
        final int buttonHeight = 16;
        final int buttonWidth = 16;

        prev.x = getPrevX(gui);
        prev.y = getButtonTop();
        prev.h = buttonHeight;
        prev.w = buttonWidth;

        next.x = getNextX(gui);
        next.y = getButtonTop();
        next.h = buttonHeight;
        next.w = buttonWidth;

        pageLabel.x = getPageX(gui);
        pageLabel.y = prev.y + 5;
        pageLabel.text = getLabelText();

        y = prev.h + prev.y;
        x = getX(gui);
        w = getWidth(gui);
        h = getHeight(gui);

        marginLeft = getMarginLeft();
        marginTop = getMarginTop();
        columns = w / 18;
        rows = h / 18;
        //sometimes width and height can be negative with certain resizing
        if(rows < 0) rows = 0;
        if(columns < 0) columns = 0;

        calculatePage();
        updateValidSlots();
    }

    private void calculatePage() {
        if (itemsPerPage == 0)
            numPages = 0;
        else
            numPages = (int) Math.ceil((float) realItems.size() / (float) itemsPerPage);

        if (firstIndex >= realItems.size())
            firstIndex = 0;

        if (numPages == 0)
            page = 0;
        else
            page = firstIndex / itemsPerPage + 1;
    }

    public void setVisible() {
        LayoutManager.addWidget(prev);
        LayoutManager.addWidget(next);
        LayoutManager.addWidget(pageLabel);

    }
    private void updateValidSlots() {
        GuiContainer gui = NEIClientUtils.getGuiContainer();
        validSlotMap = new boolean[rows * columns];
        itemsPerPage = 0;
        for (int i = 0; i < validSlotMap.length; i++)
            if (slotValid(gui, i)) {
                validSlotMap[i] = true;
                itemsPerPage++;
            }
    }

    private boolean slotValid(GuiContainer gui, int i) {
        Rectangle4i rect = getSlotRect(i);
        try {
            GuiInfo.readLock.lock();
            if(GuiInfo.guiHandlers.stream().anyMatch(handler -> handler.hideItemPanelSlot(gui, rect.x, rect.y, rect.w, rect.h)))
                return false;
        } finally {
            GuiInfo.readLock.unlock();
        }
        return true;
    }

    public Rectangle4i getSlotRect(int i) {
        return getSlotRect(i / columns, i % columns);
    }

    public Rectangle4i getSlotRect(int row, int column) {
        return new Rectangle4i(marginLeft + column * 18, marginTop + row * 18, 18, 18);
    }

    @Override
    public void draw(int mousex, int mousey) {
        if (itemsPerPage == 0)
            return;

        GuiContainerManager.enableMatrixStackLogging();
        int index = firstIndex;
        for (int i = 0; i < rows * columns && index < realItems.size(); i++) {
            if (validSlotMap[i]) {
                Rectangle4i rect = getSlotRect(i);
                if (rect.contains(mousex, mousey))
                    drawRect(rect.x, rect.y, rect.w, rect.h, 0xee555555);//highlight

                GuiContainerManager.drawItem(rect.x + 1, rect.y + 1, realItems.get(index));

                index++;
            }
        }
        GuiContainerManager.disableMatrixStackLogging();
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
        if (mouseDownSlot >= 0 && draggedStack == null && NEIClientUtils.getHeldItem() == null &&
            NEIClientConfig.hasSMPCounterPart() && !GuiInfo.hasCustomSlots(NEIClientUtils.getGuiContainer())) {
            ItemPanelSlot mouseOverSlot = getSlotMouseOver(mousex, mousey);
            ItemStack stack = new ItemPanelSlot(mouseDownSlot).item;
            if (stack != null && (mouseOverSlot == null || mouseOverSlot.slotIndex != mouseDownSlot || heldTime > 500)) {
                int amount = NEIClientConfig.getItemQuantity();
                if (amount == 0)
                    amount = stack.getMaxStackSize();

                draggedStack = NEIServerUtils.copyStack(stack, amount);
            }
        }
    }

    @Override
    public boolean handleClick(int mousex, int mousey, int button) {
        if (handleDraggedClick(mousex, mousey, button))
            return true;

        if (NEIClientUtils.getHeldItem() != null) {
            try {
                GuiInfo.readLock.lock();
                if (GuiInfo.guiHandlers.stream().anyMatch(handler -> handler.hideItemPanelSlot(NEIClientUtils.getGuiContainer(), mousex, mousey, 1, 1)))
                    return false;
            } finally {
                GuiInfo.readLock.unlock();
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
                ItemStack stack = hoverSlot.item;
                if (stack != null) {
                    int amount = NEIClientConfig.getItemQuantity();
                    if (amount == 0)
                        amount = stack.getMaxStackSize();

                    draggedStack = NEIServerUtils.copyStack(stack, amount);
                }
            } else {
                mouseDownSlot = hoverSlot.slotIndex;
            }
            return true;
        }
        return false;
    }

    private boolean handleDraggedClick(int mousex, int mousey, int button) {
        if (draggedStack == null)
            return false;

        GuiContainer gui = NEIClientUtils.getGuiContainer();
        boolean handled = false;
        try {
            GuiInfo.readLock.lock();
            for (INEIGuiHandler handler : GuiInfo.guiHandlers) {
                if (handler.handleDragNDrop(gui, mousex, mousey, draggedStack, button)) {
                    handled = true;
                    if (draggedStack.stackSize == 0) {
                        draggedStack = null;
                        return true;
                    }
                }
            }
        } finally {
            GuiInfo.readLock.unlock();
        }
        
        if (handled)
            return true;

        Slot overSlot = gui.getSlotAtPosition(mousex, mousey);
        if (overSlot != null && overSlot.isItemValid(draggedStack)) {
            if (NEIClientConfig.canCheatItem(draggedStack)) {
                int contents = overSlot.getHasStack() ? overSlot.getStack().stackSize : 0;
                int add = button == 0 ? draggedStack.stackSize : 1;
                if (overSlot.getHasStack() && !NEIServerUtils.areStacksSameType(draggedStack, overSlot.getStack()))
                    contents = 0;
                int total = Math.min(contents + add, Math.min(overSlot.getSlotStackLimit(), draggedStack.getMaxStackSize()));

                if (total > contents) {
                    NEIClientUtils.setSlotContents(overSlot.slotNumber, NEIServerUtils.copyStack(draggedStack, total), true);
                    NEICPH.sendGiveItem(NEIServerUtils.copyStack(draggedStack, total), false, false);
                    draggedStack.stackSize -= total - contents;
                }
                if (draggedStack.stackSize == 0)
                    draggedStack = null;
            } else {
                draggedStack = null;
            }
        } else if (mousex < gui.guiLeft || mousey < gui.guiTop || mousex >= gui.guiLeft + gui.xSize || mousey >= gui.guiTop + gui.ySize) {
            draggedStack = null;
        }

        return true;
    }

    @Override
    public boolean handleClickExt(int mousex, int mousey, int button) {
        return handleDraggedClick(mousex, mousey, button);
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

            NEIClientUtils.cheatItem(item, button, -1);
        }

        mouseDownSlot = -1;
    }

    @Override
    public boolean onMouseWheel(int i, int mousex, int mousey) {
        if (!contains(mousex, mousey))
            return false;

        scroll(-i);
        return true;
    }

    @Override
    public boolean handleKeyPress(int keyID, char keyChar) {
        if (keyID == NEIClientConfig.getKeyBinding("gui.next")) {
            scroll(1);
            return true;
        }
        if (keyID == NEIClientConfig.getKeyBinding("gui.prev")) {
            scroll(-1);
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
        int index = firstIndex;
        for (int i = 0; i < rows * columns && index < realItems.size(); i++)
            if (validSlotMap[i]) {
                if (getSlotRect(i).contains(mousex, mousey))
                    return new ItemPanelSlot(index);
                index++;
            }

        return null;
    }

    public void scroll(int i) {
        if (itemsPerPage != 0) {
            int oldIndex = firstIndex;
            firstIndex += i * itemsPerPage;
            if (firstIndex >= realItems.size())
                firstIndex = 0;
            if (firstIndex < 0)
                if (oldIndex > 0)
                    firstIndex = 0;
                else
                    firstIndex = (realItems.size() - 1) / itemsPerPage * itemsPerPage;

            calculatePage();
        }
    }

    public int getPage() {
        return page;
    }

    public int getNumPages() {
        return numPages;
    }

    @Override
    public boolean contains(int px, int py) {
        GuiContainer gui = NEIClientUtils.getGuiContainer();
        Rectangle4i rect = new Rectangle4i(px, py, 1, 1);
        try {
            GuiInfo.readLock.lock();
            if(GuiInfo.guiHandlers.stream().anyMatch(handler -> handler.hideItemPanelSlot(gui, rect.x, rect.y, rect.w, rect.h)))
                return false;
        } finally {
            GuiInfo.readLock.unlock();
        }
        return super.contains(px, py);
    }
}
