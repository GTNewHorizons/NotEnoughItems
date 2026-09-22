package codechicken.nei.recipe;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import codechicken.lib.gui.GuiDraw.ITooltipLineHandler;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.ItemsListTooltipLineHandler;
import codechicken.nei.ItemsTooltipLineHandler;
import codechicken.nei.ItemsTooltipLineHandler.StacksAmountRenderer;
import codechicken.nei.ItemsTooltipLineHandler.TotalAmountRenderer;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IGuiContainerOverlay;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.Recipe.RecipeIngredient;
import codechicken.nei.util.ReadableNumberConverter;

public class RecipeItemsTooltipLineHandler implements ITooltipLineHandler {

    protected static class ChanceTotalAmountRenderer extends TotalAmountRenderer {

        protected final Set<ItemStack> approximate;

        public ChanceTotalAmountRenderer(Set<ItemStack> approximate) {
            this.approximate = approximate;
        }

        @Override
        protected String formatAmount(ItemStack stack, long amount) {
            final String text = super.formatAmount(stack, amount);

            if (!this.approximate.contains(stack)) {
                return text;
            } else if (!text.isEmpty()) {
                return EnumChatFormatting.GOLD + "~" + text;
            } else if (amount == 0) {
                return EnumChatFormatting.GOLD + "~0";
            }

            return text;
        }
    }

    protected static class ChanceStacksAmountRenderer extends StacksAmountRenderer {

        protected static final int APPROXIMATE_COLOR = 0xFFAA00;

        protected final Set<ItemStack> approximate;

        public ChanceStacksAmountRenderer(Set<ItemStack> approximate) {
            this.approximate = approximate;
        }

        @Override
        protected void drawAmount(Rectangle4i rect, ItemStack stack, long amount) {
            if (amount == 0) {
                Badge.notConsumed().draw(rect);
            } else {
                super.drawAmount(rect, stack, amount);
            }
        }

        @Override
        protected String getPrefix(ItemStack stack) {
            return this.approximate.contains(stack) ? "~" : "";
        }

        @Override
        protected int getColor(ItemStack stack) {
            return this.approximate.contains(stack) ? APPROXIMATE_COLOR : super.getColor(stack);
        }
    }

    protected static final int MAX_ROWS = 15;

    protected final RecipeId recipeId;
    protected final long multiplier;
    protected boolean useInventory = false;

    protected final List<ItemsTooltipLineHandler> lines = new ArrayList<>();
    protected final Dimension size = new Dimension();
    protected String title = "";
    protected boolean created = false;

    public RecipeItemsTooltipLineHandler(RecipeId recipeId, long multiplier) {
        this.recipeId = recipeId;
        this.multiplier = multiplier;
    }

    public RecipeItemsTooltipLineHandler setUseInventory(boolean useInventory) {
        this.useInventory = useInventory;
        this.created = false;
        return this;
    }

    public RecipeId getRecipeId() {
        return this.recipeId;
    }

    public long getMultiplier() {
        return this.multiplier;
    }

    public boolean isUseInventory() {
        return this.useInventory;
    }

    public String getTitle() {
        ensureCreated();
        return this.title;
    }

    @Override
    public Dimension getSize() {
        ensureCreated();
        return this.size;
    }

    protected void ensureCreated() {
        if (!this.created) {
            this.created = true;
            createLines();
        }
    }

    @Override
    public void draw(int x, int y) {
        if (this.size.height == 0) return;

        for (ItemsTooltipLineHandler line : this.lines) {
            line.draw(x, y);
            y += line.getSize().height;
        }
    }

    protected void createLines() {
        this.lines.clear();
        this.size.setSize(0, 0);

        final RecipeHandlerRef handlerRef = RecipeHandlerRef.of(this.recipeId);

        if (handlerRef == null) {
            return;
        }

        final Recipe recipe = Recipe.of(handlerRef);
        final Set<NBTTagCompound> approximateResults = new HashSet<>();
        final Set<NBTTagCompound> approximateIngredients = new HashSet<>();
        final List<ItemStack> results = multiplyItems(recipe.getResults(), false, approximateResults);
        final List<ItemStack> ingredients = multiplyItems(recipe.getIngredients(), true, approximateIngredients);
        final ItemStackAmount ingredientsAmount = ItemStackAmount.of(ingredients);

        ingredients.sort(Comparator.comparingInt(stack -> stack.stackSize == 0 ? 0 : 1));

        this.title = handlerRef.handler.getRecipeName().trim() + " (x"
                + ReadableNumberConverter.INSTANCE.toWideReadableForm(this.multiplier)
                + ")";

        addResultLine(results, approximateResults);
        addRequiredLine(ingredients, approximateIngredients);

        if (this.useInventory) {
            final List<ItemStack> needed = subtractItems(ingredientsAmount, getInventoryItems());
            final ItemStackAmount neededAmount = ItemStackAmount.of(needed);
            final ItemStackAmount consumedAmount = ItemStackAmount.of(ingredientsAmount);
            consumedAmount.removeIf(entry -> entry.getValue() <= 0);

            if (!neededAmount.equals(consumedAmount)) {
                addNeededLine(
                        needed,
                        approximateIngredients,
                        needed.size() == 1 || ingredientsAmount.size() + neededAmount.size() <= MAX_ROWS);
            }
        }

        int width = 0;
        int height = 0;

        for (ItemsTooltipLineHandler line : this.lines) {
            width = Math.max(width, line.getSize().width);
            height += line.getSize().height;
        }

        this.size.setSize(width, height);
    }

