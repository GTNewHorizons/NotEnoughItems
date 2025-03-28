package codechicken.nei.recipe.chain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import codechicken.nei.ItemStackAmount;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.StackInfo;

public class RecipeChainIterator implements Iterator<Map<RecipeId, Long>> {

    public final ItemStackAmount inventory = new ItemStackAmount();
    private final Set<RecipeId> precessedRecipes = new HashSet<>();
    private RecipeChainMath math;
    private Set<RecipeId> topRecipes;
    private Set<RecipeId> processedRecipes;
    private List<BookmarkItem> initialItems;

    public RecipeChainIterator(RecipeChainMath math, List<BookmarkItem> initialItems) {
        this.math = math;
        this.topRecipes = this.math.outputRecipes.keySet();
        this.initialItems = new ArrayList<>(initialItems);
        this.processedRecipes = new HashSet<>(this.topRecipes);
    }

    public void updateInventory(ItemStack[] mainInventory) {
        updateInventory(ItemStackAmount.of(Arrays.asList(mainInventory)));
    }

    public void updateInventory(ItemStackAmount mainInventory) {
        this.inventory.clear();
        this.inventory.putAll(mainInventory);
    }

    @Override
    public Map<RecipeId, Long> next() {
        refreshInitialItems(this.math, this.initialItems);
        this.math.refresh();

        final Set<RecipeId> skipRecipes = new HashSet<>();
        final Map<RecipeId, Long> rootRecipes = new HashMap<>();
        final HashMap<BookmarkItem, BookmarkItem> preferredItems = new HashMap<>(this.math.preferredItems);
        preferredItems.values()
                .removeIf(item -> item == null || item.amount == 0 || this.precessedRecipes.contains(item.recipeId));
        preferredItems.keySet().removeIf(item -> item.amount == 0);

        for (Map.Entry<BookmarkItem, BookmarkItem> entry : preferredItems.entrySet()) {
            final BookmarkItem keyItem = entry.getKey();
            if (this.topRecipes.contains(keyItem.recipeId) && !skipRecipes.contains(keyItem.recipeId)) {
                final BookmarkItem item = entry.getValue();

                if (preferredItems.entrySet().stream().anyMatch(
                        pref -> !this.processedRecipes.contains(pref.getKey().recipeId)
                                && item.equals(pref.getValue()))) {
                    skipRecipes.add(item.recipeId);
                } else {
                    rootRecipes.put(
                            item.recipeId,
                            Math.max(rootRecipes.getOrDefault(item.recipeId, 0L), item.getMultiplier()));
                }
            }
        }

        for (RecipeId recipeId : skipRecipes) {
            rootRecipes.remove(recipeId);
        }

        this.processedRecipes.addAll(rootRecipes.keySet());
        rootRecipes.values().removeIf(amount -> amount == 0);
        this.topRecipes = rootRecipes.keySet();
        this.precessedRecipes.addAll(this.topRecipes);

        return rootRecipes;
    }

    @Override
    public boolean hasNext() {
        return !this.topRecipes.isEmpty();
    }

    private void refreshInitialItems(RecipeChainMath math, List<BookmarkItem> initialItems) {
        math.initialItems.clear();
        math.initialItems.addAll(initialItems);

        for (ItemStack stack : this.inventory.values()) {
            final long invStackSize = this.inventory.getOrDefault(stack, 0L);

            if (invStackSize > 0) {
                math.initialItems.add(BookmarkItem.of(-1, StackInfo.withAmount(stack, invStackSize)));
            }
        }
    }

}
