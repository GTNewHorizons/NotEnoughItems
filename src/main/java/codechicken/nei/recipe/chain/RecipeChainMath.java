package codechicken.nei.recipe.chain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import codechicken.nei.ItemStackAmount;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.Recipe.RecipeIngredient;
import codechicken.nei.recipe.StackInfo;

public class RecipeChainMath {

    private static final ItemStack ROOT_ITEM = new ItemStack(Blocks.fire);
    private static final RecipeId ROOT_RECIPE_ID = RecipeId
            .of(ROOT_ITEM, "recipe-autocrafting", Collections.emptyList());

    public final Map<RecipeId, Long> outputRecipes = new HashMap<>();

    public final List<BookmarkItem> initialItems = new ArrayList<>();
    public final List<BookmarkItem> recipeIngredients = new ArrayList<>();
    public final List<BookmarkItem> recipeResults = new ArrayList<>();

    public final Map<BookmarkItem, BookmarkItem> preferredItems = new HashMap<>();
    public final Map<BookmarkItem, Long> requiredAmount = new HashMap<>();

    private RecipeChainMath(List<BookmarkItem> recipeItems, Set<RecipeId> collapsedRecipes) {
        final Map<RecipeId, Integer> recipeState = new HashMap<>();
        final Map<RecipeId, Long> multipliers = new HashMap<>();

        for (BookmarkItem item : recipeItems) {
            if (item.recipeId != null) {
                recipeState
                        .put(item.recipeId, recipeState.getOrDefault(item.recipeId, 0) | (item.isIngredient ? 1 : 2));
            }
        }

        for (BookmarkItem item : recipeItems) {
            if (recipeState.getOrDefault(item.recipeId, 0) != 3) {
                this.initialItems.add(item.copy());
            } else if (item.isIngredient) {
                this.recipeIngredients.add(item.copyWithAmount(0));
            } else {
                this.recipeResults.add(item.copyWithAmount(0));
                multipliers.put(
                        item.recipeId,
                        Math.max(multipliers.getOrDefault(item.recipeId, 0L), item.getMultiplier()));
            }
        }

        for (Map.Entry<RecipeId, Long> entry : multipliers.entrySet()) {
            if (entry.getValue() > 1 || collapsedRecipes.contains(entry.getKey())) {
                collectPreferredItems(entry.getKey(), this.preferredItems, new HashSet<>());
                this.outputRecipes.put(entry.getKey(), entry.getValue());
            }
        }

        while (true) {
            Map<BookmarkItem, BookmarkItem> maxReference = Collections.emptyMap();
            RecipeId maxRecipeId = null;
            long maxMultiplier = 0;
            int maxDepth = 0;

            for (Map.Entry<RecipeId, Long> entry : multipliers.entrySet()) {
                final RecipeId recipeId = entry.getKey();
                if (!this.outputRecipes.containsKey(recipeId) && this.preferredItems.values().stream()
                        .noneMatch(resItem -> resItem.recipeId.equals(recipeId))) {
                    final Map<BookmarkItem, BookmarkItem> references = new HashMap<>(this.preferredItems);
                    final int depth = collectPreferredItems(recipeId, references, new HashSet<>());

                    if (maxDepth < depth || maxDepth == depth && entry.getValue() > maxMultiplier) {
                        maxMultiplier = entry.getValue();
                        maxReference = references;
                        maxRecipeId = recipeId;
                        maxDepth = depth;
                    }
                }
            }

            if (maxReference.isEmpty()) {
                break;
            }

            this.preferredItems.putAll(maxReference);
            this.outputRecipes.put(maxRecipeId, multipliers.get(maxRecipeId));
        }

        for (Map.Entry<RecipeId, Long> entry : multipliers.entrySet()) {
            if (!this.outputRecipes.containsKey(entry.getKey()) && this.preferredItems.values().stream()
                    .noneMatch(resItem -> resItem.recipeId.equals(entry.getKey()))) {
                this.outputRecipes.put(entry.getKey(), entry.getValue());
            }
        }

        for (Map.Entry<RecipeId, Long> entry : this.outputRecipes.entrySet()) {
            if (entry.getValue() == 0 && this.preferredItems.values().stream()
                    .noneMatch(prefItem -> prefItem.recipeId.equals(entry.getKey()))) {
                entry.setValue(1L);
            }
        }
    }

