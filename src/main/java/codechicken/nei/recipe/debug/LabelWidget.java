package codechicken.nei.recipe.debug;

import java.awt.Point;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.Widget;
import codechicken.nei.util.NEIMouseUtils;

public class LabelWidget extends Widget implements IUpdatableWidget {

    protected int color;
    protected boolean shadow;
    protected String value = "";
    protected String croppedValue;

    public LabelWidget(int color, boolean shadow) {
        this.color = color;
        this.shadow = shadow;
        this.h = 16;
        this.z = 2;
    }

    @Override
    public void draw(int mx, int my) {
        GuiDraw.drawString(this.croppedValue, this.x, this.y + (this.h - 8) / 2, this.color, this.shadow);
    }

    public void updateValue(String newValue) {
        this.value = newValue;
        this.croppedValue = NEIClientUtils.cropText(GuiDraw.fontRenderer, this.value, this.w);
    }

    @Override
    public List<String> handleTooltip(int mx, int my, List<String> tooltip) {

        if (contains(mx, my)) {
            tooltip.add(this.value);
        }

        return tooltip;
    }

    @Override
    public boolean handleKeyPress(int keyID, char keyChar) {
        final Point mouse = GuiDraw.getMousePosition();

        if (contains(mouse.x, mouse.y) && Keyboard.KEY_C == keyID && NEIClientUtils.controlKey()) {
            GuiScreen.setClipboardString(this.value);
            return true;
        }

        return false;
    }

    @Override
    public Map<String, String> handleHotkeys(int mousex, int mousey, Map<String, String> hotkeys) {

        if (contains(mousex, mousey)) {
            hotkeys.put(
                    NEIClientUtils.getKeyName(NEIClientUtils.CTRL_HASH + Keyboard.KEY_C, NEIMouseUtils.MOUSE_BTN_NONE),
                    NEIClientUtils.translate("debug.RecipeHandler.copy"));
        }

        return hotkeys;
    }

}
