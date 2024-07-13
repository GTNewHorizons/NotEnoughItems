package codechicken.nei.config.preset;

import java.util.LinkedList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;
import org.lwjgl.util.Rectangle;

import codechicken.core.gui.GuiScreenWidget;
import codechicken.nei.Button;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PresetsList;
import codechicken.nei.PresetsList.Preset;
import codechicken.nei.config.OptionScrollPane;
import codechicken.nei.guihook.GuiContainerManager;

public class GuiPresetSettings extends GuiScreenWidget {

    protected final Preset preset;
    protected final GuiScreen parent;
    protected final int slotIndex;

    public int marginleft;
    public int margintop;
    public int marginright;
    public int marginbottom;

    protected LeftPanel leftPanel;
    protected RightPanel rightPanel;

    protected Button backButton;
    protected Button saveButton;

    public GuiPresetSettings(GuiScreen parent, int slotIndex) {
        this.parent = parent;
        this.setMargins(6, 20, 6, 30);

        this.slotIndex = slotIndex;

        if (slotIndex == -1) {
            preset = new Preset();
        } else {
            preset = PresetsList.presets.get(slotIndex).copy();
        }

        leftPanel = new LeftPanel(preset) {

            @Override
            protected void onItemsChanges() {
                leftPanel.grid.restartFilter();
            }
        };
        rightPanel = new RightPanel(preset, slotIndex) {

            @Override
            protected void onItemsChanges() {
                leftPanel.grid.restartFilter();
            }
        };

        backButton = new Button(NEIClientUtils.translate("options.back")) {

            public boolean onButtonPress(boolean rightclick) {
                Minecraft.getMinecraft().displayGuiScreen(parent);
                return true;
            }
        };

        saveButton = new Button(NEIClientUtils.translate("options.save")) {

            public boolean onButtonPress(boolean rightclick) {
                if (state == 0) {

                    if (slotIndex == -1) {
                        PresetsList.presets.add(preset);
                    } else {
                        PresetsList.presets.set(slotIndex, preset);
                    }

                    PresetsList.savePresets();
                    Minecraft.getMinecraft().displayGuiScreen(parent);

                    return true;
                }
                return false;
            }
        };

        add(this.leftPanel);
        add(this.rightPanel);
    }

    protected void setMargins(int left, int top, int right, int bottom) {
        this.marginleft = left;
        this.margintop = top;
        this.marginright = right;
        this.marginbottom = bottom;
    }

    protected Rectangle windowBounds() {
        return new Rectangle(
                this.marginleft,
                this.margintop,
                this.width - this.marginleft - this.marginright,
                this.height - this.margintop - this.marginbottom);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    @Override
    public void updateScreen() {
        final Rectangle bounds = windowBounds();
        final int panelWidth = bounds.getWidth() * 2 / 5;

        this.leftPanel.setSize(bounds.getX(), bounds.getY() + 4, panelWidth, bounds.getHeight() - 6);
        this.rightPanel.setSize(
                bounds.getX() + bounds.getWidth() - panelWidth,
                bounds.getY() + 4,
                panelWidth,
                bounds.getHeight() - 6);

        final int w = Math.min(200, width - 40);

        backButton.w = w / 2 - 3;
        backButton.h = 20;
        backButton.x = (width - w) / 2;
        backButton.y = height - 25;

        saveButton.state = preset.name.isEmpty() || preset.mode == null || preset.items.isEmpty() ? 2 : 0;

        saveButton.w = w / 2 - 3;
        saveButton.h = 20;
        saveButton.x = width / 2 + 3;
        saveButton.y = height - 25;

        super.updateScreen();
    }

    @Override
    public void resize() {
        this.guiLeft = this.guiTop = 0;
    }

    @Override
    public void drawScreen(int mousex, int mousey, float f) {
        Rectangle bounds = windowBounds();
        drawDefaultBackground();

        drawOverlay(bounds.getY(), bounds.getHeight(), width, zLevel);
        drawCenteredString(
                Minecraft.getMinecraft().fontRenderer,
                NEIClientUtils.translate(this.slotIndex == -1 ? "presets.create" : "presets.update"),
                width / 2,
                6,
                -1);

        backButton.draw(mousex, mousey);
        saveButton.draw(mousex, mousey);

        super.drawScreen(mousex, mousey, f);

        List<String> tooltip = new LinkedList<>();
        tooltip = leftPanel.handleTooltip(mousex, mousey, tooltip);
        tooltip = rightPanel.handleTooltip(mousex, mousey, tooltip);

        GuiContainerManager.drawPagedTooltip(Minecraft.getMinecraft().fontRenderer, mousex + 12, mousey - 12, tooltip);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);

        if (backButton.contains(x, y)) {
            backButton.handleClick(x, y, button);
        }

        if (saveButton.contains(x, y)) {
            saveButton.handleClick(x, y, button);
        }
    }

    public static void drawOverlay(int y, int height, int screenwidth, float zLevel) {
        OptionScrollPane.drawOverlayTex(0, 0, screenwidth, y, zLevel);
        OptionScrollPane.drawOverlayTex(0, y + height, screenwidth, screenwidth - y - height, zLevel);
        OptionScrollPane.drawOverlayGrad(0, screenwidth, y, y + 4, zLevel);
        OptionScrollPane.drawOverlayGrad(0, screenwidth, y + height, y + height - 4, zLevel);
    }

    public static void drawOverlayTex(int x, int y, int w, int h, float zLevel) {
        GL11.glColor4f(1, 1, 1, 1);
        Minecraft.getMinecraft().renderEngine.bindTexture(Gui.optionsBackground);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y, zLevel, 0, 0);
        t.addVertexWithUV(x, y + h, zLevel, 0, h / 16D);
        t.addVertexWithUV(x + w, y + h, zLevel, w / 16D, h / 16D);
        t.addVertexWithUV(x + w, y, zLevel, w / 16D, 0);
        t.draw();
    }

    public static void drawOverlayGrad(int x1, int x2, int y1, int y2, float zLevel) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_I(0, 255);
        t.addVertex(x2, y1, zLevel);
        t.addVertex(x1, y1, zLevel);
        t.setColorRGBA_I(0, 0);
        t.addVertex(x1, y2, zLevel);
        t.addVertex(x2, y2, zLevel);
        t.draw();
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
