package codechicken.nei.bookmark.tree;

import java.util.List;
import java.util.Map;

import codechicken.nei.NEIClientUtils;
import codechicken.nei.drawable.Drawable;
import codechicken.nei.recipe.GuiRecipeButton;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.theme.UIRecipeTexture;

public class TreeOverlayButton extends GuiRecipeButton {

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
    protected Drawable getIcon(boolean mouseOver) {
        return UIRecipeTexture.ICON_OVERLAY_FILL;
    }

    @Override
    public void drawItemOverlay() {}

    public Recipe getRecipe() {
        return Recipe.of(this.handlerRef);
    }

    @Override
    public void lastKeyTyped(char keyChar, int keyID) {}

}
