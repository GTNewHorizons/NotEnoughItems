package codechicken.nei.recipe.chain;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.gui.GuiDraw.ITooltipLineHandler;
import codechicken.nei.ItemSorter;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.ItemsTooltipLineHandler;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.recipe.AutoCraftingManager;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.recipe.StackInfo;

public class RecipeChainTooltipLineHandler implements ITooltipLineHandler {

    public final int groupId;
    public final boolean crafting;
    public final RecipeChainMath math;
    protected final List<BookmarkItem> initialItems;
    protected final Map<RecipeId, Long> outputRecipes;
    protected final Map<String, List<BookmarkItem>> demand;

    protected ItemsTooltipLineHandler available;
    protected ItemsTooltipLineHandler inputs;
    protected ItemsTooltipLineHandler outputs;
    protected ItemsTooltipLineHandler remainder;
    protected Map<String, ItemsTooltipLineHandler> craftingNeeded;
    protected boolean lastShiftKey = false;
    protected boolean lastControlKey = false;

    protected Dimension size = new Dimension();

    public RecipeChainTooltipLineHandler(int groupId, boolean crafting, RecipeChainMath math) {
        this.groupId = groupId;
        this.crafting = crafting;
        this.math = math;
        this.initialItems = new ArrayList<>(this.math.initialItems);
        this.outputRecipes = new HashMap<>(this.math.outputRecipes);
        this.demand = new HashMap<>();
    }

