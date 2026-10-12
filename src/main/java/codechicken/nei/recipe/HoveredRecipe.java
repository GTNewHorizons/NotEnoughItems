package codechicken.nei.recipe;

import java.awt.Point;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.FavoriteRecipes;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.bookmark.BookmarkItem.BookmarkItemType;
import codechicken.nei.bookmark.BookmarksGridSlot;
import codechicken.nei.recipe.Recipe.RecipeId;

public final class HoveredRecipe {

    public enum Source {
        RECIPE_GUI,
        BOOKMARK,
        ITEM_PANEL,
        OTHER
    }

    public final RecipeId recipeId;
    public final Source source;
    public final boolean favorite;

    private HoveredRecipe(RecipeId recipeId, Source source, boolean favorite) {
        this.recipeId = recipeId;
        this.source = source;
        this.favorite = favorite;
    }

    public static HoveredRecipe of(GuiScreen gui, ItemStack stackover) {

        if (gui instanceof GuiRecipe<?>guiRecipe) {
            final Recipe focusedRecipe = guiRecipe.getFocusedRecipe();

            if (focusedRecipe != null) {
                return new HoveredRecipe(focusedRecipe.getRecipeId(), Source.RECIPE_GUI, false);
            }
        }

        final Point mouseover = GuiDraw.getMousePosition();
        final BookmarksGridSlot bookmarkSlot = ItemPanels.bookmarkPanel.getSlotMouseOver(mouseover.x, mouseover.y);

        if (bookmarkSlot != null) {

            if (bookmarkSlot.getType() != BookmarkItemType.INGREDIENT && bookmarkSlot.getRecipeId() != null) {
                return new HoveredRecipe(bookmarkSlot.getRecipeId(), Source.BOOKMARK, false);
            }

            return favorite(stackover, Source.BOOKMARK);
        }

        ItemsGridSlot itemSlot = ItemPanels.itemPanel.getSlotMouseOver(mouseover.x, mouseover.y);

        if (itemSlot == null) {
            itemSlot = ItemPanels.itemPanel.historyPanel.getSlotMouseOver(mouseover.x, mouseover.y);
        }

        if (itemSlot == null) {
            itemSlot = ItemPanels.itemPanel.craftablesPanel.getSlotMouseOver(mouseover.x, mouseover.y);
        }

        if (itemSlot != null) {

            if (itemSlot.getRecipeId() != null) {
                return new HoveredRecipe(itemSlot.getRecipeId(), Source.ITEM_PANEL, false);
            }

            return favorite(stackover, Source.ITEM_PANEL);
        }

        if (gui instanceof GuiRecipe<?>guiRecipe && guiRecipe.getSlotAtPosition(mouseover.x, mouseover.y) != null) {
            return favorite(stackover, Source.RECIPE_GUI);
        }

        return favorite(stackover, Source.OTHER);
    }

    private static HoveredRecipe favorite(ItemStack stackover, Source source) {
        final RecipeId recipeId = FavoriteRecipes.getFavorite(stackover);
        return recipeId != null ? new HoveredRecipe(recipeId, source, true) : null;
    }
}
