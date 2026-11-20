package codechicken.nei.bookmark;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.recipe.Recipe.RecipeId;

public class BookmarkGroup {

    public RecipeChainDetails crafting = null;
    public BookmarkViewMode viewMode;
    public boolean collapsed = false;
    public Set<RecipeId> collapsedRecipes = new HashSet<>();
    public Map<ProgressKey, Long> progress = new HashMap<>();

    /**
     * Identifies one node of the crafting tree by its position: the recipe that uses (or, for the final results,
     * produces) the item, the item itself, and the key of the parent node.
     */
    public static class ProgressKey {

        public final ProgressKey parent;
        public final RecipeId recipeId;
        public final String itemGuid;

        private final int hash;

        public ProgressKey(ProgressKey parent, RecipeId recipeId, String itemGuid) {
            this.parent = parent;
            this.recipeId = recipeId;
            this.itemGuid = itemGuid;
            this.hash = Objects.hash(parent, recipeId, itemGuid);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ProgressKey other && this.hash == other.hash
                    && Objects.equals(this.recipeId, other.recipeId)
                    && Objects.equals(this.itemGuid, other.itemGuid)
                    && Objects.equals(this.parent, other.parent);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }
    }

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

    public long getProgress(ProgressKey key) {
        return this.progress.getOrDefault(key, 0L);
    }

    public void setProgress(ProgressKey key, long done) {
        if (done <= 0) {
            this.progress.remove(key);
        } else {
            this.progress.put(key, done);
        }
    }

    public BookmarkGroup copy() {
        return new BookmarkGroup(this.viewMode);
    }
}
