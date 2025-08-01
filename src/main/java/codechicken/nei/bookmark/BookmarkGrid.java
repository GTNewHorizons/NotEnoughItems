package codechicken.nei.bookmark;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import net.minecraft.item.ItemStack;

import org.lwjgl.opengl.GL11;

import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.ItemsGrid;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.chain.RecipeChainMath;

public class BookmarkGrid extends ItemsGrid<BookmarksGridSlot, BookmarkGrid.BookmarkMouseContext> {

    public static class BookmarkMouseContext extends MouseContext {

        public final int groupId;
        public final RecipeId recipeId;
        public final boolean controlKey;
        public final boolean shiftKey;

        public BookmarkMouseContext(int slotIndex, int rowIndex, int columnIndex) {
            super(slotIndex, rowIndex, columnIndex);
            this.controlKey = NEIClientUtils.controlKey();
            this.shiftKey = NEIClientUtils.shiftKey();
            this.groupId = -1;
            this.recipeId = null;
        }

        public BookmarkMouseContext(int slotIndex, int rowIndex, int columnIndex, int groupId, RecipeId recipeId) {
            super(slotIndex, rowIndex, columnIndex);
            this.controlKey = NEIClientUtils.controlKey();
            this.shiftKey = NEIClientUtils.shiftKey();
            this.groupId = groupId;
            this.recipeId = recipeId;
        }

        @Override
        public int hashCode() {
            return Objects
                    .hash(this.groupId, this.rowIndex, this.slotIndex, this.recipeId, this.controlKey, this.shiftKey);
        }

        public boolean equals(Object obj) {

            if (super.equals(obj) && obj instanceof BookmarkMouseContext hover) {
                return this.groupId == hover.groupId && this.controlKey == hover.controlKey
                        && this.shiftKey == hover.shiftKey
                        && (this.recipeId == hover.recipeId
                                || this.recipeId != null && this.recipeId.equals(hover.recipeId));
            }

            return false;
        }

    }

    protected static final Color DRAG_COLOR = new Color(0x66555555, true);

    public static final Color GROUP_CHAIN_COLOR = new Color(0x6645DA75, true);
    public static final Color GROUP_NONE_COLOR = new Color(0xff666666, true);
    public static final Color RECIPE_COLOR = new Color(0x99A033A0, true);
    public static final Color PLACEHOLDER_COLOR = new Color(0x66222222, true);

    public static final int DEFAULT_GROUP_ID = 0;
    protected static final int GROUP_PANEL_WIDTH = 7;
    protected static final float SCALE_SPEED = 0.1f;

    protected List<BookmarkItem> bookmarkItems = new ArrayList<>();
    protected final WeakHashMap<BookmarkItem, Float> animation = new WeakHashMap<>();

    protected final Map<Integer, BookmarkGroup> groups = new HashMap<>();
    protected final BookmarkGridGenerator gridGenerator;

    private boolean isDirty = false;

    public BookmarkGrid() {
        this.groups.put(DEFAULT_GROUP_ID, new BookmarkGroup(BookmarkViewMode.DEFAULT));
        this.gridGenerator = new BookmarkGridGenerator(this);
    }

    public BookmarkViewMode getViewMode(int groupId) {
        return this.groups.get(groupId).viewMode;
    }

    public void setViewMode(int groupId, BookmarkViewMode mode) {
        if (this.groups.get(groupId).viewMode != mode) {
            this.groups.get(groupId).viewMode = mode;
            onItemsChanged();
        }
    }

    public void toggleViewMode(int groupId) {
        this.groups.get(groupId).toggleViewMode();
        onItemsChanged();
    }

    public void toggleCollapsedState(int groupId) {
        this.groups.get(groupId).toggleCollapsedState();
        onItemsChanged();
    }

    public void toggleCollapsedRecipe(int groupId, RecipeId recipeId) {
        final BookmarkGroup group = getGroup(groupId);
        final RecipeId collapsedRecipeId = group.crafting.recipeRelations.entrySet().stream()
                .filter(entry -> entry.getValue().contains(recipeId)).map(entry -> entry.getKey()).findAny()
                .orElse(recipeId);

        group.toggleCollapsedRecipe(collapsedRecipeId);

        if (group.crafting.recipeInMiddle.contains(collapsedRecipeId)) {

            if (group.collapsedRecipes.contains(collapsedRecipeId)) {

                for (BookmarkItem item : this.bookmarkItems) {
                    if (item.groupId == groupId && collapsedRecipeId.equals(item.recipeId)
                            && item.amount == item.factor) {
                        item.amount = 0;
                    }
                }

            } else {

                for (BookmarkItem item : this.bookmarkItems) {
                    if (item.groupId == groupId && collapsedRecipeId.equals(item.recipeId) && item.amount == 0) {
                        item.amount = item.factor;
                    }
                }

            }

        }

        onItemsChanged();
    }

    public boolean isCraftingMode(int groupId) {
        return this.groups.get(groupId).crafting != null;
    }

    public void setCraftingMode(int groupId, boolean on) {
        this.groups.get(groupId).setCraftingMode(on);
        onItemsChanged();
    }

    public void toggleCraftingMode(int groupId) {
        this.groups.get(groupId).toggleCraftingMode();
        onItemsChanged();
    }

    public BookmarkGroup getGroup(int groupId) {
        return this.groups.get(groupId);
    }

    public int addGroup(BookmarkGroup group) {
        int groupId = this.bookmarkItems.stream().mapToInt(item -> item.groupId).max().orElse(DEFAULT_GROUP_ID) + 1;
        this.groups.put(groupId, group);
        return groupId;
    }

    @Override
    public int getNumPages() {

        if (this.gridGenerator.gridMask == null) {
            getMask();
        }

        return this.gridGenerator.pageCount;
    }

    @Override
    public int size() {
        return this.bookmarkItems.size();
    }

    @Override
    public boolean isEmpty() {
        return this.bookmarkItems.isEmpty();
    }

