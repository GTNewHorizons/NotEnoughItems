package codechicken.nei.bookmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.ItemSorter;
import codechicken.nei.bookmark.RecipeChainDetails.BookmarkChainItem;
import codechicken.nei.bookmark.RecipeChainDetails.CalculatedType;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.StackInfo;

public class BookmarkGridGenerator {

    protected final BookmarkGrid grid;
    public List<BookmarksGridSlot> gridMask = null;

    public final Map<Integer, Integer> rowToGroupId = new HashMap<>();
    public final Map<Integer, RecipeId> itemToRecipe = new HashMap<>();

    public final Map<Integer, Integer> slotToItem = new LinkedHashMap<>();
    public final Map<Integer, Integer> itemToSlot = new LinkedHashMap<>();

    public final Map<Integer, BookmarkChainItem> calculatedItems = new HashMap<>();

    public int pageCount = 0;
    public int maxAbsoluteSlotIndex = 0;

    public BookmarkGridGenerator(BookmarkGrid grid) {
        this.grid = grid;
    }

    public int getRowGroupId(int rowIndex) {
        final int absoluteRowIndex = rowIndex + (this.grid.getPage() - 1) * this.grid.getRows();
        return rowToGroupId.getOrDefault(absoluteRowIndex, BookmarkGrid.DEFAULT_GROUP_ID);
    }

    public void generate() {
        BookmarkItem prevBookmarkItem = null;
        Map<Integer, String> borderGrid = new HashMap<>();
        int gridColumns = this.grid.getColumns();
        int size = this.grid.size();
        int absoluteSlotIndex = -2;
        int itemIndex = 0;

        this.pageCount = 0;
        this.maxAbsoluteSlotIndex = 0;
        this.gridMask = new ArrayList<>();
        this.itemToRecipe.clear();
        this.calculatedItems.clear();
        this.slotToItem.clear();
        this.itemToSlot.clear();
        this.rowToGroupId.clear();

        if (this.grid.isEmpty() || this.grid.getPerPage() == 0) {
            return;
        }

        while (itemIndex < size && absoluteSlotIndex != -1) {
            final BookmarkItem bookmarkItem = this.grid.getBookmarkItem(itemIndex);
            final BookmarkGroup group = this.grid.getGroup(bookmarkItem.groupId);

            if (group.crafting != null && (!group.crafting.calculatedItems.containsKey(itemIndex)
                    || bookmarkItem.recipeId != null && !bookmarkItem.recipeId
                            .equals(group.crafting.itemToRecipe.getOrDefault(itemIndex, bookmarkItem.recipeId)))) {
                itemIndex++;
            } else if (group.collapsed) {
                final List<Integer> results = getGroupResults(bookmarkItem.groupId, itemIndex, size);
                final String groupKey = "group:" + bookmarkItem.groupId;

                for (int index = 0; index < results.size(); index++) {
                    final int collapsedItemIndex = results.get(index);
                    final BookmarkItem item = this.grid.getBookmarkItem(collapsedItemIndex);
                    final int nextSlotIndex = nextSlotIndex(
                            absoluteSlotIndex + 1,
                            prevBookmarkItem,
                            item,
                            BookmarkViewMode.DEFAULT);

                    if (nextSlotIndex == -1 || index > 0 && (nextSlotIndex % gridColumns) == 0) {
                        break;
                    }

                    prevBookmarkItem = item;
                    absoluteSlotIndex = nextSlotIndex;
                    this.slotToItem.put(absoluteSlotIndex, collapsedItemIndex);
                    this.itemToSlot.put(collapsedItemIndex, absoluteSlotIndex);
                    this.calculatedItems.put(collapsedItemIndex, getItem(group, collapsedItemIndex));
                    borderGrid.put(absoluteSlotIndex, groupKey);
                }

                while (itemIndex < size && this.grid.getBookmarkItem(itemIndex).groupId == bookmarkItem.groupId) {
                    itemIndex++;
                }

            } else if (group.crafting != null && group.viewMode == BookmarkViewMode.DEFAULT) {
                final List<Integer> results = getGroupResults(bookmarkItem.groupId, itemIndex, size);
                List<Integer> shadows;

                for (int index = 0; index < results.size() && absoluteSlotIndex != -1; index++) {
                    final int collapsedItemIndex = results.get(index);
                    final BookmarkItem item = this.grid.getBookmarkItem(collapsedItemIndex);

                    if ((absoluteSlotIndex = nextSlotIndex(
                            absoluteSlotIndex + 1,
                            prevBookmarkItem,
                            item,
                            BookmarkViewMode.DEFAULT)) != -1) {
                        prevBookmarkItem = item;
                        this.slotToItem.put(absoluteSlotIndex, collapsedItemIndex);
                        this.itemToSlot.put(collapsedItemIndex, absoluteSlotIndex);
                        this.calculatedItems.put(collapsedItemIndex, getItem(group, collapsedItemIndex));

                        if (!(shadows = getShadows(group, item.recipeId)).isEmpty()) {
                            borderGrid.put(absoluteSlotIndex, "recipe:" + shadows.get(0));
                        }
                    }
                }

                while (itemIndex < size && this.grid.getBookmarkItem(itemIndex).groupId == bookmarkItem.groupId) {
                    itemIndex++;
                }

            } else if (group.crafting != null && group.crafting.itemToRecipe.values().contains(bookmarkItem.recipeId)) {
                absoluteSlotIndex = generateCollapsedRecipe(prevBookmarkItem, itemIndex, absoluteSlotIndex, borderGrid);
                prevBookmarkItem = bookmarkItem;

                while (itemIndex < size && bookmarkItem.equalsRecipe(this.grid.getBookmarkItem(itemIndex))) {
                    itemIndex++;
                }

            } else if ((absoluteSlotIndex = nextSlotIndex(
                    absoluteSlotIndex + 1,
                    prevBookmarkItem,
                    bookmarkItem,
                    group.viewMode)) != -1) {
                        prevBookmarkItem = bookmarkItem;
                        this.slotToItem.put(absoluteSlotIndex, itemIndex);
                        this.itemToSlot.put(itemIndex, absoluteSlotIndex);
                        this.calculatedItems.put(itemIndex, getItem(group, itemIndex));
                        itemIndex++;
                    }
        }

        this.maxAbsoluteSlotIndex = this.slotToItem.keySet().stream().mapToInt(index -> index).max().orElse(-1) + 1;
        generateGroups();
        splitGridMask();
        calculateGroupBorders(borderGrid);
    }

