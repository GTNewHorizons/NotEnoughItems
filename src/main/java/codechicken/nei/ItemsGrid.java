package codechicken.nei;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.api.GuiInfo;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.StackInfo;

public abstract class ItemsGrid<T extends ItemsGrid.ItemsGridSlot, M extends ItemsGrid.MouseContext> {

    protected static class ScreenCapture {

        private long nextCacheRefresh = 0;
        private Framebuffer framebuffer;

        public ScreenCapture() {
            this.framebuffer = new Framebuffer(1, 1, true);
            this.framebuffer.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
            Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(false);
        }

        protected int getGridRenderingCacheFPS(int mode) {
            return mode == 1 ? NEIClientConfig.getIntSetting("inventory.gridRenderingCacheFPS") : 0;
        }

        public boolean needRefresh(int mode) {
            final Minecraft minecraft = Minecraft.getMinecraft();
            return this.nextCacheRefresh == 0 || this.framebuffer.framebufferWidth != minecraft.displayWidth
                    || framebuffer.framebufferHeight != minecraft.displayHeight
                    || getGridRenderingCacheFPS(mode) > 0 && this.nextCacheRefresh < System.currentTimeMillis();
        }

        public void refreshBuffer() {
            this.nextCacheRefresh = 0;
        }

        public void captureScreen(Runnable callback, int mode) {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

            resetFramebuffer();
            this.framebuffer.bindFramebuffer(false);

            /* Set up some rendering state needed for items to work correctly */
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDepthMask(true);
            OpenGlHelper.glBlendFunc(
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            callback.run();

            GL11.glPopAttrib();

            Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(false);
            this.nextCacheRefresh = System.currentTimeMillis() + (1000 / Math.max(1, getGridRenderingCacheFPS(mode)));
        }

        private void resetFramebuffer() {
            final Minecraft minecraft = Minecraft.getMinecraft();

            if (this.framebuffer.framebufferWidth != minecraft.displayWidth
                    || framebuffer.framebufferHeight != minecraft.displayHeight) {
                this.framebuffer.createBindFramebuffer(minecraft.displayWidth, minecraft.displayHeight);
                this.framebuffer.setFramebufferFilter(GL11.GL_NEAREST);
            } else {
                this.framebuffer.framebufferClear();
            }

            // copy depth buffer from MC (fix Angelica)
            OpenGlHelper.func_153171_g(GL30.GL_READ_FRAMEBUFFER, minecraft.getFramebuffer().framebufferObject);
            OpenGlHelper.func_153171_g(GL30.GL_DRAW_FRAMEBUFFER, this.framebuffer.framebufferObject);
            GL30.glBlitFramebuffer(
                    0,
                    0,
                    minecraft.displayWidth,
                    minecraft.displayHeight,
                    0,
                    0,
                    minecraft.displayWidth,
                    minecraft.displayHeight,
                    GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT,
                    GL11.GL_NEAREST);
        }

        public void renderCapturedScreen() {
            this.framebuffer.bindFramebufferTexture();

            final Tessellator tessellator = Tessellator.instance;
            final Minecraft minecraft = Minecraft.getMinecraft();
            final ScaledResolution scaledresolution = new ScaledResolution(
                    minecraft,
                    minecraft.displayWidth,
                    minecraft.displayHeight);

            /* Set up some rendering state needed for items to work correctly */
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper
                    .glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_TEXTURE_2D);

            tessellator.startDrawingQuads();

            tessellator.addVertexWithUV(0, scaledresolution.getScaledHeight_double(), 0.0, 0, 0);
            tessellator.addVertexWithUV(
                    scaledresolution.getScaledWidth_double(),
                    scaledresolution.getScaledHeight_double(),
                    0.0,
                    1,
                    0);
            tessellator.addVertexWithUV(scaledresolution.getScaledWidth_double(), 0, 0.0, 1, 1);
            tessellator.addVertexWithUV(0, 0, 0, 0, 1);

            tessellator.draw();

            this.framebuffer.unbindFramebufferTexture();
        }

    }

    public static class ItemsGridSlot {

        protected static final float DEFAULT_SLOT_SIZE = 18f;
        protected static final float LINE_WIDTH = 0.75f;