    private int collectPreferredItems(RecipeId recipeId, Map<BookmarkItem, BookmarkItem> preferredItems,
            Set<RecipeId> visited) {
        int maxDepth = 0;
        visited.add(recipeId);

        for (BookmarkItem ingrItem : this.recipeIngredients) {
            if (ingrItem.factor > 0 && recipeId.equals(ingrItem.recipeId) && !preferredItems.containsKey(ingrItem)) {
                BookmarkItem prefItem = null;

                for (BookmarkItem item : this.recipeResults) {
                    if (item.factor > (prefItem == null ? 0 : prefItem.factor) && item.containsItems(ingrItem)
                            && !visited.contains(item.recipeId)) {
                        prefItem = item;
                    }
                }

                if (prefItem != null) {
                    preferredItems.put(ingrItem, prefItem);
                    maxDepth = Math
                            .max(maxDepth, collectPreferredItems(prefItem.recipeId, preferredItems, visited) + 1);
                }
            }
        }

        visited.remove(recipeId);
        return maxDepth;
    }

    public RecipeId createMasterRoot() {
        final List<BookmarkItem> rootIngredients = new ArrayList<>();

        for (BookmarkItem item : this.recipeResults) {
            if (this.outputRecipes.containsKey(item.recipeId) && !ROOT_RECIPE_ID.equals(item.recipeId)) {
                final long amount = item.factor * this.outputRecipes.get(item.recipeId);
                rootIngredients.add(
                        BookmarkItem.of(
                                -1,
                                item.getItemStack(amount),
                                item.getStackSize(amount),
                                ROOT_RECIPE_ID,
                                true,
                                BookmarkItem.generatePermutations(item.itemStack, null)));
            }
        }

        this.outputRecipes.clear();
        this.outputRecipes.put(ROOT_RECIPE_ID, 1L);
        this.recipeResults.removeIf(item -> ROOT_RECIPE_ID.equals(item.recipeId));
        this.recipeResults.add(BookmarkItem.of(-1, ROOT_ITEM, 1, ROOT_RECIPE_ID, false));
        this.recipeIngredients.addAll(rootIngredients);

        return ROOT_RECIPE_ID;
    }

    public boolean hasMasterRoot() {
        return this.outputRecipes.containsKey(ROOT_RECIPE_ID);
    }

    public static RecipeChainMath of(List<BookmarkItem> chainItems, Set<RecipeId> collapsedRecipes) {
        return new RecipeChainMath(chainItems, collapsedRecipes);
    }

    public static RecipeChainMath of(Recipe recipe, long multiplier) {
        final List<BookmarkItem> chainItems = new ArrayList<>();
        final RecipeId recipeId = recipe.getRecipeId();
        final ItemStack result = recipe.getResult();

        chainItems.add(BookmarkItem.of(-1, result, StackInfo.getAmount(result), recipeId, false));

        for (RecipeIngredient ingr : recipe.getIngredients()) {
            chainItems.add(
                    BookmarkItem.of(
                            -1,
                            ingr.getItemStack(),
                            ingr.getAmount(),
                            recipeId,
                            true,
                            BookmarkItem.generatePermutations(ingr.getItemStack(), recipe)));
        }

        for (BookmarkItem item : chainItems) {
            item.amount *= multiplier;
        }

        return new RecipeChainMath(chainItems, Collections.emptySet());
    }

    public ItemStackAmount getMissedItems() {
        final ItemStackAmount missedItems = new ItemStackAmount();

        for (BookmarkItem item : this.recipeResults) {
            long amount = item.amount - this.requiredAmount.getOrDefault(item, 0L);
            if (amount > 0) {
                missedItems.add(item.getItemStack(amount));
            }
        }

        for (BookmarkItem item : this.recipeIngredients) {
            long amount = this.requiredAmount.containsKey(this.preferredItems.get(item)) ? 0
                    : this.requiredAmount.getOrDefault(item, item.amount);
            if (amount > 0) {
                missedItems.add(item.getItemStack(amount));
            }
        }

        for (BookmarkItem item : this.initialItems) {
            if (this.requiredAmount.getOrDefault(item, -1L) == 0) {
                missedItems.add(item.getItemStack());
            }
        }

        return missedItems;
    }

