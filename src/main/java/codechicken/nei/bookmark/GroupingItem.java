package codechicken.nei.bookmark;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.recipe.Recipe.RecipeId;

public class GroupingItem {

    public static class PointEntry {

        public int cursorRowIndex;
        public int topRowIndex;
        public int bottomRowIndex;

        public PointEntry(int clickSlotIndex, int topSlotIndex, int bottomSlotIndex) {
            this.cursorRowIndex = clickSlotIndex;
            this.topRowIndex = topSlotIndex;
            this.bottomRowIndex = bottomSlotIndex;
        }
    }

    public BookmarkGrid grid;

    public int groupId = -1;
    public PointEntry startPoint;
    public PointEntry endPoint;

    public GroupingItem(BookmarkGrid grid, int groupId, int overRowIndex) {
        this.groupId = groupId;
        this.grid = grid;
        setStartPoint(overRowIndex);
    }

    public void setStartPoint(int overRowIndex) {

        if (this.grid.getRowItemIndex(overRowIndex, true) != Integer.MIN_VALUE) {
            this.startPoint = new PointEntry(
                    overRowIndex + getRowShift(),
                    getTopRowIndex(overRowIndex),
                    getBottomRowIndex(overRowIndex));
        } else {
            this.startPoint = new PointEntry(overRowIndex + getRowShift(), -1, -1);
        }

    }

    public void setEndPoint(int overRowIndex) {
        if (this.grid.getRowItemIndex(overRowIndex, true) != Integer.MIN_VALUE) {
            this.endPoint = new PointEntry(
                    overRowIndex + getRowShift(),
                    getTopRowIndex(overRowIndex),
                    getBottomRowIndex(overRowIndex));
        }
    }

    public int getStartSlotIndex() {
        return this.startPoint.cursorRowIndex - getRowShift();
    }

    public int getEndSlotIndex() {
        return this.endPoint != null ? this.endPoint.cursorRowIndex - getRowShift() : 0;
    }

    public boolean hasEndRow() {
        return this.endPoint != null;
    }

    private int getRowShift() {
        return (this.grid.getPage() - 1) * this.grid.getRows();
    }

    public Map<Integer, Integer> prepareGroups(Map<Integer, Integer> groups) {
        final int topRowIndex = Math.min(this.startPoint.topRowIndex, this.endPoint.topRowIndex);
        final int bottomRowIndex = Math.max(this.startPoint.bottomRowIndex, this.endPoint.bottomRowIndex);
        groups = new HashMap<>(groups);

        if (this.groupId != BookmarkGrid.DEFAULT_GROUP_ID) {

            if (this.startPoint.cursorRowIndex > this.endPoint.cursorRowIndex) {
                int rowIndex = this.endPoint.topRowIndex - 1;

                while (groups.containsKey(rowIndex) && groups.get(rowIndex) == this.groupId) {
                    groups.put(rowIndex--, BookmarkGrid.DEFAULT_GROUP_ID);
                }

            } else if (this.startPoint.cursorRowIndex < this.endPoint.cursorRowIndex) {
                int rowIndex = this.endPoint.bottomRowIndex + 1;

                while (groups.containsKey(rowIndex) && groups.get(rowIndex) == this.groupId) {
                    groups.put(rowIndex++, BookmarkGrid.DEFAULT_GROUP_ID);
                }
            }

        } else if (groups.containsKey(topRowIndex - 1)
                && groups.get(topRowIndex - 1).equals(groups.get(bottomRowIndex + 1))) {
                    int groupId = groups.get(bottomRowIndex);
                    int rowIndex = bottomRowIndex + 1;

                    while (groups.containsKey(rowIndex) && groups.get(rowIndex) == groupId) {
                        groups.put(rowIndex, -1 * groups.get(rowIndex));
                        rowIndex++;
                    }
                }

        for (int rowIndex = topRowIndex; rowIndex <= bottomRowIndex && groups.containsKey(rowIndex); rowIndex++) {
            groups.put(rowIndex, this.groupId);
        }

        return groups;
    }

    protected int getTopRowIndex(int overRowIndex) {
        final int rowShift = getRowShift();
        int itemIndex = this.grid.getRowItemIndex(overRowIndex, true);

        if (itemIndex == Integer.MIN_VALUE) {
            return -1;
        }

        final BookmarkItem item = this.grid.getCalculatedItem(itemIndex);

        if (item.recipeId != null) {
            final List<Integer> sortedItems = this.grid.getSortedItems();
            final BookmarkGroup group = this.grid.getGroup(item.groupId);
            final boolean isCollapsedGroup = group.collapsed
                    || group.crafting != null && group.viewMode == BookmarkViewMode.DEFAULT;
            final RecipeId recipeId = group.crafting != null
                    ? group.crafting.itemToRecipe.getOrDefault(itemIndex, item.recipeId)
                    : item.recipeId;
            final Set<RecipeId> recipeRelations = group.crafting != null
                    ? group.crafting.recipeRelations.getOrDefault(recipeId, Collections.singleton(recipeId))
                    : Collections.singleton(recipeId);

            for (Integer rItemIndex : sortedItems) {
                final BookmarkItem rItem = this.grid.getCalculatedItem(rItemIndex);

                if (rItem.groupId == item.groupId && (isCollapsedGroup || recipeRelations.contains(rItem.recipeId))) {
                    return this.grid.getAbsoluteSlotIndex(rItemIndex) / this.grid.getColumns();
                }
            }

            return -1;
        }

        return rowShift + overRowIndex;
    }

    protected int getBottomRowIndex(int overRowIndex) {
        final int rowShift = getRowShift();
        final int itemIndex = this.grid.getRowItemIndex(overRowIndex, false);

        if (itemIndex == Integer.MIN_VALUE) {
            return -1;
        }

        final BookmarkItem item = this.grid.getCalculatedItem(itemIndex);

        if (item.recipeId != null) {
            final List<Integer> sortedItems = this.grid.getSortedItems();
            final BookmarkGroup group = this.grid.getGroup(item.groupId);
            final boolean isCollapsedGroup = group.collapsed
                    || group.crafting != null && group.viewMode == BookmarkViewMode.DEFAULT;
            final RecipeId recipeId = group.crafting != null
                    ? group.crafting.itemToRecipe.getOrDefault(itemIndex, item.recipeId)
                    : item.recipeId;
            final Set<RecipeId> recipeRelations = group.crafting != null
                    ? group.crafting.recipeRelations.getOrDefault(recipeId, Collections.singleton(recipeId))
                    : Collections.singleton(recipeId);

            for (int index = sortedItems.size() - 1; index >= 0; index--) {
                final int rItemIndex = sortedItems.get(index);
                final BookmarkItem rItem = this.grid.getCalculatedItem(rItemIndex);

                if (rItem.groupId == item.groupId && (isCollapsedGroup || recipeRelations.contains(rItem.recipeId))) {
                    return this.grid.getAbsoluteSlotIndex(rItemIndex) / this.grid.getColumns();
                }
            }

            return -1;
        }

        return rowShift + overRowIndex;
    }

}
