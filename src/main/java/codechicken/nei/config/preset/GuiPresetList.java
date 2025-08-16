package codechicken.nei.config.preset;

import static codechicken.lib.gui.GuiDraw.getStringWidth;

import java.awt.Rectangle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import codechicken.core.gui.GuiCCButton;
import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PresetsList;
import codechicken.nei.PresetsList.Preset;
import codechicken.nei.config.GuiOptionPane;
import codechicken.nei.config.Option;

public class GuiPresetList extends GuiOptionPane {

    private final Option opt;
    protected static final int SLOT_HEIGHT = 24;
    protected static final int BUTTON_HEIGHT = 20;
    protected GuiCCButton createButton;
    protected GuiCCButton toggleButton;
    protected int sortingItemIndex = -1;

    public GuiPresetList(Option opt) {
        this.opt = opt;
        this.createButton = new GuiCCButton(0, 2, 0, 16, NEIClientUtils.translate("presets.new"))
                .setActionCommand("create");
        this.toggleButton = new GuiCCButton(0, 2, 0, 16, NEIClientUtils.translate("presets.toggle"))
                .setActionCommand("toggle");
    }

    @Override
    public void initGui() {
        super.initGui();

        this.createButton.width = getStringWidth(this.createButton.text) + 15;
        this.createButton.x = width - this.createButton.width - 15;

        this.toggleButton.width = getStringWidth(this.toggleButton.text) + 15;
        this.toggleButton.x = width - this.toggleButton.width - 15 - this.createButton.width - 10;
    }

    @Override
    public void addWidgets() {
        super.addWidgets();
        add(this.createButton);
        add(this.toggleButton);
    }

    @Override
    public void actionPerformed(String ident, Object... params) {
        super.actionPerformed(ident, params);

        if (ident.equals("create")) {
            cretePreset();
        }

        if (ident.equals("toggle")) {
            togglePreset();
        }
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int button) {
        super.mouseMovedOrUp(x, y, button);

        if (sortingItemIndex >= 0) {
            PresetsList.savePresets();
        } else if (button == 0) {
            int slot = getSlotMouseOver(x, y);

            if (slot != -1) {
                Rectangle w = pane.windowBounds();
                int mx = x - w.x;

                if (mx <= BUTTON_HEIGHT) {
                    Preset preset = PresetsList.presets.get(slot);
                    preset.enabled = !preset.enabled;
                    PresetsList.savePresets();
                } else if (mx >= 26 && mx <= w.width - 26 * 2) {
                    openPreset(slot);
                } else if (mx >= w.width - BUTTON_HEIGHT) {
                    PresetsList.presets.remove(slot);
                    PresetsList.savePresets();
                }
            }
        }

        sortingItemIndex = -1;
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long time) {
        super.mouseClickMove(x, y, button, time);

        if (sortingItemIndex == -1) {
            Rectangle w = pane.windowBounds();
            int mx = x - w.x;

            if (mx >= 26 && mx <= w.width - 26 * 2) {
                sortingItemIndex = getSlotMouseOver(x, y);
            } else {
                sortingItemIndex = -2;
            }
        } else if (sortingItemIndex >= 0) {
            Rectangle w = pane.windowBounds();

            if (y >= w.y && y <= w.y + w.height) {
                int my = y + pane.scrolledPixels() - w.y;
                int slot = my / SLOT_HEIGHT;

                if (my % SLOT_HEIGHT < BUTTON_HEIGHT && slot < PresetsList.presets.size()
                        && slot != -1
                        && slot != sortingItemIndex) {
                    PresetsList.presets.add(slot, PresetsList.presets.remove(sortingItemIndex));
                    sortingItemIndex = slot;
                }
            }
        }
    }

    protected int getSlotMouseOver(int x, int y) {
        Rectangle w = pane.windowBounds();

        if (x >= w.x && x <= w.x + w.width && y >= w.y && y <= w.y + w.height) {
            int my = y + pane.scrolledPixels() - w.y;
            int slot = my / SLOT_HEIGHT;

            if (my % SLOT_HEIGHT < BUTTON_HEIGHT && slot < PresetsList.presets.size()) {
                return slot;
            }
        }

        return -1;
    }

    protected void cretePreset() {
        Minecraft.getMinecraft().displayGuiScreen(new GuiPresetSettings(this, -1));
    }

    protected void togglePreset() {
        boolean enabled = PresetsList.presets.stream().noneMatch(g -> g.enabled);

        for (Preset preset : PresetsList.presets) {
            preset.enabled = enabled;
        }

        PresetsList.savePresets();
    }

