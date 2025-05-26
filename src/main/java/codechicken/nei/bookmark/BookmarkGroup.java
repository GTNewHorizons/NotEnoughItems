package codechicken.nei.bookmark;

import java.util.HashSet;
import java.util.Set;

import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.recipe.Recipe.RecipeId;

public class BookmarkGroup {

    public RecipeChainDetails crafting = null;
    public BookmarkViewMode viewMode;
    public boolean collapsed = false;
    public Set<RecipeId> collapsedRecipes = new HashSet<>();

    public BookmarkGroup(BookmarkViewMode viewMode) {
        this.viewMode = viewMode;
    }

    public BookmarkGroup(BookmarkViewMode viewMode, boolean crafting) {
        this.viewMode = viewMode;
        setCraftingMode(crafting);
    }

    public void toggleViewMode() {
        if (this.viewMode == BookmarkViewMode.DEFAULT) {
            this.viewMode = BookmarkViewMode.TODO_LIST;
        } else {
            this.viewMode = BookmarkViewMode.DEFAULT;
        }
    }

    public void setCraftingMode(boolean crafting) {
        if ((this.crafting != null) != crafting) {
            this.crafting = crafting ? new RecipeChainDetails() : null;
        }
    }

    public void toggleCraftingMode() {
        setCraftingMode(this.crafting == null);
    }

    public void toggleCollapsedState() {
        this.collapsed = !this.collapsed;
    }

    public void toggleCollapsedRecipe(RecipeId recipeId) {
        if (this.collapsedRecipes.contains(recipeId)) {
            this.collapsedRecipes.remove(recipeId);
        } else {
            this.collapsedRecipes.add(recipeId);
        }
    }

    public BookmarkGroup copy() {
        return new BookmarkGroup(this.viewMode);
    }
}
