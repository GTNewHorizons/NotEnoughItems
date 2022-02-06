package codechicken.nei;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.api.GuiInfo;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.ItemPanel.ItemPanelSlot;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;

import static codechicken.lib.gui.GuiDraw.drawRect;

public class ItemsGrid
{
    protected static final int SLOT_SIZE = 18;

    protected int width;
    protected int height;

    protected int marginLeft;
    protected int marginTop;

    protected ArrayList<ItemStack> realItems = new ArrayList<>();

    protected int page;
    protected int perPage;

    protected int firstIndex;
    protected int numPages;

    protected int rows;
    protected int columns;

    protected boolean[] validSlotMap;
    protected boolean[] invalidSlotMap;

    public ArrayList<ItemStack> getItems()
    {
        return realItems;
    }

    public ItemStack getItem(int idx)
    {
        return realItems.get(idx);
    }

    public int size()
    {
        return realItems.size();
    }

    public int indexOf(ItemStack stackA, boolean useNBT)
    {

        for (int idx = 0; idx < realItems.size(); idx++) {
            if (StackInfo.equalItemAndNBT(stackA, realItems.get(idx), useNBT)) {
                return idx;
            }
        }

        return -1;
    }

    public int getPage()
    {
        return page + 1;
    }

    public int getPerPage()
    {
        return perPage;
    }

    public int getNumPages()
    {
        return numPages;
    }

    public int getRows()
    {
        return rows;
    }
    
    public int getColumns()
    {
        return columns;
    }

    public void setGridSize(int mleft, int mtop, int w, int h)
    {

        //I don't like this big condition
        if (marginLeft != mleft || marginTop != mtop || width != w || height != h) {

            marginLeft = mleft;
            marginTop = mtop;

            width = Math.max(0, w);
            height = Math.max(0, h);

            columns = width / SLOT_SIZE;
            rows = height / SLOT_SIZE;
        }

    }

    public void shiftPage(int shift)
    {
        if (perPage == 0) {
            numPages = 0;
            page = 0;
            return;
        }

        numPages = (int) Math.ceil((float) realItems.size() / (float) perPage);

        page += shift;

        if (page >= numPages) {
            page = page - numPages;
        }

        if (page < 0) {
            page = numPages + page;
        }

        page = Math.max(0, Math.min(page, numPages - 1));
    }

    public void refresh(GuiContainer gui)
    {
        updateGuiOverlapSlots(gui);
        shiftPage(0);
    }

    private void updateGuiOverlapSlots(GuiContainer gui)
    {
        invalidSlotMap = new boolean[rows * columns];
        perPage = columns * rows;

        checkGuiOverlap(gui, 0, columns - 2, 1);
        checkGuiOverlap(gui, columns - 1, 1, -1);

    }

    private void checkGuiOverlap(GuiContainer gui, int start, int end, int dir)
    {
        boolean validColumn = false;

        for (int c = start; c != end && !validColumn; c += dir) {
            validColumn = true;

            for (int r = 0; r < rows; r++) {
                final int idx = columns * r + c;
                if (!slotValid(gui, r, c) && idx >= 0 && idx < invalidSlotMap.length && !invalidSlotMap[idx]) {
                    invalidSlotMap[idx] = true;
                    validColumn = false;
                    perPage--;
                }

            }

        }

    }

    private boolean slotValid(GuiContainer gui, int row, int column)
    {
        Rectangle4i rect = getSlotRect(row, column);
        
        try {
            GuiInfo.readLock.lock();
            if (GuiInfo.guiHandlers.stream().anyMatch(handler -> handler.hideItemPanelSlot(gui, rect.x, rect.y, rect.w, rect.h))) {
                return false;
            }
        } finally {
            GuiInfo.readLock.unlock();
        }

        return true;
    }

    public Rectangle4i getSlotRect(int i)
    {
        return getSlotRect(i / columns, i % columns);
    }

    public Rectangle4i getSlotRect(int row, int column)
    {
        return new Rectangle4i(marginLeft + (width % SLOT_SIZE) / 2 + column * SLOT_SIZE, marginTop + row * SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
    }

    public boolean isInvalidSlot(int idx)
    {
        return invalidSlotMap[idx];
    }

    public void draw(int mousex, int mousey)
    {
        if (getPerPage() == 0) {
            return;
        }

        ItemPanelSlot slot = getSlotMouseOver(mousex, mousey);

        GuiContainerManager.enableMatrixStackLogging();

        int idx = page * perPage;
        for (int i = 0; i < rows * columns && idx < size(); i++) {

            if (!invalidSlotMap[i]) {
                drawItem(getSlotRect(i), idx, slot);
                idx ++;
            }

        }

        GuiContainerManager.disableMatrixStackLogging();
    }

    protected void drawItem(Rectangle4i rect, int idx, ItemPanelSlot focus)
    {

        if (focus != null && focus.slotIndex == idx) {
            drawRect(rect.x, rect.y, rect.w, rect.h, 0xee555555);//highlight
        }

        GuiContainerManager.drawItem(rect.x + 1, rect.y + 1, getItem(idx));
    }

    public ItemPanelSlot getSlotMouseOver(int mousex, int mousey)
    {
        if (!contains(mousex, mousey)) {
            return null;
        }

        final int overRow = (int) ((mousey - marginTop) / SLOT_SIZE);
        final int overColumn = (int) ((mousex - marginLeft - (width % SLOT_SIZE) / 2) / SLOT_SIZE);
        final int slt = columns * overRow + overColumn;
        int idx = page * perPage + slt;

        if (overRow >= rows || overColumn >= columns) {
            return null;
        }

        for (int i = 0; i < slt; i++) {
            if (invalidSlotMap[i]) {
                idx--;
            }
        }

        return idx < size()? new ItemPanelSlot(idx, realItems.get(idx)): null;
    }

    public boolean contains(int px, int py)
    {

        if (!(new Rectangle4i(marginLeft, marginTop, width, height)).contains(px, py)) {
            return false;
        }

        final int r = (int) ((py - marginTop) / SLOT_SIZE);
        final int c = (int) ((px - marginLeft - (width % SLOT_SIZE) / 2) / SLOT_SIZE);
        final int slt = columns * r + c;

        return r >= rows || c >= columns || !invalidSlotMap[slt];
    }

}