    private List<Integer> getGroupResults(int groupId, int itemIndex, int size) {
        final BookmarkGroup group = this.grid.getGroup(groupId);
        final List<Integer> results = new ArrayList<>();

        while (itemIndex < size) {
            final BookmarkItem bookmarkItem = this.grid.getBookmarkItem(itemIndex);

            if (bookmarkItem == null || bookmarkItem.groupId != groupId) {
                break;
            }

            if (!bookmarkItem.isIngredient
                    && (group.crafting == null || group.crafting.calculatedItems.containsKey(itemIndex))) {
                results.add(itemIndex);
            }

            itemIndex++;
        }

        return results;
    }

    private int generateCollapsedRecipe(BookmarkItem prevBookmarkItem, int itemIndex, int absoluteSlotIndex,
            Map<Integer, String> borderGrid) {
        final BookmarkItem bookmarkItem = this.grid.getBookmarkItem(itemIndex);
        final BookmarkGroup group = this.grid.getGroup(bookmarkItem.groupId);
        final List<Integer> shadows = getShadows(group, bookmarkItem.recipeId);
        final String borderId = "recipe:" + shadows.get(0);

        shadows.sort(generateSortingComparator(group.crafting.calculatedItems));

        for (int shadowIndex : shadows) {
            final boolean isIngredient = shadows.get(0) != shadowIndex
                    || group.crafting.calculatedItems.get(shadowIndex).calculatedType == CalculatedType.INGREDIENT;
            final BookmarkItem shadowBookmarkItem = BookmarkItem.of(
                    bookmarkItem.groupId,
                    bookmarkItem.itemStack,
                    1,
                    bookmarkItem.recipeId,
                    isIngredient,
                    Collections.emptyMap());

            if ((absoluteSlotIndex = nextSlotIndex(
                    absoluteSlotIndex + 1,
                    prevBookmarkItem,
                    shadowBookmarkItem,
                    group.viewMode)) != -1) {
                this.slotToItem.put(absoluteSlotIndex, shadowIndex);
                this.itemToSlot.put(shadowIndex, absoluteSlotIndex);
                this.calculatedItems.put(shadowIndex, getItem(group, shadowIndex));
                this.itemToRecipe.put(shadowIndex, bookmarkItem.recipeId);
                borderGrid.put(absoluteSlotIndex, borderId);
                prevBookmarkItem = shadowBookmarkItem;
            }

        }

        return absoluteSlotIndex;
    }

