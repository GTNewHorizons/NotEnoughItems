package codechicken.nei;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import codechicken.core.gui.GuiScrollSlot;
import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.scroll.GuiHelper;

public class HiddenCategoriesWidget extends Button {

    private final Dropdown dropdown = new Dropdown(this);

    public HiddenCategoriesWidget() {
        super();
        this.z = 1;
    }

    @Override
    public String getRenderLabel() {
        return NEIClientUtils.translate("itempanel.categories.button");
    }

    @Override
    public boolean onButtonPress(boolean rightClick) {
        dropdown.toggle();
        return true;
    }

    @Override
    public void draw(int mouseX, int mouseY) {
        state = 0x4 | (ItemCategories.anyHidden() ? 1 : 0);
        super.draw(mouseX, mouseY);

        if (dropdown.isVisible()) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
            GuiContainerManager.drawItems.zLevel += 100;
            GuiContainerManager.enable2DRender();

            dropdown.draw(mouseX, mouseY, 0);

            GuiContainerManager.drawItems.zLevel -= 100;
            GL11.glPopAttrib();
        }
    }

    @Override
    public String getButtonTip() {
        return NEIClientUtils.translate("itempanel.categories.tip");
    }

    @Override
    public boolean contains(int px, int py) {
        return super.contains(px, py) || dropdown.contains(px, py);
    }

    @Override
    public boolean handleClick(int mouseX, int mouseY, int button) {
        if (dropdown.contains(mouseX, mouseY)) {
            dropdown.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        return super.handleClick(mouseX, mouseY, button);
    }

    @Override
    public void mouseUp(int mx, int my, int button) {
        if (dropdown.isVisible()) dropdown.mouseMovedOrUp(mx, my, button); // -> slotUp -> slotClicked
    }

    @Override
    public void mouseDragged(int mx, int my, int button, long heldTime) {
        if (dropdown.isVisible()) dropdown.mouseDragged(mx, my, button, heldTime);
    }

    @Override
    public boolean onMouseWheel(int i, int mx, int my) {
        if (dropdown.contains(mx, my)) {
            dropdown.scroll(-i);
            return true;
        }
        return false;
    }

    @Override
    public void onGuiClick(int mx, int my) {
        if (dropdown.isVisible() && !contains(mx, my)) dropdown.toggle();
    }

    public void closeDropdown() {
        if (dropdown.isVisible()) dropdown.toggle();
    }

    @Override
    public List<String> handleTooltip(int mx, int my, List<String> tooltip) {
        if (dropdown.contains(mx, my)) return tooltip;
        return super.handleTooltip(mx, my, tooltip);
    }

    private static class Dropdown extends GuiScrollSlot {

        private boolean visible = false;
        private final Widget parent;
        private List<ItemCategories.Category> categories = Collections.emptyList();

        private static final int ROW_PADDING = 4;
        private static final int MIN_WIDTH = 100;
        private static final int MAX_WIDTH = 200;
        private static final int MAX_HEIGHT = 100;

        private static final DrawableResource EYE_OPEN = new DrawableBuilder(
                "nei:textures/nei_sprites.png",
                0,
                112,
                10,
                10).build();
        private static final DrawableResource EYE_CLOSED = new DrawableBuilder(
                "nei:textures/nei_sprites.png",
                10,
                112,
                10,
                10).build();

        public Dropdown(Widget parent) {
            super(0, 0, 0, 0);
            this.parent = parent;

            this.zLevel = GuiContainerManager.drawItems.zLevel + 100;
        }

        public void toggle() {
            visible = !visible;

            if (visible) {
                categories = ItemCategories.all();
                recalculateLayout();
            }
        }

        private void recalculateLayout() {
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;

            int textWidth = 0;
            for (ItemCategories.Category category : categories) {
                textWidth = Math.max(textWidth, fr.getStringWidth(StatCollector.translateToLocal(category.nameKey)));
            }

            int height = Math.min(contentHeight(), MAX_HEIGHT);

            int scrollbarWidth = scrollbarDim().width;
            boolean needsScrollbar = contentHeight() > height;

            int width = textOffset() + textWidth + ROW_PADDING + (needsScrollbar ? scrollbarWidth : 0);

            Rectangle4i p = parent.bounds();
            int maxWidth = Math.min(MAX_WIDTH, p.x2() - 2);
            width = Math.max(MIN_WIDTH, Math.min(width, maxWidth));

            setSize(p.x2() - width, p.y2() + 2, width, height);
            setMargins(0, 0, needsScrollbar ? scrollbarWidth : 0, 0);
        }

        private static int textOffset() {
            return 2 + EYE_OPEN.width + 4;
        }

        public boolean isVisible() {
            return visible;
        }

        @Override
        public int getSlotHeight(int slot) {
            return 16;
        }

        @Override
        protected int getNumSlots() {
            return categories.size();
        }

        @Override
        protected void slotClicked(int slot, int button, int mx, int my, int count) {
            ItemCategories.Category category = categories.get(slot);
            ItemCategories.setHidden(category, !ItemCategories.isHidden(category));
            NEIClientUtils.playClickSound();
        }

        @Override
        protected void drawSlot(int slot, int x, int y, int mouseX, int mouseY, float frame) {
            ItemCategories.Category category = categories.get(slot);
            boolean shown = !ItemCategories.isHidden(category);
            int width = windowBounds().width;
            int height = getSlotHeight(slot);

            Rectangle rect = new Rectangle(x, y, width, height);
            Point m = GuiDraw.getMousePosition();
            boolean hover = windowBounds().contains(m.x, m.y) && rect.contains(m.x, m.y);

            LayoutManager.getLayoutStyle().drawSubsetTag(null, x, y, width, height, shown ? 2 : 0, hover);

            LayoutManager.drawIcon(x + 2, y + (height - EYE_OPEN.height + 2) / 2, shown ? EYE_OPEN : EYE_CLOSED);

            int textColor = hover ? 0xFFFFFFA0 : shown ? 0xFFE0E0E0 : 0xFFA0A0A0;
            GuiDraw.drawString(
                    StatCollector.translateToLocal(category.nameKey),
                    x + EYE_OPEN.width + 4,
                    y + (height - 8) / 2,
                    textColor);
        }

        @Override
        public void draw(int mx, int my, float frame) {
            recalculateLayout();
            super.draw(mx, my, frame);
        }

        @Override
        public void drawContent(int mx, int my, float frame) {
            Rectangle w = windowBounds(); // the area inside the pane, excluding the scrollbar
            GuiHelper.useScissor(w.x, w.y, w.width, w.height, () -> super.drawContent(mx, my, frame));
        }

        public boolean contains(int x, int y) {
            if (!visible) {
                return false;
            }

            return bounds().contains(x, y);
        }

        @Override
        public int scrollbarGuideAlignment() {
            return 0;
        }

        @Override
        public void drawScrollbar(float frame) {
            if (hasScrollbar()) super.drawScrollbar(frame);
        }
    }
}
