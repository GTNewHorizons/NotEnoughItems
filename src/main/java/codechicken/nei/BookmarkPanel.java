package codechicken.nei;

import static codechicken.lib.gui.GuiDraw.drawRect;
import static codechicken.lib.gui.GuiDraw.getMousePosition;
import static codechicken.nei.NEIClientUtils.getGuiContainer;
import static codechicken.nei.NEIClientUtils.translate;

import java.awt.Point;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import codechicken.core.CommonUtils;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemPanel.ItemPanelSlot;
import codechicken.nei.api.IBookmarkContainerHandler;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.BookmarkRecipeId;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;
import codechicken.nei.util.ReadableNumberConverter;

public class BookmarkPanel extends PanelWidget {

    protected File bookmarkFile;
    protected BookmarkLoadingState bookmarksState;
    protected SortableItem sortableItem;
    protected GroupingItem groupingItem;

    public Button namespacePrev;
    public Button namespaceNext;
    public Button pullBookmarkedItems;
    public Label namespaceLabel;

    protected List<BookmarkGrid> namespaces = new ArrayList<>();
    protected int activeNamespaceIndex = 0;

    protected static class SortableItem {

        public List<ItemStack> items = new ArrayList<>();
        public List<ItemStackMetadata> metadata = new ArrayList<>();

        public SortableItem(List<ItemStack> items, List<ItemStackMetadata> metadata) {
            this.items = items;
            this.metadata = metadata;
        }

    }

    protected static class GroupingItem {

        public boolean ungroup;
        public int rowIndexA;
        public int rowIndexB = -1;

        public GroupingItem(boolean ungroup, int rowIndexA) {
            this.ungroup = ungroup;
            this.rowIndexA = rowIndexA;
        }

        public int getTopRowIndex(BookmarkGrid BGrid) {
            int topRowIndex = Math.min(this.rowIndexA, this.rowIndexB);
            int topItemIndex = BGrid.getRowItemIndex(topRowIndex, true);
            ItemStackMetadata meta = topItemIndex >= 0 ? BGrid.metadata.get(topItemIndex) : null;

            if (meta != null && meta.recipeId != null) {
                while (topItemIndex > 0 && meta.groupId == BGrid.metadata.get(topItemIndex - 1).groupId
                        && meta.recipeId.equals(BGrid.metadata.get(topItemIndex - 1).recipeId)) {
                    topItemIndex--;
                }

                int index = BGrid.getMask().indexOf(topItemIndex);
                topRowIndex = index == -1 ? 0 : index / BGrid.columns;
            }

            return topRowIndex;
        }

        public int getBottomRowIndex(BookmarkGrid BGrid) {
            int bottomRowIndex = Math.max(this.rowIndexA, this.rowIndexB);
            int bottomItemIndex = BGrid.getRowItemIndex(bottomRowIndex, false);
            ItemStackMetadata meta = bottomItemIndex >= 0 ? BGrid.metadata.get(bottomItemIndex) : null;

            if (meta != null && meta.recipeId != null) {
                int size = BGrid.size();

                while (bottomItemIndex < size - 1 && meta.groupId == BGrid.metadata.get(bottomItemIndex + 1).groupId
                        && meta.recipeId.equals(BGrid.metadata.get(bottomItemIndex + 1).recipeId)) {
                    bottomItemIndex++;
                }

                int index = BGrid.getMask().indexOf(bottomItemIndex);
                bottomRowIndex = index == -1 ? BGrid.getLastRowIndex() : index / BGrid.columns;
            }

            return bottomRowIndex;
        }
    }

    protected static class ItemStackMetadata {

        public int factor;
        public Integer groupId;
        public BookmarkRecipeId recipeId;
        public boolean ingredient = false;
        public boolean fluidDisplay = false;

        public ItemStackMetadata(BookmarkRecipeId recipeId, int factor, boolean ingredient, Integer groupId,
                boolean fluidDisplay) {
            this.recipeId = recipeId;
            this.factor = factor;
            this.ingredient = ingredient;
            this.groupId = groupId;
            this.fluidDisplay = fluidDisplay;
        }

        public ItemStackMetadata copy() {
            return new ItemStackMetadata(this.recipeId, this.factor, this.ingredient, this.groupId, this.fluidDisplay);
        }
    }

    public static enum BookmarkViewMode {
        DEFAULT,
        TODO_LIST
    }

    public static enum BookmarkLoadingState {
        LOADING,
        LOADED
    }

    protected static class BookmarkGroup {

        public BookmarkCraftingChain crafting = null;
        public BookmarkViewMode viewMode;

        public BookmarkGroup(BookmarkViewMode viewMode) {
            this.viewMode = viewMode;
        }

        public BookmarkGroup(BookmarkViewMode viewMode, boolean crafting) {
            this.viewMode = viewMode;
            this.crafting = crafting ? new BookmarkCraftingChain() : null;
        }

        public void toggleViewMode() {
            if (this.viewMode == BookmarkViewMode.DEFAULT) {
                this.viewMode = BookmarkViewMode.TODO_LIST;
            } else {
                this.viewMode = BookmarkViewMode.DEFAULT;
            }
        }

        public void toggleCraftingMode() {
            if (this.crafting == null) {
                this.crafting = new BookmarkCraftingChain();
            } else {
                this.crafting = null;
            }
        }

        public BookmarkGroup copy() {
            return new BookmarkGroup(this.viewMode);
        }
    }

    public static class BookmarkGrid extends ItemsGrid {

        protected static final int GROUP_PANEL_WIDTH = 7;
        protected static final int DEFAULT_GROUP_ID = 0;
        protected static final float SCALE_SPEED = 0.1f;

        protected List<ItemStackMetadata> metadata = new ArrayList<>();
        protected WeakHashMap<ItemStack, Float> animation = new WeakHashMap<>();
        protected RecipeTooltipRenderer recipeTooltipRenderer = null;

        protected Map<Integer, BookmarkGroup> groups = new HashMap<>();
        protected List<Integer> gridGroupMask = new ArrayList<>();
        protected int previousPageGroupId = DEFAULT_GROUP_ID;
        protected int nextPageGroupId = DEFAULT_GROUP_ID;
        protected int focusedGroupId = -1;
        protected int pageCount = 0;

        private static class RecipeTooltipRenderer {

            public ItemStack stack = null;
            public BookmarkRecipeId recipeId = null;

            public GuiRecipe<?> gui = null;
            public Runnable createRecipeGui = null;
        }

        public BookmarkGrid() {
            this.groups.put(DEFAULT_GROUP_ID, new BookmarkGroup(BookmarkViewMode.DEFAULT));
        }

        public BookmarkViewMode getViewMode(int groupId) {
            return this.groups.get(groupId).viewMode;
        }

        public void setViewMode(int groupId, BookmarkViewMode mode) {
            if (this.groups.get(groupId).viewMode != mode) {
                this.groups.get(groupId).viewMode = mode;
                if (mode == BookmarkViewMode.DEFAULT) {
                    sortGroup(groupId);
                }

                onItemsChanged();
            }
        }

        public void toggleViewMode(int groupId) {
            this.groups.get(groupId).toggleViewMode();
            if (this.groups.get(groupId).viewMode == BookmarkViewMode.DEFAULT) {
                sortGroup(groupId);
            }
            onItemsChanged();
        }

        public boolean getCraftingMode(int groupId) {
            return this.groups.get(groupId).crafting != null;
        }

        public void setCraftingMode(int groupId, boolean on) {
            if ((this.groups.get(groupId).crafting != null) != on) {
                this.groups.get(groupId).crafting = on ? new BookmarkCraftingChain() : null;
                onItemsChanged();
            }
        }

        public void toggleCraftingMode(int groupId) {
            this.groups.get(groupId).toggleCraftingMode();
            onItemsChanged();
        }

        @Override
        public int getNumPages() {

            if (gridMask == null) {
                getMask();
            }

            return this.pageCount;
        }

        @Override
        protected List<Integer> getMask() {

            if (this.gridMask != null) {
                return this.gridMask;
            }

            if (this.perPage == 0 || size() == 0) {
                this.gridGroupMask = new ArrayList<>();
                this.gridMask = new ArrayList<>();
                return this.gridMask;
            }

            ItemStackMetadata previousMeta = new ItemStackMetadata(null, 1, false, DEFAULT_GROUP_ID, false);
            List<Integer> itemsMask = new ArrayList<>();
            List<Integer> groupsMask = new ArrayList<>();
            ItemStackMetadata meta;
            int size = size();
            int lastIdx = -1;
            int index = 0;
            int idx = 0;

            while (idx < size && lastIdx != idx) {
                lastIdx = idx;

                for (int r = 0; r < rows && idx < size; r++) {
                    for (int c = 0; c < columns && idx < size; c++) {
                        index = r * columns + c;

                        if (isInvalidSlot(index)) {
                            itemsMask.add(null);
                        } else {
                            meta = this.metadata.get(idx);

                            if (c > 0 && previousMeta.groupId != meta.groupId) {
                                // new group must start on a new line
                                itemsMask.add(null);
                            } else if (getViewMode(meta.groupId) == BookmarkViewMode.DEFAULT) {
                                previousMeta = meta;
                                itemsMask.add(idx++);
                            } else {

                                if (c == 0 && (meta.recipeId == null || meta.ingredient == false
                                        || index + 1 < rows * columns && isInvalidSlot(index + 1)
                                        || previousMeta.groupId != meta.groupId
                                        || meta.ingredient && !meta.recipeId.equals(previousMeta.recipeId))) {
                                    // In first column must be an item without recipe, a recipe result, or an ingredient
                                    // if the second column is occupied
                                    previousMeta = meta;
                                    itemsMask.add(idx++);
                                } else
                                    if (c > 0 && meta.recipeId != null && meta.recipeId.equals(previousMeta.recipeId)) {
                                        previousMeta = meta;
                                        itemsMask.add(idx++);
                                    } else {
                                        itemsMask.add(null);
                                    }

                            }

                        }

                    }
                    groupsMask.add(previousMeta.groupId);
                }
            }

            this.pageCount = (int) Math.ceil(itemsMask.size() / (float) (this.rows * this.columns));
            this.page = Math.max(0, Math.min(this.page, this.pageCount - 1));
            this.gridMask = itemsMask
                    .subList(page * rows * columns, Math.min(itemsMask.size(), (page + 1) * rows * columns));
            this.previousPageGroupId = DEFAULT_GROUP_ID;
            this.nextPageGroupId = DEFAULT_GROUP_ID;

            if (page > 0 && (this.page * this.rows) < groupsMask.size()
                    && groupsMask.get(this.page * this.rows) == groupsMask.get(this.page * this.rows - 1)) {
                this.previousPageGroupId = groupsMask.get(this.page * this.rows);
            }

            if ((this.page + 1) * this.rows < groupsMask.size()
                    && groupsMask.get((this.page + 1) * this.rows) == groupsMask.get((this.page + 1) * this.rows - 1)) {
                this.nextPageGroupId = groupsMask.get((this.page + 1) * this.rows);
            }

            this.gridGroupMask = groupsMask.subList(page * rows, Math.min(groupsMask.size(), (page + 1) * rows));

            return this.gridMask;
        }

