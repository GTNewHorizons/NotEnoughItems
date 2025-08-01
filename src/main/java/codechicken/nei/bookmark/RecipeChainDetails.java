package codechicken.nei.bookmark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.item.ItemStack;

import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.chain.RecipeChainMath;

public class RecipeChainDetails {

    public enum CalculatedType {

        INGREDIENT,
        RESULT,
        REMAINDER;

        public int toInt() {
            switch (this) {
                case RESULT:
                    return 0;
                case REMAINDER:
                    return 1;
                case INGREDIENT:
                    return 2;
            }
            return -1;
        }
    }

    public static class BookmarkChainItem {

        private final BookmarkItem item;

        private long realAmount = 0L;
        private long shiftAmount = 0L;
        private long calculatedAmount = 0L;

        public CalculatedType calculatedType;

        protected BookmarkChainItem(BookmarkItem item, long shiftItem, long calculatedItem,
                CalculatedType calculatedType) {
            this.item = item.copy();
            this.shiftAmount = shiftItem;
            this.calculatedAmount = calculatedItem;
            this.calculatedType = calculatedType;
        }

        public static BookmarkChainItem of(BookmarkItem item, long shiftItem, long calculatedAmount,
                CalculatedType calculatedType) {
            return new BookmarkChainItem(item, shiftItem, calculatedAmount, calculatedType);
        }

        public static BookmarkChainItem of(BookmarkItem item, long shiftItem, CalculatedType calculatedType) {
            return new BookmarkChainItem(item, shiftItem, item.amount, calculatedType);
        }

        public static BookmarkChainItem of(BookmarkItem item) {
            return new BookmarkChainItem(
                    item,
                    item.amount,
                    item.amount,
                    item.isIngredient ? CalculatedType.INGREDIENT : CalculatedType.RESULT);
        }

        public ItemStack getItemStack(long amount) {
            return this.item.getItemStack(amount);
        }

        public void setRealAmount(long amount) {
            this.realAmount = amount;
        }

        public long getRealAmount() {
            return this.realAmount;
        }

        public long getShiftAmount() {
            return this.shiftAmount;
        }

        public long getCalculatedAmount() {
            return this.calculatedAmount;
        }

        public BookmarkItem getItem() {
            return this.item;
        }

        public void append(long amount, long calculatedAmount) {
            this.shiftAmount += amount;
            this.calculatedAmount += calculatedAmount;
            this.item.amount = this.calculatedAmount;
        }
    }

    public final Map<Integer, BookmarkChainItem> calculatedItems = new HashMap<>();
    public final Map<Integer, RecipeId> itemToRecipe = new HashMap<>();
    public final Set<RecipeId> outputRecipes = new HashSet<>();
    public final Set<RecipeId> recipeInMiddle = new HashSet<>();

    public final Map<RecipeId, Set<RecipeId>> recipeRelations = new HashMap<>();

    public void refresh(Map<Integer, BookmarkItem> chainItems, Set<RecipeId> collapsedRecipes) {
        final Map<BookmarkItem, Integer> sortingChainItems = new HashMap<>();

        for (Map.Entry<Integer, BookmarkItem> entry : chainItems.entrySet()) {
            sortingChainItems.put(entry.getValue(), entry.getKey());
        }

        this.itemToRecipe.clear();
        this.outputRecipes.clear();
        this.recipeInMiddle.clear();
        this.calculatedItems.clear();
        this.recipeRelations.clear();
        final RecipeChainMath math = RecipeChainMath.of(new ArrayList<>(chainItems.values()), collapsedRecipes)
                .refresh();

        this.outputRecipes.addAll(math.outputRecipes.keySet());

        for (BookmarkItem item : math.preferredItems.values()) {
            if (item != null && item.recipeId != null) {
                this.recipeInMiddle.add(item.recipeId);
            }
        }

        for (BookmarkItem item : math.initialItems) {
            this.calculatedItems.put(
                    sortingChainItems.get(item),
                    BookmarkChainItem.of(item, math.requiredAmount.getOrDefault(item, 0L), CalculatedType.INGREDIENT));
        }

        if (collapsedRecipes.isEmpty()) {

            for (BookmarkItem item : math.recipeResults) {
                generateResult(math, item, sortingChainItems.get(item));
            }

            for (BookmarkItem item : math.recipeIngredients) {
                generateIngredient(math, item, sortingChainItems.get(item));
            }

        } else {
            final Set<RecipeId> topLevelRecipes = findTopLevelRecipes(math, collapsedRecipes);

            for (RecipeId recipeId : collapsedRecipes) {
                this.recipeRelations.put(
                        recipeId,
                        getRecipeRelations(math, recipeId, topLevelRecipes, new HashSet<>(Arrays.asList(recipeId))));
                generateCollapsedRecipe(math, recipeId, topLevelRecipes, sortingChainItems);
                generateShadowItems(recipeId, chainItems.values(), sortingChainItems);
            }

            for (BookmarkItem item : math.recipeResults) {
                if (!collapsedRecipes.contains(item.recipeId) && topLevelRecipes.contains(item.recipeId)) {
                    generateResult(math, item, sortingChainItems.get(item));
                }
            }

            for (BookmarkItem item : math.recipeIngredients) {
                if (!collapsedRecipes.contains(item.recipeId) && topLevelRecipes.contains(item.recipeId)) {
                    generateIngredient(math, item, sortingChainItems.get(item));
                }
            }

        }

        for (Map.Entry<Integer, BookmarkItem> entry : chainItems.entrySet()) {
            final int itemIndex = entry.getKey();
            if (itemIndex >= 0 && this.calculatedItems.containsKey(itemIndex)) {
                final BookmarkItem item = entry.getValue();
                this.calculatedItems.get(itemIndex).setRealAmount(item.amount);
            }
        }

    }

