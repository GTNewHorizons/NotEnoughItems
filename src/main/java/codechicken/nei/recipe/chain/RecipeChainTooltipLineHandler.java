package codechicken.nei.recipe.chain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.ColorUtils;
import codechicken.nei.CompositeTooltipLineHandler;
import codechicken.nei.ItemSorter;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.ItemsTooltipLineHandler;
import codechicken.nei.ItemsTooltipLineHandler.AmountRenderer;
import codechicken.nei.ItemsTooltipLineHandler.StacksAmountRenderer;
import codechicken.nei.ItemsTooltipLineHandler.TotalAmountRenderer;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.drawable.DrawableResource;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.AutoCraftingManager;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.StackInfo;

public class RecipeChainTooltipLineHandler extends CompositeTooltipLineHandler {

    protected static class MissingStacksAmountRenderer extends StacksAmountRenderer {

        protected static final int MISSING_COLOR = 0xFF5555;

        protected final Set<NBTTagCompound> missing;
        private final Map<ItemStack, Boolean> cache = new IdentityHashMap<>();

        public MissingStacksAmountRenderer(Set<NBTTagCompound> missing) {
            this.missing = missing;
        }

        @Override
        protected int getColor(ItemStack stack) {
            return this.cache.computeIfAbsent(stack, key -> this.missing.contains(StackInfo.itemStackToNBT(key, false)))
                    ? MISSING_COLOR
                    : super.getColor(stack);
        }
    }

    protected static class HandlerIconRenderer extends TotalAmountRenderer {

        protected static final int ICON_SIZE = 16;
        protected static final float BADGE_SCALE = 0.6f;
        protected static final float BADGE_OFFSET = 3f;
        // slots are drawn edge to edge, so the badge has to clear the 3d model of the neighbour slot
        protected static final float BADGE_Z = 10f;

        protected final Map<NBTTagCompound, HandlerInfo> handlers;
        private final Map<ItemStack, HandlerInfo> cache = new IdentityHashMap<>();
        private final Map<String, String> titleCache = new HashMap<>();

        public HandlerIconRenderer(Map<NBTTagCompound, HandlerInfo> handlers) {
            this.handlers = handlers;
        }

        @Override
        public void draw(int x, int y, ItemStack stack, long amount) {
            super.draw(x, y, stack, amount);
            drawHandlerBadge(
                    x,
                    y,
                    this.cache.computeIfAbsent(stack, key -> this.handlers.get(StackInfo.itemStackToNBT(key, false))));
        }

        protected void drawHandlerBadge(int x, int y, HandlerInfo handlerInfo) {
            if (handlerInfo == null) return;

            GL11.glPushMatrix();
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glTranslatef(x - BADGE_OFFSET * BADGE_SCALE, y - BADGE_OFFSET * BADGE_SCALE, BADGE_Z);
            GL11.glScalef(BADGE_SCALE, BADGE_SCALE, 1.0f);

            if (handlerInfo.hasImageOrItem()) {
                final DrawableResource image = handlerInfo.getImage();

                if (image != null) {
                    NEIClientUtils.gl2DRenderContext(() -> image.draw(0, 0));
                } else {
                    GuiContainerManager.drawItem(0, 0, handlerInfo.getItemStack(), true, "");
                }

            } else {
                drawBadgeText(handlerInfo.getHandlerName());
            }

            GL11.glPopMatrix();
        }

        protected void drawBadgeText(String handlerName) {
            final String title = this.titleCache.computeIfAbsent(handlerName, GuiRecipeTab::getHandlerTitle);
            final String text = title.isEmpty() ? "??" : title.substring(0, Math.min(2, title.length()));

            NEIClientUtils.gl2DRenderContext(
                    () -> GuiDraw.fontRenderer.drawStringWithShadow(
                            text,
                            (ICON_SIZE - GuiDraw.fontRenderer.getStringWidth(text)) / 2,
                            (ICON_SIZE - GuiDraw.fontRenderer.FONT_HEIGHT) / 2,
                            ColorUtils.buttonLabelNormal.getColor()));
        }
    }

    public final int groupId;
    public final boolean crafting;
    protected final RecipeChainMath math;
    protected final List<BookmarkItem> initialItems;
    protected final Map<RecipeId, Long> outputRecipes;

    protected boolean lastShiftKey = false;
    protected boolean lastControlKey = false;

    public RecipeChainTooltipLineHandler(int groupId, boolean crafting, RecipeChainMath math) {
        this.groupId = groupId;
        this.crafting = crafting;
        this.math = math;
        this.initialItems = new ArrayList<>(this.math.initialItems);
        this.outputRecipes = new HashMap<>(this.math.outputRecipes);
    }

