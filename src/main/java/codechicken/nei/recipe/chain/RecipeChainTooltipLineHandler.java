package codechicken.nei.recipe.chain;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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
import codechicken.nei.recipe.StackInfo;

public class RecipeChainTooltipLineHandler implements ITooltipLineHandler {

    public final int groupId;
    public final boolean crafting;
    protected final RecipeChainMath math;
    protected final List<BookmarkItem> initialItems;

    protected ItemsTooltipLineHandler available;
    protected ItemsTooltipLineHandler inputs;
    protected ItemsTooltipLineHandler outputs;
    protected ItemsTooltipLineHandler remainder;
    protected boolean lastShiftKey = false;

    protected Dimension size = new Dimension();

    public RecipeChainTooltipLineHandler(int groupId, boolean crafting, RecipeChainMath math) {
        this.groupId = groupId;
        this.crafting = crafting;
        this.math = math;
        this.initialItems = new ArrayList<>(this.math.initialItems);
    }

    private void onUpdate() {
        final List<ItemStack> available = new ArrayList<>();
        final List<ItemStack> inputs = new ArrayList<>();
        final List<ItemStack> outputs = new ArrayList<>();
        final List<ItemStack> remainder = new ArrayList<>();
        final ItemStackAmount inventory = new ItemStackAmount();

        if (lastShiftKey) {
            inventory.addAll(Arrays.asList(NEIClientUtils.mc().thePlayer.inventory.mainInventory));
        }

        if (!this.math.outputRecipes.isEmpty()) {
            this.math.initialItems.clear();

            if (lastShiftKey) {
                for (ItemStack stack : inventory.values()) {
                    final long invStackSize = inventory.get(stack);

                    if (invStackSize > 0) {
                        this.math.initialItems.add(BookmarkItem.of(-1, stack.copy()));
                    }
                }
            } else {
                this.math.initialItems.addAll(initialItems);
            }

            this.math.refresh();

            for (BookmarkItem item : math.initialItems) {
                final long amount = math.requiredAmount.getOrDefault(item, 0L);

                if (amount > 0) {
                    if (lastShiftKey) {
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
                    if (math.outputRecipes.containsKey(item.recipeId) && item.groupId != -2) {
                        outputs.add(item.getItemStack(amount));
                    } else if (lastShiftKey) {
                        remainder.add(item.getItemStack(amount));
                    }
                }
            }

            if (lastShiftKey) {
                for (ItemStack stack : math.requiredContainerItem.values()) {
                    if (stack != null) {
                        remainder.add(stack.copy());
                    }
                }
            }

        } else if (lastShiftKey) {

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

        inputs.sort(
                Comparator.comparing((ItemStack stack) -> StackInfo.getFluid(stack) != null)
                        .thenComparing(ItemSorter.instance));
        outputs.sort(
                Comparator.comparing((ItemStack stack) -> StackInfo.getFluid(stack) != null)
                        .thenComparing(ItemSorter.instance));
        remainder.sort(
                Comparator.comparing((ItemStack stack) -> StackInfo.getFluid(stack) != null)
                        .thenComparing(ItemSorter.instance));

        this.inputs = new ItemsTooltipLineHandler(
                lastShiftKey ? NEIClientUtils.translate("bookmark.crafting_chain.missing")
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

        if (lastShiftKey) {
            this.inputs.setLabelColor(EnumChatFormatting.RED);
            this.available.setLabelColor(EnumChatFormatting.GREEN);
        }

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
                            Math.max(this.remainder.getSize().width, this.available.getSize().width)));

            this.size.height += this.inputs.getSize().height + this.outputs.getSize().height
                    + this.remainder.getSize().height
                    + this.available.getSize().height;
        } else {
            this.size.height = this.size.width = 0;
        }

    }

    @Override
    public Dimension getSize() {
        if (this.lastShiftKey != (this.lastShiftKey = NEIClientUtils.shiftKey()) || this.outputs == null) {
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