    private List<Integer> getShadows(BookmarkGroup group, RecipeId recipeId) {
        return group.crafting.itemToRecipe.entrySet().stream().filter(entry -> recipeId.equals(entry.getValue()))
                .map(entry -> entry.getKey()).collect(Collectors.toList());
    }

    private int nextSlotIndex(int index, BookmarkItem previousMeta, BookmarkItem meta, BookmarkViewMode viewMode) {
        int gridColumns = this.grid.getColumns();
        int maxIndex = this.grid.getRows() * gridColumns;
        int j = 0;

        index = Math.max(0, index);

        while (j++ < maxIndex) {
            boolean isFirstColumn = (index % gridColumns) == 0;

            if (this.grid.isInvalidSlot(index % maxIndex)) {
                index++;
            } else if (!isFirstColumn && (previousMeta == null || previousMeta.groupId != meta.groupId)) {
                // new group must start on a new line
                index++;
            } else if (viewMode == BookmarkViewMode.DEFAULT) {
                return index;
            } else if (isFirstColumn && (meta.recipeId == null || previousMeta == null
                    || previousMeta.groupId != meta.groupId
                    || !meta.recipeId.equals(previousMeta.recipeId)
                    || index + 1 < maxIndex && this.grid.isInvalidSlot((index + 1) % maxIndex))) {
                        // In first column must be an item without recipe, a recipe result, or an ingredient
                        // if the second column is occupied
                        return index;
                    } else
                if (!isFirstColumn && meta.recipeId != null && meta.recipeId.equals(previousMeta.recipeId)) {
                    return index;
                } else {
                    index++;
                }

        }

        return -1;
    }

    private void generateGroups() {
        int gridColumns = this.grid.getColumns();
        int previousGroupId = BookmarkGrid.DEFAULT_GROUP_ID;

        for (int absoluteSlotIndex = 0; absoluteSlotIndex < this.maxAbsoluteSlotIndex; absoluteSlotIndex++) {
            int absoluteRowIndex = absoluteSlotIndex / gridColumns;
            if (this.rowToGroupId.getOrDefault(absoluteRowIndex, BookmarkGrid.DEFAULT_GROUP_ID)
                    == BookmarkGrid.DEFAULT_GROUP_ID) {
                int groupId = BookmarkGrid.DEFAULT_GROUP_ID;

                if (this.slotToItem.containsKey(absoluteSlotIndex)) {
                    groupId = this.calculatedItems.get(this.slotToItem.get(absoluteSlotIndex)).getItem().groupId;
                }

                this.rowToGroupId.put(absoluteRowIndex, groupId);

                if (groupId != BookmarkGrid.DEFAULT_GROUP_ID && previousGroupId == (previousGroupId = groupId)) {
                    while ((--absoluteRowIndex) >= 0
                            && this.rowToGroupId.get(absoluteRowIndex) == BookmarkGrid.DEFAULT_GROUP_ID) {
                        this.rowToGroupId.put(absoluteRowIndex, groupId);
                    }
                }

            }
        }

    }

