package codechicken.nei.bookmark;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.ItemsGrid;
import codechicken.nei.bookmark.RecipeChainDetails.BookmarkChainItem;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.Recipe.RecipeId;

public class SortableItem {

    public final BookmarkGrid grid;
    public final BookmarkItem bookmarkItem;
    public final boolean moveRecipe;
    public final boolean crossGroup;
    public final boolean isCollapsedRecipe;
    public List<Integer> items = new ArrayList<>();

    public int shiftX = -8;
    public int shiftY = -8;

    public SortableItem(BookmarkGrid grid, BookmarkItem bookmarkItem) {
        final BookmarkGroup group = grid.getGroup(bookmarkItem.groupId);

        this.grid = grid;
        this.bookmarkItem = bookmarkItem.copy();
        this.moveRecipe = bookmarkItem.recipeId != null && !bookmarkItem.isIngredient
                && (group.viewMode == BookmarkViewMode.TODO_LIST || group.crafting != null);
        this.crossGroup = !group.collapsed && group.viewMode == BookmarkViewMode.TODO_LIST
                && !this.bookmarkItem.isIngredient;
        this.isCollapsedRecipe = group.crafting != null && group.crafting.itemToRecipe.values().stream()
                .anyMatch(recipeId -> this.bookmarkItem.equalsRecipe(recipeId, this.bookmarkItem.groupId));

        this.update();
    }

    public void postDraw(int mousex, int mousey) {
        Point startRect = null;

        GuiContainerManager.drawItems.zLevel += 100;

        for (Integer itemIndex : this.grid.getSortedItems()) {
            if (this.items.contains(itemIndex)) {
                final BookmarkItem item = this.grid.getCalculatedItem(itemIndex);
                final int slotIndex = this.grid.gridGenerator.itemToSlot.get(itemIndex);
                final Rectangle4i rect = this.grid
                        .getSlotRect(slotIndex % (this.grid.getRows() * this.grid.getColumns()));

                if (startRect == null) {
                    startRect = new Point(rect.x - this.shiftX, rect.y - this.shiftY);
                }

                GuiContainerManager.drawItem(
                        mousex + rect.x - startRect.x + 1,
                        mousey + rect.y - startRect.y + 1,
                        item.getItemStack(),
                        true,
                        "");

            }
        }

        GuiContainerManager.drawItems.zLevel -= 100;
    }