    private void onUpdate() {
        final List<ItemStack> available = new ArrayList<>();
        final List<ItemStack> inputs = new ArrayList<>();
        final List<ItemStack> outputs = new ArrayList<>();
        final List<ItemStack> remainder = new ArrayList<>();
        final Map<String, List<ItemStack>> craftingNeeded = new LinkedHashMap<>();
        final ItemStackAmount inventory = new ItemStackAmount();
        final GuiContainer currentGui = NEIClientUtils.getGuiContainer();

        if (this.lastShiftKey && !(currentGui instanceof GuiRecipe<?>)) {
            inventory.putAll(AutoCraftingManager.getInventoryItems(currentGui));
        }

        if (!this.math.outputRecipes.isEmpty()) {
            this.math.initialItems.clear();
            this.math.outputRecipes.clear();
            this.math.outputRecipes.putAll(this.outputRecipes);

            if (this.lastShiftKey) {

                if (!this.lastControlKey) {
                    final List<ItemStack> items = inventory.values();
                    for (BookmarkItem item : math.recipeResults) {
                        if (!item.emptyFactor() && this.math.outputRecipes.containsKey(item.recipeId)) {
                            long amount = 0;

                            for (ItemStack stack : items) {
                                if (stack != null
                                        && NEIClientUtils.areStacksSameTypeCraftingWithNBT(stack, item.itemStack)) {
                                    amount += StackInfo.getAmount(stack);
                                }
                            }

                            if (amount >= item.getAmount()) {
                                final long itemAmount = item.getAmount(this.math.outputRecipes.get(item.recipeId));

                                if (itemAmount > 0) {
                                    amount += itemAmount - amount % itemAmount;
                                }

                                this.math.outputRecipes.put(
                                        item.recipeId,
                                        Math.max(
                                                this.math.outputRecipes.get(item.recipeId),
                                                item.getMultiplierFromAmount(amount)));
                            }
                        }
                    }
                }

                for (ItemStack stack : inventory.values()) {
                    this.math.initialItems.add(BookmarkItem.of(-1, stack.copy()));
                }

            } else {
                this.math.initialItems.addAll(initialItems);
            }

            this.math.refresh();

            this.demand.clear();
            for (BookmarkItem ingr : this.math.recipeIngredients) {
                if (ingr.recipeId != null && ingr.getAmount() > 0) {
                    this.demand.computeIfAbsent(StackInfo.getItemStackGUID(ingr.itemStack), k -> new ArrayList<>())
                            .add(ingr);
                }
            }

            for (BookmarkItem item : math.initialItems) {
                final long amount = math.requiredAmount.getOrDefault(item, 0L);

                if (amount > 0) {
                    if (this.lastShiftKey) {
                        available.add(item.getItemStack(amount));
                    } else {
                        inputs.add(item.getItemStack(amount));
                    }
                }

            }

            for (BookmarkItem item : math.recipeIngredients) {
                final long amount = math.requiredAmount.containsKey(math.preferredItems.get(item)) ? 0
                        : math.requiredAmount.getOrDefault(item, item.getAmount());

                if (amount > 0) {
                    inputs.add(item.getItemStack(amount));
                }
            }

            for (BookmarkItem item : math.recipeResults) {
                final long amount = item.getAmount() - math.requiredAmount.getOrDefault(item, 0L);

                if (amount > 0) {
                    if (math.outputRecipes.containsKey(item.recipeId)) {
                        outputs.add(item.getItemStack(amount));
                    } else if (this.lastShiftKey) {
                        remainder.add(item.getItemStack(amount));
                    }
                }
            }

            if (this.lastShiftKey) {
                for (ItemStack stack : math.containerItems) {
                    if (stack != null) {
                        remainder.add(stack.copy());
                    }
                }
            }

        } else if (this.lastShiftKey) {

            for (BookmarkItem item : this.math.initialItems) {
                if (inventory.contains(item.itemStack)) {
                    final long invAmount = inventory.get(item.itemStack) * item.fluidCellAmount;

                    if ((item.getAmount() - invAmount) > 0) {
                        inputs.add(item.getItemStack(item.getAmount() - invAmount));
                    }

                    if (Math.min(item.getAmount(), invAmount) > 0) {
                        available.add(item.getItemStack(Math.min(item.getAmount(), invAmount)));
                    }

                } else {
                    inputs.add(item.getItemStack());
                }
            }

        }
        if (this.lastShiftKey) {
            for (Map.Entry<BookmarkItem, Long> item : this.math.requiredAmount.entrySet()) {
                if (item.getKey().type == BookmarkItem.BookmarkItemType.RESULT && item.getValue() != 0) {
                    final RecipeId recipeId = item.getKey().recipeId;
                    final String label;

                    if (recipeId.isShapedRecipe() || recipeId.isShapelessRecipe()) {
                        label = NEIClientUtils.translate("bookmark.crafting_chain.needed");
                    } else {
                        final RecipeHandlerRef handlerRef = RecipeHandlerRef.of(recipeId);
                        label = handlerRef != null ? handlerRef.handler.getRecipeName() : recipeId.getHandlerName();
                    }

                    craftingNeeded.computeIfAbsent(label, k -> new ArrayList<>())
                            .add(item.getKey().getItemStack(item.getValue()));
                }
            }
        }

        inputs.sort(
                Comparator.comparing((ItemStack stack) -> StackInfo.getFluid(stack) != null)
                        .thenComparingInt(stack -> -1 * stack.stackSize).thenComparing(ItemSorter.instance));
        outputs.sort(
                Comparator.comparing((ItemStack stack) -> StackInfo.getFluid(stack) != null)
                        .thenComparingInt(stack -> -1 * stack.stackSize).thenComparing(ItemSorter.instance));
        remainder.sort(
                Comparator.comparing((ItemStack stack) -> StackInfo.getFluid(stack) != null)
                        .thenComparingInt(stack -> -1 * stack.stackSize).thenComparing(ItemSorter.instance));
        this.craftingNeeded = new LinkedHashMap<>();
        final String craftingLabel = NEIClientUtils.translate("bookmark.crafting_chain.needed");
        if (craftingNeeded.containsKey(craftingLabel)) {
            this.craftingNeeded.put(
                    craftingLabel,
                    new ItemsTooltipLineHandler(
                            craftingLabel,
                            craftingNeeded.remove(craftingLabel),
                            true,
                            Integer.MAX_VALUE));
        }

        for (Map.Entry<String, List<ItemStack>> entry : craftingNeeded.entrySet()) {
            this.craftingNeeded.put(
                    entry.getKey(),
                    new ItemsTooltipLineHandler(entry.getKey(), entry.getValue(), true, Integer.MAX_VALUE));
        }

        this.inputs = new ItemsTooltipLineHandler(
                this.lastShiftKey ? NEIClientUtils.translate("bookmark.crafting_chain.missing")
                        : NEIClientUtils.translate("bookmark.crafting_chain.input"),
                inputs,
                true,
                Integer.MAX_VALUE);

        this.available = new ItemsTooltipLineHandler(
                NEIClientUtils.translate("bookmark.crafting_chain.available"),
                available,
                true,
                Integer.MAX_VALUE);

        this.outputs = new ItemsTooltipLineHandler(
                NEIClientUtils.translate("bookmark.crafting_chain.output"),
                outputs,
                true,
                Integer.MAX_VALUE);

        this.remainder = new ItemsTooltipLineHandler(
                NEIClientUtils.translate("bookmark.crafting_chain.remainder"),
                remainder,
                true,
                Integer.MAX_VALUE);

        if (this.lastShiftKey) {
            this.inputs.setLabelColor(EnumChatFormatting.RED);
            this.available.setLabelColor(EnumChatFormatting.GREEN);
            this.craftingNeeded.values().forEach(h -> h.setLabelColor(EnumChatFormatting.BLUE));
        }

        this.size.height = this.size.width = 0;

        if (!this.inputs.isEmpty() || !this.outputs.isEmpty()
                || !this.remainder.isEmpty()
                || !this.available.isEmpty()) {

            if (!this.math.outputRecipes.isEmpty()) {
                this.size.height = 2 + GuiDraw.fontRenderer.FONT_HEIGHT;
            }

            this.size.width = Stream.concat(
                    Arrays.asList(this.inputs, this.outputs, this.remainder, this.available).stream(),
                    this.craftingNeeded.values().stream()).mapToInt(c -> c.getSize().width).max().getAsInt();

            this.size.height += Stream.concat(
                    Arrays.asList(this.inputs, this.outputs, this.remainder, this.available).stream(),
                    this.craftingNeeded.values().stream()).mapToInt(c -> c.getSize().height).sum();
        }

    }