    @Override
    public List<BookmarksGridSlot> getMask() {

        if (this.isDirty) {
            this.isDirty = false;
            this.bookmarkItems = new ArrayList<>(new LinkedHashSet<>(this.bookmarkItems));
            sortIngredients();

            this.realItems.clear();
            for (BookmarkItem item : this.bookmarkItems) {
                this.realItems.add(item.itemStack);
            }
        }

        if (this.gridGenerator.gridMask != null) {
            return this.gridGenerator.gridMask;
        }

        this.gridGenerator.generate();
        this.page = Math.max(0, Math.min(this.page, this.gridGenerator.pageCount - 1));

        if (ItemPanels.bookmarkPanel.sortableItem != null) {
            ItemPanels.bookmarkPanel.sortableItem.update();
        }

        return this.gridGenerator.gridMask;
    }

    @Override
    protected BookmarkMouseContext getMouseContext(int mousex, int mousey) {

        if (contains(mousex, mousey) && !LayoutManager.bookmarkPanel.inEditingState()) {
            return generateMouseContext(mousex, mousey);
        }

        return null;
    }

    private BookmarkMouseContext generateMouseContext(int mousex, int mousey) {
        final BookmarksGridSlot hovered = getSlotMouseOver(mousex, mousey);

        if (hovered != null) {
            return new BookmarkMouseContext(
                    hovered.slotIndex,
                    hovered.slotIndex / columns,
                    hovered.slotIndex % columns,
                    hovered.getGroupId(),
                    this.gridGenerator.itemToRecipe.getOrDefault(hovered.itemIndex, hovered.getRecipeId()));
        }

        final int rowIndex = getHoveredRowIndex(false);

        if (rowIndex != -1) {
            final int groupId = getRowGroupId(rowIndex);
            final int rowItemIndex = getRowItemIndex(rowIndex, false);

            if (rowItemIndex != Integer.MIN_VALUE) {
                int columnIndex = getColumnIndex(mousex);
                BookmarkGroup group = getGroup(groupId);
                RecipeId recipeId = null;

                if (!group.collapsed) {
                    recipeId = this.gridGenerator.itemToRecipe
                            .getOrDefault(rowItemIndex, getCalculatedItem(rowItemIndex).recipeId);
                }

                return new BookmarkMouseContext(
                        -1,
                        rowIndex,
                        columnIndex < 0 || columnIndex > this.columns ? -1 : columnIndex,
                        groupId,
                        recipeId);

            }

        }

        return null;
    }

    public int getRowGroupId(int rowIndex) {

        if (this.gridGenerator.gridMask == null) {
            getMask();
        }

        return this.gridGenerator.getRowGroupId(rowIndex);
    }

    public int getRowItemIndex(int rowIndex, boolean firstItemIndex) {

        if (this.gridGenerator.gridMask == null) {
            getMask();
        }

        int absoluteSlotIndex = (this.page * this.rows + rowIndex) * this.columns
                + (firstItemIndex ? 0 : this.columns - 1);

        for (int index = 0; index < this.columns; index++) {
            if (this.gridGenerator.slotToItem.containsKey(absoluteSlotIndex + index * (firstItemIndex ? 1 : -1))) {
                return this.gridGenerator.slotToItem.get(absoluteSlotIndex + index * (firstItemIndex ? 1 : -1));
            }
        }

        return Integer.MIN_VALUE;
    }

    public int getHoveredRowIndex(boolean groupPanel) {
        final Point mouse = GuiDraw.getMousePosition();
        final int leftBorder = marginLeft + paddingLeft;
        final int r = getRowIndex(mouse.y);

        if (this.isEmpty()) {
            return -1;
        }

        if (!(new Rectangle4i(
                leftBorder - (groupPanel ? BookmarkGrid.GROUP_PANEL_WIDTH : 0),
                marginTop,
                columns * SLOT_SIZE,
                (getLastRowIndex() + 1) * SLOT_SIZE)).contains(mouse.x, mouse.y)) {
            return -1;
        }

        if (groupPanel && mouse.x >= leftBorder - GROUP_PANEL_WIDTH && mouse.x < leftBorder) {
            return r;
        }

        if (!groupPanel && !isInvalidSlot(columns * r + ((mouse.x - leftBorder) / SLOT_SIZE))) {
            return r;
        }

        return -1;
    }

    @Override
    public void setGridSize(int mleft, int mtop, int w, int h) {
        super.setGridSize(mleft + GROUP_PANEL_WIDTH, mtop, w - GROUP_PANEL_WIDTH, h);
    }

    private void sortIngredients() {

        for (int itemIndex = 0; itemIndex < this.bookmarkItems.size(); itemIndex++) {
            final BookmarkItem item = this.bookmarkItems.get(itemIndex);

            if (item.groupId != DEFAULT_GROUP_ID) {
                final List<BookmarkItem> items = this.bookmarkItems.stream().filter(itm -> itm.groupId == item.groupId)
                        .collect(Collectors.toList());
                this.bookmarkItems.removeAll(items);
                this.bookmarkItems.addAll(itemIndex, items);
                itemIndex += items.size() - 1;
            }
        }

        for (Entry<Integer, BookmarkGroup> entry : this.groups.entrySet()) {
            final Map<RecipeId, Integer> recipeState = new HashMap<>();
            final BookmarkGroup group = entry.getValue();
            final int groupId = entry.getKey();

            if (group.viewMode == BookmarkViewMode.TODO_LIST || group.crafting != null) {
                sortItemsInsideGroup(groupId);
            }

            for (BookmarkItem item : this.bookmarkItems) {
                if (item.groupId == groupId && item.recipeId != null) {
                    recipeState.put(
                            item.recipeId,
                            recipeState.getOrDefault(item.recipeId, 0) | (item.isIngredient ? 1 : 2));
                }
            }

            for (BookmarkItem item : this.bookmarkItems) {
                if (item.groupId == groupId && item.isIngredient
                        && (item.recipeId == null || recipeState.getOrDefault(item.recipeId, 1) == 1)) {
                    recipeState.remove(item.recipeId);
                    item.isIngredient = false;
                    item.recipeId = null;
                }
            }

            group.collapsedRecipes.removeIf(recipeId -> !recipeState.containsKey(recipeId));

            if (group.crafting != null) {
                group.crafting.refresh(createChainItems(groupId), group.collapsedRecipes);
            }
        }

    }

