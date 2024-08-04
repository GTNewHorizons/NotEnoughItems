package codechicken.nei;

import static codechicken.lib.gui.GuiDraw.drawRect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemPanel.ItemPanelSlot;
import codechicken.nei.api.GuiInfo;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.StackInfo;

public class ItemsGrid {

    public static final int SLOT_SIZE = 18;

    protected int width;
    protected int height;

    protected int marginLeft;
    protected int marginTop;

    protected int paddingLeft;

    protected ArrayList<ItemStack> realItems = new ArrayList<>();

    protected int page;
    protected int perPage;

    protected int rows;
    protected int columns;
    protected List<Integer> gridMask = null;

    protected boolean[] invalidSlotMap;

    protected Label messageLabel = new Label(getMessageOnEmpty(), true);

    @Nullable
    private Framebuffer framebuffer = null;

    protected boolean refreshBuffer = true;

    public ArrayList<ItemStack> getItems() {
        return realItems;
    }

    public ItemStack getItem(int idx) {
        return realItems.get(idx);
    }

    public int size() {
        return realItems.size();
    }

    public int indexOf(ItemStack stackA, boolean useNBT) {

        for (int idx = 0; idx < realItems.size(); idx++) {
            if (StackInfo.equalItemAndNBT(stackA, realItems.get(idx), useNBT)) {
                return idx;
            }
        }

        return -1;
    }

    public void setPage(int page) {
        this.page = Math.max(0, Math.min(page, getNumPages() - 1));
        onGridChanged();
    }

    public int getPage() {
        return page + 1;
    }

    public int getPerPage() {
        return perPage;
    }