    @Override
    public Dimension getSize() {
        maybeUpdate();
        return this.size;
    }

    public void maybeUpdate() {
        boolean update = this.outputs == null;
        update = this.lastShiftKey != (this.lastShiftKey = NEIClientUtils.shiftKey()) || update;
        update = this.lastControlKey != (this.lastControlKey = NEIClientUtils.controlKey()) || update;

        if (update) {
            onUpdate();
        }
    }

    public List<BookmarkItem> getDemand(ItemStack stack) {
        return this.demand.get(StackInfo.getItemStackGUID(stack));
    }

    @Override
    public void draw(int x, int y) {
        if (this.size.height == 0) return;

        if (!this.math.outputRecipes.isEmpty()) {
            GuiDraw.fontRenderer.drawStringWithShadow(
                    EnumChatFormatting.AQUA + NEIClientUtils.translate("bookmark.crafting_chain"),
                    x,
                    y + 2,
                    0xee555555);

            y += 2 + GuiDraw.fontRenderer.FONT_HEIGHT;
        }

        if (NEIClientConfig.recipeChainDir() == 0) {
            if (!this.inputs.isEmpty()) {
                this.inputs.draw(x, y);
                y += this.inputs.getSize().height;
            }

            if (!this.available.isEmpty()) {
                this.available.draw(x, y);
                y += this.available.getSize().height;
            }

            for (ItemsTooltipLineHandler handler : this.craftingNeeded.values()) {
                if (!handler.isEmpty()) {
                    handler.draw(x, y);
                    y += handler.getSize().height;
                }
            }

            if (!this.outputs.isEmpty()) {
                this.outputs.draw(x, y);
                y += this.outputs.getSize().height;
            }

        } else {

            if (!this.outputs.isEmpty()) {
                this.outputs.draw(x, y);
                y += this.outputs.getSize().height;
            }

            if (!this.inputs.isEmpty()) {
                this.inputs.draw(x, y);
                y += this.inputs.getSize().height;
            }
            for (ItemsTooltipLineHandler handler : this.craftingNeeded.values()) {
                if (!handler.isEmpty()) {
                    handler.draw(x, y);
                    y += handler.getSize().height;
                }
            }

            if (!this.available.isEmpty()) {
                this.available.draw(x, y);
                y += this.available.getSize().height;
            }
        }

        if (!this.remainder.isEmpty()) {
            this.remainder.draw(x, y);
        }

    }

}
