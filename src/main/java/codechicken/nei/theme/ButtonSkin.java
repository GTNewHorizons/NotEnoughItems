package codechicken.nei.theme;

import org.lwjgl.opengl.GL11;

import codechicken.nei.drawable.DrawableResource;

public final class ButtonSkin {

    private final DrawableResource normal;
    private final DrawableResource hover;
    private final DrawableResource active;
    private final DrawableResource disabled;

    public ButtonSkin(DrawableResource normal, DrawableResource hover, DrawableResource active,
            DrawableResource disabled) {
        this.normal = normal;
        this.hover = hover;
        this.active = active;
        this.disabled = disabled;
    }

    public DrawableResource background(boolean enabled, boolean active, boolean hovered) {
        if (!enabled) return this.disabled;
        if (active) return this.active;
        return hovered ? this.hover : this.normal;
    }

    public void draw(int x, int y, int w, int h, DrawableResource icon, boolean enabled, boolean active,
            boolean hovered) {
        GL11.glColor4f(1, 1, 1, 1);
        background(enabled, active, hovered).draw(x, y, w, h);
        drawIcon(x, y, w, h, icon, enabled);
    }

    public static void drawIcon(int x, int y, int w, int h, DrawableResource icon, boolean enabled) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1, 1, 1, enabled ? 1 : 0.5f);
        icon.draw(x + (w - icon.getWidth()) / 2, y + (h - icon.getHeight()) / 2);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
    }

}