        public final ItemStack item;
        public final int slotIndex;
        public final int itemIndex;

        public Color borderColor = Color.BLACK;

        public boolean borderLeft = false;
        public boolean borderTop = false;
        public boolean borderRight = false;
        public boolean borderBottom = false;

        public ItemsGridSlot(int slotIndex, int itemIndex, ItemStack itemStack) {
            this.slotIndex = slotIndex;
            this.itemIndex = itemIndex;
            this.item = itemStack;
        }

        public ItemStack getItemStack() {
            return this.item;
        }

        public <M extends MouseContext> void beforeDraw(Rectangle4i rect, M mouseContext) {
            if (mouseContext != null && mouseContext.slotIndex == this.slotIndex) {
                NEIClientUtils.drawRect(rect.x, rect.y, rect.w, rect.h, HIGHLIGHT_COLOR);
            }
        }

        public <M extends MouseContext> void afterDraw(Rectangle4i rect, M mouseContext) {}

        public void drawItem(Rectangle4i rect) {
            drawItem(getItemStack(), rect);
        }

        protected void drawItem(ItemStack stack, Rectangle4i rect) {

            if (rect.w != DEFAULT_SLOT_SIZE) {
                final float panelFactor = rect.w / DEFAULT_SLOT_SIZE;
                GL11.glTranslatef(rect.x + panelFactor, rect.y + panelFactor, 0);
                GL11.glScaled(panelFactor, panelFactor, 1);

                GuiContainerManager.drawItem(0, 0, stack, true, "");

                GL11.glScaled(1f / panelFactor, 1f / panelFactor, 1);
                GL11.glTranslatef(-1 * (rect.x + panelFactor), -1 * (rect.y + panelFactor), 0);
            } else {
                GuiContainerManager.drawItem(rect.x + 1, rect.y + 1, stack, true, "");
            }

        }

        public void drawBorder(Rectangle4i rect) {
            float halfLineWidth = LINE_WIDTH / 2f;

            if (this.borderLeft) {
                NEIClientUtils
                        .drawRect(rect.x - halfLineWidth, rect.y + halfLineWidth, LINE_WIDTH, rect.h, this.borderColor);
            }

            if (this.borderRight) {
                NEIClientUtils.drawRect(
                        rect.x + rect.w - halfLineWidth,
                        rect.y - halfLineWidth,
                        LINE_WIDTH,
                        rect.h,
                        this.borderColor);
            }

            if (this.borderTop) {
                NEIClientUtils
                        .drawRect(rect.x - halfLineWidth, rect.y - halfLineWidth, rect.w, LINE_WIDTH, this.borderColor);
            }

            if (this.borderBottom) {
                NEIClientUtils.drawRect(
                        rect.x + halfLineWidth,
                        rect.y + rect.h - halfLineWidth,
                        rect.w,
                        LINE_WIDTH,
                        this.borderColor);
            }
        }

    }

    public static class MouseContext {

        public final int slotIndex;
        public final int columnIndex;
        public final int rowIndex;

        public MouseContext(int slotIndex, int rowIndex, int columnIndex) {
            this.slotIndex = slotIndex;
            this.rowIndex = rowIndex;
            this.columnIndex = columnIndex;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.rowIndex, this.slotIndex, this.columnIndex);
        }