    protected void openPreset(int slot) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiPresetSettings(this, slot));
    }

    @Override
    public int contentHeight() {
        return PresetsList.presets.size() * SLOT_HEIGHT;
    }

    @Override
    public void drawContent(int mx, int my, float frame) {
        int scrolled = pane.scrolledPixels();
        Rectangle w = pane.windowBounds();
        int y = 0;

        for (int slot = 0; slot < PresetsList.presets.size(); ++slot) {

            if (y + SLOT_HEIGHT > scrolled && y < scrolled + w.height) {

                if (sortingItemIndex != slot) {
                    drawSlot(w, slot, w.x, w.y + y - scrolled, mx, my - y);
                }

            }

            y += SLOT_HEIGHT;
        }

        if (sortingItemIndex >= 0) {
            drawSlot(w, sortingItemIndex, w.x, w.y + my - scrolled - BUTTON_HEIGHT / 2, mx, my - y);
        }
    }

    protected void drawSlot(Rectangle w, int slot, int x, int y, int mx, int my) {
        final Rectangle4i delete = new Rectangle4i(x + w.width - BUTTON_HEIGHT, y, BUTTON_HEIGHT, BUTTON_HEIGHT);
        final Rectangle4i enabled = new Rectangle4i(x, y, BUTTON_HEIGHT, BUTTON_HEIGHT);
        final Rectangle4i option = new Rectangle4i(x + 26, y, w.width - 26 * 2, BUTTON_HEIGHT);

        final int enabledState = y + my >= w.y && y + my <= w.y + w.height && enabled.contains(x + mx, y + my) ? 2 : 1;
        final int optionState = y + my >= w.y && y + my <= w.y + w.height && option.contains(x + mx, y + my) ? 2 : 1;
        final int deleteState = y + my >= w.y && y + my <= w.y + w.height && delete.contains(x + mx, y + my) ? 2 : 1;
        final Preset preset = PresetsList.presets.get(slot);

        final String displayName = preset.name;
        final String modeName;

        switch (preset.mode) {
            case HIDE:
                modeName = EnumChatFormatting.GOLD + NEIClientUtils.translate("presets.mode.hide.char")
                        + EnumChatFormatting.RESET;
                break;
            case SUBSET:
                modeName = EnumChatFormatting.DARK_PURPLE + NEIClientUtils.translate("presets.mode.subset.char")
                        + EnumChatFormatting.RESET;
                break;
            case REMOVE:
                modeName = EnumChatFormatting.RED + NEIClientUtils.translate("presets.mode.remove.char")
                        + EnumChatFormatting.RESET;
                break;
            case GROUP:
                modeName = EnumChatFormatting.AQUA + NEIClientUtils.translate("presets.mode.group.char")
                        + EnumChatFormatting.RESET;
                break;
            default:
                modeName = "?";
                break;
        }

        // enabled icon
        GL11.glColor4f(1, 1, 1, 1);
        LayoutManager.drawButtonBackground(
                enabled.x,
                enabled.y,
                enabled.w,
                enabled.h,
                true,
                preset.enabled ? 0 : enabledState);
        GuiDraw.drawString("✔", enabled.x + 7, enabled.y + 6, enabledState == 2 ? 0xFFFFFFA0 : 0xFFE0E0E0);

        // preset name
        GL11.glColor4f(1, 1, 1, 1);
        LayoutManager.drawButtonBackground(option.x, option.y, option.w, option.h, true, optionState);
        GuiDraw.drawString("⋮⋮", option.x + 4, option.y + 5, optionState == 2 ? 0xFFFFFFA0 : 0xFFE0E0E0);
        GuiDraw.drawString(
                NEIClientUtils.cropText(this.fontRendererObj, modeName + " " + displayName, option.w - 16),
                option.x + 6 + 4,
                option.y + 6,
                optionState == 2 ? 0xFFFFFFA0 : 0xFFE0E0E0);

        // remove icon
        GL11.glColor4f(1, 1, 1, 1);
        LayoutManager.drawButtonBackground(delete.x, delete.y, delete.w, delete.h, true, deleteState);
        GuiDraw.drawString("✕", delete.x + 7, delete.y + 6, deleteState == 2 ? 0xFFFFFFA0 : 0xFFE0E0E0);
    }

    @Override
    public GuiScreen getParentScreen() {
        return opt.slot.getGui();
    }

    @Override
    public String getTitle() {
        return opt.translateN(opt.name);
    }

}