        protected int getRowGroupId(int rowIndex) {
            if (gridMask == null) {
                getMask();
            }

            return rowIndex < this.gridGroupMask.size() ? this.gridGroupMask.get(rowIndex) : DEFAULT_GROUP_ID;
        }

        protected int getRowItemIndex(int rowIndex, boolean dir) {
            final List<Integer> mask = getMask();
            final int size = mask.size();
            int i = dir ? 0 : columns - 1;

            while (i >= 0 && i < columns) {

                if ((rowIndex * columns + i) < size && mask.get(rowIndex * columns + i) != null) {
                    return mask.get(rowIndex * columns + i);
                }

                i += dir ? 1 : -1;
            }

            return -1;
        }

        protected int getHoveredRowIndex(boolean groupPanel) {
            final Point mouse = getMousePosition();
            final int leftBorder = marginLeft + paddingLeft;
            final int r = (int) ((mouse.y - marginTop) / SLOT_SIZE);

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
            super.setGridSize(mleft + GROUP_PANEL_WIDTH, mtop, w, h);
        }

        @Override
        public void draw(int mousex, int mousey) {
            if (getPerPage() == 0) {
                return;
            }

            final int focusedRowIndex = getHoveredRowIndex(true);

            if (focusedRowIndex != -1) {
                drawRect(
                        marginLeft + paddingLeft - GROUP_PANEL_WIDTH,
                        marginTop + focusedRowIndex * SLOT_SIZE,
                        GROUP_PANEL_WIDTH,
                        SLOT_SIZE,
                        0xee555555);
            }

            final GroupingItem groupingItem = LayoutManager.bookmarkPanel.groupingItem;
            List<Integer> groupMask = this.gridGroupMask;
            int groupStartIndex = -2;
            int previoudGroupId = DEFAULT_GROUP_ID;

            if (groupingItem != null && groupingItem.rowIndexB != -1) {
                int topRowIndex = groupingItem.getTopRowIndex(this);
                int bottomRowIndex = groupingItem.getBottomRowIndex(this);
                int groupIdA = groupMask.get(groupingItem.rowIndexA);
                int groupId = groupingItem.ungroup ? DEFAULT_GROUP_ID : (groupIdA == DEFAULT_GROUP_ID ? -1 : groupIdA);
                groupMask = new ArrayList<>(groupMask);

                for (int rowIndex = topRowIndex; rowIndex <= bottomRowIndex; rowIndex++) {
                    groupMask.set(rowIndex, groupId);
                }

                if (groupIdA != DEFAULT_GROUP_ID && !groupingItem.ungroup
                        && groupingItem.rowIndexA != groupingItem.rowIndexB) {
                    if (groupingItem.rowIndexB < groupingItem.rowIndexA) {
                        for (int rowIndex = topRowIndex - 1; rowIndex >= 0
                                && groupMask.get(rowIndex) == groupIdA; rowIndex--) {
                            groupMask.set(rowIndex, DEFAULT_GROUP_ID);
                        }
                    } else if (groupingItem.rowIndexB > groupingItem.rowIndexA) {
                        for (int rowIndex = bottomRowIndex + 1; rowIndex < groupMask.size()
                                && groupMask.get(rowIndex) == groupIdA; rowIndex++) {
                            groupMask.set(rowIndex, DEFAULT_GROUP_ID);
                        }
                    }
                }

            }

            if (this.previousPageGroupId != DEFAULT_GROUP_ID && this.previousPageGroupId == groupMask.get(0)) {
                previoudGroupId = this.previousPageGroupId;
                groupStartIndex = -1;
            }

            for (int rowIndex = 0; rowIndex < groupMask.size(); rowIndex++) {
                int groupId = groupMask.get(rowIndex);

                if (groupStartIndex != -2 && previoudGroupId != DEFAULT_GROUP_ID && previoudGroupId != groupId) {
                    drawGroup(Math.max(DEFAULT_GROUP_ID, previoudGroupId), groupStartIndex, rowIndex - 1);
                    groupStartIndex = -2;
                }

                if (groupStartIndex == -2 && groupId != DEFAULT_GROUP_ID) {
                    groupStartIndex = rowIndex;
                }

                previoudGroupId = groupId;
            }

            if (groupStartIndex != -2) {
                final int rowIndex = this.nextPageGroupId != DEFAULT_GROUP_ID
                        && this.nextPageGroupId == groupMask.get(groupMask.size() - 1) ? this.rows : getLastRowIndex();
                drawGroup(Math.max(DEFAULT_GROUP_ID, previoudGroupId), groupStartIndex, rowIndex);
            }

            if (NEIClientUtils.shiftKey() && !LayoutManager.bookmarkPanel.inEditingState()) {
                final int rowId = getHoveredRowIndex(true);

                if (rowId != -1) {
                    this.focusedGroupId = getRowGroupId(rowId);
                } else {
                    final ItemPanelSlot focused = getSlotMouseOver(mousex, mousey);
                    this.focusedGroupId = focused != null ? this.metadata.get(focused.slotIndex).groupId : -1;
                }

            } else {
                this.focusedGroupId = -1;
            }

            super.draw(mousex, mousey);
        }

        private void drawGroup(int groupId, int rowIndexStart, int rowIndexEnd) {
            final int halfWidth = GROUP_PANEL_WIDTH / 2;
            final int heightPadding = SLOT_SIZE / 4;
            final int leftPosition = marginLeft + paddingLeft - halfWidth - 1;
            final int color = this.groups.get(groupId).crafting != null ? 0x6645DA75 : 0xff666666;
            int width = (Math.min(rowIndexEnd, this.rows - 1) - Math.max(0, rowIndexStart) + 1) * SLOT_SIZE;
            int top = marginTop + Math.max(0, rowIndexStart) * SLOT_SIZE;

            if (rowIndexStart >= 0) {
                drawRect(leftPosition, marginTop + rowIndexStart * SLOT_SIZE + heightPadding, halfWidth, 1, color);
                top += heightPadding + 1;
                width -= heightPadding + 1;
            }

            if (rowIndexEnd < this.rows) {
                drawRect(leftPosition, marginTop + (rowIndexEnd + 1) * SLOT_SIZE - heightPadding, halfWidth, 1, color);
                width -= heightPadding;
            }

            drawRect(leftPosition, top, 1, width, color);
        }

        private void removeDuplicateItems() {
            final HashMap<BookmarkRecipeId, Integer> recipeCache = new HashMap<>();
            final HashMap<ItemStack, Integer> itemsCache = new HashMap<>();
            final HashSet<String> unique = new HashSet<>();
            ItemStackMetadata meta;
            ItemStack stack;
            int index = 0;
            String key;

            while (index < this.metadata.size()) {
                stack = this.realItems.get(index);
                meta = this.metadata.get(index);
                key = String.valueOf(meta.groupId);

                if (!itemsCache.containsKey(stack)) {
                    for (ItemStack item : itemsCache.keySet()) {
                        if (StackInfo.equalItemAndNBT(stack, item, true)) {
                            itemsCache.put(stack, itemsCache.get(item));
                            break;
                        }
                    }
                }

                if (!itemsCache.containsKey(stack)) {
                    itemsCache.put(stack, itemsCache.size());
                }

                key = key + ":" + itemsCache.get(stack);

                if (meta.recipeId != null) {
                    if (!recipeCache.containsKey(meta.recipeId)) {
                        for (BookmarkRecipeId item : recipeCache.keySet()) {
                            if (item.equals(meta.recipeId)) {
                                recipeCache.put(meta.recipeId, recipeCache.get(item));
                                break;
                            }
                        }
                    }

                    if (!recipeCache.containsKey(meta.recipeId)) {
                        recipeCache.put(meta.recipeId, recipeCache.size());
                    }

                    key = key + ":" + recipeCache.get(meta.recipeId);
                }

                if (unique.contains(key)) {
                    this.realItems.remove(index);
                    this.metadata.remove(index);
                } else {
                    unique.add(key);
                    index++;
                }
            }

        }

