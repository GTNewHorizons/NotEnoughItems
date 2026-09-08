package codechicken.nei.bookmark.tree;

import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11;

import codechicken.nei.NEIClientUtils;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerRef;

public class TreeOverlayButton extends GuiRecipeButton {

    protected static final DrawableResource ICON_FILL = new DrawableBuilder(
            "nei:textures/nei_sprites.png",
            28,
            76,
            9,
            10).build();
    protected static final int BUTTON_ID_SHIFT = 4;

    public final GuiCraftingTree treeGui;

    public TreeOverlayButton(GuiCraftingTree treeGui, RecipeHandlerRef handlerRef, int x, int y) {
        super(handlerRef, x, y, handlerRef.recipeIndex + BUTTON_ID_SHIFT, "+");
        this.treeGui = treeGui;
    }

    @Override
    public List<String> handleTooltip(List<String> currenttip) {
        currenttip.add(NEIClientUtils.translate("bookmark.tree.overlay"));
        return currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(int mousex, int mousey, Map<String, String> hotkeys) {
        return hotkeys;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        if (this.treeGui != null) {
            this.treeGui.addRecipe(Recipe.of(this.handlerRef));
        }
    }

    @Override
    protected void drawContent(Minecraft minecraft, int y, int x, boolean mouseOver) {
        final int iconX = this.xPosition + (this.width - ICON_FILL.width - 1) / 2;
        final int iconY = this.yPosition + (this.height - ICON_FILL.height) / 2;

        GL11.glColor4f(1, 1, 1, this.enabled ? 1 : 0.5f);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        ICON_FILL.draw(iconX, iconY);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
    }

    @Override
    public void drawItemOverlay() {}

    public Recipe getRecipe() {
        return Recipe.of(this.handlerRef);
    }

    @Override
    public void lastKeyTyped(char keyChar, int keyID) {}

}
