package codechicken.nei.recipe.chain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import codechicken.nei.ItemStackAmount;
import codechicken.nei.ItemStackSet;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.BookmarkItem.BookmarkItemType;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.Recipe.RecipeIngredient;
import codechicken.nei.recipe.StackInfo;

public class RecipeChainMath {

    protected static class ContainerItemResult {

        public ItemStack stack;
        public ItemStack containerItem;
        public long leftSteps;

        public ContainerItemResult(ItemStack stack, long leftSteps, ItemStack containerItem) {
            this.stack = stack;
            this.leftSteps = leftSteps;
            this.containerItem = containerItem;
        }
    }

    private static final ItemStack ROOT_ITEM = new ItemStack(Blocks.fire);
    private static final RecipeId ROOT_RECIPE_ID = RecipeId
            .of(ROOT_ITEM, "recipe-autocrafting", Collections.emptyList());

    public final Map<RecipeId, Long> outputRecipes = new HashMap<>();

    public final List<BookmarkItem> initialItems = new ArrayList<>();
    public final List<BookmarkItem> recipeIngredients = new ArrayList<>();
    public final List<BookmarkItem> recipeResults = new ArrayList<>();

    protected final Map<RecipeId, List<BookmarkItem>> ingredientsByRecipe = new HashMap<>();
    protected final Map<RecipeId, List<BookmarkItem>> resultsByRecipe = new HashMap<>();

    public final Map<BookmarkItem, BookmarkItem> preferredItems = new HashMap<>();

    public final Map<BookmarkItem, Long> requiredAmount = new HashMap<>();

    public final List<ItemStack> containerItemsCrafting = new ArrayList<>();
    public final List<ItemStack> containerItemsInventory = new ArrayList<>();

    private final List<ItemStack> containerItemsBlacklist = new ArrayList<>();

