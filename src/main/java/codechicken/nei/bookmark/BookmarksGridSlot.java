package codechicken.nei.bookmark;

import java.awt.Color;

import net.minecraft.item.ItemStack;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.ItemsGrid.MouseContext;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.bookmark.BookmarkGrid.BookmarkMouseContext;
import codechicken.nei.bookmark.RecipeChainDetails.CalculatedType;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.ReadableNumberConverter;

public class BookmarksGridSlot extends ItemsGridSlot {

    protected enum ShownItemType {
        REAL,
        SHIFT,
        CALCULATED
    }

    protected static final float LINE_WIDTH = 0.75f;

    protected static final Color INGREDIENTS_COLOR = new Color(0x6645DA75, true);

    protected static final Color RESULTS_COLOR = new Color(0x9966CCFF, true);
    protected static final Color RESULTS_BG_COLOR = new Color(0x3366CCFF, true);
    protected static final Color RESULTS_BG_FLUID_COLOR = new Color(0x5566CCFF, true);

    protected static final Color REMAINDER_COLOR = new Color(0x55A033A0, true);
    protected static final Color REMAINDER_BG_COLOR = new Color(0x33A033A0, true);
    protected static final Color REMAINDER_BG_FLUID_COLOR = new Color(0x44A033A0, true);

    protected final ItemStack emptyItemStack;
    protected final long realAmount;
    protected final long shiftAmount;
    protected final long calculatedAmount;

    protected final long realMultiplier;
    protected final long calculatedMultiplier;

    protected final BookmarkGroup group;
    protected final boolean isFluidDisplay;
    protected final CalculatedType calculatedType;
    protected final CalculatedType realType;
    protected final BookmarkItem bookmarkItem;

    protected final boolean isOutputRecipe;
    protected final boolean isFirstOutput;
    protected final int rowIndex;

    public BookmarksGridSlot(int slotIndex, int itemIndex, long realAmount, long shiftAmount, long calculatedAmount,
            BookmarkItem bookmarkItem, CalculatedType calculatedType, BookmarkGroup group) {
        super(
                slotIndex,
                itemIndex,
                calculatedType != null ? bookmarkItem.getItemStack(calculatedAmount)
                        : bookmarkItem.getItemStack(realAmount));

        this.emptyItemStack = bookmarkItem.getItemStack(0);
        this.realAmount = realAmount;
        this.shiftAmount = shiftAmount;
        this.calculatedAmount = calculatedAmount;

        this.group = group;
        this.calculatedType = calculatedType;
        this.bookmarkItem = bookmarkItem;

        this.isOutputRecipe = group.crafting != null && group.crafting.outputRecipes.contains(bookmarkItem.recipeId);
        this.isFluidDisplay = StackInfo.itemStackToNBT(bookmarkItem.itemStack).hasKey("gtFluidName");

        this.realMultiplier = bookmarkItem.getMultiplier(realAmount);
        this.calculatedMultiplier = bookmarkItem.getMultiplier(calculatedAmount);

        if (this.group.crafting != null && this.group.crafting.calculatedItems.containsKey(this.itemIndex)) {
            this.realType = this.group.crafting.calculatedItems.get(this.itemIndex).calculatedType
                    == CalculatedType.INGREDIENT ? CalculatedType.INGREDIENT : CalculatedType.RESULT;
        } else {
            this.realType = this.bookmarkItem.isIngredient
                    || this.group.crafting != null && this.bookmarkItem.recipeId == null ? CalculatedType.INGREDIENT
                            : CalculatedType.RESULT;
        }

        final BookmarkGrid grid = ItemPanels.bookmarkPanel.getGrid();
        final BookmarkItem prevItem = grid.getCalculatedItem(
                grid.gridGenerator.slotToItem.getOrDefault(grid.gridGenerator.itemToSlot.get(this.itemIndex) - 1, -1));
        this.isFirstOutput = this.itemIndex >= 0
                && (prevItem == null || prevItem.isIngredient || !this.bookmarkItem.equalsRecipe(prevItem));
        this.rowIndex = this.slotIndex / grid.getColumns();
    }

    public long getMultiplier() {
        return this.calculatedType != null ? this.calculatedMultiplier : this.realMultiplier;
    }

    public BookmarkItem getBookmarkItem() {
        return this.bookmarkItem;
    }

    public RecipeId getRecipeId() {
        return this.bookmarkItem.recipeId;
    }

    public boolean isIngredient() {
        return this.bookmarkItem.isIngredient;
    }

    public BookmarkGroup getGroup() {
        return this.group;
    }

    public int getGroupId() {
        return this.bookmarkItem.groupId;
    }

    protected ShownItemType getShownItemType(BookmarkMouseContext mouseContext) {

        if (mouseContext != null) {
            if (this.showShiftItem(mouseContext)) {
                return ShownItemType.SHIFT;
            } else if (this.showRealItem(mouseContext)) {
                return ShownItemType.REAL;
            }
        }

        if (this.calculatedType != null) {
            return ShownItemType.CALCULATED;
        }

        return null;
    }

    protected boolean showShiftItem(BookmarkMouseContext mouseContext) {

        if (!mouseContext.shiftKey || this.bookmarkItem.groupId != mouseContext.groupId) {
            return false;
        }

        if (this.group.crafting != null) {
            return true;
        }

        return this.bookmarkItem.equalsRecipe(mouseContext.recipeId, mouseContext.groupId);
    }