    private Set<RecipeId> getRecipeRelations(RecipeChainMath math, RecipeId recipeId, Set<RecipeId> topLevelRecipes,
            Set<RecipeId> recipes) {

        for (BookmarkItem item : math.recipeIngredients) {
            if (item.recipeId.equals(recipeId)) {
                final BookmarkItem prefItem = math.preferredItems.get(item);
                if (prefItem != null && !topLevelRecipes.contains(prefItem.recipeId)
                        && recipes.add(prefItem.recipeId)) {
                    getRecipeRelations(math, prefItem.recipeId, topLevelRecipes, recipes);
                }
            }
        }

        return recipes;
    }

    private void generateCollapsedRecipe(RecipeChainMath math, RecipeId recipeId, Set<RecipeId> topLevelRecipes,
            Map<BookmarkItem, Integer> sortingChainItems) {

        final List<BookmarkChainItem> items = collectItems(
                math,
                recipeId,
                this.recipeRelations.get(recipeId),
                topLevelRecipes);

        for (BookmarkChainItem value : items) {
            int itemIndex = sortingChainItems.get(value.getItem());

            if (value.getItem().isIngredient || !recipeId.equals(value.getItem().recipeId)) {
                itemIndex *= -1;
            }

            if (!value.getItem().isIngredient && !math.outputRecipes.containsKey(value.getItem().recipeId)) {
                value.calculatedType = CalculatedType.REMAINDER;
            }

            this.itemToRecipe.put(itemIndex, recipeId);
            this.calculatedItems.put(itemIndex, value);
        }
    }

    private void generateShadowItems(RecipeId recipeId, Iterable<BookmarkItem> chainItems,
            Map<BookmarkItem, Integer> sortingChainItems) {
        final Set<RecipeId> recipeRelations = this.recipeRelations.get(recipeId);
        final List<BookmarkItem> subChainItems = new ArrayList<>();

        for (BookmarkItem item : chainItems) {
            if (recipeRelations.contains(item.recipeId)) {
                subChainItems.add(item.copyWithAmount(recipeId.equals(item.recipeId) ? item.amount : 0));
            }
        }

        final RecipeChainMath math = RecipeChainMath.of(subChainItems, Collections.emptySet()).refresh();
        final List<BookmarkChainItem> items = collectItems(
                math,
                recipeId,
                this.recipeRelations.get(recipeId),
                Collections.emptySet());

        for (BookmarkChainItem value : items) {
            int itemIndex = sortingChainItems.get(value.getItem());

            if (value.getItem().isIngredient || !recipeId.equals(value.getItem().recipeId)) {
                itemIndex *= -1;
            }

            if (this.calculatedItems.containsKey(itemIndex)) {
                this.calculatedItems.get(itemIndex).setRealAmount(value.getShiftAmount());
            }
        }

    }

