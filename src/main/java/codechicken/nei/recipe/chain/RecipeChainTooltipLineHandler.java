package codechicken.nei.recipe.chain;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import codechicken.nei.recipe.StackInfo;

public class RecipeChainTooltipLineHandler implements ITooltipLineHandler {

    public final int groupId;
    public final boolean crafting;
    protected final RecipeChainMath math;
    protected final List<BookmarkItem> initialItems;
    protected final Map<RecipeId, Long> outputRecipes;

    protected ItemsTooltipLineHandler available;
    protected ItemsTooltipLineHandler inputs;
    protected ItemsTooltipLineHandler outputs;
    protected ItemsTooltipLineHandler remainder;
    protected ItemsTooltipLineHandler craftingNeeded;
    protected boolean lastShiftKey = false;
    protected boolean lastControlKey = false;

    protected Dimension size = new Dimension();

    public RecipeChainTooltipLineHandler(int groupId, boolean crafting, RecipeChainMath math) {
        this.groupId = groupId;
        this.crafting = crafting;
        this.math = math;
        this.initialItems = new ArrayList<>(this.math.initialItems);
        this.outputRecipes = new HashMap<>(this.math.outputRecipes);
    }

    private void onUpdate() {
        final List<ItemStack> available = new ArrayList<>();
        final List<ItemStack> inputs = new ArrayList<>();
        final List<ItemStack> outputs = new ArrayList<>();
        final List<ItemStack> remainder = new ArrayList<>();
        final List<ItemStack> craftingNeeded = new ArrayList<>();
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
                        if (item.factor > 0 && this.math.outputRecipes.containsKey(item.recipeId)) {
                            long amount = 0;

                            for (ItemStack stack : items) {
                                if (stack != null
                                        && NEIClientUtils.areStacksSameTypeCraftingWithNBT(stack, item.itemStack)) {
                                    amount += StackInfo.getAmount(stack);
                                }
                            }

                            if (amount >= item.amount) {
                                final long itemAmount = item.factor * this.math.outputRecipes.get(item.recipeId);
                                if (itemAmount > 0) {
                                    amount += itemAmount - amount % itemAmount;
                                }

                                this.math.outputRecipes.put(
                                        item.recipeId,
                                        Math.max(this.math.outputRecipes.get(item.recipeId), amount / item.factor));
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
                        : math.requiredAmount.getOrDefault(item, item.amount);

                if (amount > 0) {
                    inputs.add(item.getItemStack(amount));
                }
            }

            for (BookmarkItem item : math.recipeResults) {
                final long amount = item.amount - math.requiredAmount.getOrDefault(item, 0L);

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

                    if ((item.amount - invAmount) > 0) {
                        inputs.add(item.getItemStack(item.amount - invAmount));
                    }

                    if (Math.min(item.amount, invAmount) > 0) {
                        available.add(item.getItemStack(Math.min(item.amount, invAmount)));
                    }

                } else {
                    inputs.add(item.getItemStack());
                }
            }

        }
        if (this.lastShiftKey) {
            for (Map.Entry<BookmarkItem, Long> item : this.math.requiredAmount.entrySet()) {
                if (item.getKey().type == BookmarkItem.BookmarkItemType.RESULT && item.getValue() != 0)
                    craftingNeeded.add(item.getKey().getItemStack(item.getValue()));
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

        this.craftingNeeded = new ItemsTooltipLineHandler(
                NEIClientUtils.translate("bookmark.crafting_chain.needed"),
                craftingNeeded,
                true,
                Integer.MAX_VALUE);

        if (this.lastShiftKey) {
            this.inputs.setLabelColor(EnumChatFormatting.RED);
            this.available.setLabelColor(EnumChatFormatting.GREEN);
            this.craftingNeeded.setLabelColor(EnumChatFormatting.BLUE);
        }

        this.size.height = this.size.width = 0;

        if (!this.inputs.isEmpty() || !this.outputs.isEmpty()
                || !this.remainder.isEmpty()
                || !this.available.isEmpty()) {

            if (!this.math.outputRecipes.isEmpty()) {
                this.size.height = 2 + GuiDraw.fontRenderer.FONT_HEIGHT;
            }
            this.size.width = Math.max(
                    this.inputs.getSize().width,
                    Math.max(
                            this.outputs.getSize().width,
                            Math.max(
                                    this.remainder.getSize().width,
                                    Math.max(this.available.getSize().width, this.craftingNeeded.getSize().width))));

            this.size.height += this.inputs.getSize().height + this.outputs.getSize().height
                    + this.remainder.getSize().height
                    + this.available.getSize().height
                    + this.craftingNeeded.getSize().height;
        }

    }

    @Override
    public Dimension getSize() {
        boolean update = this.outputs == null;
        update = this.lastShiftKey != (this.lastShiftKey = NEIClientUtils.shiftKey()) || update;
        update = this.lastControlKey != (this.lastControlKey = NEIClientUtils.controlKey()) || update;

        if (update) {
            onUpdate();
        }
        return this.size;
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

            if (!this.craftingNeeded.isEmpty()) {
                this.craftingNeeded.draw(x, y);
                y += this.craftingNeeded.getSize().height;
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

            if (!this.craftingNeeded.isEmpty()) {
                this.craftingNeeded.draw(x, y);
                y += this.craftingNeeded.getSize().height;
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
