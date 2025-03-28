package codechicken.nei.config;

import java.awt.Point;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import codechicken.core.gui.GuiScreenWidget;
import codechicken.lib.gui.GuiDraw;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemsGrid;
import codechicken.nei.guihook.GuiContainerManager;

public class GuiPanelSettings extends GuiScreenWidget {

    private final Option opt;

    private PanelPlaceholder bookmarksPanel;
    private PanelPlaceholder itemsPanel;

    protected static class PanelResizeButton {

        private int x;
        private int y;
        private int w;
        private int h;

        public PanelResizeButton(int w, int h) {
            this.w = w;
            this.h = h;
        }

        public void draw(int mousex, int mousey) {
            drawRect(x, y, x + w, y + h, 0xbbaaaaaa);
            drawRect(x + 2, y + 2, x + w - 2, y + h - 2, contains(mousex, mousey) ? 0xFF000000 : 0xbb000000);
        }

        public boolean contains(int pointX, int pointY) {
            return (new Rectangle4i(x, y, w, h)).contains(pointX, pointY);
        }
    }

    protected static class PanelPlaceholder {

        protected static int PANEL_SIZE = 16;

        private final Option opt;
        private final String name;
        private Rectangle4i margin;
        private Point dragDown;
        private String dragDir;
        private int x = 0;
        private int y = 0;
        private int w = 0;
        private int h = 0;

        private PanelResizeButton leftButton = new PanelResizeButton(PANEL_SIZE, PANEL_SIZE * 2);
        private PanelResizeButton topButton = new PanelResizeButton(PANEL_SIZE * 2, PANEL_SIZE);
        private PanelResizeButton rightButton = new PanelResizeButton(PANEL_SIZE, PANEL_SIZE * 2);
        private PanelResizeButton bottomButton = new PanelResizeButton(PANEL_SIZE * 2, PANEL_SIZE);

        public PanelPlaceholder(Option opt, String name) {
            this.opt = opt;
            this.name = name;
        }

        public void resize(Rectangle4i margin) {
            this.margin = margin;
            int[] padding = getPaddings();

            x = margin.x + padding[0];
            y = margin.y + padding[1];
            w = margin.w - padding[2] - padding[0];
            h = margin.h - padding[3] - padding[1];

            leftButton.x = x;
            leftButton.y = y + h / 2 - PANEL_SIZE;

            rightButton.x = x + w - PANEL_SIZE;
            rightButton.y = y + h / 2 - PANEL_SIZE;

            topButton.x = x + w / 2 - PANEL_SIZE;
            topButton.y = y;

            bottomButton.x = x + w / 2 - PANEL_SIZE;
            bottomButton.y = y + h - PANEL_SIZE;
        }

        public void draw(int mousex, int mousey) {
            drawRect(x, y, x + w, y + PANEL_SIZE, 0xbbaaaaaa); // top-panel
            drawRect(x, y + h - PANEL_SIZE, x + w, y + h, 0xbbaaaaaa); // bottom-panel

            drawRect(x, y, x + 2, y + h, 0xbbaaaaaa); // border left
            drawRect(x + w, y, x + w - 2, y + h, 0xbbaaaaaa); // border right

            drawRect(x, y, x + w, y + 2, 0xbbaaaaaa); // border top
            drawRect(x, y + h, x + w, y + h - 2, 0xbbaaaaaa); // border bottom

            drawItems();

            leftButton.draw(mousex, mousey);
            rightButton.draw(mousex, mousey);
            topButton.draw(mousex, mousey);
            bottomButton.draw(mousex, mousey);
        }

        protected int[] getPaddings() {
            final int minWidth = ItemsGrid.SLOT_SIZE;
            final int minHeight = ItemsGrid.SLOT_SIZE;

            int paddingLeft = (int) Math.ceil(margin.w * opt.renderTag(name + ".left").getIntValue() / 100000.0);
            int paddingTop = (int) Math.ceil(margin.h * opt.renderTag(name + ".top").getIntValue() / 100000.0);
            int paddingRight = (int) Math.ceil(margin.w * opt.renderTag(name + ".right").getIntValue() / 100000.0);
            int paddingBottom = (int) Math.ceil(margin.h * opt.renderTag(name + ".bottom").getIntValue() / 100000.0);

            if (dragDir != null) {
                final Point mouse = GuiDraw.getMousePosition();
                Point drag = new Point(mouse.x - dragDown.x, mouse.y - dragDown.y);

                if (dragDir == "left") {
                    paddingLeft = Math
                            .min(Math.max(0, margin.w - paddingRight - minWidth), Math.max(0, paddingLeft + drag.x));
                } else if (dragDir == "right") {
                    paddingRight = Math
                            .min(Math.max(0, margin.w - paddingLeft - minWidth), Math.max(0, paddingRight - drag.x));
                } else if (dragDir == "top") {
                    paddingTop = Math
                            .min(Math.max(0, margin.h - paddingBottom - minHeight), Math.max(0, paddingTop + drag.y));
                } else if (dragDir == "bottom") {
                    paddingBottom = Math
                            .min(Math.max(0, margin.h - paddingTop - minHeight), Math.max(0, paddingBottom - drag.y));
                } else if (dragDir == "move") {
                    int width = margin.w - paddingRight - paddingLeft;
                    int height = margin.h - paddingTop - paddingBottom;

                    paddingLeft = Math.min(Math.max(0, margin.w - width), Math.max(0, paddingLeft + drag.x));
                    paddingTop = Math.min(Math.max(0, margin.h - height), Math.max(0, paddingTop + drag.y));

                    paddingRight = margin.w - paddingLeft - width;
                    paddingBottom = margin.h - paddingTop - height;
                }
            }

            int deltaHeight = Math.min(0, margin.h - paddingTop - paddingBottom - minHeight) / 2;

            paddingLeft = Math.min(paddingLeft, Math.max(0, margin.w - paddingRight - minWidth));
            paddingRight = Math.min(paddingRight, Math.max(0, margin.w - paddingLeft - minWidth));
            paddingTop = Math.min(margin.h - minHeight, Math.max(0, paddingTop + deltaHeight));
            paddingBottom = Math.min(margin.h - paddingTop - minHeight, Math.max(0, paddingBottom - deltaHeight));

            return new int[] { paddingLeft, paddingTop, paddingRight, paddingBottom };
        }