    private List<BookmarkChainItem> collectItems(RecipeChainMath math, RecipeId recipeId, Set<RecipeId> recipeRelations,
            Set<RecipeId> topLevelRecipes) {
        final Map<String, BookmarkChainItem> items = new HashMap<>();
        final Map<RecipeId, Long> multiplier = new HashMap<>();

        for (RecipeId relRecipeId : recipeRelations) {
            for (BookmarkItem item : math.recipeResults) {
                if (item.recipeId.equals(relRecipeId)) {
                    final long amount = item.amount - math.requiredAmount.getOrDefault(item, 0L);

                    if (item.recipeId.equals(recipeId)) {
                        multiplier.put(item.recipeId, item.getMultiplier());
                        items.computeIfAbsent(
                                StackInfo.getItemStackGUID(item.itemStack),
                                is -> BookmarkChainItem.of(item, 0, 0, CalculatedType.RESULT))
                                .append(amount, item.amount);
                    } else if (amount > 0) {
                        items.computeIfAbsent(
                                StackInfo.getItemStackGUID(item.itemStack),
                                is -> BookmarkChainItem.of(item, 0, 0, CalculatedType.RESULT)).append(amount, amount);
                    }
                }
            }

            for (BookmarkItem item : math.recipeIngredients) {
                if (item.recipeId.equals(relRecipeId)) {
                    final BookmarkItem prefItem = math.preferredItems.get(item);
                    final long amount = math.requiredAmount.containsKey(prefItem) ? 0
                            : math.requiredAmount.getOrDefault(item, item.amount);
                    final long refAmount = prefItem != null && !topLevelRecipes.contains(prefItem.recipeId)
                            ? math.requiredAmount.getOrDefault(prefItem, 0L)
                            : 0;

                    if (amount != 0 || item.amount > refAmount && math.requiredAmount.containsKey(prefItem)
                            || recipeId.equals(item.recipeId) && multiplier.get(recipeId) == 0) {
                        items.computeIfAbsent(
                                StackInfo.getItemStackGUID(item.itemStack),
                                is -> BookmarkChainItem.of(item, 0, 0, CalculatedType.INGREDIENT))
                                .append(amount, item.amount - refAmount);
                    }
                }
            }
        }

        return new ArrayList<>(items.values());
    }

    private void generateResult(RecipeChainMath math, BookmarkItem item, int itemIndex) {
        final long amount = item.amount - math.requiredAmount.getOrDefault(item, 0L);
        final CalculatedType calculatedType = math.outputRecipes.containsKey(item.recipeId) ? CalculatedType.RESULT
                : CalculatedType.REMAINDER;

        this.calculatedItems.put(itemIndex, BookmarkChainItem.of(item, amount, calculatedType));
    }

    private void generateIngredient(RecipeChainMath math, BookmarkItem item, int itemIndex) {
        final BookmarkItem prefItem = math.preferredItems.get(item);
        final long amount = math.requiredAmount.containsKey(prefItem) ? 0
                : math.requiredAmount.getOrDefault(item, item.amount);

        this.calculatedItems.put(itemIndex, BookmarkChainItem.of(item, amount, CalculatedType.INGREDIENT));
    }

    private Set<RecipeId> findTopLevelRecipes(RecipeChainMath math, Set<RecipeId> collapsedRecipes) {
        final Set<RecipeId> result = new HashSet<>(math.outputRecipes.keySet());

        for (BookmarkItem item : math.recipeResults) {
            if (!result.contains(item.recipeId)) {
                final Set<RecipeId> parents = getRecipeParents(
                        math,
                        item.recipeId,
                        new HashSet<>(),
                        new HashSet<>(Arrays.asList(item.recipeId)));

                if (parents.size() != 1 || parents.stream().noneMatch(collapsedRecipes::contains)) {
                    result.add(item.recipeId);
                }
            }
        }

        return result;
    }

    private Set<RecipeId> getRecipeParents(RecipeChainMath math, RecipeId recipeId, Set<RecipeId> parents,
            Set<RecipeId> visited) {
        final Set<RecipeId> recipeIds = math.preferredItems.entrySet().stream()
                .filter(
                        entry -> entry.getValue() != null && recipeId.equals(entry.getValue().recipeId)
                                && !visited.contains(entry.getKey().recipeId))
                .map(entry -> entry.getKey().recipeId).collect(Collectors.toSet());

        visited.addAll(recipeIds);

        if (recipeIds.isEmpty()) {
            parents.add(recipeId);
        } else {

            for (RecipeId ingrRecipeId : recipeIds) {
                if (math.outputRecipes.containsKey(ingrRecipeId)) {
                    parents.add(ingrRecipeId);
                } else {
                    getRecipeParents(math, ingrRecipeId, parents, visited);
                }
            }

        }

        return parents;
    }

}