        public boolean equals(Object obj) {

            if (obj instanceof MouseContext context) {
                return this.columnIndex == context.columnIndex && this.rowIndex == context.rowIndex
                        && this.slotIndex == context.slotIndex;
            }

            return false;
        }

    }

    public static int SLOT_SIZE = 18;
    public static final Color HIGHLIGHT_COLOR = new Color(0xee555555, true);

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

    protected boolean[] invalidSlotMap;

    protected Label messageLabel = new Label(getMessageOnEmpty(), true);

    protected ScreenCapture screenCapture = null;

    public ArrayList<ItemStack> getItems() {
        return realItems;
    }

    public ItemStack getItem(int itemIndex) {
        return realItems.get(itemIndex);
    }

    public int size() {
        return realItems.size();
    }

    public boolean isEmpty() {
        return realItems.isEmpty();
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
        final List<T> mask = getMask();

        return this.columns > 0 && !mask.isEmpty()
                ? (int) Math.ceil((float) (mask.get(mask.size() - 1).slotIndex + 1) / this.columns) - 1
                : 0;
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
        if (this.screenCapture != null) {
            this.screenCapture.refreshBuffer();
        }
    }

    public void onItemsChanged() {
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

    public T getSlotMouseOver(int mousex, int mousey) {
        if (!contains(mousex, mousey)) {
            return null;
        }

        final int overRow = getRowIndex(mousey);
        final int overColumn = getColumnIndex(mousex);
        final int slt = columns * overRow + overColumn;

        if (overRow >= rows || overColumn >= columns) {
            return null;
        }

        return getSlotBySlotIndex(slt);
    }

    public T getSlotBySlotIndex(int slotIndex) {
        return getMask().stream().filter(item -> item.slotIndex == slotIndex).findAny().orElse(null);
    }

    public T getSlotByItemIndex(int itemIndex) {
        return getMask().stream().filter(item -> item.itemIndex == itemIndex).findAny().orElse(null);
    }

    public Rectangle4i getItemRect(int itemIndex) {
        final T item = getSlotByItemIndex(itemIndex);
        return item != null ? getSlotRect(item.slotIndex) : null;
    }

    public Rectangle4i getSlotRect(int slotIndex) {
        return getSlotRect(slotIndex / columns, slotIndex % columns);
    }

    public Rectangle4i getSlotRect(int row, int column) {
        return new Rectangle4i(
                marginLeft + paddingLeft + column * SLOT_SIZE,
                marginTop + row * SLOT_SIZE,
                SLOT_SIZE,
                SLOT_SIZE);
    }

    public int getRowIndex(int mousey) {
        return (mousey - this.marginTop) / SLOT_SIZE;
    }

    public int getColumnIndex(int mousex) {
        return (mousex - this.marginLeft - this.paddingLeft) / SLOT_SIZE;
    }

    public boolean isInvalidSlot(int index) {
        return invalidSlotMap[index];
    }

    public abstract List<T> getMask();

    protected abstract M getMouseContext(int mousex, int mousey);

    protected void beforeDrawItems(int mousex, int mousey, M mouseContext) {
        for (T item : getMask()) {
            item.beforeDraw(getSlotRect(item.slotIndex), mouseContext);
        }
        for (T item : getMask()) {
            item.drawBorder(getSlotRect(item.slotIndex));
        }
    }

    protected void afterDrawItems(int mousex, int mousey, M mouseContext) {
        for (T item : getMask()) {
            item.afterDraw(getSlotRect(item.slotIndex), mouseContext);
        }
    }

    public void update() {}

    protected void drawItems() {

        for (T item : getMask()) {
            item.drawItem(getSlotRect(item.slotIndex));
        }

    }

    protected int getGridRenderingCacheMode() {
        return NEIClientConfig.getGridRenderingCacheMode();
    }

    public void draw(int mousex, int mousey) {
        if (getPerPage() == 0) {
            return;
        }

        final int gridRenderingCacheMode = getGridRenderingCacheMode();
        final M mouseContext = getMouseContext(mousex, mousey);

        beforeDrawItems(mousex, mousey, mouseContext);

        if (gridRenderingCacheMode > 0) {

            if (this.screenCapture == null) {
                this.screenCapture = new ScreenCapture();
            }

            if (this.screenCapture.needRefresh(gridRenderingCacheMode)) {
                this.screenCapture.captureScreen(() -> {
                    GuiContainerManager.enableMatrixStackLogging();
                    drawItems();
                    GuiContainerManager.disableMatrixStackLogging();
                }, gridRenderingCacheMode);
            }

            this.screenCapture.renderCapturedScreen();
        } else {
            GuiContainerManager.enableMatrixStackLogging();
            drawItems();
            GuiContainerManager.disableMatrixStackLogging();
        }

        afterDrawItems(mousex, mousey, mouseContext);
    }

    public void setVisible() {
        if (getItems().isEmpty() && getMessageOnEmpty() != null) {
            LayoutManager.addWidget(messageLabel);
        }
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

        return !isInvalidSlot(columns * getRowIndex(py) + getColumnIndex(px));
    }

    public String getMessageOnEmpty() {
        return null;
    }
}