    @Override
    protected boolean needsUpdate() {
        final boolean shiftChanged = this.lastShiftKey != (this.lastShiftKey = NEIClientUtils.shiftKey());
        return this.lastControlKey != (this.lastControlKey = NEIClientUtils.controlKey()) || shiftChanged;
    }

    @Override
    protected void createLines() {
        if (this.outputRecipes.isEmpty() && !this.lastShiftKey) {
            return;
        }

        final List<ItemStack> available = new ArrayList<>();
        final List<ItemStack> inputs = new ArrayList<>();
        final List<ItemStack> outputs = new ArrayList<>();
        final List<ItemStack> remainder = new ArrayList<>();
        final List<ItemStack> craftingNeeded = new ArrayList<>();
        final Map<NBTTagCompound, HandlerInfo> craftingHandlers = new HashMap<>();
        final ItemStackAmount inventory = new ItemStackAmount();
        final GuiContainer currentGui = NEIClientUtils.getGuiContainer();

        if (this.lastShiftKey && currentGui != null && !(currentGui instanceof GuiRecipe<?>)) {
            inventory.putAll(AutoCraftingManager.getInventoryItems(currentGui));
        }

        if (!this.outputRecipes.isEmpty()) {
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
                this.math.initialItems.addAll(this.initialItems);
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
                    } else {
                        remainder.add(item.getItemStack(amount));
                    }
                }
            }

            for (ItemStack stack : math.containerItemsInventory) {
                if (stack != null) {
                    remainder.add(stack.copy());
                }
            }
            for (ItemStack stack : math.containerItemsCrafting) {
                if (stack != null) {
                    remainder.add(stack.copy());
                }
            }

        } else {

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
                    final ItemStack stack = item.getKey().getItemStack(item.getValue());

                    craftingNeeded.add(stack);
                    putHandlerInfo(craftingHandlers, stack, item.getKey().recipeId);
                }
            }
        }

        final Set<NBTTagCompound> missing = new HashSet<>();

        if (this.lastShiftKey) {
            for (ItemStack stack : inputs) {
                missing.add(StackInfo.itemStackToNBT(stack, false));
            }
        }

        // items with a shortage are already in the inputs list, holding the missing amount instead of the required one
        final List<ItemStack> covered = new ArrayList<>();

        for (ItemStack stack : available) {
            if (!missing.contains(StackInfo.itemStackToNBT(stack, false))) {
                covered.add(stack);
            }
        }

        final Comparator<ItemStack> comparator = Comparator
                .comparing((ItemStack stack) -> StackInfo.getFluid(stack) != null)
                .thenComparingInt(stack -> -1 * stack.stackSize).thenComparing(ItemSorter.instance);

        inputs.sort(comparator);
        covered.sort(comparator);
        outputs.sort(comparator);
        remainder.sort(comparator);

        // missing items stay on top of the list
        inputs.addAll(covered);

        if (this.lastShiftKey) {
            outputs.addAll(remainder);
        }

        addLine(
                NEIClientUtils.translate("bookmark.crafting_chain"),
                outputs,
                outputs.size() == 1,
                EnumChatFormatting.AQUA,
                new TotalAmountRenderer());

        addLine(
                NEIClientUtils.translate("bookmark.crafting_chain.needed"),
                craftingNeeded,
                false,
                EnumChatFormatting.BLUE,
                new HandlerIconRenderer(craftingHandlers));

        addLine(
                NEIClientUtils.translate("bookmark.crafting_chain.input"),
                inputs,
                this.lastShiftKey,
                EnumChatFormatting.YELLOW,
                this.lastShiftKey ? new MissingStacksAmountRenderer(missing) : new TotalAmountRenderer());
    }

    protected static void putHandlerInfo(Map<NBTTagCompound, HandlerInfo> handlers, ItemStack stack,
            RecipeId recipeId) {
        if (recipeId == null) {
            return;
        }

        final HandlerInfo handlerInfo = GuiRecipeTab.getHandlerInfo(recipeId.getHandlerName(), null);

        handlers.put(
                StackInfo.itemStackToNBT(stack, false),
                handlerInfo != null ? handlerInfo : GuiRecipeTab.DEFAULT_HANDLER_INFO);
    }

    protected void addLine(String label, List<ItemStack> items, boolean vertical, EnumChatFormatting labelColor,
            AmountRenderer amountRenderer) {
        final ItemsTooltipLineHandler line = vertical ? ItemsTooltipLineHandler.list(label, items, maxLineRows())
                : ItemsTooltipLineHandler.grid(label, items, maxLineRows());

        if (line.isEmpty()) {
            return;
        }

        line.setLabelColor(labelColor);
        line.setAmountRenderer(amountRenderer);

        this.lines.add(line);
    }

}