    public void sortItemsInsideGroup(int groupId) {
        final int size = this.bookmarkItems.size();
        int itemIndex = 0;

        while (itemIndex < size) {
            final BookmarkItem item = this.bookmarkItems.get(itemIndex);
            if (item.groupId == groupId && item.recipeId != null) {
                final Map<Integer, Integer> sortingRank = new HashMap<>();
                final List<BookmarkItem> sortedItems = new ArrayList<>();
                final List<Integer> items = new ArrayList<>();

                for (int index = itemIndex; index < size; index++) {
                    if (item.equalsRecipe(this.bookmarkItems.get(index))) {
                        sortingRank.put(index, this.bookmarkItems.get(index).isIngredient ? 1 : -1);
                        items.add(index);
                    }
                }

                items.sort((a, b) -> sortingRank.get(a) - sortingRank.get(b));

                for (int index : items) {
                    sortedItems.add(this.bookmarkItems.get(index));
                }

                this.bookmarkItems.removeAll(sortedItems);
                this.bookmarkItems.addAll(itemIndex, sortedItems);
                itemIndex += sortedItems.size();
            } else {
                itemIndex++;
            }
        }

    }

    @Override
    public void draw(int mousex, int mousey) {
        if (getPerPage() == 0) {
            return;
        }

        if (this.gridGenerator.gridMask == null) {
            getMask();
        }

        if (!this.isEmpty() && this.gridGenerator.maxAbsoluteSlotIndex > 0) {
            final GroupingItem groupingItem = ItemPanels.bookmarkPanel.groupingItem;
            final int sortableGroupId = ItemPanels.bookmarkPanel.sortableGroup != null
                    ? ItemPanels.bookmarkPanel.sortableGroup.groupId
                    : -3;
            final int rowShift = this.page * this.rows;
            Map<Integer, Integer> groups = this.gridGenerator.rowToGroupId;

            if (groupingItem != null && groupingItem.hasEndRow()) {
                groups = groupingItem.prepareGroups(groups);
            } else if (sortableGroupId != -3) {

                for (int rowIndex = 0; rowIndex < this.rows; rowIndex++) {
                    if (groups.getOrDefault(rowShift + rowIndex, DEFAULT_GROUP_ID) == sortableGroupId) {
                        NEIClientUtils.drawRect(
                                marginLeft + paddingLeft - GROUP_PANEL_WIDTH,
                                marginTop + rowIndex * SLOT_SIZE,
                                GROUP_PANEL_WIDTH,
                                SLOT_SIZE,
                                DRAG_COLOR);
                    }
                }

            } else if (!ItemPanels.bookmarkPanel.inEditingState()) {
                final int focusedRowIndex = getHoveredRowIndex(true);

                if (focusedRowIndex != -1) {
                    NEIClientUtils.drawRect(
                            marginLeft + paddingLeft - GROUP_PANEL_WIDTH,
                            marginTop + focusedRowIndex * SLOT_SIZE,
                            GROUP_PANEL_WIDTH,
                            SLOT_SIZE,
                            HIGHLIGHT_COLOR);
                }
            }

            int previousPageGroupId = groups.getOrDefault(rowShift - 1, DEFAULT_GROUP_ID);
            int nextPageGroupId = groups.getOrDefault(rowShift + this.rows, DEFAULT_GROUP_ID);
            int previousGroupId = DEFAULT_GROUP_ID;
            int groupStartIndex = -2;

            if (previousPageGroupId == groups.get(rowShift)) {
                previousGroupId = previousPageGroupId;
                groupStartIndex = -1;
            }

            for (int rowIndex = 0; rowIndex < this.rows && groups.containsKey(rowShift + rowIndex); rowIndex++) {
                int groupId = groups.get(rowShift + rowIndex);

                if (groupStartIndex != -2 && previousGroupId != groupId) {

                    if (previousGroupId != DEFAULT_GROUP_ID && previousGroupId != sortableGroupId) {
                        drawGroup(previousGroupId, groupStartIndex, rowIndex - 1);
                    } else {
                        drawShadowGroup(groupStartIndex, rowIndex - 1);
                    }

                    groupStartIndex = -2;
                }

                if (groupStartIndex == -2) {
                    groupStartIndex = rowIndex;
                }

                previousGroupId = groupId;
            }

            if (groupStartIndex != -2) {
                final int rowIndex = nextPageGroupId != DEFAULT_GROUP_ID
                        && nextPageGroupId == groups.get(rowShift + this.rows - 1) ? this.rows : getLastRowIndex();

                if (previousGroupId != DEFAULT_GROUP_ID && previousGroupId != sortableGroupId) {
                    drawGroup(previousGroupId, groupStartIndex, rowIndex);
                } else {
                    drawShadowGroup(groupStartIndex, rowIndex);
                }
            }

        }

        super.draw(mousex, mousey);
    }