        protected void drawItems() {
            final int columns = w / ItemsGrid.SLOT_SIZE;
            final int rows = (h - (PANEL_SIZE + 2) * 2) / ItemsGrid.SLOT_SIZE;
            final int paddingLeft = (w % ItemsGrid.SLOT_SIZE) / 2;
            final List<ItemStack> items = ItemPanels.itemPanel.getItems();
            if (items.isEmpty()) return;

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    drawItem(
                            x + paddingLeft + ItemsGrid.SLOT_SIZE * c + 1,
                            y + PANEL_SIZE + 2 + ItemsGrid.SLOT_SIZE * r + 1,
                            items.get((r * columns + c) % items.size()));
                }
            }
        }

        protected void drawItem(int x, int y, ItemStack stack) {
            final float scale = ItemsGrid.SLOT_SIZE / 18f;
            final float inverseScaleFactor = 1.0f / scale;
            final float shiftX = x * inverseScaleFactor;
            final float shiftY = y * inverseScaleFactor;

            GL11.glScaled(scale, scale, 1);
            GL11.glTranslated(shiftX, shiftY, 0);

            GuiContainerManager.drawItem(0, 0, stack, true, "");

            GL11.glTranslated(-1 * shiftX, -1 * shiftY, 0);
            GL11.glScaled(inverseScaleFactor, inverseScaleFactor, 1);
        }

        public boolean contains(int pointX, int pointY) {
            return (new Rectangle4i(x, y, w, h)).contains(pointX, pointY);
        }

        protected void mouseClicked(int x, int y, int button) {

            if (contains(x, y)) {
                dragDown = GuiDraw.getMousePosition();

                if (leftButton.contains(x, y)) {
                    dragDir = "left";
                } else if (rightButton.contains(x, y)) {
                    dragDir = "right";
                } else if (topButton.contains(x, y)) {
                    dragDir = "top";
                } else if (bottomButton.contains(x, y)) {
                    dragDir = "bottom";
                } else {
                    dragDir = "move";
                }
            }
        }

        protected void mouseMovedOrUp(int x, int y, int button) {

            if (button == 0 && dragDown != null) {
                int[] padding = getPaddings();

                opt.renderTag(name + ".left").setIntValue(padding[0] * 100000 / margin.w);
                opt.renderTag(name + ".top").setIntValue(padding[1] * 100000 / margin.h);
                opt.renderTag(name + ".right").setIntValue(padding[2] * 100000 / margin.w);
                opt.renderTag(name + ".bottom").setIntValue(padding[3] * 100000 / margin.h);

                dragDir = null;
                dragDown = null;
            }
        }
    }

    public GuiPanelSettings(Option opt) {
        super(176, 198);
        this.opt = opt;

        bookmarksPanel = new PanelPlaceholder(opt, opt.name + ".bookmarks");
        itemsPanel = new PanelPlaceholder(opt, opt.name + ".items");
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    @Override
    public void keyTyped(char c, int keycode) {

        if (keycode == Keyboard.KEY_ESCAPE || keycode == Keyboard.KEY_BACK) {
            Minecraft.getMinecraft().displayGuiScreen(opt.slot.getGui());
            return;
        }

        super.keyTyped(c, keycode);
    }

    protected void drawGuiContainerBackgroundLayer(float var1, int var2, int var3) {
        GL11.glColor4f(1, 1, 1, 1);
        CCRenderState.changeTexture("nei:textures/gui/inv.png");

        int x = guiLeft;
        int y = guiTop - 4;

        drawTexturedModalRect(x - 23, y, 0, 0, 199, 204);
    }

    @Override
    public void drawScreen(int mousex, int mousey, float f) {

        drawGuiContainerBackgroundLayer(f, mousex, mousey);
        drawDefaultBackground();

        RenderHelper.enableGUIStandardItemLighting();
        GL11.glPushMatrix();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);

        bookmarksPanel.resize(getBookmarkDefaultSize());
        itemsPanel.resize(getItemsDefaultSize());

        bookmarksPanel.draw(mousex, mousey);
        itemsPanel.draw(mousex, mousey);

        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glPopMatrix();
        RenderHelper.enableStandardItemLighting();
    }

    private Rectangle4i getBookmarkDefaultSize() {
        return new Rectangle4i(2, 2, guiLeft - 4, height - 4);
    }

    protected Rectangle4i getItemsDefaultSize() {
        return new Rectangle4i(guiLeft + xSize + 2, 2, width - (guiLeft + xSize + 4), height - 4);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        bookmarksPanel.mouseClicked(x, y, button);
        itemsPanel.mouseClicked(x, y, button);
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int button) {
        bookmarksPanel.mouseMovedOrUp(x, y, button);
        itemsPanel.mouseMovedOrUp(x, y, button);
    }
}
