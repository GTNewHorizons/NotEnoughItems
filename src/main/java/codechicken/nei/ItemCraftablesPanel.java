package codechicken.nei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.ItemsGrid.MouseContext;
import codechicken.nei.NEIClientUtils.Alignment;
import codechicken.nei.recipe.AutoCraftingManager;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.RecipeInfo;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.ReadableNumberConverter;

public class ItemCraftablesPanel
        extends AbstractSubpanel<ItemsGrid<ItemCraftablesPanel.CraftablesGridSlot, MouseContext>> {

    public static class CraftablesGridSlot extends ItemsGridSlot {

        protected final long realAmount;
        protected final boolean isFluidDisplay;
        protected final RecipeId recipeId;

        public CraftablesGridSlot(int slotIndex, int itemIndex, ItemStack itemStack, RecipeId recipeId) {
            super(slotIndex, itemIndex, itemStack);
            this.realAmount = StackInfo.getAmount(itemStack);
            this.isFluidDisplay = StackInfo.isFluidDisplayItem(itemStack);
            this.recipeId = recipeId;
        }

        @Override
        public RecipeId getRecipeId() {
            return this.recipeId;
        }

        @Override
        public <M extends MouseContext> void afterDraw(Rectangle4i rect, M mouseContext) {
            drawStackSize(rect);
        }

        protected void drawStackSize(Rectangle4i rect) {
            long stackSize = this.realAmount;

            if (stackSize > 1) {
                final float panelFactor = (rect.w - 2) / (DEFAULT_SLOT_SIZE - 2);
                String amountString = "";

                if (stackSize < 10_000) {
                    amountString = String.valueOf(stackSize);
                } else {
                    amountString = ReadableNumberConverter.INSTANCE.toWideReadableForm(stackSize);
                }

                if (this.isFluidDisplay) {
                    amountString += "L";
                }

                NEIClientUtils.drawNEIOverlayText(
                        amountString,
                        new Rectangle4i(rect.x + 1, rect.y + 1, rect.w - 2, rect.h - 2),
                        panelFactor,
                        0xFFFFFF,
                        true,
                        this.isFluidDisplay ? Alignment.BottomLeft : Alignment.BottomRight);
            }
        }

    }

    private static class CraftablesSnapshot {

        final GuiContainer guiContainer;
        final ItemStackAmount inventory;
        final boolean showOnlyFavorites;
        final int maxCandidates;

        CraftablesSnapshot(GuiContainer guiContainer, ItemStackAmount inventory, boolean showOnlyFavorites,
                int maxCandidates) {
            this.guiContainer = guiContainer;
            this.inventory = inventory;
            this.showOnlyFavorites = showOnlyFavorites;
            this.maxCandidates = maxCandidates;
        }
    }

    private static class RecipeCandidate {

        final IRecipeHandler handler;
        final int recipeIndex;
        final List<PositionedStack> ingredientStacks;
        final ItemStack sortKeyItem;

        RecipeCandidate(IRecipeHandler handler, int recipeIndex, List<PositionedStack> ingredientStacks,
                ItemStack sortKeyItem) {
            this.handler = handler;
            this.recipeIndex = recipeIndex;
            this.ingredientStacks = ingredientStacks;
            this.sortKeyItem = sortKeyItem;
        }
    }

    private static final Comparator<ItemStack> ITEM_SORT_COMPARATOR = Comparator
            .comparing(FavoriteRecipes::containsManual).thenComparing(FavoriteRecipes::contains).reversed()
            .thenComparing(ItemSorter.instance);

    public static List<String> guiBlacklist = new ArrayList<>();
    private final int MIN_DELAY = 600;

    private Map<String, ArrayList<ICraftingHandler>> identHandlers = new HashMap<>();
    private Map<ItemStack, RecipeId> availableRecipes = new HashMap<>();

    private GuiContainer lastGuiContainer = null;
    private ItemStackAmount lastPlayerInventory = null;
    private long lastPlayerInventorySync = 0;
    private int lastFavoritesCount = 0;

    private volatile CraftablesSnapshot pendingSnapshot;
    private final AtomicReference<Map<ItemStack, RecipeId>> pendingResult = new AtomicReference<>();
    private final RestartableTask task = new RestartableTask("NEI Craftables Panel") {

        @Override
        public void execute() {
            pendingResult.set(generateCraftables(ItemCraftablesPanel.this.pendingSnapshot));
        }
    };

    public ItemCraftablesPanel() {
        this.grid = new ItemsGrid<>() {

            protected List<CraftablesGridSlot> gridMask;

            @Override
            protected void onGridChanged() {
                this.gridMask = null;
                super.onGridChanged();
            }

            @Override
            public List<CraftablesGridSlot> getMask() {

                if (this.gridMask == null) {
                    final int maxSlotIndex = this.rows * this.columns;
                    final List<CraftablesGridSlot> gridMask = new ArrayList<>();
                    this.realItems.clear();

                    if (!ItemCraftablesPanel.this.availableRecipes.isEmpty()) {
                        final List<Map.Entry<ItemStack, RecipeId>> entries = ItemCraftablesPanel.this.availableRecipes
                                .entrySet().stream().sorted(Map.Entry.comparingByKey(ITEM_SORT_COMPARATOR))
                                .limit(maxSlotIndex).collect(Collectors.toList());
                        int slotIndex = 0;
                        int itemIndex = 0;

                        for (Map.Entry<ItemStack, RecipeId> entry : entries) {
                            if (!isInvalidSlot(slotIndex)) {
                                this.realItems.add(entry.getKey());
                                gridMask.add(
                                        new CraftablesGridSlot(
                                                slotIndex,
                                                itemIndex++,
                                                entry.getKey(),
                                                entry.getValue()));
                            }
                            if (slotIndex++ >= maxSlotIndex) {
                                break;
                            }
                        }

                        this.gridMask = gridMask;
                    } else {
                        this.gridMask = Collections.emptyList();
                    }

                    ItemCraftablesPanel.this.updateLinePadding();
                }

                return this.gridMask;
            }

            @Override
            protected MouseContext getMouseContext(int mousex, int mousey) {
                final ItemsGridSlot hovered = getSlotMouseOver(mousex, mousey);

                if (hovered != null) {
                    return new MouseContext(
                            hovered.slotIndex,
                            hovered.slotIndex / this.columns,
                            hovered.slotIndex % this.columns);
                }

                return null;
            }

        };
    }

    @Override
    protected ItemStack getDraggedStackWithQuantity(ItemStack itemStack) {
        return ItemQuantityField.prepareStackWithQuantity(itemStack, StackInfo.getAmount(itemStack));
    }

    @Override
    public void draw(int mousex, int mousey) {
        if (!this.availableRecipes.isEmpty()) {
            super.draw(mousex, mousey);
        }
    }

    @Override
    public int setPanelWidth(int width) {
        if (getGuiContainer() != this.lastGuiContainer) updateCraftables();

        final int columns = width / ItemsGrid.SLOT_SIZE;
        final int useRows = NEIClientConfig.getIntSetting("inventory.craftables.useRows");
        final int rows = (int) Math.min(Math.ceil(this.availableRecipes.size() * 1f / columns), useRows);

        this.w = width;
        this.h = 8 + ItemsGrid.SLOT_SIZE * Math.max(rows, 1);

        return rows;
    }

    public void update() {
        this.splittingLineColor = NEIClientConfig.getSetting("inventory.craftables.color").getHexValue();
        applyPendingResult();
        updateCraftables();
        super.update();
    }

    private void applyPendingResult() {
        final Map<ItemStack, RecipeId> result = this.pendingResult.getAndSet(null);

        if (result != null && NEIClientConfig.showCraftablesPanelWidget()) {
            this.availableRecipes = result;
            this.grid.onGridChanged();
        }
    }

    private void updateCraftables() {

        if (!NEIClientConfig.showCraftablesPanelWidget()) {
            clearCraftables();
        } else if (this.lastPlayerInventory == null
                || Math.abs(System.currentTimeMillis() - this.lastPlayerInventorySync) >= MIN_DELAY) {
                    final GuiContainer firstGui = getGuiContainer();
                    this.lastPlayerInventorySync = System.currentTimeMillis();

                    if (firstGui != null && !ItemCraftablesPanel.guiBlacklist.contains(firstGui.getClass().getName())) {
                        final boolean showOnlyFavorites = NEIClientConfig
                                .getBooleanSetting("inventory.craftables.favoritesOnly");
                        final ItemStackAmount inv = AutoCraftingManager.getInventoryItems(firstGui);

                        if (this.lastGuiContainer != firstGui
                                || showOnlyFavorites && this.lastFavoritesCount != FavoriteRecipes.size()
                                || !inv.equals(this.lastPlayerInventory)) {
                            this.lastPlayerInventory = inv;
                            this.lastGuiContainer = firstGui;
                            this.lastFavoritesCount = FavoriteRecipes.size();
                            final int maxRows = NEIClientConfig.getIntSetting("inventory.craftables.useRows") + 2;
                            final int maxCandidates = Math.max(this.grid.columns, 1) * maxRows;
                            this.pendingSnapshot = new CraftablesSnapshot(
                                    firstGui,
                                    inv,
                                    showOnlyFavorites,
                                    maxCandidates);
                            this.task.restart();
                        }

                    } else {
                        clearCraftables();
                    }

                }

    }

    private void clearCraftables() {
        if (!this.availableRecipes.isEmpty()) {
            this.availableRecipes = Collections.emptyMap();
            this.lastPlayerInventory = null;
            this.grid.onGridChanged();
        }
    }

    private GuiContainer getGuiContainer() {
        final GuiContainer firstGui = NEIClientUtils.getGuiContainer();
        return (firstGui instanceof GuiRecipe<?>gui) ? gui.firstGui : firstGui;
    }

    private Map<ItemStack, RecipeId> generateCraftables(CraftablesSnapshot snapshot) {
        final List<IRecipeHandler> availableHandlers = getAvailableHandlers(snapshot.guiContainer);
        final Map<ItemStack, RecipeId> availableRecipes = new HashMap<>();

        if (!availableHandlers.isEmpty()) {
            final Map<Item, List<ItemStack>> invByItem = indexInventoryByItem(snapshot.inventory.values());

            if (snapshot.showOnlyFavorites) {
                for (IRecipeHandler handler : availableHandlers) {
                    availableRecipes.putAll(filterHandlerRecipes(handler, invByItem, true));
                }
            } else {
                final List<RecipeCandidate> candidates = Collections.synchronizedList(new ArrayList<>());

                for (IRecipeHandler handler : availableHandlers) {
                    collectHandlerCandidates(handler, invByItem, candidates);
                }

                candidates.sort(Comparator.comparing(c -> c.sortKeyItem, ITEM_SORT_COMPARATOR));
                candidates.subList(0, Math.min(candidates.size(), snapshot.maxCandidates)).parallelStream()
                        .forEach(candidate -> buildCandidate(candidate, availableRecipes));
            }
        }

        return availableRecipes;
    }

    private List<IRecipeHandler> getAvailableHandlers(GuiContainer firstGui) {
        final List<IRecipeHandler> availableHandlers = new ArrayList<>();

        for (String ident : RecipeInfo.getOverlayHandlerIdents(firstGui)) {
            availableHandlers.addAll(identHandlers.computeIfAbsent(ident, GuiCraftingRecipe::getCraftingHandlers));
        }

        return availableHandlers;
    }

    private static Map<Item, List<ItemStack>> indexInventoryByItem(List<ItemStack> invStacks) {
        final Map<Item, List<ItemStack>> index = new HashMap<>();

        for (ItemStack stack : invStacks) {
            index.computeIfAbsent(stack.getItem(), i -> new ArrayList<>()).add(stack);
        }

        return index;
    }

    private void collectHandlerCandidates(IRecipeHandler handler, Map<Item, List<ItemStack>> invByItem,
            List<RecipeCandidate> candidates) {
        IntStream.range(0, handler.numRecipes()).parallel().forEach(recipeIndex -> {
            final List<PositionedStack> ingredientStacks = handler.getIngredientStacks(recipeIndex);

            if (existsIngredients(ingredientStacks, invByItem)) {
                final ItemStack sortKeyItem = getCheapResultItem(handler, recipeIndex);

                if (sortKeyItem != null) {
                    candidates.add(new RecipeCandidate(handler, recipeIndex, ingredientStacks, sortKeyItem));
                }
            }
        });
    }

    private void buildCandidate(RecipeCandidate candidate, Map<ItemStack, RecipeId> availableRecipes) {
        final ItemStack result = getCheapResult(candidate.handler, candidate.recipeIndex);

        if (result != null) {
            final RecipeId recipeId = RecipeId.of(candidate.handler, candidate.recipeIndex, candidate.ingredientStacks);

            synchronized (availableRecipes) {
                availableRecipes.put(result, recipeId);
            }
        }
    }

    private static ItemStack getCheapResultItem(IRecipeHandler handler, int recipeIndex) {
        final PositionedStack resultStack = handler.getResultStack(recipeIndex);

        if (resultStack != null) {
            return resultStack.item;
        }

        final List<PositionedStack> otherStacks = handler.getOtherStacks(recipeIndex);

        for (PositionedStack otherStack : otherStacks) {
            if (!FluidContainerRegistry.isContainer(otherStack.items[0])
                    || StackInfo.getFluid(otherStack.items[0]) != null) {
                return otherStack.item;
            }
        }

        return otherStacks.isEmpty() ? null : otherStacks.get(0).item;
    }

    private static ItemStack getCheapResult(IRecipeHandler handler, int recipeIndex) {
        final PositionedStack resultStack = handler.getResultStack(recipeIndex);
        final List<PositionedStack> results = resultStack != null ? Collections.singletonList(resultStack)
                : handler.getOtherStacks(recipeIndex);

        if (results.isEmpty()) {
            return null;
        }

        ItemStack stack = null;

        if (results.size() > 1) {
            for (PositionedStack result : results) {
                if (!FluidContainerRegistry.isContainer(result.item) || StackInfo.getFluid(result.item) != null) {
                    stack = result.item;
                    break;
                }
            }
        }

        if (stack == null) {
            stack = results.get(0).item;
        }

        int stackSize = 0;

        for (PositionedStack result : results) {
            if (containsSameType(result, stack)) {
                stackSize += StackInfo.getAmount(result.item);
            }
        }

        return StackInfo.withAmount(stack, stackSize);
    }

    private static boolean containsSameType(PositionedStack pStack, ItemStack stack) {
        final ItemStack normalized = StackInfo.withAmount(stack, 0);

        for (ItemStack permutation : pStack.items) {
            if (NEIServerUtils.areStacksSameTypeWithNBT(StackInfo.withAmount(permutation, 0), normalized)) {
                return true;
            }
        }

        return false;
    }

    private Map<ItemStack, RecipeId> filterHandlerRecipes(IRecipeHandler handler, Map<Item, List<ItemStack>> invByItem,
            boolean showOnlyFavorites) {
        final Map<ItemStack, RecipeId> availableRecipes = new HashMap<>();

        IntStream.range(0, handler.numRecipes()).parallel().forEach(recipeIndex -> {
            final List<PositionedStack> ingredientStacks = handler.getIngredientStacks(recipeIndex);

            if (existsIngredients(ingredientStacks, invByItem)) {
                final ItemStack result = getCheapResult(handler, recipeIndex);

                if (result != null) {
                    final RecipeId recipeId = RecipeId.of(handler, recipeIndex, ingredientStacks);

                    if (!showOnlyFavorites || FavoriteRecipes.getFavorite(recipeId) != null) {
                        synchronized (availableRecipes) {
                            availableRecipes.put(result, recipeId);
                        }
                    }
                }
            }
        });

        return availableRecipes;
    }

    private boolean existsIngredients(List<PositionedStack> ingredients, Map<Item, List<ItemStack>> invByItem) {
        final Map<ItemStack, Integer> usedItems = new HashMap<>();

        for (PositionedStack pStack : ingredients) {
            ItemStack used = null;

            for (ItemStack permutation : pStack.items) {
                final List<ItemStack> candidates = invByItem.get(permutation.getItem());

                if (candidates == null) continue;

                for (ItemStack is : candidates) {
                    if (usedItems.getOrDefault(is, is.stackSize) >= pStack.item.stackSize
                            && NEIServerUtils.areStacksSameTypeCrafting(permutation, is)) {
                        used = is;
                        break;
                    }
                }

                if (used != null) break;
            }

            if (used != null) {
                usedItems.put(used, usedItems.getOrDefault(used, used.stackSize) - pStack.item.stackSize);
            } else {
                return false;
            }
        }

        return !usedItems.isEmpty();
    }

}