        private void sortGroup(int groupId) {
            final int dir = getViewMode(groupId) == BookmarkViewMode.TODO_LIST ? 1 : -1;
            final int size = this.metadata.size();
            ItemStackMetadata meta;
            int idx = 0;

            while (idx < size) {
                meta = this.metadata.get(idx);
                if (meta.groupId == groupId && meta.recipeId != null) {
                    final HashMap<Integer, Integer> sortingRank = new HashMap<>();
                    final ArrayList<ItemStackMetadata> sortedMetadata = new ArrayList<>();
                    final ArrayList<ItemStack> sortedItems = new ArrayList<>();
                    final ArrayList<Integer> items = new ArrayList<>();

                    for (int index = idx; index < size; index++) {
                        if (this.metadata.get(index).groupId == groupId
                                && meta.recipeId.equals(this.metadata.get(index).recipeId)) {
                            sortingRank.put(index, (this.metadata.get(index).ingredient ? 1 : -1) * dir);
                            items.add(index);
                        }
                    }

                    items.sort((a, b) -> sortingRank.get(a) - sortingRank.get(b));

                    for (int index : items) {
                        sortedItems.add(this.realItems.get(index));
                        sortedMetadata.add(this.metadata.get(index));
                    }

                    this.realItems.removeAll(sortedItems);
                    this.metadata.removeAll(sortedMetadata);

                    this.realItems.addAll(idx, sortedItems);
                    this.metadata.addAll(idx, sortedMetadata);

                    idx += sortedItems.size();
                } else {
                    idx++;
                }
            }

        }

        private void sortIngredients() {
            if (this.size() == 0) {
                return;
            }

            removeDuplicateItems();
            boolean inEditingState = LayoutManager.bookmarkPanel.inEditingState();

            for (int groupId : this.groups.keySet()) {
                BookmarkGroup group = this.groups.get(groupId);

                if (group.viewMode == BookmarkViewMode.TODO_LIST) {
                    sortGroup(groupId);
                }

                if (group.crafting != null && !inEditingState) {
                    ArrayList<ItemStackMetadata> groupMetadata = new ArrayList<>();
                    ArrayList<ItemStack> groupItems = new ArrayList<>();

                    for (int idx = 0; idx < this.metadata.size(); idx++) {
                        if (this.metadata.get(idx).groupId == groupId) {
                            groupItems.add(this.realItems.get(idx));
                            groupMetadata.add(this.metadata.get(idx));
                        }
                    }

                    group.crafting.refresh(groupItems, groupMetadata);
                }

            }
        }

        protected void onItemsChanged() {
            sortIngredients();
            onGridChanged();
        }

        protected void createGroup(GroupingItem groupingItem) {
            final List<Integer> mask = getMask();
            final int topRowIndex = groupingItem.getTopRowIndex(this);
            final int bottomRowIndex = groupingItem.getBottomRowIndex(this);
            final int groupIdA = getRowGroupId(groupingItem.rowIndexA);
            final int startMaskIndex = topRowIndex * columns;
            final int endMaskIntex = Math.min((bottomRowIndex + 1) * columns, mask.size());
            int startItemIndex = -1;
            int endItemIndex = -1;
            int groupId = DEFAULT_GROUP_ID;

            if (!groupingItem.ungroup) {
                if (groupIdA == DEFAULT_GROUP_ID) {
                    for (ItemStackMetadata meta : this.metadata) {
                        if (meta.groupId > groupId) {
                            groupId = meta.groupId;
                        }
                    }
                    this.groups.put(++groupId, new BookmarkGroup(getViewMode(DEFAULT_GROUP_ID)));
                } else {
                    groupId = groupIdA;
                }
            }

            for (int index = startMaskIndex; index < endMaskIntex; index++) {
                if (mask.get(index) != null) {
                    startItemIndex = startItemIndex == -1 ? mask.get(index) : startItemIndex;
                    this.metadata.get(mask.get(index)).groupId = groupId;
                    endItemIndex = mask.get(index);
                }
            }

            if (startItemIndex != -1 && endItemIndex != -1
                    && groupIdA != DEFAULT_GROUP_ID
                    && !groupingItem.ungroup
                    && groupingItem.rowIndexA != groupingItem.rowIndexB) {
                if (groupingItem.rowIndexB == topRowIndex) {
                    for (int idx = startItemIndex - 1; idx >= 0 && this.metadata.get(idx).groupId == groupIdA; idx--) {
                        this.metadata.get(idx).groupId = DEFAULT_GROUP_ID;
                    }
                } else {
                    for (int idx = endItemIndex + 1; idx < this.metadata.size()
                            && this.metadata.get(idx).groupId == groupIdA; idx++) {
                        this.metadata.get(idx).groupId = DEFAULT_GROUP_ID;
                    }
                }
            }

            final HashSet<Integer> usedSetIds = new HashSet<>();
            for (ItemStackMetadata meta : this.metadata) {
                usedSetIds.add(meta.groupId);
            }

            this.groups.keySet().removeIf((Integer k) -> k != DEFAULT_GROUP_ID && !usedSetIds.contains(k));

            onItemsChanged();
        }

        protected void removeGroup(int groupId) {

            for (int i = this.metadata.size() - 1; i >= 0; i--) {
                if (this.metadata.get(i).groupId == groupId) {
                    this.metadata.remove(i);
                    this.realItems.remove(i);
                }
            }

            if (groupId != DEFAULT_GROUP_ID) {
                this.groups.remove(groupId);
            }

            onItemsChanged();
        }

        public int indexOf(ItemStack stackA, BookmarkRecipeId recipeId) {
            return indexOf(stackA, recipeId, DEFAULT_GROUP_ID);
        }

        public int indexOf(ItemStack stackA, BookmarkRecipeId recipeId, int groupId) {

            for (int idx = 0; idx < realItems.size(); idx++) {
                final ItemStackMetadata meta = getMetadata(idx);
                if (meta.groupId == groupId
                        && (recipeId == null && meta.recipeId == null
                                || recipeId != null && meta.recipeId != null && recipeId.equals(meta.recipeId))
                        && StackInfo.equalItemAndNBT(stackA, getItem(idx), true)) {
                    return idx;
                }
            }

            return -1;
        }

        public ItemStack getItem(int idx) {
            final ItemStack stack = super.realItems.get(idx);
            final ItemStackMetadata meta = this.getMetadata(idx);
            final BookmarkCraftingChain crafting = this.groups.get(meta.groupId).crafting;

            if (crafting != null && crafting.calculatedItems.containsKey(stack)) {
                return crafting.calculatedItems.get(stack);
            }

            return stack;
        }

        public ItemStackMetadata getMetadata(int idx) {
            return metadata.get(idx);
        }

        public void addItem(ItemStack stackA, ItemStackMetadata meta) {
            addItem(stackA, meta, true);
        }

        public void addItem(ItemStack stackA, ItemStackMetadata meta, boolean animate) {
            this.realItems.add(stackA);
            this.metadata.add(meta);

            if (animate && !NEIClientConfig.shouldCacheItemRendering() && NEIClientConfig.areBookmarksAnimated()) {
                this.animation.put(stackA, 0f);
            }

            onItemsChanged();
        }

        protected void removeRecipe(int idx, boolean removeFullRecipe) {
            final ItemStackMetadata meta = getMetadata(idx);

            if (meta.recipeId != null && (removeFullRecipe || !meta.ingredient)) {
                removeRecipe(meta.recipeId, meta.groupId);
            } else {
                removeItem(idx);
            }
        }

        protected void removeRecipe(BookmarkRecipeId recipeIdA, int groupId) {
            BookmarkRecipeId recipeIdB;

            for (int slotIndex = metadata.size() - 1; slotIndex >= 0; slotIndex--) {
                recipeIdB = metadata.get(slotIndex).recipeId;

                if (recipeIdB != null && metadata.get(slotIndex).groupId == groupId && recipeIdB.equals(recipeIdA)) {
                    removeItem(slotIndex);
                }
            }
        }

        protected boolean removeItem(int idx) {
            realItems.remove(idx);
            metadata.remove(idx);
            onItemsChanged();
            return true;
        }

        public BookmarkRecipeId getRecipeId(int idx) {
            return getMetadata(idx).recipeId;
        }

        protected void moveItem(SortableItem sortableItem, int slotIndex) {
            moveItem(
                    sortableItem,
                    slotIndex,
                    slotIndex < 0 ? DEFAULT_GROUP_ID : this.metadata.get(slotIndex).groupId,
                    slotIndex < this.realItems.indexOf(sortableItem.items.get(0)));
        }

        protected void moveItem(SortableItem sortableItem, int slotIndex, int groupId, boolean moveUp) {
            if (slotIndex == -1) return;

            if (sortableItem.items.indexOf(this.realItems.get(slotIndex)) == -1) {
                final ItemStack stackA = this.realItems.get(slotIndex);

                this.realItems.removeAll(sortableItem.items);
                this.metadata.removeAll(sortableItem.metadata);

                slotIndex = this.realItems.indexOf(stackA) + (moveUp ? 0 : 1);

                for (ItemStackMetadata sm : sortableItem.metadata) {
                    sm.groupId = groupId;
                }

                this.realItems.addAll(slotIndex, sortableItem.items);
                this.metadata.addAll(slotIndex, sortableItem.metadata);

                onItemsChanged();
            } else if (sortableItem.metadata.get(0).groupId != groupId) {

                for (ItemStackMetadata meta : sortableItem.metadata) {
                    meta.groupId = groupId;
                }

                onItemsChanged();
            }

        }

        protected boolean shouldCacheItemRendering() {
            return NEIClientConfig.shouldCacheItemRendering() && this.focusedGroupId == -1;
        }

