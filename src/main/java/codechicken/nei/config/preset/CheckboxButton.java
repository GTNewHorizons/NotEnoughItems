package codechicken.nei.config.preset;

import static codechicken.lib.gui.GuiDraw.drawStringC;

import org.lwjgl.opengl.GL11;

import codechicken.nei.Button;
import codechicken.nei.LayoutManager;

public abstract class CheckboxButton extends Button {

    protected boolean checked = false;

    public CheckboxButton(String s) {
        super(s);
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
        this.onChange();
    }

    public boolean isChecked() {
        return this.checked;
    }

    @Override
    public void draw(int mousex, int mousey) {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor4f(1, 1, 1, 1);
        int tex;

        if ((this.state & 0x3) == 2) {
            tex = 0;
        } else if ((this.state & 0x4) == 0 && contains(mousex, mousey) || (this.state & 0x3) == 1) {
            tex = 2;
        } else {
            tex = 1;
        }

        int colour = tex == 2 ? 0xffffa0 : tex == 0 ? 0x601010 : 0xe0e0e0;
        LayoutManager.drawButtonBackground(x, y, w, h, true, isChecked() ? 0 : tex);
        drawStringC(getRenderLabel(), x + w / 2, y + (h - 8) / 2, colour);
    }

    public boolean onButtonPress(boolean rightclick) {
        this.setChecked(!isChecked());
        return true;
    }

    protected abstract void onChange();

}
