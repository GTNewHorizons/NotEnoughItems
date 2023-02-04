package codechicken.nei.config;

import static codechicken.lib.gui.GuiDraw.drawRect;
import static codechicken.lib.gui.GuiDraw.drawStringC;

import java.awt.Rectangle;

import org.lwjgl.input.Keyboard;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;

public class OptionKeyBind extends OptionButton {

    private boolean editing = false;
    private boolean useHash = false;
    private int keyboard = Keyboard.CHAR_NONE;

    public OptionKeyBind(String name) {
        this(name, false);
    }

    public OptionKeyBind(final String name, final boolean useHash) {
        super("keys." + name);
        this.useHash = useHash;
    }

    @Override
    public void onMouseClicked(int mousex, int mousey, int button) {
        if (editing) {
            resetChanges();
        }
    }

    @Override
    public void keyTyped(char c, int keycode) {

        if (!editing) {
            return;
        }

        if (keycode == Keyboard.KEY_RETURN) {
            applyChanges();
            return;
        }

        keyboard = useHash ? NEIClientUtils.getKeyHash() : keycode;

        if (keyboard != Keyboard.CHAR_NONE) {
            applyChanges();
        }
    }

    @Override
    public boolean onClick(int btn) {

        if (editing) {
            return true;
        } else if (btn == 0) {
            editing = true;
            return true;
        } else if (btn == 1) {
            keyboard = Keyboard.CHAR_NONE;
            applyChanges();
            return true;
        }

        return false;
    }

    protected void applyChanges() {
        setValue(keyboard);
        resetChanges();
    }

    protected void resetChanges() {
        editing = false;
        keyboard = Keyboard.CHAR_NONE;
    }

    public boolean conflicted() {
        final int keyboard = getValue();

        if (keyboard <= 0) {
            return false;
        }

        for (Option o : slot.options) {
            if (o instanceof OptionKeyBind && o != this && ((OptionKeyBind) o).getValue() == keyboard) {
                return true;
            }
        }

        return false;
    }

    public void setValue(int keycode) {
        getTag().setIntValue(keycode);
    }

    public int getValue() {
        return renderTag().getIntValue();
    }

    @Override
    public String getPrefix() {
        return translateN(name);
    }

    @Override
    public void drawButton(int mx, int my) {

        if (editing) {
            Rectangle b = buttonSize();
            drawRect(b.x, b.y, b.width, b.height, -6250336);
            drawRect(b.x + 1, b.y + 1, b.width - 2, b.height - 2, -16777216);
            drawStringC(getButtonText(), b.x, b.y, b.width, b.height, getTextColour(mx, my));
        } else {
            super.drawButton(mx, my);
        }
    }

    @Override
    public String getButtonText() {
        final int metahash = Math.max(editing ? NEIClientUtils.getMetaHash() : getValue(), 0);
        return NEIClientConfig.getKeyName(metahash, useHash, editing);
    }

    @Override
    public int getTextColour(int mousex, int mousey) {
        return !editing && conflicted() ? 0xFFFF6666 : -1;
    }
}
