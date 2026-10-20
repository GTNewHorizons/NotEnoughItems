package codechicken.nei.recipe;

import static codechicken.nei.NEIClientUtils.translate;

import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL11;

import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.ItemPanels;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.tree.GuiCraftingTree;
import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;

/**
 * Button shown next to a recipe that saves it (and the recipes of its ingredients) as a new crafting group and opens
 * the crafting tree for that group.
 */
public class GuiCraftingTreeButton extends GuiRecipeButton {

    /*
     * The tree is drawn half a pixel off center inside these sprites, so that it ends up exactly in the middle of the
     * button.
     */
    protected static final DrawableResource ICON_TREE = new DrawableBuilder(
            "nei:textures/nei_sprites.png",
            16,
            116,
            8,
            8).build();
    protected static final DrawableResource ICON_TREE_HOVER = new DrawableBuilder(
            "nei:textures/nei_sprites.png",
            24,
            116,
            8,
            8).build();

    private static final int BUTTON_ID_START = 64;

    protected final Recipe recipe;

    public GuiCraftingTreeButton(RecipeHandlerRef handlerRef, int x, int y) {
        super(handlerRef, x, y, BUTTON_ID_START + handlerRef.recipeIndex, "");
        this.recipe = Recipe.of(this.handlerRef);
        this.visible = this.recipe.getResult() != null;
    }

    @Override
    protected void drawContent(Minecraft minecraft, int x, int y, boolean mouseOver) {
        final DrawableResource icon = mouseOver ? ICON_TREE_HOVER : ICON_TREE;
        final int iconX = this.xPosition + (this.width - icon.width) / 2;
        final int iconY = this.yPosition + (this.height - icon.height) / 2;

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        icon.draw(iconX, iconY);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        final BookmarkGrid grid = ItemPanels.bookmarkPanel.getGrid();
        final int existingGroupId = grid.findCraftingGroupStartingWith(this.recipe.getRecipeId());

        // do not add the same tree to the bookmarks twice
        if (existingGroupId != -1) {
            GuiCraftingTree.openCraftingTreeGui(grid, existingGroupId);
        } else if (ItemPanels.bookmarkPanel
                .addGroup(GuiFavoriteButton.getRecipesTree(this.recipe), BookmarkViewMode.TODO_LIST, true)) {
                    GuiCraftingTree.openCraftingTreeGui(grid, grid.getNextGroupId() - 1);
                }
    }

    @Override
    public List<String> handleTooltip(List<String> currenttip) {
        currenttip.add(translate("recipe.open_crafting_tree"));
        return currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(int mousex, int mousey, Map<String, String> hotkeys) {
        return hotkeys;
    }

    @Override
    public void lastKeyTyped(char keyChar, int keyID) {}

    @Override
    public void drawItemOverlay() {}

}