        @Override
        protected void beforeDrawSlot(@Nullable ItemPanelSlot focus, int idx, Rectangle4i rect) {

            if (LayoutManager.bookmarkPanel.sortableItem != null || !NEIClientUtils.shiftKey()) {
                super.beforeDrawSlot(focus, idx, rect);
            } else if (NEIClientUtils.shiftKey() && !LayoutManager.bookmarkPanel.inEditingState()) {
                ItemStack stack = this.realItems.get(idx);
                ItemStackMetadata meta = this.getMetadata(idx);
                BookmarkGroup groupMeta = this.groups.get(meta.groupId);

                if (groupMeta.crafting != null && meta.groupId == this.focusedGroupId) {

                    if (groupMeta.crafting.inputs.containsKey(stack)) {
                        drawRect(rect.x, rect.y, rect.w, rect.h, 0x6645DA75); // inputs
                    } else if (groupMeta.crafting.outputs.containsKey(stack)) {
                        drawRect(rect.x, rect.y, rect.w, rect.h, 0x9966CCFF); // exports
                    }

                } else if (focus != null && meta.recipeId != null
                        && meta.groupId == getMetadata(focus.slotIndex).groupId
                        && meta.recipeId.equals(this.getRecipeId(focus.slotIndex))) {
                            drawRect(rect.x, rect.y, rect.w, rect.h, meta.ingredient ? 0x6645DA75 : 0x9966CCFF); // highlight
                                                                                                                 // recipe
                        } else {
                            super.beforeDrawSlot(focus, idx, rect);
                        }

            }

        }

        @Override
        protected void afterDrawSlot(@Nullable ItemPanelSlot focus, int idx, Rectangle4i rect) {
            final ItemStackMetadata meta = this.getMetadata(idx);

            if (meta.ingredient == true || meta.recipeId == null || LayoutManager.bookmarkPanel.sortableItem != null) {
                return;
            }

            final ItemStack stack = this.realItems.get(idx);
            final BookmarkGroup groupMeta = this.groups.get(meta.groupId);
            int multiplier = 0;

            if (NEIClientUtils.shiftKey()) {
                final ItemStackMetadata prevMeta = idx > 0 ? this.getMetadata(idx - 1) : null;
                if (prevMeta == null || prevMeta.ingredient || !meta.recipeId.equals(prevMeta.recipeId)) {
                    if (groupMeta.crafting != null && meta.groupId == this.focusedGroupId
                            && groupMeta.crafting.multiplier.containsKey(stack)) {
                        multiplier = this.groups.get(meta.groupId).crafting.multiplier.get(stack);
                    } else if (focus != null && meta.factor > 0
                            && meta.groupId == this.metadata.get(focus.slotIndex).groupId
                            && meta.recipeId.equals(this.metadata.get(focus.slotIndex).recipeId)) {
                                multiplier = StackInfo.itemStackToNBT(stack).getInteger("Count") / meta.factor;
                            }
                }
            }

            if (multiplier > 0) {
                drawRecipeMarker(
                        rect.x,
                        rect.y,
                        GuiContainerManager.getFontRenderer(stack),
                        "x" + ReadableNumberConverter.INSTANCE.toWideReadableForm(multiplier),
                        16777215);
            } else if (meta.recipeId != null && !meta.ingredient && NEIClientConfig.showRecipeMarker()) {
                drawRecipeMarker(rect.x, rect.y, GuiContainerManager.getFontRenderer(stack), "R", 0xA0A0A0);
            }

        }

        @Override
        protected void drawItem(Rectangle4i rect, int idx) {

            if (LayoutManager.bookmarkPanel.sortableItem == null
                    || LayoutManager.bookmarkPanel.sortableItem.items.get(0) != this.realItems.get(idx)) {
                ItemStack realStack = this.realItems.get(idx);
                final ItemStackMetadata meta = this.getMetadata(idx);
                final BookmarkGroup groupMeta = this.groups.get(meta.groupId);
                ItemStack drawStack = realStack;

                if (groupMeta.crafting != null && meta.groupId == this.focusedGroupId) {

                    if (groupMeta.crafting.inputs.containsKey(drawStack)) {
                        drawStack = groupMeta.crafting.inputs.get(drawStack);
                    } else if (groupMeta.crafting.outputs.containsKey(drawStack)) {
                        drawStack = groupMeta.crafting.outputs.get(drawStack);
                    } else if (groupMeta.crafting.intermediate.containsKey(drawStack)) {
                        drawStack = groupMeta.crafting.intermediate.get(drawStack);
                    }

                } else if (groupMeta.crafting != null && groupMeta.crafting.calculatedItems.containsKey(drawStack)) {
                    drawStack = groupMeta.crafting.calculatedItems.get(drawStack);
                }

                String stackSize = meta.fluidDisplay || drawStack.stackSize == 0 ? ""
                        : ReadableNumberConverter.INSTANCE.toWideReadableForm(drawStack.stackSize);

                if (this.animation.containsKey(realStack) && this.animation.get(realStack) < 1) {
                    final float currentScale = this.animation.get(realStack) + SCALE_SPEED;

                    if (currentScale >= 1) {
                        this.animation.remove(realStack);
                    } else {
                        this.animation.put(realStack, currentScale);
                    }

                    drawPoppingItem(rect, drawStack, stackSize, currentScale);
                } else {
                    GuiContainerManager.drawItem(rect.x + 1, rect.y + 1, drawStack, true, stackSize);
                }

            }

        }

        protected void drawPoppingItem(Rectangle4i rect, ItemStack stack, String stackSize, float currentScale) {
            GL11.glScalef(currentScale, currentScale, currentScale); // push & pop matrix crashes the game

            GuiContainerManager.drawItem(
                    Math.round((rect.x + 1 + (SLOT_SIZE / 2 - currentScale * SLOT_SIZE / 2)) / currentScale),
                    Math.round((rect.y + 1 + (SLOT_SIZE / 2 - currentScale * SLOT_SIZE / 2)) / currentScale),
                    stack,
                    true,
                    stackSize);

            GL11.glScalef(1 / currentScale, 1 / currentScale, 1 / currentScale);
        }

        protected void drawRecipeMarker(int offsetX, int offsetY, FontRenderer fontRenderer, String text, int color) {
            final float scaleFactor = fontRenderer.getUnicodeFlag() ? 0.85f : 0.5f;
            final float inverseScaleFactor = 1.0f / scaleFactor;

            GuiContainerManager.enable2DRender();
            GL11.glScaled(scaleFactor, scaleFactor, scaleFactor);

            final int X = (int) (((float) offsetX + 1.0f) * inverseScaleFactor);
            final int Y = (int) (((float) offsetY + 1.0f) * inverseScaleFactor);
            fontRenderer.drawStringWithShadow(text, X, Y, color);

            GL11.glScaled(inverseScaleFactor, inverseScaleFactor, inverseScaleFactor);
            GuiContainerManager.enable3DRender();
        }

        public void update() {

            if (this.recipeTooltipRenderer != null) {

                if (this.recipeTooltipRenderer.createRecipeGui != null) {
                    this.recipeTooltipRenderer.createRecipeGui.run();
                    this.recipeTooltipRenderer.createRecipeGui = null;
                }

                if (this.recipeTooltipRenderer.gui != null) {
                    this.recipeTooltipRenderer.gui.updateAsTooltip();
                }

            }

        }

        public void postDrawTooltips(int mousex, int mousey, List<String> tooltip) {
            if (NEIClientConfig.getRecipeTooltipsMode() != 0) {
                try {
                    drawRecipeTooltip(mousex, mousey, tooltip);
                } catch (Exception e) {
                    NEIClientConfig.logger.warn("Cannot draw recipe tooltip", e);
                }
            }
        }

        private void drawRecipeTooltip(int mousex, int mousey, List<String> itemTooltip) {
            if (!NEIClientConfig.isLoaded()) {
                return;
            }

            ItemPanelSlot focused = getSlotMouseOver(mousex, mousey);

            if (focused == null) {
                this.recipeTooltipRenderer = null;
                return;
            }

            final ItemStackMetadata meta = this.metadata.get(focused.slotIndex);
            final int tooltipMode = NEIClientConfig.getRecipeTooltipsMode();

            if (meta.recipeId == null || meta.ingredient) {
                this.recipeTooltipRenderer = null;
                return;
            }

            if (this.groups.get(meta.groupId).viewMode == BookmarkViewMode.DEFAULT && tooltipMode != 1
                    && tooltipMode != 3) {
                this.recipeTooltipRenderer = null;
                return;
            }

            if (this.groups.get(meta.groupId).viewMode == BookmarkViewMode.TODO_LIST && tooltipMode != 2
                    && tooltipMode != 3) {
                this.recipeTooltipRenderer = null;
                return;
            }

            if (this.recipeTooltipRenderer == null || this.recipeTooltipRenderer.recipeId != meta.recipeId) {
                this.recipeTooltipRenderer = new RecipeTooltipRenderer();
                this.recipeTooltipRenderer.stack = this.realItems.get(focused.slotIndex);
                this.recipeTooltipRenderer.recipeId = meta.recipeId;

                this.recipeTooltipRenderer.createRecipeGui = () -> {
                    GuiRecipe<?> gui = GuiCraftingRecipe.createRecipeGui(
                            "recipeId",
                            false,
                            this.recipeTooltipRenderer.stack,
                            this.recipeTooltipRenderer.recipeId);

                    if (gui != null) {
                        gui.initGui();
                        gui.guiTop = 0;
                        gui.guiLeft = 0;
                    }

                    this.recipeTooltipRenderer.gui = gui;
                };
            }

            if (this.recipeTooltipRenderer.gui != null) {
                final Minecraft mc = Minecraft.getMinecraft();
                int recipeTooltipLines = Math.max(1, itemTooltip.size());

                GL11.glPushMatrix();
                final float tooltipYOffset;
                if (mousey - marginTop > height / 2) {
                    tooltipYOffset = mousey - this.recipeTooltipRenderer.gui.getHeightAsWidget() + 8;
                } else {
                    tooltipYOffset = mousey + ((recipeTooltipLines < 2) ? 1 : 3 + ((recipeTooltipLines - 1) * 10));
                }
                GL11.glTranslatef(mousex, tooltipYOffset, 500);
                GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                RenderHelper.disableStandardItemLighting();
                this.recipeTooltipRenderer.gui.drawGuiContainerBackgroundLayer(0.0f, -100, -100);
                GL11.glPopAttrib();

                if (this.recipeTooltipRenderer.gui.slotcontainer != null) {
                    GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                    RenderHelper.enableGUIStandardItemLighting();
                    GL11.glEnable(GL12.GL_RESCALE_NORMAL);
                    @SuppressWarnings("unchecked")
                    List<Slot> slots = (List<Slot>) this.recipeTooltipRenderer.gui.slotcontainer.inventorySlots;

                    for (Slot slot : slots) {
                        if (slot != null && slot.getStack() != null) {
                            GuiContainerManager.drawItem(slot.xDisplayPosition, slot.yDisplayPosition, slot.getStack());
                        }
                    }

                    GL11.glPopAttrib();
                }
                this.recipeTooltipRenderer.gui.drawGuiContainerForegroundLayer(-100, -100);
                for (GuiButton btn : this.recipeTooltipRenderer.gui.getOverlayButtons()) {
                    btn.drawButton(mc, -100, -100);
                }
                GL11.glPopMatrix();
            }

        }

    }