    public void drawGroup(int groupId, int rowIndexStart, int rowIndexEnd) {
        final int halfWidth = GROUP_PANEL_WIDTH / 2;
        final int heightPadding = SLOT_SIZE / 4;
        final int leftPosition = marginLeft + paddingLeft - halfWidth - 1;
        final Color color = groupId > DEFAULT_GROUP_ID && this.groups.get(groupId).crafting != null ? GROUP_CHAIN_COLOR
                : GROUP_NONE_COLOR;
        int height = (Math.min(rowIndexEnd, this.rows - 1) - Math.max(0, rowIndexStart) + 1) * SLOT_SIZE;
        int top = marginTop + Math.max(0, rowIndexStart) * SLOT_SIZE;

        if (rowIndexStart >= 0) {
            NEIClientUtils
                    .drawRect(leftPosition, marginTop + rowIndexStart * SLOT_SIZE + heightPadding, halfWidth, 1, color);
            top += heightPadding + 1;
            height -= heightPadding + 1;
        }

        if (rowIndexEnd < this.rows) {
            NEIClientUtils.drawRect(
                    leftPosition,
                    marginTop + (rowIndexEnd + 1) * SLOT_SIZE - heightPadding,
                    halfWidth,
                    1,
                    color);
            height -= heightPadding;
        }

        NEIClientUtils.drawRect(leftPosition, top, 1, height, color);
    }

    private void drawShadowGroup(int rowIndexStart, int rowIndexEnd) {
        int top = marginTop + Math.max(0, rowIndexStart) * SLOT_SIZE;
        int height = (Math.min(rowIndexEnd, this.rows - 1) - Math.max(0, rowIndexStart) + 1) * SLOT_SIZE;

        NEIClientUtils.drawRect(
                marginLeft + paddingLeft - GROUP_PANEL_WIDTH / 2 - 1,
                top + 2,
                1,
                height - 4,
                PLACEHOLDER_COLOR);
    }

    @Override
    public void onItemsChanged() {
        this.isDirty = true;
        onGridChanged();
    }

    @Override
    public void onGridChanged() {
        this.gridGenerator.gridMask = null;
        super.onGridChanged();
    }

    public Map<Integer, BookmarkItem> createChainItems(int groupId) {
        final Map<Integer, BookmarkItem> chainItems = new HashMap<>();

        for (int itemIndex = 0; itemIndex < this.bookmarkItems.size(); itemIndex++) {
            BookmarkItem item = this.bookmarkItems.get(itemIndex);
            if (item.groupId == groupId) {
                chainItems.put(itemIndex, item.copy());
            }
        }

        return chainItems;
    }

    public RecipeChainMath createRecipeChainMath(int groupId) {
        return createRecipeChainMath(groupId, null);
    }

    public RecipeChainMath createRecipeChainMath(int groupId, RecipeId recipeId) {
        final BookmarkGroup group = getGroup(groupId);
        final List<BookmarkItem> chainItems = new ArrayList<>();
        final Set<RecipeId> collapsedRecipes = group.crafting != null && recipeId == null ? group.collapsedRecipes
                : Collections.emptySet();
        Set<RecipeId> recipeRelations = null;

        if (group.collapsed) {
            recipeId = null;
        }

        if (group.crafting != null && recipeId != null) {
            for (Map.Entry<RecipeId, Set<RecipeId>> entry : group.crafting.recipeRelations.entrySet()) {
                if (entry.getValue().contains(recipeId)) {
                    recipeId = entry.getKey();
                    break;
                }
            }
        }

        if (group.crafting != null && group.crafting.recipeRelations.containsKey(recipeId)) {
            recipeRelations = group.crafting.recipeRelations.get(recipeId);
        } else if (recipeId != null) {
            recipeRelations = new HashSet<>(Arrays.asList(recipeId));
        }

        for (int itemIndex = 0; itemIndex < this.bookmarkItems.size(); itemIndex++) {
            final BookmarkItem item = this.bookmarkItems.get(itemIndex);
            if (item.groupId == groupId && (recipeRelations == null || recipeRelations.contains(item.recipeId))) {
                long amount = item.amount;

                if (recipeId != null && group.crafting != null
                        && group.crafting.calculatedItems.containsKey(itemIndex)
                        && recipeId.equals(item.recipeId)) {
                    amount = group.crafting.calculatedItems.get(itemIndex).getCalculatedAmount();
                }

                chainItems.add(item.copyWithAmount(amount));
            }
        }

        if (group.crafting == null) {
            for (BookmarkItem item : chainItems) {
                item.recipeId = null;
            }
        }

        return RecipeChainMath.of(chainItems, collapsedRecipes);
    }

    public void createGroup(GroupingItem groupingItem) {
        final Map<Integer, List<BookmarkItem>> rowItems = collectItemsToRows();
        final Map<Integer, Integer> newGroups = new HashMap<>();
        boolean change = false;

        for (Map.Entry<Integer, Integer> entry : groupingItem.prepareGroups(this.gridGenerator.rowToGroupId)
                .entrySet()) {
            if (this.gridGenerator.rowToGroupId.get(entry.getKey()) != entry.getValue()) {
                int groupId = entry.getValue();

                if (groupId == Integer.MIN_VALUE) {
                    groupId = newGroups.computeIfAbsent(
                            groupId,
                            grId -> addGroup(new BookmarkGroup(getViewMode(DEFAULT_GROUP_ID))));
                } else if (groupId < 0) {
                    groupId = newGroups
                            .computeIfAbsent(groupId, grId -> addGroup(new BookmarkGroup(getViewMode(Math.abs(grId)))));
                }

                for (BookmarkItem item : rowItems.getOrDefault(entry.getKey(), Collections.emptyList())) {
                    item.groupId = groupId;
                }

                change = true;
            }
        }

        if (change) {
            this.bookmarkItems = rowItems.values().stream().flatMap(items -> items.stream())
                    .collect(Collectors.toList());

            final Set<Integer> usedGroupIds = new HashSet<>();
            for (BookmarkItem item : this.bookmarkItems) {
                usedGroupIds.add(item.groupId);
            }

            this.groups.keySet().removeIf(k -> k != DEFAULT_GROUP_ID && !usedGroupIds.contains(k));

            onItemsChanged();
        }

    }

