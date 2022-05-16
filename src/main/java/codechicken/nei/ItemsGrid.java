package codechicken.nei;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.api.GuiInfo;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.ItemPanel.ItemPanelSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;

import static codechicken.lib.gui.GuiDraw.drawRect;

public class ItemsGrid
{
    public static final int SLOT_SIZE = 18;

    protected int width;
    protected int height;

    protected int marginLeft;
    protected int marginTop;

    protected int paddingLeft;

    protected ArrayList<ItemStack> realItems = new ArrayList<>();

    protected int page;
    protected int perPage;

    protected int firstIndex;
    protected int numPages;

    protected int rows;
    protected int columns;

    protected boolean[] validSlotMap;
    protected boolean[] invalidSlotMap;

    @Nullable
    private Framebuffer framebuffer = null;
    protected boolean refreshBuffer = true;

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
        if(width != w || height != h || mleft != marginLeft || mtop != marginTop)
            refreshBuffer = true;

        marginLeft = mleft;
        marginTop = mtop;

        width = Math.max(0, w);
        height = Math.max(0, h);

        columns = width / SLOT_SIZE;
        rows = height / SLOT_SIZE;

        paddingLeft = (width % SLOT_SIZE) / 2;
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
        if(shift != 0)
            refreshBuffer = true;
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
                if (GuiInfo.hideItemPanelSlot(gui, getSlotRect(r, c)) && idx >= 0 && idx < invalidSlotMap.length && !invalidSlotMap[idx]) {
                    invalidSlotMap[idx] = true;
                    validColumn = false;
                    perPage--;
                }

            }

        }

    }

    public Rectangle4i getSlotRect(int i)
    {
        return getSlotRect(i / columns, i % columns);
    }

    public Rectangle4i getSlotRect(int row, int column)
    {
        return new Rectangle4i(marginLeft + paddingLeft + column * SLOT_SIZE, marginTop + row * SLOT_SIZE, SLOT_SIZE, SLOT_SIZE);
    }

    public boolean isInvalidSlot(int idx)
    {
        return invalidSlotMap[idx];
    }

    private void drawFocusOutline(int mousex, int mousey)
    {
        ItemPanelSlot slot = getSlotMouseOver(mousex, mousey);
        if (slot != null) {
            int pageIdx = slot.slotIndex % perPage;
            if(invalidSlotMap[pageIdx])
                return;
            Rectangle4i rect = getSlotRect(pageIdx);
            drawRect(rect.x, rect.y, rect.w, rect.h, 0xee555555);//highlight
        }
    }

    public void draw(int mousex, int mousey)
    {
        if (getPerPage() == 0) {
            return;
        }

        ItemPanelSlot slot = getSlotMouseOver(mousex, mousey);

        boolean shouldCache = NEIClientConfig.shouldCacheItemRendering() && !PresetsWidget.inEditMode();

        if(shouldCache) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (framebuffer == null) {
                framebuffer = new Framebuffer(minecraft.displayWidth, minecraft.displayHeight, true);
                framebuffer.framebufferColor[0] = 0.0F;
                framebuffer.framebufferColor[1] = 0.0F;
                framebuffer.framebufferColor[2] = 0.0F;
            }
            if (refreshBuffer) {
                framebuffer.createBindFramebuffer(minecraft.displayWidth, minecraft.displayHeight);
                framebuffer.framebufferClear();
                framebuffer.bindFramebuffer(false);
                /* Set up some rendering state needed for items to work correctly */
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDepthMask(false);
                OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            } else {
                drawFocusOutline(mousex, mousey);
                GL11.glEnable(GL11.GL_BLEND);
                OpenGlHelper.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                framebuffer.bindFramebufferTexture();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                ScaledResolution res = new ScaledResolution(minecraft, minecraft.displayWidth, minecraft.displayHeight);
                Tessellator tessellator = Tessellator.instance;
                tessellator.startDrawingQuads();
                tessellator.addVertexWithUV(0, res.getScaledHeight_double(), 0.0, 0, 0);
                tessellator.addVertexWithUV(res.getScaledWidth_double(), res.getScaledHeight_double(), 0.0, 1, 0);
                tessellator.addVertexWithUV(res.getScaledWidth_double(), 0, 0.0, 1, 1);
                tessellator.addVertexWithUV(0, 0, 0, 0, 1);
                tessellator.draw();
                OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
                return;
            }
        } else {
            drawFocusOutline(mousex, mousey);
        }

        GuiContainerManager.enableMatrixStackLogging();

        int idx = page * perPage;
        for (int i = 0; i < rows * columns && idx < size(); i++) {
            if (!invalidSlotMap[i]) {
                drawItem(getSlotRect(i), idx, slot);
                idx ++;
            }
        }

        GuiContainerManager.disableMatrixStackLogging();

        if (refreshBuffer && shouldCache) {
            refreshBuffer = false;
            Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(false);
        }
    }

    protected void drawItem(Rectangle4i rect, int idx, ItemPanelSlot focus)
    {
        GuiContainerManager.drawItem(rect.x + 1, rect.y + 1, getItem(idx));
    }

    public ItemPanelSlot getSlotMouseOver(int mousex, int mousey)
    {
        if (!contains(mousex, mousey)) {
            return null;
        }

        final int overRow = (int) ((mousey - marginTop) / SLOT_SIZE);
        final int overColumn = (int) ((mousex - marginLeft - paddingLeft) / SLOT_SIZE);
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

        if (!(new Rectangle4i(marginLeft + paddingLeft, marginTop, columns * SLOT_SIZE, height)).contains(px, py)) {
            return false;
        }

        final int r = (int) ((py - marginTop) / SLOT_SIZE);
        final int c = (int) ((px - marginLeft - paddingLeft) / SLOT_SIZE);
        final int slt = columns * r + c;

        return r >= rows || c >= columns || !invalidSlotMap[slt];
    }

}