    public BookmarkPanel() {
        grid = new BookmarkGrid();
    }

    @Override
    public void init() {
        super.init();

        namespaceLabel = new Label("1", true);

        namespacePrev = new Button("Prev") {

            public boolean onButtonPress(boolean rightclick) {

                if (inEditingState() || rightclick) {
                    return false;
                }

                return prevNamespace();
            }

            @Override
            public String getRenderLabel() {
                return "<";
            }
        };

        namespaceNext = new Button("Next") {

            public boolean onButtonPress(boolean rightclick) {
                if (inEditingState() || rightclick) {
                    return false;
                }

                return nextNamespace();
            }

            @Override
            public String getRenderLabel() {
                return ">";
            }
        };

        pullBookmarkedItems = new Button("Pull") {

            public boolean onButtonPress(boolean rightclick) {
                if (rightclick) {
                    return false;
                }
                return pullBookmarkItems(-1, false);
            }

            @Override
            public String getRenderLabel() {
                return "P";
            }
        };

        namespaces.add(new BookmarkGrid());
        setNamespace(activeNamespaceIndex);
    }

    @Override
    public String getLabelText() {

        if (((BookmarkGrid) grid).getCraftingMode(BookmarkGrid.DEFAULT_GROUP_ID)) {
            return String.format("§2[§r%d/%d§2]§r", getPage(), Math.max(1, getNumPages()));
        }

        return String.format("%d/%d", getPage(), Math.max(1, getNumPages()));
    }

    public boolean inEditingState() {
        return this.sortableItem != null || this.draggedStack != null
                || this.groupingItem != null && this.groupingItem.rowIndexB != -1;
    }

    public void addItem(ItemStack itemStack) {
        addItem(itemStack, true);
    }

    public void addItem(ItemStack itemStack, boolean saveStackSize) {
        final BookmarkGrid BGrid = (BookmarkGrid) grid;
        int idx = BGrid.indexOf(itemStack, true);

        if (idx != -1) {
            BGrid.removeItem(idx);
        }

        addOrRemoveItem(itemStack, null, null, false, saveStackSize);
    }

    public void addOrRemoveItem(ItemStack stackA) {
        addOrRemoveItem(stackA, null, null, false, false);
    }

    public void addOrRemoveItem(ItemStack stackover, final String handlerName, final List<PositionedStack> ingredients,
            boolean saveIngredients, boolean saveStackSize) {
        loadBookmarksIfNeeded();

        final Point mousePos = getMousePosition();
        final ItemPanelSlot slot = getSlotMouseOver(mousePos.x, mousePos.y);
        final BookmarkGrid BGrid = (BookmarkGrid) grid;

        if (slot != null && StackInfo.equalItemAndNBT(slot.item, stackover, true)) {
            BGrid.removeRecipe(slot.slotIndex, saveIngredients);
        } else {
            final NBTTagCompound nbTagA = StackInfo.itemStackToNBT(stackover, saveStackSize);
            final ItemStack normalizedA = StackInfo.loadFromNBT(nbTagA, saveStackSize ? nbTagA.getInteger("Count") : 0);
            BookmarkRecipeId recipeId = null;

            if (handlerName != "" && ingredients != null) {
                recipeId = new BookmarkRecipeId(handlerName, ingredients);
            }

            final int idx = BGrid.indexOf(normalizedA, recipeId);

            if (idx != -1) {
                BGrid.removeRecipe(idx, saveIngredients);
            } else {

                if (saveIngredients && handlerName != "" && ingredients != null) {
                    final Map<String, Integer> ingredientCount = new HashMap<>();
                    final Map<String, NBTTagCompound> uniqueIngredients = new LinkedHashMap<>();

                    BGrid.removeRecipe(recipeId, BookmarkGrid.DEFAULT_GROUP_ID);

                    for (PositionedStack stack : ingredients) {
                        final NBTTagCompound nbTag = StackInfo.itemStackToNBT(stack.item, saveStackSize);
                        final String GUID = StackInfo.getItemStackGUID(stack.item);

                        if (!uniqueIngredients.containsKey(GUID)) {
                            ingredientCount.put(GUID, nbTag.getInteger("Count"));
                            uniqueIngredients.put(GUID, nbTag);
                        } else {
                            ingredientCount.put(GUID, ingredientCount.get(GUID) + nbTag.getInteger("Count"));
                        }

                    }

                    for (String GUID : uniqueIngredients.keySet()) {
                        BGrid.addItem(
                                StackInfo.loadFromNBT(
                                        uniqueIngredients.get(GUID),
                                        saveStackSize ? ingredientCount.get(GUID) : 0),
                                new ItemStackMetadata(
                                        recipeId != null ? recipeId.copy() : null,
                                        ingredientCount.get(GUID),
                                        true,
                                        BookmarkGrid.DEFAULT_GROUP_ID,
                                        uniqueIngredients.get(GUID).hasKey("gtFluidName")));
                    }
                }

                BGrid.addItem(
                        normalizedA,
                        new ItemStackMetadata(
                                recipeId,
                                nbTagA.getInteger("Count"),
                                false,
                                BookmarkGrid.DEFAULT_GROUP_ID,
                                nbTagA.hasKey("gtFluidName")));
            }
        }

        fixCountOfNamespaces();
        saveBookmarks();
    }

    public BookmarkRecipeId getBookmarkRecipeId(int slotIndex) {
        ItemStackMetadata meta = ((BookmarkGrid) grid).getMetadata(slotIndex);
        return meta.ingredient ? null : meta.recipeId;
    }

    public BookmarkRecipeId getBookmarkRecipeId(ItemStack stackA) {
        final BookmarkGrid BGrid = (BookmarkGrid) grid;
        BookmarkRecipeId recipeId = null;

        for (int idx = 0; idx < BGrid.realItems.size(); idx++) {
            if (StackInfo.equalItemAndNBT(stackA, BGrid.realItems.get(idx), true)) {
                if ((recipeId = getBookmarkRecipeId(idx)) != null) {
                    return recipeId;
                }
            }
        }

        return null;
    }

    public int getHoveredGroupId(boolean groupPanel) {
        final BookmarkGrid BGrid = (BookmarkGrid) grid;
        final int overRowIndex = BGrid.getHoveredRowIndex(groupPanel);
        return overRowIndex >= 0 ? BGrid.getRowGroupId(overRowIndex) : -1;
    }

    public void removeGroup(int groupId) {
        final BookmarkGrid BGrid = (BookmarkGrid) grid;

        if (groupId >= 0 || groupId < BGrid.groups.size()) {
            BGrid.removeGroup(groupId);
            saveBookmarks();
        }
    }

    protected String getNamespaceLabelText(boolean shortFormat) {
        String activePage = String.valueOf(activeNamespaceIndex + 1);

        return shortFormat ? activePage : (activePage + "/" + fixCountOfNamespaces());
    }

    protected int fixCountOfNamespaces() {

        if (namespaces.get(getNameSpaceSize() - 1).size() > 0) {
            namespaces.add(new BookmarkGrid());
        } else if (activeNamespaceIndex == getNameSpaceSize() - 2 && grid.size() == 0) {
            namespaces.remove(getNameSpaceSize() - 1);
        }

        return getNameSpaceSize();
    }

    protected boolean removeEmptyNamespaces() {

        if (activeNamespaceIndex != getNameSpaceSize() - 1 && grid.size() == 0) {
            namespaces.remove(activeNamespaceIndex);
            setNamespace(activeNamespaceIndex);
            return true;
        }

        return false;
    }

    protected boolean prevNamespace() {
        if (bookmarksState != BookmarkLoadingState.LOADED) {
            return false;
        }

        fixCountOfNamespaces();
        removeEmptyNamespaces();

        if (activeNamespaceIndex == 0) {
            setNamespace(getNameSpaceSize() - 1);
        } else {
            setNamespace(activeNamespaceIndex - 1);
        }

        return true;
    }

    protected boolean nextNamespace() {
        if (bookmarksState != BookmarkLoadingState.LOADED) {
            return false;
        }

        if (removeEmptyNamespaces()) {
            return true;
        }

        if (activeNamespaceIndex == fixCountOfNamespaces() - 1) {
            setNamespace(0);
        } else {
            setNamespace(activeNamespaceIndex + 1);
        }

        return true;
    }

    protected void setNamespace(int namespaceIndex) {
        activeNamespaceIndex = namespaceIndex;
        grid = namespaces.get(activeNamespaceIndex);

        if (grid.size() == 0 && activeNamespaceIndex > 0) {
            ((BookmarkGrid) grid).setViewMode(
                    BookmarkGrid.DEFAULT_GROUP_ID,
                    namespaces.get(activeNamespaceIndex - 1).getViewMode(BookmarkGrid.DEFAULT_GROUP_ID));
        }

    }