    private Map<Integer, List<BookmarkItem>> collectItemsToRows() {
        final Map<Integer, List<BookmarkItem>> results = new HashMap<>();
        final int totalRows = (int) Math.ceil(this.gridGenerator.maxAbsoluteSlotIndex / (float) this.columns);
        final List<BookmarkItem> allItems = new ArrayList<>(this.bookmarkItems);

        for (int rowIndex = 0; rowIndex < totalRows; rowIndex++) {
            int itemIndex = Integer.MIN_VALUE;

            for (int index = 0; index < this.columns; index++) {
                if (this.gridGenerator.slotToItem.containsKey(rowIndex * this.columns + index)) {
                    itemIndex = this.gridGenerator.slotToItem.get(rowIndex * this.columns + index);
                    break;
                }
            }

            if (itemIndex != Integer.MIN_VALUE) {
                final BookmarkItem item = getCalculatedItem(itemIndex);
                final BookmarkGroup group = getGroup(item.groupId);
                final List<BookmarkItem> items = new ArrayList<>();

                if (group.collapsed || group.crafting != null && group.viewMode == BookmarkViewMode.DEFAULT) {

                    for (BookmarkItem itm : allItems) {
                        if (itm.groupId == item.groupId) {
                            items.add(itm);
                        }
                    }

                } else if (item.recipeId != null && group.crafting != null
                        && group.crafting.itemToRecipe.containsKey(itemIndex)) {
                            final Set<RecipeId> recipeRelations = group.crafting.recipeRelations
                                    .getOrDefault(item.recipeId, Collections.singleton(item.recipeId));

                            for (BookmarkItem itm : allItems) {
                                if (itm.groupId == item.groupId && recipeRelations.contains(itm.recipeId)) {
                                    items.add(itm);
                                }
                            }

                        } else {

                            for (int index = 0; index < this.columns; index++) {
                                if (this.gridGenerator.slotToItem.containsKey(rowIndex * this.columns + index)) {
                                    final BookmarkItem itm = this.bookmarkItems
                                            .get(this.gridGenerator.slotToItem.get(rowIndex * this.columns + index));
                                    if (allItems.contains(itm)) {
                                        items.add(itm);
                                    }
                                }
                            }

                        }

                results.put(rowIndex, items);
                allItems.removeAll(items);
            }

        }

        results.put(totalRows, allItems);

        return results;
    }

    public void removeGroup(int groupId) {
        this.bookmarkItems.removeIf(item -> item.groupId == groupId);

        if (groupId != DEFAULT_GROUP_ID) {
            this.groups.remove(groupId);
        }

        onItemsChanged();
    }

    public void moveGroup(SortableGroup sortableGroup, int overRowIndex) {
        int absoluteRowIndex = this.page * this.rows + overRowIndex;
        int absoluteSlotIndex = absoluteRowIndex * this.columns;
        int overGroupId = getRowGroupId(overRowIndex);

        if (sortableGroup.groupId == overGroupId) {
            return;
        }

        boolean mouseDown = false;

        for (int rowId = 0; rowId < absoluteRowIndex && !mouseDown; rowId++) {
            if (this.gridGenerator.rowToGroupId.get(rowId) == sortableGroup.groupId) {
                mouseDown = true;
            }
        }

        if (mouseDown) {
            absoluteRowIndex += 1;
            absoluteSlotIndex += this.columns;
        }

        if (mouseDown && absoluteSlotIndex >= this.gridGenerator.maxAbsoluteSlotIndex) {
            final int itemIndex = this.gridGenerator.slotToItem
                    .getOrDefault(this.gridGenerator.maxAbsoluteSlotIndex - 1, Integer.MIN_VALUE);

            if (itemIndex == Integer.MIN_VALUE || getCalculatedItem(itemIndex).groupId == sortableGroup.groupId) {
                return;
            }

            moveItems(null, sortableGroup.getBookmarkItems());
        } else if (this.gridGenerator.rowToGroupId.containsKey(absoluteRowIndex)) {
            overGroupId = this.gridGenerator.rowToGroupId.get(absoluteRowIndex);

            if (overGroupId != DEFAULT_GROUP_ID && overGroupId
                    == this.gridGenerator.rowToGroupId.getOrDefault(absoluteRowIndex - 1, DEFAULT_GROUP_ID)) {
                return;
            }

            final int itemIndex = this.gridGenerator.slotToItem.getOrDefault(absoluteSlotIndex, Integer.MIN_VALUE);
            BookmarkItem bookmarkItem = getCalculatedItem(itemIndex);

            if (bookmarkItem == null) {
                return;
            }

            if (bookmarkItem.groupId != DEFAULT_GROUP_ID) {
                for (int i = 0; i < this.bookmarkItems.size(); i++) {
                    if (this.bookmarkItems.get(i).groupId == bookmarkItem.groupId) {
                        bookmarkItem = this.bookmarkItems.get(i);
                        break;
                    }
                }
            }

            moveItems(bookmarkItem, sortableGroup.getBookmarkItems());
        }

    }

    public int indexOf(int groupId, ItemStack stackA, RecipeId recipeId, boolean ingredient) {

        for (int itemIndex = 0; itemIndex < this.bookmarkItems.size(); itemIndex++) {
            final BookmarkItem item = this.bookmarkItems.get(itemIndex);
            if (item.groupId == groupId && item.isIngredient == ingredient
                    && (recipeId == item.recipeId || recipeId != null && recipeId.equals(item.recipeId))
                    && StackInfo.equalItemAndNBT(stackA, item.itemStack, true)) {
                return itemIndex;
            }
        }

        return -1;
    }

    public BookmarkItem getCalculatedItem(int itemIndex) {

        if (this.gridGenerator.calculatedItems.containsKey(itemIndex)) {
            return this.gridGenerator.calculatedItems.get(itemIndex).getItem();
        }

        return null;
    }

    public BookmarkItem getBookmarkItem(int itemIndex) {

        if (itemIndex < 0 || itemIndex >= this.bookmarkItems.size()) {
            return null;
        }

        return this.bookmarkItems.get(itemIndex);
    }

