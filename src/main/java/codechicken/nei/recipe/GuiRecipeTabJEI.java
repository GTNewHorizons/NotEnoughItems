package codechicken.nei.recipe;

import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;

public class GuiRecipeTabJEI extends GuiRecipeTab {
    public static final int TAB_WIDTH = 24;
    public static final int TAB_HEIGHT = 24;
    private static final DrawableResource selectedTabImage =
            new DrawableBuilder("nei:textures/nei_tabbed_sprites.png", 0, 16, 24, 24).build();
    private static final DrawableResource unselectedTabImage =
            new DrawableBuilder("nei:textures/nei_tabbed_sprites.png", 24, 16, 24, 24).build();

    public GuiRecipeTabJEI(GuiRecipe<?> guiRecipe, IRecipeHandler handler, int x, int y) {
        super(guiRecipe, handler, x, y);
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
        return x + 4;
    }

    @Override
    protected int getForegroundIconY() {
        return y + 4;
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