    public int getNameSpaceSize() {
        return namespaces.size();
    }

    public void setBookmarkFile(String worldPath) {

        final File dir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/" + worldPath);

        if (!dir.exists()) {
            dir.mkdirs();
        }

        if (bookmarksState == BookmarkLoadingState.LOADED) {
            saveBookmarks();
        }

        bookmarkFile = new File(dir, "bookmarks.ini");

        if (!bookmarkFile.exists()) {
            final File globalBookmarks = new File(CommonUtils.getMinecraftDir(), "saves/NEI/global/bookmarks.ini");
            final File configBookmarks = new File(NEIClientConfig.configDir, "bookmarks.ini");
            final File defaultBookmarks = configBookmarks.exists() ? configBookmarks : globalBookmarks;

            if (defaultBookmarks.exists()) {

                try {
                    bookmarkFile.createNewFile();

                    InputStream src = new FileInputStream(defaultBookmarks);
                    OutputStream dst = new FileOutputStream(bookmarkFile);

                    IOUtils.copy(src, dst);

                    src.close();
                    dst.close();

                } catch (IOException e) {}
            }
        }

        bookmarksState = null;
    }

    public void saveBookmarks() {

        if (bookmarkFile == null || bookmarksState != BookmarkLoadingState.LOADED) {
            return;
        }

        ArrayList<String> strings = new ArrayList<>();

        for (int grpIdx = 0; grpIdx < getNameSpaceSize() - 1; grpIdx++) {
            final BookmarkGrid grid = namespaces.get(grpIdx);

            JsonObject settings = new JsonObject();
            JsonObject groups = new JsonObject();
            settings.add("active", new JsonPrimitive(activeNamespaceIndex == grpIdx));

            for (int groupId : grid.groups.keySet()) {
                BookmarkGroup group = grid.groups.get(groupId);
                JsonObject groupJson = new JsonObject();
                groupJson.add("viewmode", new JsonPrimitive(group.viewMode.toString()));
                groupJson.add("crafting", new JsonPrimitive(group.crafting != null));
                groups.add(String.valueOf(groupId), groupJson);
            }

            settings.add("groups", groups);
            strings.add("; " + NBTJson.toJson(settings));

            for (int idx = 0; idx < grid.size(); idx++) {

                try {
                    final NBTTagCompound nbTag = StackInfo.itemStackToNBT(grid.realItems.get(idx));

                    if (nbTag != null) {
                        JsonObject row = new JsonObject();
                        ItemStackMetadata meta = grid.metadata.get(idx);

                        row.add("item", NBTJson.toJsonObject(nbTag));
                        row.add("factor", new JsonPrimitive(meta.factor));
                        row.add("ingredient", new JsonPrimitive(meta.ingredient));

                        if (meta.groupId != BookmarkGrid.DEFAULT_GROUP_ID) {
                            row.add("groupId", new JsonPrimitive(meta.groupId));
                        }

                        if (meta.recipeId != null) {
                            row.add("recipeId", meta.recipeId.toJsonObject());
                        }

                        strings.add(NBTJson.toJson(row));
                    }

                } catch (JsonSyntaxException e) {
                    NEIClientConfig.logger.error("Failed to stringify bookmarked ItemStack to json string");
                }
            }
        }

        try (FileOutputStream output = new FileOutputStream(bookmarkFile)) {
            IOUtils.writeLines(strings, "\n", output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NEIClientConfig.logger.error("Filed to save bookmarks list to file {}", bookmarkFile, e);
        }
    }

    public void loadBookmarksIfNeeded() {

        if (bookmarksState != null || bookmarksState == BookmarkLoadingState.LOADING) {
            return;
        }

        bookmarksState = BookmarkLoadingState.LOADING;

        if (bookmarkFile == null || !bookmarkFile.exists()) {
            bookmarksState = BookmarkLoadingState.LOADED;
            return;
        }

        List<String> itemStrings;
        try (FileInputStream reader = new FileInputStream(bookmarkFile)) {
            NEIClientConfig.logger.info("Loading bookmarks from file {}", bookmarkFile);
            itemStrings = IOUtils.readLines(reader, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NEIClientConfig.logger.error("Failed to load bookmarks from file {}", bookmarkFile, e);
            return;
        }

        final JsonParser parser = new JsonParser();
        final List<BookmarkGrid> namespaces = new ArrayList<>();
        namespaces.add(new BookmarkGrid());
        BookmarkGrid grid = namespaces.get(0);
        int groupId = BookmarkGrid.DEFAULT_GROUP_ID;
        int namespaceIndex = 0;

        for (String itemStr : itemStrings) {

            try {

                if (itemStr.isEmpty()) {
                    itemStr = "; {}";
                }

                if (itemStr.startsWith("; ")) {
                    JsonObject settings = parser.parse(itemStr.substring(2)).getAsJsonObject();

                    if (grid.size() > 0) {
                        // do not create empty namespaces
                        grid = new BookmarkGrid();
                        namespaces.add(grid);
                    }

                    if (settings.get("viewmode") != null) {
                        grid.groups.get(BookmarkGrid.DEFAULT_GROUP_ID).viewMode = BookmarkViewMode
                                .valueOf(settings.get("viewmode").getAsString());
                    } else if (settings.get("groups") != null && settings.get("groups") instanceof JsonObject) {
                        final JsonObject jsonObject = (JsonObject) settings.get("groups");
                        BookmarkGroup group;
                        JsonObject value;

                        for (Map.Entry<String, JsonElement> jsonEntry : jsonObject.entrySet()) {
                            if (jsonEntry.getValue() instanceof JsonObject) {
                                value = (JsonObject) jsonEntry.getValue();
                                group = new BookmarkGroup(
                                        value.has("viewmode")
                                                ? BookmarkViewMode.valueOf(value.get("viewmode").getAsString())
                                                : BookmarkViewMode.DEFAULT,
                                        value.has("crafting") ? value.get("crafting").getAsBoolean() : false);
                                grid.groups.put(Integer.valueOf(jsonEntry.getKey()), group);
                            }
                        }
                    }

                    if (settings.get("active") != null && settings.get("active").getAsBoolean()) {
                        namespaceIndex = namespaces.size() - 1;
                    }

                    continue;
                }

                JsonObject jsonObject = parser.parse(itemStr).getAsJsonObject();
                BookmarkRecipeId recipeId = null;
                NBTTagCompound itemStackNBT;

                if (jsonObject.get("item") != null) {
                    itemStackNBT = (NBTTagCompound) NBTJson.toNbt(jsonObject.get("item"));
                } else { // old format
                    itemStackNBT = (NBTTagCompound) NBTJson.toNbt(jsonObject);
                }

                if (jsonObject.get("recipeId") != null && jsonObject.get("recipeId") instanceof JsonObject) {
                    recipeId = new BookmarkRecipeId((JsonObject) jsonObject.get("recipeId"));
                }

                ItemStack itemStack = StackInfo.loadFromNBT(itemStackNBT);

                if (itemStack != null) {
                    groupId = jsonObject.has("groupId") ? jsonObject.get("groupId").getAsInt()
                            : BookmarkGrid.DEFAULT_GROUP_ID;
                    grid.realItems.add(itemStack);
                    grid.metadata.add(
                            new ItemStackMetadata(
                                    recipeId,
                                    jsonObject.has("factor") ? Math.abs(jsonObject.get("factor").getAsInt())
                                            : (itemStackNBT.hasKey("gtFluidName") ? 144 : 1),
                                    jsonObject.has("ingredient") ? jsonObject.get("ingredient").getAsBoolean() : false,
                                    grid.groups.containsKey(groupId) ? groupId : BookmarkGrid.DEFAULT_GROUP_ID,
                                    itemStackNBT.hasKey("gtFluidName")));
                } else {
                    NEIClientConfig.logger.warn(
                            "Failed to load bookmarked ItemStack from json string, the item no longer exists:\n{}",
                            itemStr);
                }

            } catch (IllegalArgumentException | JsonSyntaxException | IllegalStateException e) {
                NEIClientConfig.logger.error("Failed to load bookmarked ItemStack from json string:\n{}", itemStr);
            }
        }

        for (BookmarkGrid gr : namespaces) {
            gr.onItemsChanged();
        }

        this.namespaces = namespaces;
        bookmarksState = BookmarkLoadingState.LOADED;
        setNamespace(namespaceIndex);
    }

    @Override
    public void resize(GuiContainer gui) {
        loadBookmarksIfNeeded();
        super.resize(gui);
    }

    @Override
    protected int resizeHeader(GuiContainer gui) {
        final LayoutStyleMinecraft layout = (LayoutStyleMinecraft) LayoutManager.getLayoutStyle();
        final int rows = (int) Math.ceil((double) layout.buttonCount / layout.numButtons);
        final int diff = rows * BookmarkGrid.SLOT_SIZE + getMarginTop(gui) - y;

        if (diff > 0) {
            y += diff;
            h -= diff;
        }

        return super.resizeHeader(gui);
    }

    @Override
    protected int resizeFooter(GuiContainer gui) {
        final int BUTTON_SIZE = 16;

        final ButtonCycled button = LayoutManager.bookmarksButton;
        final int leftBorder = y + h > button.y ? button.x + button.w + 2 : x;
        final int rightBorder = x + w;
        final int center = leftBorder + Math.max(0, (rightBorder - leftBorder) / 2);
        int labelWidth = 2;

        namespacePrev.h = namespaceNext.h = pullBookmarkedItems.h = BUTTON_SIZE;
        namespacePrev.w = namespaceNext.w = pullBookmarkedItems.w = BUTTON_SIZE;
        namespacePrev.y = namespaceNext.y = pullBookmarkedItems.y = y + h - BUTTON_SIZE;

        if (rightBorder - leftBorder >= 70) {
            labelWidth = 36;
            namespaceLabel.text = getNamespaceLabelText(false);
        } else {
            labelWidth = 18;
            namespaceLabel.text = getNamespaceLabelText(true);
        }

        namespaceLabel.y = namespacePrev.y + 5;
        namespaceLabel.x = center;

        namespacePrev.x = center - labelWidth / 2 - 2 - namespacePrev.w;
        namespaceNext.x = center + labelWidth / 2 + 2;
        pullBookmarkedItems.x = center + 2 * labelWidth / 2 + 2;

        return BUTTON_SIZE + 2;
    }

    @Override
    public void setVisible() {
        super.setVisible();

        if (grid.getPerPage() > 0) {
            LayoutManager.addWidget(namespacePrev);
            LayoutManager.addWidget(namespaceNext);
            LayoutManager.addWidget(namespaceLabel);
            if (BookmarkContainerInfo.getBookmarkContainerHandler(getGuiContainer()) != null) {
                LayoutManager.addWidget(pullBookmarkedItems);
            }
        }
    }

    protected String getPositioningSettingName() {
        return "world.panels.bookmarks";
    }

    public int getMarginLeft(GuiContainer gui) {
        return PADDING;
    }

    public int getMarginTop(GuiContainer gui) {
        return PADDING;
    }

    public int getWidth(GuiContainer gui) {
        return gui.width - (gui.xSize + gui.width) / 2 - PADDING * 2;
    }

    public int getHeight(GuiContainer gui) {
        return gui.height - getMarginTop(gui) - PADDING;
    }

    protected ItemStack getDraggedStackWithQuantity(int mouseDownSlot) {
        final ItemStack stack = grid.getItem(mouseDownSlot);

        if (stack == null) {
            return null;
        }

        final ItemStackMetadata meta = ((BookmarkGrid) grid).getMetadata(mouseDownSlot);
        int amount = stack.stackSize;

        if (amount == 0 && !meta.fluidDisplay) {
            amount = NEIClientConfig.showItemQuantityWidget() ? NEIClientConfig.getItemQuantity() : 0;

            if (amount == 0) {
                amount = stack.getMaxStackSize();
            }
        }

        return NEIServerUtils.copyStack(stack, amount);
    }

    @Override
    public void mouseDragged(int mousex, int mousey, int button, long heldTime) {

        if (button == 0 && NEIClientUtils.shiftKey() && mouseDownSlot >= 0) {
            ItemPanelSlot mouseOverSlot = getSlotMouseOver(mousex, mousey);

            if (this.sortableItem == null) {

                if (grid.getItem(mouseDownSlot) != null
                        && (mouseOverSlot == null || mouseOverSlot.slotIndex != mouseDownSlot || heldTime > 250)) {
                    final BookmarkGrid BGrid = (BookmarkGrid) grid;
                    final ItemStackMetadata meta = BGrid.getMetadata(mouseDownSlot);
                    final List<ItemStack> items = new ArrayList<>();
                    final List<ItemStackMetadata> metadata = new ArrayList<>();

                    if (meta.recipeId == null || meta.ingredient
                            || BGrid.getViewMode(meta.groupId) == BookmarkViewMode.DEFAULT) {
                        items.add(BGrid.realItems.get(this.mouseDownSlot));
                        metadata.add(BGrid.metadata.get(this.mouseDownSlot));
                    } else {

                        for (int i = 0; i < BGrid.metadata.size(); i++) {
                            if (BGrid.metadata.get(i).recipeId != null && meta.groupId == BGrid.metadata.get(i).groupId
                                    && meta.recipeId.equals(BGrid.metadata.get(i).recipeId)) {
                                items.add(BGrid.realItems.get(i));
                                metadata.add(BGrid.metadata.get(i));
                            }
                        }

                    }

                    this.sortableItem = new SortableItem(items, metadata);
                    grid.onGridChanged();
                }

            } else {
                final BookmarkGrid BGrid = (BookmarkGrid) grid;
                final ItemStackMetadata sortMeta = this.sortableItem.metadata.get(0);
                final BookmarkViewMode sortViewMode = BGrid.getViewMode(sortMeta.groupId);

                if (sortViewMode == BookmarkViewMode.TODO_LIST && !sortMeta.ingredient) {
                    mouseOverSlot = getSlotMouseOver(grid.marginLeft + grid.paddingLeft, mousey);

                    if (mouseOverSlot != null) {
                        float ySlot = (float) (mousey - BGrid.marginTop) / BookmarkGrid.SLOT_SIZE;
                        int lastRowIndex = BGrid.getLastRowIndex();
                        int overRowIndex = (int) ySlot;
                        int beforeGroupId = overRowIndex > 0 ? BGrid.getRowGroupId(overRowIndex - 1)
                                : BookmarkGrid.DEFAULT_GROUP_ID;
                        int afterGroupId = overRowIndex < lastRowIndex ? BGrid.getRowGroupId(overRowIndex + 1)
                                : BookmarkGrid.DEFAULT_GROUP_ID;
                        int overGroupId = BGrid.metadata.get(mouseOverSlot.slotIndex).groupId;
                        ySlot -= overRowIndex;

                        if (this.sortableItem.items.indexOf(BGrid.realItems.get(mouseOverSlot.slotIndex)) == -1
                                && overGroupId == sortMeta.groupId) {

                            if (mouseOverSlot.slotIndex < BGrid.realItems.indexOf(sortableItem.items.get(0))) {
                                BGrid.moveItem(this.sortableItem, mouseOverSlot.slotIndex, overGroupId, true);
                            } else {
                                BGrid.moveItem(
                                        this.sortableItem,
                                        BGrid.getRowItemIndex(overRowIndex, false),
                                        overGroupId,
                                        false);
                            }

                        } else if (ySlot <= 0.25) {

                            if (BGrid.getViewMode(beforeGroupId) == BookmarkViewMode.TODO_LIST
                                    && !existsRecipeIdInGroupId(beforeGroupId, sortMeta.recipeId)) {
                                BGrid.moveItem(this.sortableItem, mouseOverSlot.slotIndex, beforeGroupId, true);
                            }

                        } else if (ySlot > 0.25 && ySlot <= 0.5) {

                            if (beforeGroupId != afterGroupId && beforeGroupId != BookmarkGrid.DEFAULT_GROUP_ID
                                    && (afterGroupId != BookmarkGrid.DEFAULT_GROUP_ID
                                            || BGrid.getViewMode(overGroupId) == BookmarkViewMode.DEFAULT)
                                    && BGrid.getViewMode(BookmarkGrid.DEFAULT_GROUP_ID) == BookmarkViewMode.TODO_LIST
                                    && !existsRecipeIdInGroupId(BookmarkGrid.DEFAULT_GROUP_ID, sortMeta.recipeId)) {
                                beforeGroupId = BookmarkGrid.DEFAULT_GROUP_ID;
                            }

                            if (BGrid.getViewMode(beforeGroupId) == BookmarkViewMode.TODO_LIST
                                    && !existsRecipeIdInGroupId(beforeGroupId, sortMeta.recipeId)) {
                                BGrid.moveItem(this.sortableItem, mouseOverSlot.slotIndex, beforeGroupId, true);
                            }

                        } else if (ySlot > 0.5 && ySlot < 0.75) {

                            if (beforeGroupId != afterGroupId
                                    && (beforeGroupId != BookmarkGrid.DEFAULT_GROUP_ID
                                            || BGrid.getViewMode(overGroupId) == BookmarkViewMode.DEFAULT)
                                    && afterGroupId != BookmarkGrid.DEFAULT_GROUP_ID
                                    && BGrid.getViewMode(BookmarkGrid.DEFAULT_GROUP_ID) == BookmarkViewMode.TODO_LIST
                                    && !existsRecipeIdInGroupId(BookmarkGrid.DEFAULT_GROUP_ID, sortMeta.recipeId)) {
                                afterGroupId = BookmarkGrid.DEFAULT_GROUP_ID;
                            }

                            if (BGrid.getViewMode(afterGroupId) == BookmarkViewMode.TODO_LIST
                                    && !existsRecipeIdInGroupId(afterGroupId, sortMeta.recipeId)) {
                                BGrid.moveItem(
                                        this.sortableItem,
                                        BGrid.getRowItemIndex(overRowIndex, false),
                                        afterGroupId,
                                        false);
                            }

                        } else if (ySlot >= 0.75) {

                            if (BGrid.getViewMode(afterGroupId) == BookmarkViewMode.TODO_LIST
                                    && !existsRecipeIdInGroupId(afterGroupId, sortMeta.recipeId)) {
                                BGrid.moveItem(
                                        this.sortableItem,
                                        BGrid.getRowItemIndex(overRowIndex, false),
                                        afterGroupId,
                                        false);
                            }

                        }

                    }

                } else if (mouseOverSlot != null
                        && this.sortableItem.items.indexOf(BGrid.realItems.get(mouseOverSlot.slotIndex)) == -1) {
                            final ItemStackMetadata meta = BGrid.getMetadata(mouseOverSlot.slotIndex);

                            if (meta.groupId == sortMeta.groupId) {

                                if (sortViewMode == BookmarkViewMode.DEFAULT) {
                                    BGrid.moveItem(this.sortableItem, mouseOverSlot.slotIndex);
                                } else if (sortViewMode == BookmarkViewMode.TODO_LIST && meta.recipeId != null
                                        && meta.recipeId.equals(sortMeta.recipeId)) {
                                            BGrid.moveItem(this.sortableItem, mouseOverSlot.slotIndex);
                                        }

                            }

                        }

            }

            return;
        } else if (this.groupingItem != null) {
            final int overRowIndex = (mousey - grid.marginTop) / BookmarkGrid.SLOT_SIZE;

            if (this.groupingItem.rowIndexB != -1 || overRowIndex != this.groupingItem.rowIndexA || heldTime > 250) {
                this.groupingItem.rowIndexB = Math.max(0, Math.min(overRowIndex, grid.getLastRowIndex()));
            }

            return;
        }

        super.mouseDragged(mousex, mousey, button, heldTime);
    }

    private boolean existsRecipeIdInGroupId(int groupId, BookmarkRecipeId recipeId) {
        if (recipeId == null) return false;

        for (ItemStackMetadata meta : ((BookmarkGrid) grid).metadata) {
            if (meta.groupId == groupId && this.sortableItem.metadata.indexOf(meta) == -1
                    && recipeId.equals(meta.recipeId)) {
                return true;
            }
        }

        return false;
    }

    private int getNextSlot() {
        final List<Integer> mask = grid.getMask();
        final int columns = grid.getColumns();
        final int perPage = grid.getRows() * columns;
        final boolean line = ((BookmarkGrid) grid).getViewMode(BookmarkGrid.DEFAULT_GROUP_ID)
                == BookmarkViewMode.TODO_LIST;

        for (int i = mask.size(); i < perPage; i++) {
            if (!grid.isInvalidSlot(i) && (!line || (i % columns) == 0)) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public void postDraw(int mousex, int mousey) {

        if (this.sortableItem != null) {
            GuiContainerManager.drawItems.zLevel += 100;
            GuiContainerManager.drawItem(mousex - 8, mousey - 8, this.sortableItem.items.get(0).copy(), true);
            GuiContainerManager.drawItems.zLevel -= 100;
        }

        if (ItemPanels.itemPanel.draggedStack != null && this.contains(mousex, mousey)) {
            final int idx = getNextSlot();

            if (idx >= 0) {
                Rectangle4i rect = grid.getSlotRect(idx);
                drawRect(rect.x, rect.y, rect.w, rect.h, 0xee555555); // highlight
            }
        }

        super.postDraw(mousex, mousey);
    }

    @Override
    public boolean handleClickExt(int mousex, int mousey, int button) {

        if (button == 0 || button == 1) {
            final int overRowIndex = ((BookmarkGrid) grid).getHoveredRowIndex(true);

            if (overRowIndex != -1) {
                this.groupingItem = new GroupingItem(button == 1, overRowIndex);
                return true;
            }
        }

        if (new Rectangle4i(pagePrev.x + pagePrev.w, pagePrev.y, pageNext.x - (pagePrev.x + pagePrev.w), pagePrev.h)
                .contains(mousex, mousey)) {
            final BookmarkGrid BGrid = (BookmarkGrid) grid;

            if (button == 0) {
                BGrid.toggleViewMode(BookmarkGrid.DEFAULT_GROUP_ID);
            } else if (button == 1) {
                BGrid.toggleCraftingMode(BookmarkGrid.DEFAULT_GROUP_ID);
            }

            return true;
        } else {
            return super.handleClickExt(mousex, mousey, button);
        }
    }

    @Override
    public List<String> handleTooltip(int mx, int my, List<String> tooltip) {

        if (new Rectangle4i(pagePrev.x + pagePrev.w, pagePrev.y, pageNext.x - (pagePrev.x + pagePrev.w), pagePrev.h)
                .contains(mx, my)) {
            tooltip.add(translate("bookmark.viewmode.toggle.tip"));
        }
        if (new Rectangle4i(pullBookmarkedItems.x, pullBookmarkedItems.y, pullBookmarkedItems.w, pullBookmarkedItems.h)
                .contains(mx, my) && BookmarkContainerInfo.getBookmarkContainerHandler(getGuiContainer()) != null) {
            tooltip.add(translate("bookmark.pullBookmarkedItems.tip"));
        }

        return super.handleTooltip(mx, my, tooltip);
    }

    @Override
    public void mouseUp(int mousex, int mousey, int button) {

        if (this.sortableItem != null) {
            this.sortableItem = null;
            this.mouseDownSlot = -1;
            grid.onItemsChanged(); /* make sure grid redraws the new item */
            saveBookmarks();
        } else if (this.groupingItem != null) {
            final BookmarkGrid BGrid = (BookmarkGrid) grid;

            if (this.groupingItem.rowIndexB != -1) {
                BGrid.createGroup(this.groupingItem);
            } else {
                int groupId = BGrid.getRowGroupId(this.groupingItem.rowIndexA);

                if (groupId != BookmarkGrid.DEFAULT_GROUP_ID) {
                    if (button == 0) {
                        BGrid.toggleViewMode(groupId);
                    } else if (button == 1) {
                        BGrid.toggleCraftingMode(groupId);
                    }
                }

            }

            this.mouseDownSlot = -1;
            this.groupingItem = null;
            grid.onItemsChanged(); /* make sure grid redraws the new item */
            saveBookmarks();
        } else {
            super.mouseUp(mousex, mousey, button);
        }
    }

    @Override
    public boolean onMouseWheel(int shift, int mousex, int mousey) {

        if (!inEditingState() && new Rectangle4i(
                namespacePrev.x,
                namespacePrev.y,
                namespaceNext.x + namespaceNext.w - namespacePrev.x,
                namespacePrev.h).contains(mousex, mousey)) {

            if (shift > 0) {
                prevNamespace();
            } else {
                nextNamespace();
            }

            saveBookmarks();
            return true;
        }

        if (!contains(mousex, mousey)) {
            return false;
        }

        if (NEIClientUtils.controlKey()) {
            final ItemPanelSlot slot = getSlotMouseOver(mousex, mousey);

            if (slot != null) {
                final BookmarkGrid BGrid = (BookmarkGrid) grid;
                final ItemStackMetadata overMeta = BGrid.getMetadata(slot.slotIndex);
                final HashMap<Integer, ItemStack> items = new HashMap<>();
                int shiftMultiplier = 1;

                if (NEIClientUtils.altKey()) {
                    shiftMultiplier = NEIClientConfig.showItemQuantityWidget() ? NEIClientConfig.getItemQuantity() : 0;
                    if (shiftMultiplier == 0) {
                        shiftMultiplier = slot.item.getMaxStackSize();
                    }
                }

                if (NEIClientUtils.shiftKey()) {
                    ItemStackMetadata ingrMeta;

                    for (int slotIndex = grid.size() - 1; slotIndex >= 0; slotIndex--) {
                        ingrMeta = BGrid.getMetadata(slotIndex);

                        if (ingrMeta.recipeId != null && ingrMeta.groupId == overMeta.groupId
                                && ingrMeta.recipeId.equals(overMeta.recipeId)
                                && slotIndex != slot.slotIndex) {
                            items.put(slotIndex, shiftStackSize(BGrid, slotIndex, shift, shiftMultiplier));
                        }
                    }

                }

                items.put(slot.slotIndex, shiftStackSize(BGrid, slot.slotIndex, shift, shiftMultiplier));

                for (int slotIndex : items.keySet()) {
                    if (items.get(slotIndex) != null) {
                        BGrid.realItems.set(slotIndex, items.get(slotIndex));
                    }
                }

                BGrid.onItemsChanged();
                saveBookmarks();
                return true;
            }
        }

        return super.onMouseWheel(shift, mousex, mousey);
    }

    private ItemStack shiftStackSize(BookmarkGrid BGrid, int slotIndex, int shift, int shiftMultiplier) {
        final NBTTagCompound nbTag = StackInfo.itemStackToNBT(BGrid.getItem(slotIndex));
        final ItemStackMetadata meta = BGrid.getMetadata(slotIndex);

        if (meta.factor > 0) {
            final int multiplier = nbTag.getInteger("Count") / meta.factor;
            final long count = ((long) (multiplier + shift * shiftMultiplier) / shiftMultiplier) * shiftMultiplier
                    * meta.factor;

            if (count <= Integer.MAX_VALUE) {
                return StackInfo.loadFromNBT(nbTag, Math.max(count, 0));
            }
        }

        return null;
    }

    public boolean pullBookmarkItems(int groupId, boolean onlyIngredients) {
        IBookmarkContainerHandler containerHandler = BookmarkContainerInfo
                .getBookmarkContainerHandler(getGuiContainer());

        if (containerHandler == null) {
            return false;
        }

        final BookmarkGrid BGrid = (BookmarkGrid) grid;
        final ArrayList<ItemStack> items = new ArrayList<>();
        final ItemStackMap<Long> uniqueItems = new ItemStackMap<>();
        final BookmarkGroup group = groupId >= 0 ? BGrid.groups.get(groupId) : null;
        ItemStackMetadata meta;

        for (int idx = 0; idx < BGrid.realItems.size(); idx++) {
            meta = BGrid.metadata.get(idx);

            if (groupId == -1 || groupId == meta.groupId) {
                final ItemStack stack = BGrid.getItem(idx);

                if (!onlyIngredients || meta.ingredient && (group == null || group.crafting == null
                        || group.crafting.inputs.containsKey(BGrid.realItems.get(idx)))) {
                    uniqueItems.put(
                            stack,
                            uniqueItems.getOrDefault(stack, 0L) + StackInfo.itemStackToNBT(stack).getInteger("Count"));
                }

            }
        }

        for (ItemStackMap.Entry<Long> entry : uniqueItems.entries()) {
            if (entry.value > 0) {
                items.add(
                        StackInfo.loadFromNBT(
                                StackInfo.itemStackToNBT(entry.key),
                                Math.min(entry.value, 9 * 4 * entry.key.getMaxStackSize())));
            }
        }

        if (items.isEmpty()) {
            return false;
        }

        containerHandler.pullBookmarkItemsFromContainer(getGuiContainer(), items);
        return true;
    }
}
