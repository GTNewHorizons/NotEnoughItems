package codechicken.nei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.ItemsGrid.MouseContext;
import codechicken.nei.NEIClientUtils.Alignment;
import codechicken.nei.recipe.AutoCraftingManager;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.ICraftingHandler;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;
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
            this.isFluidDisplay = StackInfo.itemStackToNBT(itemStack).hasKey("gtFluidName");
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

    public static List<String> guiBlacklist = new ArrayList<>();
    private final int MIN_DELAY = 600;

    private Map<String, ArrayList<ICraftingHandler>> identHandlers = new HashMap<>();
    private Map<ItemStack, RecipeId> availableRecipes = new HashMap<>();

    private GuiContainer lastGuiContainer = null;
    private ItemStackAmount lastPlayerInventory = null;
    private long lastPlayerInventorySync = 0;

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
                    int maxSlotIndex = this.rows * this.columns;
                    final AtomicInteger slotIndex = new AtomicInteger(0);
                    final AtomicInteger itemIndex = new AtomicInteger(0);
                    final List<CraftablesGridSlot> gridMask = new ArrayList<>();
                    this.realItems.clear();

                    if (!ItemCraftablesPanel.this.availableRecipes.isEmpty()) {
                        ItemCraftablesPanel.this.availableRecipes.entrySet().stream()
                                .sorted(Map.Entry.comparingByKey(ItemSorter.instance)).forEach(entry -> {
                                    if (!isInvalidSlot(slotIndex.get())) {
                                        this.realItems.add(entry.getKey());
                                        gridMask.add(
                                                new CraftablesGridSlot(
                                                        slotIndex.get(),
                                                        itemIndex.getAndIncrement(),
                                                        entry.getKey(),
                                                        entry.getValue()));
                                    }
                                    slotIndex.incrementAndGet();
                                });

                        this.gridMask = gridMask.subList(0, Math.min(gridMask.size(), maxSlotIndex));
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
        updateCraftables();
        super.update();
    }

    private void updateCraftables() {

        if (!NEIClientConfig.showCraftablesPanelWidget()) {
            clearCraftables();
        } else if (this.lastPlayerInventory == null
                || Math.abs(System.currentTimeMillis() - this.lastPlayerInventorySync) >= MIN_DELAY) {
                    final GuiContainer firstGui = getGuiContainer();
                    this.lastPlayerInventorySync = System.currentTimeMillis();

                    if (firstGui != null && !ItemCraftablesPanel.guiBlacklist.contains(firstGui.getClass().getName())) {
                        final ItemStackAmount inv = AutoCraftingManager.getInventoryItems(firstGui);

                        if (this.lastGuiContainer != (this.lastGuiContainer = firstGui)
                                || !inv.equals(this.lastPlayerInventory)) {
                            this.lastPlayerInventory = inv;
                            this.availableRecipes = generateCraftables(firstGui);
                            this.grid.onGridChanged();
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
        return (firstGui instanceof GuiRecipe gui) ? gui.firstGui : firstGui;
    }

    private Map<ItemStack, RecipeId> generateCraftables(GuiContainer firstGui) {
        final List<IRecipeHandler> availableHandlers = getAvailableHandlers(firstGui);
        final Map<ItemStack, RecipeId> availableRecipes = new HashMap<>();

        if (!availableHandlers.isEmpty()) {
            final List<ItemStack> invStacks = ItemCraftablesPanel.this.lastPlayerInventory.values();

            for (IRecipeHandler handler : availableHandlers) {
                availableRecipes.putAll(filterHandlerRecipes(handler, firstGui, invStacks));
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

    private Map<ItemStack, RecipeId> filterHandlerRecipes(IRecipeHandler handler, GuiContainer firstGui,
            List<ItemStack> invStacks) {
        final Map<ItemStack, RecipeId> availableRecipes = new HashMap<>();

        IntStream.range(0, handler.numRecipes()).parallel().forEach(recipeIndex -> {
            if (existsIngredients(firstGui, handler.getIngredientStacks(recipeIndex), invStacks)) {
                final Recipe recipe = Recipe.of(handler, recipeIndex);

                if (!recipe.getResults().isEmpty()) {
                    synchronized (availableRecipes) {
                        availableRecipes.put(recipe.getResult(), recipe.getRecipeId());
                    }
                }
            }
        });

        return availableRecipes;
    }

    private boolean existsIngredients(GuiContainer firstGui, List<PositionedStack> ingredients,
            List<ItemStack> invStacks) {
        final Map<ItemStack, Integer> usedItems = new HashMap<>();

        for (PositionedStack pStack : ingredients) {
            final ItemStack used = invStacks.stream().filter(
                    is -> usedItems.getOrDefault(is, is.stackSize) >= pStack.item.stackSize && pStack.contains(is))
                    .findAny().orElse(null);

            if (used != null) {
                usedItems.put(used, usedItems.getOrDefault(used, used.stackSize) - pStack.item.stackSize);
            } else {
                return false;
            }
        }

        return !usedItems.isEmpty();
    }

}
