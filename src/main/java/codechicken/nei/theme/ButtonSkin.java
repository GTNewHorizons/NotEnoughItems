package codechicken.nei.theme;

import org.lwjgl.opengl.GL11;

import codechicken.nei.drawable.Drawable;

public final class ButtonSkin {

    private final Drawable normal;
    private final Drawable hover;
    private final Drawable active;
    private final Drawable disabled;

    public ButtonSkin(Drawable normal, Drawable hover, Drawable active, Drawable disabled) {
        this.normal = normal;
        this.hover = hover;
        this.active = active;
        this.disabled = disabled;
    }

    public Drawable background(boolean enabled, boolean active, boolean hovered) {
        if (!enabled) return this.disabled;
        if (active) return this.active;
        return hovered ? this.hover : this.normal;
    }

    public void draw(int x, int y, int w, int h, Drawable icon, boolean enabled, boolean active, boolean hovered) {
        GL11.glColor4f(1, 1, 1, 1);
        background(enabled, active, hovered).draw(x, y, w, h);
        drawIcon(x, y, w, h, icon, enabled ? 0xFFFFFFFF : 0x80FFFFFF);
    }

    public static void drawIcon(int x, int y, int w, int h, Drawable icon, int argb) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4ub((byte) (argb >> 16), (byte) (argb >> 8), (byte) argb, (byte) (argb >>> 24));
        icon.draw(x + (w - icon.getWidth()) / 2, y + (h - icon.getHeight()) / 2);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glDisable(GL11.GL_BLEND);
    }

}
