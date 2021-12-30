package codechicken.nei.config;

import codechicken.nei.Image;
import codechicken.nei.LayoutManager;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import org.lwjgl.opengl.GL11;

import java.awt.Rectangle;

public class OptionToggleButtonBoubs extends OptionButton {
    private static final DrawableResource coolBoubs = new DrawableBuilder("nei:textures/nei_tabbed_sprites.png", 48, 16, 22, 22).build();
    private static final DrawableResource angryBoubs = new DrawableBuilder("nei:textures/nei_tabbed_sprites.png", 70, 16, 22, 22).build();

    public final boolean prefixed;

    public OptionToggleButtonBoubs(String name, boolean prefixed) {
        super(name);
        this.prefixed = prefixed;
    }

    public OptionToggleButtonBoubs(String name) {
        this(name, false);
    }

    public boolean state() {
        return renderTag().getBooleanValue();
    }

    @Override
    public void drawButton(int mx, int my) {
        Rectangle b = buttonSize();
        LayoutManager.drawButtonBackground(b.x, b.y, b.width, b.height, true, getButtonTex(mx, my));
        final Image image = state() ? coolBoubs : angryBoubs;
        LayoutManager.drawIcon(b.x + (b.width/2) - (coolBoubs.getWidth()/2), b.y, image);
    }
    
    public String getButtonText() {
        return translateN(name + (state() ? ".true" : ".false"));
    }

    @Override
    public String getPrefix() {
        return prefixed ? translateN(name) : null;
    }

    @Override
    public boolean onClick(int button) {
        getTag().setBooleanValue(!state());
        return true;
    }
}