    public int getAbsoluteSlotIndex(int itemIndex) {
        return this.gridGenerator.itemToSlot.getOrDefault(itemIndex, -1);
    }

    public List<Integer> getSortedItems() {
        return new ArrayList<>(this.gridGenerator.slotToItem.values());
    }

    public void addItem(BookmarkItem item, boolean animate) {
        this.bookmarkItems.add(item);

        if (animate && NEIClientConfig.getGridRenderingCacheMode() == 0 && NEIClientConfig.areBookmarksAnimated()) {
            this.animation.put(item, 0f);
        }

        onItemsChanged();
    }

    public void addRecipe(Recipe recipe, int groupId) {
        addRecipe(recipe, groupId, 1);
    }

    public void addRecipe(Recipe recipe, int groupId, int times) {
        final RecipeId recipeId = recipe.getRecipeId();
        final List<ItemStack> results = recipe.getResults().stream().map(res -> res.getItemStack())
                .collect(Collectors.toList());
        final List<ItemStack> ingredients = recipe.getIngredients().stream().map(ingr -> ingr.getItemStack())
                .collect(Collectors.toList());

        for (ItemStack stack : ItemStackAmount.of(results).values()) {
            int currentAmount = StackInfo.getAmount(stack);
            this.addItem(
                    BookmarkItem.of(
                            groupId,
                            StackInfo.withAmount(stack, (long) currentAmount * times),
                            currentAmount,
                            recipeId,
                            false),
                    true);
        }

        for (ItemStack stack : ItemStackAmount.of(ingredients).values()) {
            int currentAmount = StackInfo.getAmount(stack);
            this.addItem(
                    BookmarkItem.of(
                            groupId,
                            StackInfo.withAmount(stack, (long) currentAmount * times),
                            currentAmount,
                            recipeId,
                            true,
                            BookmarkItem.generatePermutations(stack, recipe)),
                    true);
        }
    }

    public void removeRecipe(int itemIndex, boolean removeFullRecipe) {
        final BookmarkItem item = getCalculatedItem(itemIndex);

        if (item == null) {
            return;
        }

        if (!removeFullRecipe && item.recipeId != null && !item.isIngredient) {
            removeFullRecipe = this.bookmarkItems.stream()
                    .noneMatch(m -> !m.isIngredient && !m.equals(item) && item.equalsRecipe(m));
        }

        if (item.recipeId != null && removeFullRecipe) {
            removeRecipe(item.recipeId, item.groupId);
        } else {
            this.bookmarkItems.remove(itemIndex);
            onItemsChanged();
        }
    }

    public boolean removeRecipe(RecipeId recipeId, int groupId) {

        if (this.bookmarkItems.removeIf(item -> item.equalsRecipe(recipeId, groupId))) {
            onItemsChanged();
            return true;
        }

        return false;
    }

    public boolean existsRecipe(RecipeId recipeId, int groupId) {
        return this.bookmarkItems.stream().anyMatch(item -> item.equalsRecipe(recipeId, groupId));
    }

    public RecipeId getRecipeId(int itemIndex) {
        final BookmarkItem bookmarkItem = getCalculatedItem(itemIndex);
        return bookmarkItem != null ? bookmarkItem.recipeId : null;
    }

    public void moveItem(SortableItem sortableItem, int overRowIndex, int overGroupId, boolean moveAfter) {
        final BookmarkGroup overGroup = getGroup(overGroupId);
        int absoluteSlotIndex = (this.page * this.rows + overRowIndex) * this.columns;

        if (sortableItem.isCollapsedRecipe && sortableItem.bookmarkItem.groupId != overGroupId || overGroup.collapsed
                || overGroup.viewMode == BookmarkViewMode.DEFAULT) {
            return;
        }

        if (sortableItem.bookmarkItem.groupId != overGroupId) {

            for (BookmarkItem item : this.bookmarkItems) {
                if (sortableItem.containsItem(item)) {
                    item.groupId = overGroupId;
                }
            }

            sortableItem.bookmarkItem.groupId = overGroupId;
            onItemsChanged();
        }

        if (moveAfter) {
            absoluteSlotIndex += this.columns;
        }

        if (moveAfter && absoluteSlotIndex >= this.gridGenerator.maxAbsoluteSlotIndex) {
            final int itemIndex = this.gridGenerator.slotToItem
                    .getOrDefault(this.gridGenerator.maxAbsoluteSlotIndex - 1, Integer.MIN_VALUE);

            if (itemIndex == Integer.MIN_VALUE || sortableItem.items.contains(itemIndex)) {
                return;
            }

            moveItems(null, sortableItem.getBookmarkItems());
        } else if (this.gridGenerator.slotToItem.containsKey(absoluteSlotIndex)) {
            final int itemIndex = this.gridGenerator.slotToItem.get(absoluteSlotIndex);
            final List<Integer> sortedIndexes = new ArrayList<>(this.gridGenerator.slotToItem.values());
            BookmarkItem bookmarkItem = getCalculatedItem(itemIndex);
            final BookmarkGroup group = getGroup(bookmarkItem.groupId);
            final int sortIndex = sortedIndexes.indexOf(itemIndex);

            if (sortIndex == -1 || sortableItem.items.contains(itemIndex)
                    || sortIndex > 0 && sortableItem.items.contains(sortedIndexes.get(sortIndex - 1))) {
                return;
            }

            if (group.crafting != null && sortIndex > 0) {
                final RecipeId recipeId = group.crafting.itemToRecipe.getOrDefault(itemIndex, bookmarkItem.recipeId);

                if (recipeId != null) {
                    if (recipeId.equals(this.gridGenerator.itemToRecipe.get(sortedIndexes.get(sortIndex - 1)))) {
                        return;
                    }

                    for (int i = 0; i < this.bookmarkItems.size(); i++) {
                        if (this.bookmarkItems.get(i).groupId == bookmarkItem.groupId
                                && recipeId.equals(this.bookmarkItems.get(i).recipeId)) {
                            bookmarkItem = this.bookmarkItems.get(i);
                            break;
                        }
                    }
                }
            }

            moveItems(bookmarkItem, sortableItem.getBookmarkItems());
        }

    }

