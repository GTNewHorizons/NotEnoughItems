package codechicken.nei;

import static codechicken.lib.gui.GuiDraw.drawString;
import static codechicken.lib.gui.GuiDraw.drawStringC;

public class Label extends Widget {
    final boolean centered;
    final int colour;
    public String text;
    boolean visible = true;

    public Label(String s, boolean center, int color) {
        text = s;
        centered = center;
        colour = color;
    }

    public Label(String s, boolean center) {
        this(s, center, 0xFFFFFFFF);
    }

    @Override
    public void draw(int mousex, int mousey) {
        if (!visible) return;
        if (centered) drawStringC(text, x, y, colour);
        else drawString(text, x, y, colour);
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
