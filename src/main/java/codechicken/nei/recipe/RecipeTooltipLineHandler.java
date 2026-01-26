package codechicken.nei.recipe;

import java.awt.Dimension;

import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.gui.GuiDraw.ITooltipLineHandler;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.scroll.GuiHelper;

public class RecipeTooltipLineHandler implements ITooltipLineHandler {

    private static final int BG_PADDING = 5;
    private static final int TRANSPARENCY_BORDER = 4;
    private final DrawableResource BG_TEXTURE = new DrawableBuilder(
            "nei:textures/gui/recipebg.png",
            0,
            0,
            176 + TRANSPARENCY_BORDER * 2,
            166 + TRANSPARENCY_BORDER * 2).build();

    protected long lastUpdate = System.currentTimeMillis();
    protected String recipeName = "";
    protected GuiRecipe<?> gui = null;
    protected RecipeHandlerRef handlerRef = null;
    protected NEIRecipeWidget widget = null;
    protected RecipeId recipeId = null;
    protected boolean createdGui = false;

    public RecipeTooltipLineHandler(RecipeId recipeId) {
        this.recipeId = recipeId;
    }

    public ItemStack getItemStack() {
        return this.recipeId.getResult();
    }

    public RecipeId getRecipeId() {
        return this.recipeId;
    }

    @Override
    public Dimension getSize() {

        if (this.widget == null && !this.createdGui) {
            this.handlerRef = RecipeHandlerRef.of(this.recipeId);
            this.createdGui = true;

            if (handlerRef != null) {
                this.widget = new NEIRecipeWidget(this.handlerRef);
                this.widget.showAsWidget(true);
                this.widget.x = BG_PADDING;
                this.widget.y = BG_PADDING + 12;

                this.recipeName = this.widget.getRecipeHandlerRef().handler.getRecipeName().trim();
            }
        }

        if (this.widget != null) {
            return new Dimension(this.widget.w + BG_PADDING * 2, this.widget.h + BG_PADDING * 2 + 12);
        }

        return new Dimension(0, 0);
    }

    @Override
    public void draw(int x, int y) {
        if (this.widget == null) return;

        if ((System.currentTimeMillis() - lastUpdate) > 50) {
            lastUpdate = System.currentTimeMillis();
            this.handlerRef.handler.onUpdate();
            this.widget.update();
        }

        final Dimension size = getSize();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);

        final int zTranslation = GuiContainerManager.TOOLTIP_Z_OFFSET;
        GL11.glTranslatef(x, y, zTranslation);

        GuiContainerManager.enable2DRender();
        GL11.glColor4f(1, 1, 1, 1);

        BG_TEXTURE.draw(
                -TRANSPARENCY_BORDER,
                -TRANSPARENCY_BORDER,
                size.width + TRANSPARENCY_BORDER * 2,
                size.height + TRANSPARENCY_BORDER * 2,
                BG_PADDING + TRANSPARENCY_BORDER,
                BG_PADDING + TRANSPARENCY_BORDER,
                BG_PADDING + TRANSPARENCY_BORDER,
                BG_PADDING + TRANSPARENCY_BORDER);

        GL11.glEnable(GL11.GL_ALPHA_TEST);

        GuiDraw.drawRect(BG_PADDING, BG_PADDING, size.width - BG_PADDING * 2, 12, 0x30000000);
        GuiDraw.drawStringC(this.recipeName, this.widget.w / 2, BG_PADDING + 2, 0xffffff);

        GuiHelper.useScissor(
                this.widget.x,
                this.widget.y,
                this.widget.w,
                this.widget.h,
                () -> { this.widget.draw(0, 0); });

        GL11.glPopAttrib();
        GL11.glTranslatef(-x, -y, -zTranslation);
    }

}
