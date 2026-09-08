package codechicken.nei.bookmark.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.BookmarkItem.BookmarkItemType;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.chain.RecipeChainMath;

public class CraftingTreeMath extends RecipeChainMath {

    public final List<ItemTreeSlot> rootSlots = new ArrayList<>();
    public final List<ItemTreeSlot> items = new ArrayList<>();
    public final Map<ItemTreeSlot, ItemTreeSlot> parentOf = new HashMap<>();
    public final Map<ItemTreeSlot, List<ItemTreeSlot>> childrenOf = new HashMap<>();

    private final Deque<ItemTreeSlot> parentStack = new ArrayDeque<>();
    private ItemTreeSlot lastItem = null;

    public CraftingTreeMath(List<BookmarkItem> chainItems, Set<RecipeId> collapsedRecipes) {
        super(chainItems, collapsedRecipes);
    }

    @Override
    public RecipeChainMath refresh() {
        this.rootSlots.clear();
        this.items.clear();
        this.parentOf.clear();
        this.childrenOf.clear();
        this.parentStack.clear();
        this.lastItem = null;

        return super.refresh();
    }

    @Override
    protected void calculationStepTrigger(BookmarkItem ingrItem, BookmarkItem prefItem, long requestedAmount,
            long containerAmount, long inventoryAmount, long ingrAmount, long produced) {

        if (ingrItem.type == BookmarkItemType.RESULT) {
            final ItemTreeSlot slot = new ItemTreeSlot(
                    ingrItem,
                    Math.max(0, this.requiredAmount.get(prefItem) - requestedAmount),
                    requestedAmount,
                    this.requiredAmount.get(prefItem),
                    0,
                    prefItem);

            this.rootSlots.add(slot);
            this.items.add(slot);

            this.lastItem = slot;
        } else {

            if (prefItem != null && hasContainerItem(prefItem.itemStack)) {

                if (requestedAmount > 1) {
                    final long requestedSteps = getToolsContainerItems(
                            prefItem.itemStack.copy(),
                            requestedAmount).leftSteps;
                    requestedAmount = (long) Math
                            .ceil((double) requestedAmount / Math.max(1, requestedAmount - requestedSteps));
                }

                if (containerAmount > 1) {
                    final long containerSteps = getToolsContainerItems(
                            prefItem.itemStack.copy(),
                            containerAmount).leftSteps;
                    containerAmount = (long) Math
                            .ceil((double) containerAmount / Math.max(1, containerAmount - containerSteps));
                }
            }

            final ItemTreeSlot slot = new ItemTreeSlot(
                    ingrItem,
                    0,
                    requestedAmount,
                    produced,
                    containerAmount,
                    prefItem);

            this.items.add(slot);
            this.parentOf.put(slot, this.parentStack.peek());
            this.childrenOf.computeIfAbsent(this.parentStack.peek(), k -> new ArrayList<>()).add(slot);

            this.lastItem = slot;
        }
    }

    @Override
    protected void prepareIngredients(RecipeId recipeId, long multiplier, List<RecipeId> visited) {
        this.parentStack.push(this.lastItem);
        super.prepareIngredients(recipeId, multiplier, visited);
        this.lastItem = this.parentStack.pop();
    }

    @Override
    protected void calculateSuitableRecipe(BookmarkItem ingrItem, long ingrMultiplier, List<RecipeId> visited) {
        final BookmarkItem prefItem = this.preferredItems.get(ingrItem);
        super.calculateSuitableRecipe(ingrItem, ingrMultiplier, visited);

        if (prefItem != null && !isForeignBoxBoundary(ingrItem)
                && !visited.contains(prefItem.recipeId)
                && !this.childrenOf.containsKey(this.lastItem)) {
            visited.add(prefItem.recipeId);
            prepareIngredients(prefItem.recipeId, visited);
            visited.remove(prefItem.recipeId);
        }

    }

    protected void prepareIngredients(RecipeId recipeId, List<RecipeId> visited) {
        this.parentStack.push(this.lastItem);

        for (BookmarkItem item : this.ingredientsByRecipe.getOrDefault(recipeId, Collections.emptyList())) {
            if (!item.emptyFactor()) {
                calculateSuitableRecipe(item, visited);
            }
        }

        this.lastItem = this.parentStack.pop();
    }

    protected void calculateSuitableRecipe(BookmarkItem ingrItem, List<RecipeId> visited) {
        final BookmarkItem prefItem = this.preferredItems.get(ingrItem);
        final ItemTreeSlot slot = new ItemTreeSlot(ingrItem, 0, 0, 0, 0, prefItem);

        this.items.add(slot);
        this.parentOf.put(slot, this.parentStack.peek());
        this.childrenOf.computeIfAbsent(this.parentStack.peek(), k -> new ArrayList<>()).add(slot);

        this.lastItem = slot;

        if (prefItem != null && !isForeignBoxBoundary(ingrItem) && !visited.contains(prefItem.recipeId)) {
            visited.add(prefItem.recipeId);
            prepareIngredients(prefItem.recipeId, visited);
            visited.remove(prefItem.recipeId);
        }

    }

}
