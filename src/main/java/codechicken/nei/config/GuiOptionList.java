package codechicken.nei.config;

import static codechicken.lib.gui.GuiDraw.drawMultilineTip;
import static codechicken.lib.gui.GuiDraw.fontRenderer;
import static codechicken.lib.gui.GuiDraw.getMousePosition;
import static net.minecraft.util.StatCollector.translateToLocal;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import codechicken.core.gui.GuiCCButton;
import codechicken.core.gui.GuiScreenWidget;
import codechicken.core.gui.GuiScrollSlot;

public class GuiOptionList extends GuiScreenWidget {

    public class OptionScrollSlot extends GuiScrollSlot {

        public final ArrayList<Option> options = new ArrayList<>();

        public OptionScrollSlot() {
            super(0, 0, 0, 0);
            setMargins(0, 4, 20, 4);
        }

        @Override
        public void onAdded(GuiScreen s) {
            super.onAdded(s);
            options.addAll(optionList.optionList);
            for (Option o : options) o.onAdded(this);
        }

        @Override
        public int getSlotHeight(int slot) {
            return options.get(slot).getHeight() + 4;
        }

        public int slotWidth() {
            return windowBounds().width - 24;
        }

        private Rectangle slotBounds(int slot) {
            return new Rectangle(24, 2, slotWidth(), getSlotHeight(slot) - 4);
        }

        @Override
        protected int getNumSlots() {
            return options.size();
        }

        @Override
        protected void drawSlot(int slot, int x, int y, int mx, int my, float frame) {
            Option o = options.get(slot);
            GL11.glTranslatef(x, y, 0);
            GL11.glTranslatef(24, 2, 0);
            o.draw(mx - 24, my - 2, frame);
            GL11.glTranslatef(-x - 24, -y - 2, 0);
        }

        @Override
        public void drawBackground(float frame) {
            Rectangle sbar = scrollbarBounds();
            drawRect(sbar.x, y, sbar.x + sbar.width, y + height, 0xFF000000);
        }

        @Override
        public void drawOverlay(float frame) {
            OptionScrollPane.drawOverlay(y, height, parentScreen.width, zLevel);
        }

        @Override
        public int scrollbarGuideAlignment() {
            return 0;
        }

        @Override
        public Dimension scrollbarDim() {
            Dimension dim = super.scrollbarDim();
            dim.width = 6;
            return dim;
        }

        @Override
        public void update() {
            super.update();
            for (Option o : options) o.update();
        }

        @Override
        protected void slotClicked(int slot, int button, int mx, int my, int count) {
            if (slotBounds(slot).contains(mx, my)) {
                options.get(slot).mouseClicked(mx - 24, my - 2, button);
            }
        }

        @Override
        public void mouseClicked(int mx, int my, int button) {
            for (Option o : options) {
                o.onMouseClicked(mx, my, button);
            }

            super.mouseClicked(mx, my, button);
        }

        @Override
        public void keyTyped(char c, int keycode) {
            super.keyTyped(c, keycode);
            for (Option o : options) o.keyTyped(c, keycode);
        }

        public void resize() {
            int width = Math.min(parentScreen.width - 80, 320);
            setSize((parentScreen.width - width) / 2, 20, width, parentScreen.height - 50);
        }

        @Override
        public void mouseScrolled(int x, int y, int scroll) {
            scroll(-scroll);
        }

        public GuiOptionList getGui() {
            return ((GuiOptionList) parentScreen);
        }

        public List<String> handleTooltip(int mx, int my, List<String> tooltip) {
            int sy = my - y + scrolledPixels();
            int slot = getSlot(sy);
            if (slot >= 0) tooltip = handleTooltip(slot, mx - x, sy - getSlotY(slot), tooltip);
            return tooltip;
        }

        private List<String> handleTooltip(int slot, int mx, int my, List<String> tooltip) {
            if (slotBounds(slot).contains(mx, my)) return options.get(slot).handleTooltip(mx - 24, my - 2, tooltip);
            return tooltip;
        }
    }

    protected final GuiScreen parent;
    protected final OptionList optionList;

    protected OptionScrollSlot slot;
    protected GuiCCButton backButton;

    public GuiOptionList(GuiScreen parent, OptionList optionList) {
        this.parent = parent;
        this.optionList = optionList;
    }

    @Override
    public void initGui() {
        xSize = width;
        ySize = height;
        super.initGui();
    }

    @Override
    public void resize() {
        slot.resize();
        backButton.width = Math.min(200, width - 40);
        backButton.x = (width - backButton.width) / 2;
        backButton.y = height - 25;
    }

    @Override
    public void addWidgets() {
        add(slot = new OptionScrollSlot());
        add(backButton = new GuiCCButton(0, 0, 0, 20, translateToLocal("nei.options.back")).setActionCommand("back"));
    }

    @Override
    public void actionPerformed(String ident, Object... params) {
        if (ident.equals("back")) {
            Minecraft.getMinecraft().displayGuiScreen(parent);
        }
    }

    @Override
    public void drawBackground() {
        drawDefaultBackground();
    }

    @Override
    public void drawForeground() {
        drawCenteredString(fontRenderer, translateToLocal(optionList.fullName()), width / 2, 6, -1);
        drawTooltip();
    }

    private void drawTooltip() {
        List<String> tooltip = new LinkedList<>();
        Point mouse = getMousePosition();
        tooltip = slot.handleTooltip(mouse.x, mouse.y, tooltip);
        drawMultilineTip(mouse.x + 12, mouse.y - 12, tooltip);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void keyTyped(char c, int keycode) {
        if (keycode == Keyboard.KEY_ESCAPE) {
            GuiScreen p = parent;
            while (p instanceof GuiOptionList) p = ((GuiOptionList) p).parent;

            Minecraft.getMinecraft().displayGuiScreen(p);
        } else {
            super.keyTyped(c, keycode);
        }
    }
}
