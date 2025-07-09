package codechicken.nei.recipe.chain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import codechicken.nei.ItemStackAmount;
import codechicken.nei.NEIClientUtils;
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
    public final List<ItemStack> containerItems = new ArrayList<>();
    private final List<ItemStack> containerItemsBlacklist = new ArrayList<>();

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
                    collectPreferredItems(recipeId, references, new HashSet<>());
                    final int depth = getMaxDepth(recipeId, references);

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

    private void collectPreferredItems(RecipeId recipeId, Map<BookmarkItem, BookmarkItem> preferredItems,
            Set<RecipeId> visited) {

        visited.add(recipeId);

        for (BookmarkItem ingrItem : this.recipeIngredients) {
            if (ingrItem.factor > 0 && recipeId.equals(ingrItem.recipeId) && !preferredItems.containsKey(ingrItem)) {
                BookmarkItem activeItem = null;
                BookmarkItem prefItem = null;

                for (BookmarkItem item : this.recipeResults) {
                    if (item.factor > 0 && !visited.contains(item.recipeId) && item.containsItems(ingrItem)) {

                        if ((activeItem == null || item.factor > activeItem.factor) && NEIClientUtils
                                .areStacksSameTypeCraftingWithNBT(ingrItem.itemStack, item.itemStack)) {
                            activeItem = item;
                        } else if (prefItem == null || item.factor > prefItem.factor) {
                            prefItem = item;
                        }

                    }
                }

                if (activeItem != null) {
                    prefItem = activeItem;
                }

                if (prefItem != null) {
                    preferredItems.put(ingrItem, prefItem);
                    collectPreferredItems(prefItem.recipeId, preferredItems, visited);
                }
            }
        }

        visited.remove(recipeId);
    }

    private int getMaxDepth(RecipeId recipeId, Map<BookmarkItem, BookmarkItem> preferredItems) {
        int maxDepth = 0;

        for (BookmarkItem ingrItem : this.recipeIngredients) {
            if (ingrItem.factor > 0 && recipeId.equals(ingrItem.recipeId) && preferredItems.containsKey(ingrItem)) {
                maxDepth = Math.max(maxDepth, getMaxDepth(preferredItems.get(ingrItem).recipeId, preferredItems) + 1);
            }
        }

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
        this.containerItems.clear();
        this.containerItemsBlacklist.clear();

        for (RecipeId recipeId : this.outputRecipes.keySet()) {
            collectPreferredItems(recipeId, this.preferredItems, new HashSet<>());
        }
    }

    public RecipeChainMath refresh() {
        resetCalculation();

        if (this.outputRecipes.containsKey(ROOT_RECIPE_ID)) {
            for (BookmarkItem ingrItem : this.recipeIngredients) {
                if (ROOT_RECIPE_ID.equals(ingrItem.recipeId)
                        && ingrItem.itemStack.getItem().hasContainerItem(ingrItem.itemStack)) {
                    this.containerItemsBlacklist.add(ingrItem.itemStack);
                }
            }
        }

        for (BookmarkItem prefItem : this.recipeResults) {
            if (prefItem.factor > 0 && this.outputRecipes.containsKey(prefItem.recipeId)) {
                final long prefAmount = prefItem.factor * this.outputRecipes.get(prefItem.recipeId);

                if (prefItem.itemStack.getItem().hasContainerItem(prefItem.itemStack)) {
                    this.containerItemsBlacklist.add(prefItem.itemStack);
                }

                this.preferredItems.put(prefItem, prefItem);
                calculateSuitableRecipe(prefItem, prefAmount, new ArrayList<>());
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
                calculateSuitableRecipe(item, item.factor * stepShift, visited);
            }
        }
    }

    private void calculateSuitableRecipe(BookmarkItem ingrItem, long ingrAmount, List<RecipeId> visited) {
        final BookmarkItem prefItem = this.preferredItems.get(ingrItem);

        // calculate existing containers
        if (ingrAmount > 0) {
            for (ItemStack stack : ingrItem.permutations.values()) {
                if (hasContainerItem(stack)) {
                    final long stackSize = ingrItem.getStackSize(ingrAmount);
                    final long shiftSize = shiftContainerItems(stack, stackSize);
                    if (stackSize != shiftSize && (ingrAmount = shiftSize * ingrItem.fluidCellAmount) == 0) {
                        break;
                    }
                }
            }
        }

        // calculate existing initial items
        if (ingrAmount > 0) {
            for (BookmarkItem item : this.initialItems) {
                if (item.containsItems(ingrItem)
                        && (ingrAmount = addRequiredAmount(item, ingrAmount, item.amount)) == 0) {
                    break;
                }
            }
        }

        // shift amount
        if (prefItem == null) {
            addRequiredAmount(ingrItem, ingrAmount, Long.MAX_VALUE);
        } else if (visited.contains(prefItem.recipeId)) {
            addRequiredAmount(prefItem, ingrAmount, Long.MAX_VALUE);
        } else {
            addRequiredAmount(prefItem, ingrAmount, Long.MAX_VALUE);
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

    private long addRequiredAmount(BookmarkItem prefItem, long ingrAmount, long maxAmount) {
        long shiftAmount = this.requiredAmount.getOrDefault(prefItem, 0L);

        if (hasContainerItem(prefItem.itemStack)) {
            ItemStack itemStack = prefItem.itemStack;

            while (ingrAmount > 0 && shiftAmount < maxAmount) {
                itemStack = itemStack.copy();
                itemStack.stackSize = 1;

                this.containerItems.add(itemStack);
                ingrAmount = shiftContainerItems(itemStack, prefItem.getStackSize(ingrAmount))
                        * prefItem.fluidCellAmount;

                shiftAmount += prefItem.fluidCellAmount;
            }

        } else {
            long initAmount = Math.min(ingrAmount, maxAmount - shiftAmount);

            shiftAmount += initAmount;
            ingrAmount -= initAmount;
        }

        this.requiredAmount.put(prefItem, shiftAmount);

        return ingrAmount;
    }

    private long shiftContainerItems(ItemStack aStack, long steps) {

        for (int i = 0; i < this.containerItems.size() && steps > 0; i++) {
            ItemStack bStack = this.containerItems.get(i);

            if (bStack != null && NEIClientUtils.areStacksSameTypeCraftingWithNBT(aStack, bStack)) {

                while (bStack != null && steps > 0) {
                    final Optional<ItemStack> containerItem = StackInfo.getContainerItem(bStack);

                    steps--;

                    if (containerItem != null && containerItem.isPresent()) {
                        bStack = containerItem.get();

                        if (aStack.getItem() != bStack.getItem()) {
                            this.containerItems.add(bStack);
                            bStack = null;
                        }
                    } else {
                        bStack = null;
                    }
                }

                this.containerItems.set(i, bStack);
            }

        }

        this.containerItems.removeIf(stack -> stack == null);

        return steps;
    }

    private boolean hasContainerItem(ItemStack aStack) {

        if (aStack.getItem().hasContainerItem(aStack)) {

            for (ItemStack bStack : this.containerItemsBlacklist) {
                if (NEIClientUtils.areStacksSameTypeCraftingWithNBT(aStack, bStack)) {
                    return false;
                }
            }

            return true;
        }

        return false;
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