    public int getNumPages() {
        if (perPage > 0) {
            return (int) Math.ceil((float) realItems.size() / (float) perPage);
        }

        return 0;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public int getLastRowIndex() {
        return this.columns > 0 ? (int) Math.ceil((float) this.getMask().size() / this.columns) - 1 : 0;
    }

    public void setGridSize(int mleft, int mtop, int w, int h) {
        marginLeft = mleft;
        marginTop = mtop;

        width = Math.max(0, w);
        height = Math.max(0, h);

        columns = width / SLOT_SIZE;
        rows = height / SLOT_SIZE;

        paddingLeft = (width % SLOT_SIZE) / 2;

        messageLabel.x = marginLeft + width / 2;
        messageLabel.y = marginTop + height / 2;
    }

    public void shiftPage(int shift) {
        if (perPage == 0) {
            page = 0;
            return;
        }

        final int numPages = getNumPages();
        final int oldPage = page;
        page += shift;

        if (page >= numPages) {
            page = page - numPages;
        }

        if (page < 0) {
            page = numPages + page;
        }

        page = Math.max(0, Math.min(page, numPages - 1));

        if (page != oldPage) {
            onGridChanged();
        }
    }

    protected void onGridChanged() {
        refreshBuffer = true;
        this.gridMask = null;
    }

    protected void onItemsChanged() {
        onGridChanged();
    }

    public void refresh(GuiContainer gui) {
        updateGuiOverlapSlots(gui);
        shiftPage(0);
    }

    protected void updateGuiOverlapSlots(GuiContainer gui) {
        final boolean[] oldSlotMap = invalidSlotMap;
        final int oldRows = rows;
        final int oldColumns = columns;

        invalidSlotMap = new boolean[rows * columns];
        perPage = columns * rows;

        if (gui != null) {
            if (NEIClientConfig.optimizeGuiOverlapComputation()) {
                checkGuiOverlap(gui, 0, columns - 2, 1);
                checkGuiOverlap(gui, columns - 1, 1, -1);
            } else {
                checkGuiOverlap(gui, 0, columns, 1);
            }
        }

        if (oldRows != rows || oldColumns != columns || !Arrays.equals(oldSlotMap, invalidSlotMap)) {
            onGridChanged();
        }
    }

    private void checkGuiOverlap(GuiContainer gui, int start, int end, int dir) {
        boolean validColumn = false;

        for (int c = start; c != end && (!NEIClientConfig.optimizeGuiOverlapComputation() || !validColumn); c += dir) {
            validColumn = true;

            for (int r = 0; r < rows; r++) {
                final int idx = columns * r + c;
                if (idx >= 0 && idx < invalidSlotMap.length
                        && !invalidSlotMap[idx]
                        && GuiInfo.hideItemPanelSlot(gui, getSlotRect(r, c))) {
                    invalidSlotMap[idx] = true;
                    validColumn = false;
                    perPage--;
                }
            }
        }
    }

    public Rectangle4i getItemRect(int idx) {
        final List<Integer> mask = getMask();

        for (int i = 0; i < mask.size(); i++) {
            if (mask.get(i) != null && idx == mask.get(i)) {
                return getSlotRect(i);
            }
        }

        return null;
    }

    public Rectangle4i getSlotRect(int i) {
        return getSlotRect(i / columns, i % columns);
    }

    public Rectangle4i getSlotRect(int row, int column) {
        return new Rectangle4i(
                marginLeft + paddingLeft + column * SLOT_SIZE,
                marginTop + row * SLOT_SIZE,
                SLOT_SIZE,
                SLOT_SIZE);
    }

    public boolean isInvalidSlot(int index) {
        return invalidSlotMap[index];
    }

    protected List<Integer> getMask() {

        if (this.gridMask == null) {
            this.gridMask = new ArrayList<>();
            int idx = page * perPage;

            for (int i = 0; i < rows * columns && idx < size(); i++) {
                this.gridMask.add(isInvalidSlot(i) ? null : idx++);
            }

        }

        return this.gridMask;
    }

    protected void beforeDrawItems(int mousex, int mousey, @Nullable ItemPanelSlot focused) {

        final List<Integer> mask = getMask();

        for (int i = 0; i < mask.size(); i++) {
            if (mask.get(i) != null) {
                beforeDrawSlot(focused, mask.get(i), getSlotRect(i));
            }
        }
    }

    protected void afterDrawItems(int mousex, int mousey, @Nullable ItemPanelSlot focused) {
        final List<Integer> mask = getMask();

        for (int i = 0; i < mask.size(); i++) {
            if (mask.get(i) != null) {
                afterDrawSlot(focused, mask.get(i), getSlotRect(i));
            }
        }
    }

    public void update() {}

    protected void beforeDrawSlot(@Nullable ItemPanelSlot focused, int slotIdx, Rectangle4i rect) {
        if (focused != null && focused.slotIndex == slotIdx) {
            drawRect(rect.x, rect.y, rect.w, rect.h, 0xee555555); // highlight
        }
    }

    protected void afterDrawSlot(@Nullable ItemPanelSlot focused, int slotIdx, Rectangle4i rect) {}

    private void blitExistingBuffer() {
        Minecraft minecraft = Minecraft.getMinecraft();
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        getFrameBuffer().bindFramebufferTexture();
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
    }

    private Framebuffer getFrameBuffer() {

        if (framebuffer == null) {
            framebuffer = new Framebuffer(1, 1, true);
            framebuffer.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
        }

        return framebuffer;
    }

    private void drawItems() {
        GuiContainerManager.enableMatrixStackLogging();

        final List<Integer> mask = getMask();
        for (int i = 0; i < mask.size(); i++) {
            if (mask.get(i) != null) {
                drawItem(getSlotRect(i), mask.get(i));
            }
        }

        GuiContainerManager.disableMatrixStackLogging();
    }

    protected boolean shouldCacheItemRendering() {
        return NEIClientConfig.shouldCacheItemRendering();
    }

    public void draw(int mousex, int mousey) {
        if (getPerPage() == 0) {
            return;
        }

        final ItemPanelSlot focused = getSlotMouseOver(mousex, mousey);

        if (shouldCacheItemRendering()) {

            if (refreshBuffer) {
                Minecraft minecraft = Minecraft.getMinecraft();
                Framebuffer framebuffer = getFrameBuffer();
                framebuffer.createBindFramebuffer(minecraft.displayWidth, minecraft.displayHeight);
                framebuffer.framebufferClear();
                framebuffer.bindFramebuffer(false);

                /* Set up some rendering state needed for items to work correctly */
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDepthMask(true);
                OpenGlHelper.glBlendFunc(
                        GL11.GL_SRC_ALPHA,
                        GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_SRC_ALPHA,
                        GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

                drawItems();

                Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(false);
                refreshBuffer = false;
            }

            beforeDrawItems(mousex, mousey, focused);
            blitExistingBuffer();
        } else {
            beforeDrawItems(mousex, mousey, focused);
            drawItems();
        }

        afterDrawItems(mousex, mousey, focused);
    }

    public void setVisible() {
        if (getItems().isEmpty() && getMessageOnEmpty() != null) {
            LayoutManager.addWidget(messageLabel);
        }
    }

    protected void drawItem(Rectangle4i rect, int idx) {
        GuiContainerManager.drawItem(rect.x + 1, rect.y + 1, getItem(idx));
    }

    public ItemPanelSlot getSlotMouseOver(int mousex, int mousey) {
        if (!contains(mousex, mousey)) {
            return null;
        }

        final int overRow = (int) ((mousey - marginTop) / SLOT_SIZE);
        final int overColumn = (int) ((mousex - marginLeft - paddingLeft) / SLOT_SIZE);
        final int slt = columns * overRow + overColumn;

        if (overRow >= rows || overColumn >= columns) {
            return null;
        }

        final List<Integer> mask = getMask();

        if (mask.size() > slt && mask.get(slt) != null) {
            final int idx = mask.get(slt);

            return new ItemPanelSlot(idx, getItem(idx));
        }

        return null;
    }

    public boolean contains(int px, int py) {
        final Rectangle4i rect = new Rectangle4i(
                marginLeft + paddingLeft,
                marginTop,
                columns * SLOT_SIZE,
                rows * SLOT_SIZE);

        if (!rect.contains(px, py)) {
            return false;
        }

        final int r = (int) ((py - marginTop) / SLOT_SIZE);
        final int c = (int) ((px - marginLeft - paddingLeft) / SLOT_SIZE);

        return !isInvalidSlot(columns * r + c);
    }

    public String getMessageOnEmpty() {
        return null;
    }
}
