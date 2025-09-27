package codechicken.nei.recipe;

import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;

public abstract class GuiRecipeTabCreative extends GuiRecipeTab {

    public static final int TAB_WIDTH = 28;
    public static final int TAB_HEIGHT = 31;
    private static final DrawableResource selectedTabImage = new DrawableBuilder(
            "minecraft:textures/gui/container/creative_inventory/tabs.png",
            28,
            32,
            28,
            32).build();
    private static final DrawableResource unselectedTabImage = new DrawableBuilder(
            "minecraft:textures/gui/container/creative_inventory/tabs.png",
            28,
            0,
            28,
            30).build();

    public GuiRecipeTabCreative(IRecipeHandler handler, int x, int y) {
        super(handler, x, y);
    }

    @Override
    public int getWidth() {
        return TAB_WIDTH;
    }

    @Override
    public int getHeight() {
        return TAB_HEIGHT;
    }

    @Override
    protected int getForegroundIconX() {
        return x + (w - 16) / 2;
    }

    @Override
    protected int getForegroundIconY() {
        return y + h - 22;
    }

    @Override
    public DrawableResource getSelectedTabImage() {
        return selectedTabImage;
    }

    @Override
    public DrawableResource getUnselectedTabImage() {
        return unselectedTabImage;
    }
}