    protected RecipeChainMath(List<BookmarkItem> recipeItems, Set<RecipeId> collapsedRecipes) {
        final Map<RecipeId, Long> multipliers = new HashMap<>();

        for (BookmarkItem item : recipeItems) {
            if (item.recipeId == null || item.type == BookmarkItemType.ITEM) {
                this.initialItems.add(item.copy());
            } else if (item.type == BookmarkItemType.INGREDIENT) {
                this.recipeIngredients.add(item.copyWithMultiplier(0));
            } else {
                this.recipeResults.add(item.copyWithMultiplier(0));
                multipliers.put(
                        item.recipeId,
                        Math.max(multipliers.getOrDefault(item.recipeId, 0L), item.getMultiplier()));
            }
        }

        rebuildRecipeIndex();

        for (Map.Entry<RecipeId, Long> entry : multipliers.entrySet()) {
            if (entry.getValue() > 1 || collapsedRecipes.contains(entry.getKey())) {
                collectPreferredItems(entry.getKey(), this.preferredItems, new HashSet<>());
                removeLoop(entry.getKey(), this.preferredItems, new HashSet<>());
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
                    removeLoop(recipeId, references, new HashSet<>());
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
            final RecipeId recipeId = entry.getKey();
            final boolean isOutputRecipe = this.outputRecipes.containsKey(recipeId);
            final boolean recipeInMiddle = this.preferredItems.values().stream()
                    .anyMatch(resItem -> resItem.recipeId.equals(recipeId));

            if (!isOutputRecipe && !recipeInMiddle) {
                this.outputRecipes.put(recipeId, entry.getValue());
            } else if (isOutputRecipe && recipeInMiddle) {
                this.outputRecipes.put(recipeId, Math.max(0, entry.getValue() - 1));
            }
        }

    }

    private void rebuildRecipeIndex() {
        this.ingredientsByRecipe.clear();
        this.resultsByRecipe.clear();

        for (BookmarkItem item : this.recipeIngredients) {
            this.ingredientsByRecipe.computeIfAbsent(item.recipeId, k -> new ArrayList<>()).add(item);
        }

        for (BookmarkItem item : this.recipeResults) {
            this.resultsByRecipe.computeIfAbsent(item.recipeId, k -> new ArrayList<>()).add(item);
        }
    }

    private void collectPreferredItems(RecipeId recipeId, Map<BookmarkItem, BookmarkItem> preferredItems,
            Set<RecipeId> visited) {

        visited.add(recipeId);

        for (BookmarkItem ingrItem : this.ingredientsByRecipe.getOrDefault(recipeId, Collections.emptyList())) {
            if (!ingrItem.emptyFactor() && !preferredItems.containsKey(ingrItem)) {
                BookmarkItem activeItem = null;
                BookmarkItem prefItem = null;

                for (BookmarkItem item : this.recipeResults) {
                    if (!item.emptyFactor() && !visited.contains(item.recipeId) && item.containsItems(ingrItem)) {

                        if ((activeItem == null || item.getAmount(1) > activeItem.getAmount(1)) && NEIClientUtils
                                .areStacksSameTypeCraftingWithNBT(ingrItem.itemStack, item.itemStack)) {
                            activeItem = item;
                        } else if (prefItem == null || item.getAmount(1) > prefItem.getAmount(1)) {
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

        for (BookmarkItem ingrItem : preferredItems.keySet()) {
            if (!ingrItem.emptyFactor() && recipeId.equals(ingrItem.recipeId)) {
                maxDepth = Math.max(maxDepth, getMaxDepth(preferredItems.get(ingrItem).recipeId, preferredItems) + 1);
            }
        }

        return maxDepth;
    }

    private void removeLoop(RecipeId recipeId, Map<BookmarkItem, BookmarkItem> preferredItems, Set<RecipeId> visited) {
        visited.add(recipeId);

        for (BookmarkItem ingrItem : this.ingredientsByRecipe.getOrDefault(recipeId, Collections.emptyList())) {
            if (!ingrItem.emptyFactor() && preferredItems.containsKey(ingrItem)) {
                BookmarkItem prefItem = preferredItems.get(ingrItem);

                if (visited.contains(prefItem.recipeId)) {
                    preferredItems.remove(ingrItem);
                } else {
                    removeLoop(prefItem.recipeId, preferredItems, visited);
                }

            }
        }

        visited.remove(recipeId);
    }

    private List<RecipeId> getOutputRecipeProcessingOrder() {

        if (this.outputRecipes.size() <= 1) {
            return new ArrayList<>(this.outputRecipes.keySet());
        }

        final Map<RecipeId, Set<RecipeId>> dependents = new HashMap<>();
        final Map<RecipeId, Integer> inDegree = new HashMap<>();

        for (RecipeId recipeId : this.outputRecipes.keySet()) {
            inDegree.put(recipeId, 0);
        }

        for (RecipeId recipeId : this.outputRecipes.keySet()) {
            final Set<RecipeId> dependencies = new HashSet<>();
            collectBoxDependencies(recipeId, recipeId, dependencies, new HashSet<>());
            dependents.put(recipeId, dependencies);

            for (RecipeId dependency : dependencies) {
                inDegree.merge(dependency, 1, Integer::sum);
            }
        }

        final List<RecipeId> order = new ArrayList<>();
        final ArrayDeque<RecipeId> queue = new ArrayDeque<>();

        for (RecipeId recipeId : this.outputRecipes.keySet()) {
            if (inDegree.get(recipeId) == 0) {
                queue.add(recipeId);
            }
        }

        while (!queue.isEmpty()) {
            final RecipeId recipeId = queue.poll();
            order.add(recipeId);

            for (RecipeId dependency : dependents.getOrDefault(recipeId, Collections.emptySet())) {
                if (inDegree.merge(dependency, -1, Integer::sum) == 0) {
                    queue.add(dependency);
                }
            }
        }

        if (order.size() != this.outputRecipes.size()) {
            for (RecipeId recipeId : this.outputRecipes.keySet()) {
                if (!order.contains(recipeId)) {
                    order.add(recipeId);
                }
            }
        }

        return order;
    }

    private void collectBoxDependencies(RecipeId boxId, RecipeId currentRecipeId, Set<RecipeId> dependencies,
            Set<RecipeId> visited) {

        if (!visited.add(currentRecipeId)) {
            return;
        }

        for (BookmarkItem ingrItem : this.ingredientsByRecipe.getOrDefault(currentRecipeId, Collections.emptyList())) {
            if (ingrItem.emptyFactor()) {
                continue;
            }

            final BookmarkItem prefItem = this.preferredItems.get(ingrItem);
            if (prefItem == null) {
                continue;
            }

            if (this.outputRecipes.containsKey(prefItem.recipeId) && !prefItem.recipeId.equals(boxId)) {
                dependencies.add(prefItem.recipeId);
            } else {
                collectBoxDependencies(boxId, prefItem.recipeId, dependencies, visited);
            }
        }

        visited.remove(currentRecipeId);
    }

    public RecipeId createMasterRoot() {
        final List<BookmarkItem> rootIngredients = new ArrayList<>();

        for (BookmarkItem item : this.recipeResults) {
            if (this.outputRecipes.containsKey(item.recipeId) && !ROOT_RECIPE_ID.equals(item.recipeId)) {
                final long multiplier = this.outputRecipes.get(item.recipeId);
                final long amount = item.getAmount(multiplier);
                rootIngredients.add(
                        BookmarkItem.builder(-1, item.getItemStack(amount)).factor(item.getStackSize(amount))
                                .permutations(BookmarkItem.Builder.generatePermutations(item.getItemStack(amount)))
                                .recipeId(ROOT_RECIPE_ID).type(BookmarkItemType.INGREDIENT).build());

            }
        }

        this.outputRecipes.clear();
        this.outputRecipes.put(ROOT_RECIPE_ID, 1L);
        this.recipeResults.removeIf(item -> ROOT_RECIPE_ID.equals(item.recipeId));
        this.recipeResults.add(
                BookmarkItem.builder(-1, ROOT_ITEM).factor(1).recipeId(ROOT_RECIPE_ID).type(BookmarkItemType.RESULT)
                        .build());
        this.recipeIngredients.addAll(rootIngredients);
        rebuildRecipeIndex();

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
        final ItemStack result = recipe.getResult();
        final ItemStackSet ingredients = new ItemStackSet();

        chainItems
                .add(BookmarkItem.builder(-1, result, recipe, BookmarkItemType.RESULT).multiplier(multiplier).build());

        for (RecipeIngredient ingr : recipe.getIngredients()) {
            ingredients.add(ingr.getItemStack());
        }

        for (ItemStack ingrStack : ingredients.values()) {
            chainItems.add(
                    BookmarkItem.builder(-1, ingrStack, recipe, BookmarkItemType.INGREDIENT).multiplier(multiplier)
                            .build());
        }

        return new RecipeChainMath(chainItems, Collections.emptySet());
    }

    public ItemStackAmount getMissedItems() {
        final ItemStackAmount missedItems = new ItemStackAmount();

        for (BookmarkItem item : this.recipeResults) {
            long amount = item.getAmount() - this.requiredAmount.getOrDefault(item, 0L);
            if (amount > 0) {
                missedItems.add(item.getItemStack(amount));
            }
        }

        for (BookmarkItem item : this.recipeIngredients) {
            long amount = this.requiredAmount.containsKey(this.preferredItems.get(item)) ? 0
                    : this.requiredAmount.getOrDefault(item, item.getAmount());
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

    protected boolean isForeignBoxBoundary(BookmarkItem ingrItem) {
        final BookmarkItem prefItem = this.preferredItems.get(ingrItem);
        return prefItem != null && ingrItem.type != BookmarkItemType.RESULT
                && this.outputRecipes.containsKey(prefItem.recipeId);
    }

    private void resetCalculation() {

        for (BookmarkItem item : this.recipeIngredients) {
            item.multiplier = 0;
        }

        for (BookmarkItem item : this.recipeResults) {
            item.multiplier = 0;
        }

        this.preferredItems.clear();
        this.requiredAmount.clear();
        this.containerItemsCrafting.clear();
        this.containerItemsInventory.clear();
        this.containerItemsBlacklist.clear();

        for (RecipeId recipeId : this.outputRecipes.keySet()) {
            collectPreferredItems(recipeId, this.preferredItems, new HashSet<>());
            removeLoop(recipeId, this.preferredItems, new HashSet<>());
        }
    }

    public RecipeChainMath refresh() {
        final boolean isPausedItemDamageSound = StackInfo.isPausedItemDamageSound();
        StackInfo.pauseItemDamageSound(true);

        try {
            resetCalculation();

            if (this.outputRecipes.containsKey(ROOT_RECIPE_ID)) {
                for (BookmarkItem ingrItem : this.recipeIngredients) {
                    if (ROOT_RECIPE_ID.equals(ingrItem.recipeId)
                            && ingrItem.itemStack.getItem().hasContainerItem(ingrItem.itemStack)) {
                        this.containerItemsBlacklist.add(ingrItem.itemStack);
                    }
                }
            }

            for (RecipeId recipeId : getOutputRecipeProcessingOrder()) {
                final long prefMultiplier = this.outputRecipes.get(recipeId);

                for (BookmarkItem prefItem : this.resultsByRecipe.getOrDefault(recipeId, Collections.emptyList())) {
                    if (prefItem.emptyFactor()) {
                        continue;
                    }

                    if (prefItem.itemStack.getItem().hasContainerItem(prefItem.itemStack)) {
                        this.containerItemsBlacklist.add(prefItem.itemStack);
                    }

                    this.preferredItems.put(prefItem, prefItem);
                    calculateSuitableRecipe(prefItem, prefMultiplier, new ArrayList<>());
                    this.preferredItems.remove(prefItem);
                }
            }

            for (BookmarkItem prefItem : this.recipeResults) {
                if (!prefItem.emptyFactor() && this.outputRecipes.containsKey(prefItem.recipeId)
                        && this.requiredAmount.containsKey(prefItem)) {
                    final long prefAmount = prefItem.getAmount(this.outputRecipes.get(prefItem.recipeId));
                    this.requiredAmount.put(prefItem, this.requiredAmount.get(prefItem) - prefAmount);
                }
            }
        } finally {
            StackInfo.pauseItemDamageSound(isPausedItemDamageSound);
        }

        return this;
    }

    protected void calculateSuitableRecipe(BookmarkItem ingrItem, long ingrMultiplier, List<RecipeId> visited) {
        final BookmarkItem prefItem = this.preferredItems.get(ingrItem);
        long ingrAmount = ingrItem
                .getAmount(ingrMultiplier, prefItem != null ? prefItem.itemStack : ingrItem.itemStack);
        final long requested = ingrAmount;
        long containerAmount = 0;
        long inventoryAmount = 0;

        // calculate existing containers
        if (ingrAmount > 0) {
            for (ItemStack stack : ingrItem.permutations.values()) {
                if (hasContainerItem(stack)) {
                    long stackSize = ingrItem.getStackSize(ingrAmount);
                    long shiftSize = shiftContainerItems(stack, stackSize, this.containerItemsCrafting);

                    if (stackSize != shiftSize) {
                        final long newIngrAmount = shiftSize * ingrItem.fluidCellAmount;
                        containerAmount += ingrAmount - newIngrAmount;
                        if ((ingrAmount = newIngrAmount) == 0) {
                            break;
                        }
                    }

                    stackSize = shiftSize;
                    shiftSize = shiftContainerItems(stack, stackSize, this.containerItemsInventory);

                    if (stackSize != shiftSize) {
                        final long newIngrAmount = shiftSize * ingrItem.fluidCellAmount;
                        inventoryAmount += ingrAmount - newIngrAmount;
                        if ((ingrAmount = newIngrAmount) == 0) {
                            break;
                        }
                    }

                }
            }
        }

        // calculate existing initial items
        if (ingrAmount > 0) {
            for (BookmarkItem item : this.initialItems) {
                if (item.containsItems(ingrItem)) {
                    final long newIngrAmount = addRequiredAmount(
                            item,
                            ingrAmount,
                            item.getAmount(),
                            this.containerItemsInventory);
                    inventoryAmount += ingrAmount - newIngrAmount;
                    if ((ingrAmount = newIngrAmount) == 0) {
                        break;
                    }
                }
            }
        }

        // shift amount
        final BookmarkItem producer = prefItem != null ? prefItem : ingrItem;
        final long beforeShift = this.requiredAmount.getOrDefault(producer, 0L);

        addRequiredAmount(producer, ingrAmount, Long.MAX_VALUE, this.containerItemsCrafting);

        calculationStepTrigger(
                ingrItem,
                prefItem,
                requested,
                containerAmount,
                inventoryAmount,
                ingrAmount,
                this.requiredAmount.getOrDefault(producer, 0L) - beforeShift);

        if (prefItem != null && !isForeignBoxBoundary(ingrItem) && !visited.contains(prefItem.recipeId)) {
            final long multiplier = Math
                    .max(0, prefItem.getMultiplierFromAmount(this.requiredAmount.get(prefItem)) - prefItem.multiplier);

            if (multiplier > 0) {
                addShift(prefItem.recipeId, multiplier);
                visited.add(prefItem.recipeId);
                prepareIngredients(prefItem.recipeId, multiplier, visited);
                visited.remove(prefItem.recipeId);
            }
        }

    }

    protected void calculationStepTrigger(BookmarkItem ingrItem, BookmarkItem prefItem, long requested,
            long containerAmount, long inventoryAmount, long ingrAmount, long produced) {}

    protected void prepareIngredients(RecipeId recipeId, long multiplier, List<RecipeId> visited) {

        for (BookmarkItem item : this.ingredientsByRecipe.getOrDefault(recipeId, Collections.emptyList())) {
            if (!item.emptyFactor()) {
                calculateSuitableRecipe(item, multiplier, visited);
            }
        }

    }

    protected long addRequiredAmount(BookmarkItem prefItem, long ingrAmount, long maxAmount,
            List<ItemStack> containerItems) {
        long shiftAmount = this.requiredAmount.getOrDefault(prefItem, 0L);

        if (hasContainerItem(prefItem.itemStack)) {
            ItemStack itemStack = prefItem.itemStack;

            while (ingrAmount > 0 && shiftAmount < maxAmount) {
                long multiplier = 1;
                itemStack = itemStack.copy();
                itemStack.stackSize = 1;

                ingrAmount = shiftContainerItems(itemStack, prefItem.getStackSize(ingrAmount), containerItems)
                        * prefItem.fluidCellAmount;

                if (ingrAmount > 0) {
                    long steps = prefItem.getStackSize(ingrAmount);
                    final ContainerItemResult result = getToolsContainerItems(itemStack, steps);

                    if (result.stack == null) {
                        final long stepsPerReplacement = steps - result.leftSteps;
                        multiplier = Math
                                .min(steps / stepsPerReplacement, (maxAmount - shiftAmount) / prefItem.fluidCellAmount);
                        steps -= multiplier * stepsPerReplacement;
                    } else {
                        steps = result.leftSteps;
                    }

                    if (result.containerItem != null) {
                        long stackSize = result.containerItem.stackSize * multiplier;

                        while (stackSize > Integer.MAX_VALUE) {
                            final ItemStack copy = result.containerItem.copy();
                            copy.stackSize = Integer.MAX_VALUE;
                            containerItems.add(copy);
                            stackSize -= copy.stackSize;
                        }

                        result.containerItem.stackSize = (int) stackSize;
                        containerItems.add(result.containerItem);
                    }

                    if (result.stack != null) {
                        containerItems.add(result.stack);
                    }

                    ingrAmount = steps * prefItem.fluidCellAmount;
                }

                shiftAmount += multiplier * prefItem.fluidCellAmount;
            }

        } else {
            long initAmount = Math.min(ingrAmount, maxAmount - shiftAmount);

            shiftAmount += initAmount;
            ingrAmount -= initAmount;
        }

        this.requiredAmount.put(prefItem, shiftAmount);

        return ingrAmount;
    }

    private long shiftContainerItems(ItemStack aStack, long steps, List<ItemStack> containerItems) {
        final int initialSize = containerItems.size();

        for (int i = 0; i < initialSize && steps > 0; i++) {
            ItemStack bStack = containerItems.get(i);

            if (bStack != null && NEIClientUtils.areStacksSameTypeCraftingWithNBT(aStack, bStack)) {
                final ContainerItemResult result = getToolsContainerItems(bStack, steps);

                bStack = result.stack;
                steps = result.leftSteps;

                if (result.containerItem != null) {
                    containerItems.add(result.containerItem);
                }

                containerItems.set(i, bStack);
            }

        }

        containerItems.removeIf(stack -> stack == null);

        return steps;
    }

    protected ContainerItemResult getToolsContainerItems(ItemStack aStack, long steps) {
        final NBTTagCompound tagCompound = aStack.getTagCompound();

        if (tagCompound != null && tagCompound.hasKey("GT.ToolStats")) {
            final int damagePerContainerCraft = getGTToolDamagePerContainerCraft(aStack);

            if (damagePerContainerCraft > 0) {
                final NBTTagCompound toolStats = tagCompound.getCompoundTag("GT.ToolStats");
                final long maxDamage = toolStats.getLong("MaxDamage");
                final long damage = toolStats.getLong("Damage");
                final long leftSteps = (maxDamage - damage + damagePerContainerCraft - 1) / damagePerContainerCraft;
                final long availableSteps = Math.min(steps, Math.max(1, leftSteps));

                steps -= availableSteps;

                if ((damage + availableSteps * damagePerContainerCraft) >= maxDamage || leftSteps <= 0) {
                    aStack = null;
                } else {
                    toolStats.setLong("Damage", damage + availableSteps * damagePerContainerCraft);
                }

                return new ContainerItemResult(aStack, steps, null);
            }

        }

        final Item item = aStack.getItem();

        while (aStack != null && steps > 0) {
            aStack = item.getContainerItem(aStack);

            steps--;

            if (aStack != null && item != aStack.getItem()) {
                return new ContainerItemResult(null, steps, aStack);
            }
        }

        return new ContainerItemResult(aStack, steps, null);
    }

    private int getGTToolDamagePerContainerCraft(ItemStack aStack) {

        try {
            final Object toolStats = aStack.getItem().getClass().getMethod("getToolStats", ItemStack.class)
                    .invoke(aStack.getItem(), aStack);
            return (int) toolStats.getClass().getMethod("getToolDamagePerContainerCraft").invoke(toolStats);
        } catch (Throwable th) {
            th.printStackTrace();
        }

        return 0;
    }

    protected boolean hasContainerItem(ItemStack aStack) {

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
        for (BookmarkItem item : this.ingredientsByRecipe.getOrDefault(recipeId, Collections.emptyList())) {
            item.multiplier += shift;
        }

        for (BookmarkItem item : this.resultsByRecipe.getOrDefault(recipeId, Collections.emptyList())) {
            item.multiplier += shift;
        }
    }

}