    protected boolean showRealItem(BookmarkMouseContext mouseContext) {

        if (!mouseContext.controlKey || this.bookmarkItem.groupId != mouseContext.groupId) {
            return false;
        }

        if (this.slotIndex == mouseContext.slotIndex || this.group.collapsed) {
            return true;
        }

        if (mouseContext.recipeId == null && this.group.viewMode == BookmarkViewMode.TODO_LIST
                && mouseContext.rowIndex == this.rowIndex) {
            return true;
        }

        if (this.bookmarkItem.equalsRecipe(mouseContext.recipeId, mouseContext.groupId)) {
            return true;
        }

        return this.group.crafting != null && mouseContext.recipeId != null
                && mouseContext.recipeId.equals(this.group.crafting.itemToRecipe.get(this.itemIndex));
    }

    @Override
    public void drawItem(Rectangle4i rect) {
        super.drawItem(this.emptyItemStack, rect);
    }

    @Override
    public <M extends MouseContext> void beforeDraw(Rectangle4i rect, M mouseContext) {
        final ShownItemType shownItemType = getShownItemType((BookmarkMouseContext) mouseContext);
        boolean showMarker = NEIClientConfig.showRecipeMarkerMode() == 2;
        CalculatedType calculatedType = null;

        if (shownItemType == ShownItemType.REAL) {
            calculatedType = this.realType;
        } else if (shownItemType == ShownItemType.SHIFT) {

            if (this.calculatedType != null) {
                calculatedType = this.shiftAmount > 0 ? this.calculatedType : null;
                showMarker = false;
            } else if (group.crafting == null) {
                calculatedType = this.realType;
            }

        }

        if (!this.bookmarkItem.isIngredient && this.bookmarkItem.recipeId != null
                && calculatedType == null
                && showMarker
                && (mouseContext == null || mouseContext.slotIndex != this.slotIndex)) {
            drawMerkerBackground(rect);
        }

        if (calculatedType == null || !drawBackground(rect, calculatedType)) {
            super.beforeDraw(rect, mouseContext);
        }
    }

    protected boolean drawBackground(Rectangle4i rect, CalculatedType calculatedType) {

        switch (calculatedType) {
            case INGREDIENT:
                NEIClientUtils.drawRect(rect.x, rect.y, rect.w, rect.h, INGREDIENTS_COLOR);
                break;
            case RESULT:
                NEIClientUtils.drawRect(rect.x, rect.y, rect.w, rect.h, RESULTS_COLOR);
                break;
            case REMAINDER:
                NEIClientUtils.drawRect(rect.x, rect.y, rect.w, rect.h, REMAINDER_COLOR);
                break;
            default:
                return false;
        }

        return true;
    }

    protected void drawMerkerBackground(Rectangle4i rect) {
        Color color = this.isFluidDisplay ? RESULTS_BG_FLUID_COLOR : RESULTS_BG_COLOR;

        if (this.shiftAmount > 0 && this.calculatedType == CalculatedType.REMAINDER) {
            color = this.isFluidDisplay ? REMAINDER_BG_FLUID_COLOR : REMAINDER_BG_COLOR;
        }

        NEIClientUtils.drawRect(rect.x, rect.y, rect.w, rect.h, color);
    }

    @Override
    public <M extends MouseContext> void afterDraw(Rectangle4i rect, M mouseContext) {
        final ShownItemType shownItemType = getShownItemType((BookmarkMouseContext) mouseContext);

        drawStackSize(rect, shownItemType);

        if (this.bookmarkItem.isIngredient || this.bookmarkItem.recipeId == null) {
            return;
        }

        if (this.isFirstOutput) {
            if (shownItemType == ShownItemType.REAL) {
                drawMarker(
                        rect,
                        "x" + ReadableNumberConverter.INSTANCE.toWideReadableForm(this.realMultiplier),
                        0xAAAAAA);
            } else if (shownItemType == ShownItemType.SHIFT) {
                drawMarker(rect, "x" + ReadableNumberConverter.INSTANCE.toWideReadableForm(getMultiplier()), 0xFFFFFF);
            } else if (this.isOutputRecipe) {
                drawMarker(
                        rect,
                        "x" + ReadableNumberConverter.INSTANCE.toWideReadableForm(this.realMultiplier),
                        0xAAAAAA);
            } else if (NEIClientConfig.showRecipeMarkerMode() == 1) {
                drawMarker(rect, "R", 0xA0A0A0);
            }
        } else if (NEIClientConfig.showRecipeMarkerMode() == 1) {
            drawMarker(rect, "R", 0xA0A0A0);
        }

    }

    protected void drawMarker(Rectangle4i rect, String text, int color) {
        final float panelFactor = rect.w / DEFAULT_SLOT_SIZE;
        NEIClientUtils.drawMarker(rect.x + panelFactor, rect.y + panelFactor, panelFactor, text, color, false, false);
    }

    protected void drawStackSize(Rectangle4i rect, ShownItemType shownItemType) {
        long amount = this.calculatedAmount;

        if (shownItemType == null || shownItemType == ShownItemType.REAL || this.calculatedType == null) {
            amount = this.realAmount;
        } else if (shownItemType == ShownItemType.SHIFT) {
            amount = this.shiftAmount;
        }

        if (amount > 0 || shownItemType == ShownItemType.REAL && this.bookmarkItem.factor > 0) {
            final float panelFactor = rect.w / DEFAULT_SLOT_SIZE;
            final long stackSize = this.bookmarkItem.getStackSize(amount);
            String amountString = "";

            if (stackSize < 10_000) {
                amountString = String.valueOf(stackSize);
            } else {
                amountString = ReadableNumberConverter.INSTANCE.toWideReadableForm(stackSize);
            }

            if (this.isFluidDisplay) {
                amountString += "L";
            }

            NEIClientUtils.drawMarker(
                    rect.x + panelFactor,
                    rect.y + panelFactor,
                    panelFactor,
                    amountString,
                    0xFFFFFF,
                    !this.isFluidDisplay,
                    true);
        }
    }

}
