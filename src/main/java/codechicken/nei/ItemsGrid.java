package codechicken.nei;

import static codechicken.lib.gui.GuiDraw.drawRect;
import static codechicken.lib.gui.GuiDraw.getMousePosition;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemPanel.ItemPanelSlot;
import codechicken.nei.api.GuiInfo;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.BookmarkRecipeId;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
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
    protected boolean showRecipeTooltips = false;

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
            if (StackInfo.equalItemAndNBT(stackA, getItem(idx), useNBT)) {
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
    }

    public void setShowRecipeTooltips(boolean show) {
        this.showRecipeTooltips = show;
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

        if (NEIClientConfig.optimizeGuiOverlapComputation()) {
            checkGuiOverlap(gui, 0, columns - 2, 1);
            checkGuiOverlap(gui, columns - 1, 1, -1);
        } else {
            checkGuiOverlap(gui, 0, columns, 1);
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
                if (GuiInfo.hideItemPanelSlot(gui, getSlotRect(r, c)) && idx >= 0
                        && idx < invalidSlotMap.length
                        && !invalidSlotMap[idx]) {
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
        List<Integer> mask = new ArrayList<>();

        int idx = page * perPage;
        for (int i = 0; i < rows * columns && idx < size(); i++) {
            mask.add(isInvalidSlot(i) ? null : idx++);
        }

        return mask;
    }

    private void drawSlotOutlines(int mousex, int mousey) {
        ItemPanelSlot focused = getSlotMouseOver(mousex, mousey);
        final List<Integer> mask = getMask();

        for (int i = 0; i < mask.size(); i++) {
            if (mask.get(i) != null) {
                drawSlotOutline(focused, mask.get(i), getSlotRect(i));
            }
        }
    }

    private int recipeTooltipSlotIdx = -1;
    private int recipeTooltipLines = 2;
    private Runnable recipeTooltipUpdater = null;
    private GuiRecipe<?> recipeTooltipGui = null;

    public void update() {
        if (recipeTooltipUpdater != null) {
            recipeTooltipUpdater.run();
            recipeTooltipUpdater = null;
        }
        if (recipeTooltipGui != null) {
            recipeTooltipGui.updateAsTooltip();
        }
    }

    private static <T> List<T> listOrEmptyList(final List<T> listOrNull) {
        return listOrNull == null ? Collections.emptyList() : listOrNull;
    }

    private void drawRecipeTooltip(int mousex, int mousey, List<String> itemTooltip) {
        if (!NEIClientConfig.isLoaded()) {
            return;
        }

        final Minecraft mc = Minecraft.getMinecraft();
        ItemPanelSlot focused = getSlotMouseOver(mousex, mousey);
        if (focused == null) {
            recipeTooltipSlotIdx = -1;
            recipeTooltipGui = null;
            return;
        }
        final List<Integer> mask = getMask();

        int slotIdx = -1;
        for (int i = 0; i < mask.size(); i++) {
            if (mask.get(i) == null) {
                continue;
            }
            if (focused.slotIndex != mask.get(i)) {
                continue;
            }
            slotIdx = i;
            break;
        }
        if (slotIdx == -1) {
            recipeTooltipSlotIdx = -1;
            recipeTooltipGui = null;
            return;
        }

        final Point mouseover = getMousePosition();
        final ItemPanelSlot panelSlot = ItemPanels.bookmarkPanel.getSlotMouseOver(mouseover.x, mouseover.y);
        final BookmarkRecipeId recipeId;
        if (panelSlot != null) {
            recipeId = ItemPanels.bookmarkPanel.getBookmarkRecipeId(panelSlot.slotIndex);
        } else {
            recipeId = ItemPanels.bookmarkPanel.getBookmarkRecipeId(focused.item);
        }
        if (recipeId == null) {
            return;
        }

        if (slotIdx != recipeTooltipSlotIdx) {
            recipeTooltipSlotIdx = slotIdx;
            recipeTooltipGui = null;
            recipeTooltipUpdater = () -> {
                recipeTooltipGui = GuiCraftingRecipe.createRecipeGui("item", false, false, false, focused.item);
                if (recipeTooltipGui != null) {
                    recipeTooltipGui.limitToOneRecipe();
                    recipeTooltipGui.initGui(true);
                    recipeTooltipGui.guiTop = 0;
                    recipeTooltipGui.guiLeft = 0;
                }
                recipeTooltipLines = Math.max(1, itemTooltip.size());
            };
        }
        if (recipeTooltipGui == null) {
            return;
        }

        GL11.glPushMatrix();
        final float tooltipYOffset;
        if (mousey - marginTop > height / 2) {
            tooltipYOffset = mousey - recipeTooltipGui.getHeightAsWidget() + 8;
        } else {
            tooltipYOffset = mousey + ((recipeTooltipLines < 2) ? 1 : 3 + ((recipeTooltipLines - 1) * 10));
        }
        GL11.glTranslatef(mousex, tooltipYOffset, 500);

        final GuiContainer gui;
        if (mc.currentScreen instanceof GuiRecipe) {
            gui = ((GuiRecipe<?>) mc.currentScreen).firstGui;
        } else if (mc.currentScreen instanceof GuiContainer) {
            gui = (GuiContainer) mc.currentScreen;
        } else {
            gui = null;
        }
        recipeTooltipGui.drawGuiContainerBackgroundLayer(0.0f, -100, -100);
        if (recipeTooltipGui.slotcontainer != null) {
            @SuppressWarnings("unchecked")
            List<Slot> slots = (List<Slot>) recipeTooltipGui.slotcontainer.inventorySlots;
            for (Slot slot : slots) {
                if (slot != null && slot.getStack() != null) {
                    GuiContainerManager.drawItem(slot.xDisplayPosition, slot.yDisplayPosition, slot.getStack());
                }
            }
        }
        recipeTooltipGui.drawGuiContainerForegroundLayer(-100, -100);
        for (GuiButton btn : recipeTooltipGui.getOverlayButtons()) {
            btn.drawButton(mc, -100, -100);
        }
        GL11.glPopMatrix();
    }

    protected void drawSlotOutline(@Nullable ItemPanelSlot focused, int slotIdx, Rectangle4i rect) {
        if (focused != null && focused.slotIndex == slotIdx) {
            drawRect(rect.x, rect.y, rect.w, rect.h, 0xee555555); // highlight
        }
    }

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

    public void draw(int mousex, int mousey) {
        if (getPerPage() == 0) {
            return;
        }

        if (NEIClientConfig.shouldCacheItemRendering()) {

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

            drawSlotOutlines(mousex, mousey);
            blitExistingBuffer();
        } else {
            drawSlotOutlines(mousex, mousey);
            drawItems();
        }
    }

    public void postDrawTooltips(int mousex, int mousey, List<String> tooltip) {
        if (NEIClientConfig.showRecipeTooltips() && showRecipeTooltips) {
            try {
                drawRecipeTooltip(mousex, mousey, tooltip);
            } catch (Exception e) {
                NEIClientConfig.logger.warn("Cannot draw recipe tooltip", e);
            }
        }
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

        if (!(new Rectangle4i(marginLeft + paddingLeft, marginTop, columns * SLOT_SIZE, height)).contains(px, py)) {
            return false;
        }

        final int r = (int) ((py - marginTop) / SLOT_SIZE);
        final int c = (int) ((px - marginLeft - paddingLeft) / SLOT_SIZE);
        final int slt = columns * r + c;

        return r >= rows || c >= columns || !isInvalidSlot(slt);
    }

    public String getMessageOnEmpty() {
        return null;
    }
}