    protected void addResultLine(List<ItemStack> items, Set<NBTTagCompound> approximate) {
        final String label = NEIClientUtils.translate("recipe.items.results");
        final ItemsTooltipLineHandler line = items.size() == 1 ? new ItemsListTooltipLineHandler(label, items, MAX_ROWS)
                : new ItemsTooltipLineHandler(label, items);

        if (line.isEmpty()) {
            return;
        }

        line.setLabelColor(EnumChatFormatting.GREEN);

        final Set<ItemStack> approximateItems = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ItemStack stack : line.getItems()) {
            if (approximate.contains(StackInfo.itemStackToNBT(stack, false))) {
                approximateItems.add(stack);
            }
        }

        line.setAmountRenderer(new ChanceTotalAmountRenderer(approximateItems));

        this.lines.add(line);
    }

    protected void addRequiredLine(List<ItemStack> items, Set<NBTTagCompound> approximate) {
        final String label = NEIClientUtils.translate("recipe.items.ingredients");
        final ItemsTooltipLineHandler line = new ItemsListTooltipLineHandler(label, items, MAX_ROWS);

        if (line.isEmpty()) {
            return;
        }

        line.setLabelColor(EnumChatFormatting.YELLOW);

        final Set<ItemStack> approximateItems = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ItemStack stack : line.getItems()) {
            if (approximate.contains(StackInfo.itemStackToNBT(stack, false))) {
                approximateItems.add(stack);
            }
        }

        line.setAmountRenderer(new ChanceStacksAmountRenderer(approximateItems));

        this.lines.add(line);
    }

    protected void addNeededLine(List<ItemStack> items, Set<NBTTagCompound> approximate, boolean vertical) {
        final String label = NEIClientUtils.translate("recipe.items.ingredients_needed");
        final ItemsTooltipLineHandler line = vertical ? new ItemsListTooltipLineHandler(label, items, MAX_ROWS)
                : new ItemsTooltipLineHandler(label, items);

        if (line.isEmpty()) {
            return;
        }

        line.setLabelColor(EnumChatFormatting.RED);

        final Set<ItemStack> approximateItems = Collections.newSetFromMap(new IdentityHashMap<>());

        for (ItemStack stack : line.getItems()) {
            if (approximate.contains(StackInfo.itemStackToNBT(stack, false))) {
                approximateItems.add(stack);
            }
        }

        line.setAmountRenderer(new ChanceStacksAmountRenderer(approximateItems));

        this.lines.add(line);
    }

    protected List<ItemStack> multiplyItems(List<RecipeIngredient> items, boolean ingredients,
            Set<NBTTagCompound> approximate) {
        final ItemStackAmount chanceAmounts = new ItemStackAmount();
        final List<ItemStack> result = new ArrayList<>();

        for (RecipeIngredient item : items) {
            chanceAmounts.add(item.getItemStack(), (long) item.getAmount() * item.getChance());
        }

        for (Map.Entry<NBTTagCompound, Long> entry : chanceAmounts.entrySet()) {
            final long amount = entry.getValue() * this.multiplier;
            final long entryAmount = ingredients
                    ? (amount + PositionedStack.CHANCE_FULL - 1) / PositionedStack.CHANCE_FULL
                    : amount / PositionedStack.CHANCE_FULL;
            final ItemStack stack = StackInfo.loadFromNBT(entry.getKey(), entryAmount);

            if (amount % PositionedStack.CHANCE_FULL != 0) {
                approximate.add(entry.getKey());
            }

            result.add(stack);
        }

        return result;
    }

    protected static ItemStackAmount getInventoryItems() {
        GuiScreen screen = NEIClientUtils.mc().currentScreen;

        if (screen instanceof IGuiContainerOverlay overlay) {
            screen = overlay.getFirstScreen();
        }

        if (screen instanceof GuiContainer container) {
            return AutoCraftingManager.getInventoryItems(container);
        }

        return new ItemStackAmount();
    }

    protected static List<ItemStack> subtractItems(ItemStackAmount items, ItemStackAmount subtrahend) {
        final List<ItemStack> result = new ArrayList<>();

        for (Map.Entry<NBTTagCompound, Long> entry : items.entrySet()) {
            final ItemStack stack = StackInfo.loadFromNBT(entry.getKey(), 0);
            final long amount = entry.getValue() - subtrahend.getOrDefault(stack, 0L);

            if (amount > 0) {
                result.add(StackInfo.withAmount(stack, amount));
            }
        }

        return result;
    }

}