    public void moveItem(SortableItem sortableItem, int itemIndex) {
        final List<Integer> sortedIndexes = new ArrayList<>(this.gridGenerator.slotToItem.values());
        int sortIndex = sortedIndexes.indexOf(itemIndex);
        final boolean moveAfter = sortIndex > sortedIndexes.indexOf(sortableItem.getItemIndex());
        final BookmarkGroup group = getGroup(sortableItem.bookmarkItem.groupId);
        BookmarkItem bookmarkItem = getCalculatedItem(itemIndex);

        if (bookmarkItem == null || sortIndex == -1 || sortableItem.items.contains(itemIndex) || group.collapsed) {
            return;
        }

        if (moveAfter) {
            sortIndex = findNextBookmarkItem(itemIndex);

            if (sortIndex == -1) {
                return;
            }

            if (sortIndex < sortedIndexes.size()) {
                bookmarkItem = getCalculatedItem(sortedIndexes.get(sortIndex));
            } else {
                bookmarkItem = null;
            }
        }

        if (bookmarkItem != null && (group.crafting != null && group.viewMode == BookmarkViewMode.DEFAULT
                || group.viewMode == BookmarkViewMode.TODO_LIST && !sortableItem.bookmarkItem.isIngredient)) {
            while (sortIndex > 0 && bookmarkItem.equalsRecipe(getCalculatedItem(sortedIndexes.get(sortIndex - 1)))) {
                sortIndex--;
            }

            bookmarkItem = getCalculatedItem(sortedIndexes.get(sortIndex));
        }

        moveItems(bookmarkItem, sortableItem.getBookmarkItems());
    }

    private int findNextBookmarkItem(int itemIndex) {
        final List<Integer> sortedIndexes = new ArrayList<>(this.gridGenerator.slotToItem.values());
        final int sortIndex = sortedIndexes.indexOf(itemIndex);
        final BookmarkItem target = getCalculatedItem(itemIndex);
        final BookmarkGroup group = getGroup(target.groupId);
        int nextSortIndex = sortIndex + 1;

        if (group.viewMode == BookmarkViewMode.TODO_LIST) {
            final int rowId = this.gridGenerator.itemToSlot.get(itemIndex) / this.columns;

            while (nextSortIndex < sortedIndexes.size()
                    && (this.gridGenerator.itemToSlot.get(sortedIndexes.get(nextSortIndex)) / this.columns) == rowId) {
                nextSortIndex++;
            }
        }

        if (nextSortIndex < sortedIndexes.size()) {
            final int nextItemIndex = sortedIndexes.get(nextSortIndex);

            if (group.viewMode == BookmarkViewMode.TODO_LIST && !target.isIngredient
                    && target.equalsRecipe(getCalculatedItem(nextItemIndex))) {
                return -1;
            }

            if (group.crafting != null) {
                final RecipeId recipeId = group.crafting.itemToRecipe.get(itemIndex);

                if (recipeId != null && recipeId.equals(this.gridGenerator.itemToRecipe.get(nextItemIndex))) {
                    return -1;
                }
            }

            return nextSortIndex;
        }

        return Integer.MAX_VALUE;
    }

    private void moveItems(BookmarkItem bookmarkItem, List<BookmarkItem> items) {
        this.bookmarkItems.removeAll(items);
        final int index = bookmarkItem == null ? this.bookmarkItems.size() : this.bookmarkItems.indexOf(bookmarkItem);
        this.bookmarkItems.addAll(index, items);
        onItemsChanged();
    }

    public void shiftGroupAmount(int targetGroupId, long shift) {
        final BookmarkGroup group = getGroup(targetGroupId);

        if (group.crafting == null) {
            final List<BookmarkItem> items = new ArrayList<>();
            final Set<Long> sizes = new HashSet<>();

            for (BookmarkItem item : this.bookmarkItems) {
                if (item.groupId == targetGroupId && item.amount > 0) {
                    sizes.add(Math.max(item.getStackSize(), item.factor / item.fluidCellAmount));
                    items.add(item);
                }
            }

            final long gcd = sizes.stream().reduce(0L, (a, b) -> gcd(a, b));
            long multiplier = Integer.MAX_VALUE;

            for (BookmarkItem item : items) {
                long customFactor = (long) Math.ceil(item.amount / (double) gcd);
                long customMultiplier = (long) (customFactor > 0 ? Math.ceil(item.amount / (double) customFactor) : 0);
                multiplier = Math.min(shiftMultiplier(customMultiplier, shift, 1), multiplier);
            }

            for (BookmarkItem item : items) {
                long customFactor = (long) Math.ceil(item.amount / (double) gcd);
                item.amount = customFactor * multiplier;
            }
        } else {
            final Map<RecipeId, Long> recipeMultipliers = new HashMap<>();
            final Set<RecipeId> outputRecipes = group.crafting.outputRecipes;
            final List<BookmarkItem> items = new ArrayList<>();

            for (BookmarkItem item : this.bookmarkItems) {
                if (item.factor > 0
                        && outputRecipes.stream().anyMatch(recipeId -> item.equalsRecipe(recipeId, targetGroupId))) {
                    final long multiplier = recipeMultipliers.getOrDefault(item.recipeId, (long) Integer.MAX_VALUE);
                    final boolean isOutputRecipe = group.crafting != null
                            && !group.crafting.recipeInMiddle.contains(item.recipeId);

                    recipeMultipliers.put(
                            item.recipeId,
                            Math.min(
                                    shiftMultiplier(
                                            Math.max(isOutputRecipe ? 1 : 0, item.getMultiplier()),
                                            shift,
                                            isOutputRecipe ? 1 : 0),
                                    multiplier));
                    items.add(item);
                }
            }

            for (BookmarkItem item : items) {
                item.amount = item.factor * recipeMultipliers.getOrDefault(item.recipeId, 0L);
            }
        }

        this.onItemsChanged();
    }