    public void update() {
        final BookmarkGroup group = this.grid.getGroup(this.bookmarkItem.groupId);

        this.items.clear();

        if (group.collapsed) {
            return;
        }

        for (Map.Entry<Integer, BookmarkChainItem> entry : this.grid.gridGenerator.calculatedItems.entrySet()) {
            if (containsItem(entry.getValue().getItem())) {
                this.items.add(entry.getKey());
            }
        }

        if (this.isCollapsedRecipe && group.crafting != null) {
            for (Map.Entry<Integer, RecipeId> entry : group.crafting.itemToRecipe.entrySet()) {
                if (this.bookmarkItem.equalsRecipe(entry.getValue(), this.bookmarkItem.groupId)) {
                    this.items.add(entry.getKey());
                }
            }
        }

    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    public int getItemIndex() {
        return this.items.isEmpty() ? -1 : this.items.get(0);
    }

    private boolean canInsert(BookmarkGroup group) {
        return group.viewMode == BookmarkViewMode.TODO_LIST && !group.collapsed;
    }

    public void mouseDragged(int mousex, int mousey) {
        final BookmarkViewMode sortViewMode = this.grid.getViewMode(this.bookmarkItem.groupId);
        BookmarksGridSlot mouseOverSlot = this.grid.getSlotMouseOver(mousex, mousey);

        if (this.crossGroup) {
            final Rectangle4i topLeftSlot = this.grid.getSlotRect(0);
            mouseOverSlot = this.grid.getSlotMouseOver(topLeftSlot.x, mousey);

            if (mouseOverSlot != null) {
                float ySlot = (float) (mousey - topLeftSlot.y) / ItemsGrid.SLOT_SIZE;
                int overRowIndex = (int) ySlot;
                int beforeGroupId = this.grid.getRowGroupId(overRowIndex - 1);
                int afterGroupId = this.grid.getRowGroupId(overRowIndex + 1);
                int overGroupId = mouseOverSlot.getGroupId();
                ySlot -= overRowIndex;

                if (overGroupId == this.bookmarkItem.groupId && !this.items.contains(mouseOverSlot.itemIndex)) {
                    this.grid.moveItem(this, mouseOverSlot.itemIndex);
                } else if (ySlot <= 0.25) {
                    final BookmarkGroup group = this.grid.getGroup(beforeGroupId);

                    if (beforeGroupId != overGroupId && canInsert(group)
                            && !existsRecipe(this.bookmarkItem.recipeId, beforeGroupId)) {
                        this.grid.moveItem(this, overRowIndex, beforeGroupId, false);
                    }

                } else if (ySlot > 0.25 && ySlot <= 0.5) {

                    if (beforeGroupId == overGroupId) {
                        return;
                    }

                    if (overGroupId != BookmarkGrid.DEFAULT_GROUP_ID
                            && beforeGroupId != BookmarkGrid.DEFAULT_GROUP_ID) {
                        beforeGroupId = BookmarkGrid.DEFAULT_GROUP_ID;
                    }

                    if (overGroupId == BookmarkGrid.DEFAULT_GROUP_ID && beforeGroupId != BookmarkGrid.DEFAULT_GROUP_ID
                            && afterGroupId != BookmarkGrid.DEFAULT_GROUP_ID) {
                        return;
                    }

                    if (canInsert(this.grid.getGroup(beforeGroupId))
                            && !existsRecipe(this.bookmarkItem.recipeId, beforeGroupId)) {
                        this.grid.moveItem(this, overRowIndex, beforeGroupId, false);
                    }

                } else if (ySlot > 0.5 && ySlot < 0.75) {

                    if (afterGroupId == overGroupId) {
                        return;
                    }

                    if (overGroupId != BookmarkGrid.DEFAULT_GROUP_ID && afterGroupId != BookmarkGrid.DEFAULT_GROUP_ID) {
                        afterGroupId = BookmarkGrid.DEFAULT_GROUP_ID;
                    }

                    if (overGroupId == BookmarkGrid.DEFAULT_GROUP_ID && beforeGroupId != BookmarkGrid.DEFAULT_GROUP_ID
                            && afterGroupId != BookmarkGrid.DEFAULT_GROUP_ID) {
                        return;
                    }

                    if (canInsert(this.grid.getGroup(afterGroupId))
                            && !existsRecipe(this.bookmarkItem.recipeId, afterGroupId)) {
                        this.grid.moveItem(this, overRowIndex, afterGroupId, true);
                    }

                } else if (ySlot >= 0.75) {
                    final BookmarkGroup group = this.grid.getGroup(afterGroupId);

                    if (afterGroupId != overGroupId && canInsert(group)
                            && !existsRecipe(this.bookmarkItem.recipeId, afterGroupId)) {
                        this.grid.moveItem(this, overRowIndex, afterGroupId, true);
                    }

                }

            } else {
                final int overRowIndex = this.grid.getHoveredRowIndex(false);
                final int overGroupId = this.grid.getRowGroupId(overRowIndex);

                if (overRowIndex != -1 && overGroupId == this.bookmarkItem.groupId) {
                    final int absoluteSlotIndex = ((this.grid.getPage() - 1) * this.grid.getRows() + overRowIndex)
                            * this.grid.getColumns();

                    if (absoluteSlotIndex < this.grid.gridGenerator.itemToSlot.getOrDefault(getItemIndex(), -1)) {
                        this.grid.moveItem(this, overRowIndex, overGroupId, true);
                    } else {
                        this.grid.moveItem(this, overRowIndex, overGroupId, false);
                    }

                }

            }

        } else if (mouseOverSlot != null && !this.items.contains(mouseOverSlot.itemIndex)
                && mouseOverSlot.getGroupId() == this.bookmarkItem.groupId
                && (sortViewMode == BookmarkViewMode.DEFAULT || sortViewMode == BookmarkViewMode.TODO_LIST
                        && mouseOverSlot.getBookmarkItem().equalsRecipe(this.bookmarkItem))) {
                            this.grid.moveItem(this, mouseOverSlot.itemIndex);
                        }

    }

    private boolean existsRecipe(RecipeId recipeId, int groupId) {
        if (recipeId == null) return false;

        for (int itemIndex = 0; itemIndex < this.grid.bookmarkItems.size(); itemIndex++) {
            if (!this.items.contains(itemIndex)
                    && this.grid.bookmarkItems.get(itemIndex).equalsRecipe(recipeId, groupId)) {
                return true;
            }
        }

        return false;
    }

    public List<BookmarkItem> getBookmarkItems() {
        final BookmarkGroup group = this.grid.getGroup(this.bookmarkItem.groupId);
        final List<BookmarkItem> items = new ArrayList<>();
        final List<RecipeId> recipeRelations = new ArrayList<>();

        if (this.isCollapsedRecipe && group.crafting != null) {
            recipeRelations.addAll(
                    group.crafting.recipeRelations.getOrDefault(this.bookmarkItem.recipeId, Collections.emptySet()));
        }

        for (BookmarkItem item : this.grid.bookmarkItems) {
            if (containsItem(item)
                    || item.groupId == this.bookmarkItem.groupId && recipeRelations.contains(item.recipeId)) {
                items.add(item);
            }
        }

        return items;
    }

    public boolean containsItem(BookmarkItem slotItem) {
        return this.moveRecipe ? this.bookmarkItem.equalsRecipe(slotItem) : this.bookmarkItem.equals(slotItem);
    }

}