    private void resetCalculation() {

        for (BookmarkItem item : this.recipeIngredients) {
            item.amount = 0;
        }

        for (BookmarkItem item : this.recipeResults) {
            item.amount = 0;
        }

        this.preferredItems.clear();
        this.requiredAmount.clear();

        for (RecipeId recipeId : this.outputRecipes.keySet()) {
            collectPreferredItems(recipeId, this.preferredItems, new HashSet<>());
        }

    }

    public RecipeChainMath refresh() {
        resetCalculation();

        for (BookmarkItem prefItem : this.recipeResults) {
            if (prefItem.factor > 0 && this.outputRecipes.containsKey(prefItem.recipeId)) {
                final long prefAmount = prefItem.factor * this.outputRecipes.get(prefItem.recipeId);

                this.preferredItems.put(prefItem, prefItem);
                calculateSuitableRecipe(prefItem, prepareAmount(prefItem, prefAmount), new ArrayList<>());
                this.preferredItems.remove(prefItem);
            }
        }

        for (BookmarkItem prefItem : this.recipeResults) {
            if (prefItem.factor > 0 && this.outputRecipes.containsKey(prefItem.recipeId)
                    && this.requiredAmount.containsKey(prefItem)) {
                final long prefAmount = prefItem.factor * this.outputRecipes.get(prefItem.recipeId);
                this.requiredAmount.put(prefItem, this.requiredAmount.get(prefItem) - prefAmount);
            }
        }

        return this;
    }

    private void prepareIngredients(RecipeId recipeId, long stepShift, List<RecipeId> visited) {
        for (BookmarkItem item : this.recipeIngredients) {
            if (item.factor > 0 && recipeId.equals(item.recipeId)) {
                calculateSuitableRecipe(item, prepareAmount(item, item.factor * stepShift), visited);
            }
        }
    }

    private void calculateSuitableRecipe(BookmarkItem ingrItem, long ingrAmount, List<RecipeId> visited) {
        final BookmarkItem prefItem = this.preferredItems.get(ingrItem);

        if (prefItem == null) {
            this.requiredAmount.put(ingrItem, this.requiredAmount.getOrDefault(ingrItem, 0L) + ingrAmount);
        } else if (visited.contains(prefItem.recipeId)) {
            this.requiredAmount.put(prefItem, this.requiredAmount.getOrDefault(prefItem, 0L) + ingrAmount);
        } else {
            this.requiredAmount.put(prefItem, this.requiredAmount.getOrDefault(prefItem, 0L) + ingrAmount);
            final long shift = (long) Math
                    .ceil((this.requiredAmount.get(prefItem) - prefItem.amount) / (double) prefItem.factor);

            if (shift > 0) {
                addShift(prefItem.recipeId, shift);

                visited.add(prefItem.recipeId);
                prepareIngredients(prefItem.recipeId, shift, visited);
                visited.remove(prefItem.recipeId);
            }
        }

    }

    private long prepareAmount(BookmarkItem ingrItem, long ingrAmount) {
        if (ingrAmount == 0) return 0;

        for (BookmarkItem item : this.initialItems) {
            if (item.containsItems(ingrItem)) {
                long initAmount = Math.min(ingrAmount, item.amount - this.requiredAmount.getOrDefault(item, 0L));
                if (initAmount > 0) {
                    this.requiredAmount.put(item, this.requiredAmount.getOrDefault(item, 0L) + initAmount);
                    ingrAmount -= initAmount;
                    if (ingrAmount == 0) break;
                }
            }
        }

        return ingrAmount;
    }

    private void addShift(RecipeId recipeId, long shift) {
        for (BookmarkItem item : this.recipeIngredients) {
            if (recipeId.equals(item.recipeId)) {
                item.amount += item.factor * shift;
            }
        }

        for (BookmarkItem item : this.recipeResults) {
            if (recipeId.equals(item.recipeId)) {
                item.amount += item.factor * shift;
            }
        }
    }

}