    public void shiftItemAmount(int targetItemIndex, long shift) {
        final BookmarkItem targetItem = getCalculatedItem(targetItemIndex);
        final BookmarkGroup group = getGroup(targetItem.groupId);

        if (group.collapsed) {
            shiftGroupAmount(targetItem.groupId, shift);
        } else if (targetItem.recipeId != null) {
            final RecipeId recipeId = this.gridGenerator.itemToRecipe
                    .getOrDefault(targetItemIndex, targetItem.recipeId);
            long multiplier = Integer.MAX_VALUE;

            for (BookmarkItem item : this.bookmarkItems) {
                if (item.equalsRecipe(recipeId, targetItem.groupId) && item.factor > 0) {
                    multiplier = Math.min(shiftMultiplier(Math.max(0, item.getMultiplier()), shift, 0), multiplier);
                }
            }

            for (BookmarkItem item : this.bookmarkItems) {
                if (item.equalsRecipe(recipeId, targetItem.groupId) && item.factor > 0) {
                    item.amount = item.factor * multiplier;
                }
            }

        } else {

            for (BookmarkItem item : this.bookmarkItems) {
                if (targetItem.equals(item)) {
                    item.amount = item.factor * shiftMultiplier(item.getMultiplier(), shift, 0);
                    break;
                }
            }

        }

        this.onItemsChanged();
    }

    private long shiftMultiplier(long multiplier, long shift, int minMultiplier) {
        final long currentMultiplier = ((multiplier + shift) / shift) * shift;
        return Math.min(
                Integer.MAX_VALUE,
                currentMultiplier <= 0 && multiplier > 1 ? 1 : Math.max(minMultiplier, currentMultiplier));
    }

    private long gcd(long a, long b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    @Override
    protected void beforeDrawItems(int mousex, int mousey, BookmarkMouseContext mouseContext) {

        if (LayoutManager.bookmarkPanel.inEditingState()) {
            final SortableGroup sortableGroup = LayoutManager.bookmarkPanel.sortableGroup;
            final SortableItem sortableItem = LayoutManager.bookmarkPanel.sortableItem;
            final List<BookmarksGridSlot> borders = new ArrayList<>();

            for (BookmarksGridSlot slot : getMask()) {
                Rectangle4i rect = getSlotRect(slot.slotIndex);

                if (sortableGroup != null && slot.getGroupId() == sortableGroup.groupId) {
                    NEIClientUtils.drawRect(rect.x, rect.y, rect.w, rect.h, DRAG_COLOR);
                } else if (sortableItem != null && sortableItem.items.contains(slot.itemIndex)) {
                    NEIClientUtils.drawRect(rect.x, rect.y, rect.w, rect.h, DRAG_COLOR);
                    borders.add(slot);
                } else {
                    borders.add(slot);
                }

            }

            for (BookmarksGridSlot slot : borders) {
                slot.drawBorder(getSlotRect(slot.slotIndex));
            }

        } else {
            super.beforeDrawItems(mousex, mousey, mouseContext);
        }

    }

    @Override
    protected void afterDrawItems(int mousex, int mousey, BookmarkMouseContext mouseContext) {

        if (LayoutManager.bookmarkPanel.inEditingState()) {
            final SortableGroup sortableGroup = LayoutManager.bookmarkPanel.sortableGroup;
            final SortableItem sortableItem = LayoutManager.bookmarkPanel.sortableItem;

            for (BookmarksGridSlot slot : getMask()) {
                if ((sortableGroup == null || slot.getGroupId() != sortableGroup.groupId)
                        && (sortableItem == null || !sortableItem.items.contains(slot.itemIndex))) {
                    slot.afterDraw(getSlotRect(slot.slotIndex), mouseContext);
                }
            }
        } else {
            super.afterDrawItems(mousex, mousey, mouseContext);
        }
    }

    @Override
    protected void drawItems() {
        final SortableGroup sortableGroup = LayoutManager.bookmarkPanel.sortableGroup;
        final SortableItem sortableItem = LayoutManager.bookmarkPanel.sortableItem;

        for (BookmarksGridSlot slot : getMask()) {

            if ((sortableGroup == null || slot.getGroupId() != sortableGroup.groupId)
                    && (sortableItem == null || !sortableItem.items.contains(slot.itemIndex))) {
                final BookmarkItem item = slot.getBookmarkItem();

                if (this.animation.containsKey(item) && this.animation.get(item) < 1) {
                    final float currentScale = this.animation.get(item) + SCALE_SPEED;

                    if (currentScale >= 1) {
                        this.animation.remove(item);
                    } else {
                        this.animation.put(item, currentScale);
                    }

                    drawPoppingItem(slot, getSlotRect(slot.slotIndex), currentScale);
                } else {
                    slot.drawItem(getSlotRect(slot.slotIndex));
                }

            }

        }

    }

    protected void drawPoppingItem(BookmarksGridSlot slot, Rectangle4i rect, float currentScale) {
        final float inverseScaleFactor = 1f / currentScale;
        final float shiftX = (rect.x + (rect.w - rect.w * currentScale) / 2f) * inverseScaleFactor;
        final float shiftY = (rect.y + (rect.h - rect.h * currentScale) / 2f) * inverseScaleFactor;

        GL11.glScalef(currentScale, currentScale, 1);
        GL11.glTranslated(shiftX, shiftY, 0);

        slot.drawItem(new Rectangle4i(0, 0, rect.w, rect.h));

        GL11.glTranslated(-1 * shiftX, -1 * shiftY, 0);
        GL11.glScalef(inverseScaleFactor, inverseScaleFactor, 1);
    }

}
