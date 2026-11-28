package codechicken.nei.theme;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.color.ColorResource;

import codechicken.nei.drawable.Drawable;

public final class ButtonSkin {

    private final Drawable normal;
    private final Drawable hover;
    private final Drawable active;
    private final Drawable disabled;

    private final ColorResource foregroundNormal;
    private final ColorResource foregroundHover;
    private final ColorResource foregroundActive;
    private final ColorResource foregroundDisabled;

    public ButtonSkin(Drawable normal, Drawable hover, Drawable active, Drawable disabled,
            ColorResource foregroundNormal, ColorResource foregroundHover, ColorResource foregroundActive,
            ColorResource foregroundDisabled) {

        this.normal = normal;
        this.hover = hover;
        this.active = active;
        this.disabled = disabled;

        this.foregroundNormal = foregroundNormal;
        this.foregroundHover = foregroundHover;
        this.foregroundActive = foregroundActive;
        this.foregroundDisabled = foregroundDisabled;
    }

    public ButtonSkin withForeground(ColorResource normal, ColorResource hover, ColorResource active,
            ColorResource disabled) {
        return new ButtonSkin(this.normal, this.hover, this.active, this.disabled, normal, hover, active, disabled);
    }

    public Drawable background(boolean enabled, boolean active, boolean hovered) {
        if (!enabled) return this.disabled;
        if (active) return this.active;
        return hovered ? this.hover : this.normal;
    }

    public int foreground(boolean enabled, boolean active, boolean hovered) {
        if (!enabled) return this.foregroundDisabled.getColor();
        if (active) return this.foregroundActive.getColor();
        return hovered ? this.foregroundHover.getColor() : this.foregroundNormal.getColor();
    }

    public void draw(int x, int y, int w, int h, Drawable icon, boolean enabled, boolean active, boolean hovered) {
        GL11.glColor4f(1, 1, 1, 1);
        background(enabled, active, hovered).draw(x, y, w, h);
        drawIcon(x, y, w, h, icon, enabled, active, hovered);
    }

    public void drawIcon(int x, int y, int w, int h, Drawable icon, boolean enabled, boolean active, boolean hovered) {
        final int argb = foreground(enabled, active, hovered);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4ub((byte) (argb >> 16), (byte) (argb >> 8), (byte) argb, (byte) (argb >>> 24));
        icon.draw(x + (w - icon.getWidth()) / 2, y + (h - icon.getHeight()) / 2);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glDisable(GL11.GL_BLEND);
    }

}