    private Comparator<Integer> generateSortingComparator(Map<Integer, BookmarkChainItem> calculatedItems) {
        return Comparator.comparingInt((Integer index) -> calculatedItems.get(index).calculatedType.toInt())
                .thenComparing(index -> StackInfo.getFluid(calculatedItems.get(index).getItem().itemStack) != null)
                .thenComparing(
                        (i1, i2) -> ItemSorter.instance.compare(
                                calculatedItems.get(i1).getItem().itemStack,
                                calculatedItems.get(i2).getItem().itemStack));
    }

    private void splitGridMask() {
        int gridColumns = this.grid.getColumns();
        int rows = this.grid.getRows();
        int perPage = rows * gridColumns;

        this.pageCount = (int) Math.ceil(this.maxAbsoluteSlotIndex / (float) perPage);
        int page = Math.max(0, Math.min(this.grid.getPage(), this.pageCount) - 1);

        for (int absoluteSlotIndex = page * perPage; absoluteSlotIndex
                < Math.min(this.maxAbsoluteSlotIndex, (page + 1) * perPage); absoluteSlotIndex++) {
            if (this.slotToItem.get(absoluteSlotIndex) != null) {
                this.gridMask
                        .add(getBookmarkGridSlot(absoluteSlotIndex % perPage, this.slotToItem.get(absoluteSlotIndex)));
            }
        }
    }

    private BookmarkChainItem getItem(BookmarkGroup group, int itemIndex) {
        if (group.crafting != null) {
            return group.crafting.calculatedItems.get(itemIndex);
        } else {
            BookmarkChainItem item = BookmarkChainItem.of(this.grid.getBookmarkItem(itemIndex));
            item.setRealAmount(item.getShiftAmount());
            return item;
        }
    }

    private BookmarksGridSlot getBookmarkGridSlot(int slotIndex, int itemIndex) {
        final BookmarkChainItem chainItem = this.calculatedItems.get(itemIndex);
        final BookmarkGroup group = this.grid.getGroup(chainItem.getItem().groupId);

        if (group.crafting == null) {
            return new BookmarksGridSlot(
                    slotIndex,
                    itemIndex,
                    chainItem.getRealAmount(),
                    0,
                    0,
                    chainItem.getItem(),
                    null,
                    group);
        } else {
            return new BookmarksGridSlot(
                    slotIndex,
                    itemIndex,
                    chainItem.getRealAmount(),
                    chainItem.getShiftAmount(),
                    chainItem.getCalculatedAmount(),
                    chainItem.getItem(),
                    chainItem.calculatedType,
                    group);
        }
    }

    protected void calculateGroupBorders(Map<Integer, String> borderGrid) {
        int gridColumns = this.grid.getColumns();
        int page = Math.max(1, Math.min(this.grid.getPage(), this.pageCount)) - 1;
        int shiftIndex = page * this.grid.getRows() * gridColumns;

        for (BookmarksGridSlot slot : this.gridMask) {
            if (borderGrid.get(shiftIndex + slot.slotIndex) == null) {
                continue;
            }

            String groupIndex = borderGrid.get(shiftIndex + slot.slotIndex);
            int column = slot.slotIndex % gridColumns;
            int row = slot.slotIndex / gridColumns;
            int prevSlotIndex = shiftIndex + (row - 1) * gridColumns + column;
            int nextSlotIndex = shiftIndex + (row + 1) * gridColumns + column;
            BookmarkGroup group = slot.getGroup();

            if (group.crafting == null) {
                slot.borderColor = BookmarkGrid.GROUP_NONE_COLOR;
            } else if (group.collapsed) {
                slot.borderColor = BookmarkGrid.GROUP_CHAIN_COLOR;
            } else {
                slot.borderColor = BookmarkGrid.RECIPE_COLOR;
            }

            slot.borderLeft = column == 0 || !groupIndex.equals(borderGrid.get(shiftIndex + slot.slotIndex - 1));
            slot.borderRight = column + 1 == gridColumns
                    || !groupIndex.equals(borderGrid.get(shiftIndex + slot.slotIndex + 1));

            slot.borderTop = !groupIndex.equals(borderGrid.get(prevSlotIndex));
            slot.borderBottom = !groupIndex.equals(borderGrid.get(nextSlotIndex));
        }

    }

}
