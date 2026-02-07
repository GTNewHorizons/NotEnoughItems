package codechicken.nei;

import java.awt.Point;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.commons.io.IOUtils;

import codechicken.core.CommonUtils;
import codechicken.lib.gui.GuiDraw;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.api.IBookmarkContainerHandler;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkGroup;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.BookmarkItem.BookmarkItemType;
import codechicken.nei.bookmark.BookmarkStorage;
import codechicken.nei.bookmark.BookmarksGridSlot;
import codechicken.nei.bookmark.GroupingItem;
import codechicken.nei.bookmark.SortableGroup;
import codechicken.nei.bookmark.SortableItem;
import codechicken.nei.recipe.AcceptsFollowingTooltipLineHandler;
import codechicken.nei.recipe.BookmarkRecipeId;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.Recipe.RecipeIngredient;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.chain.RecipeChainMath;
import codechicken.nei.recipe.chain.RecipeChainTooltipLineHandler;
import codechicken.nei.util.NEIMouseUtils;

public class BookmarkPanel extends PanelWidget<BookmarkGrid> {

    @Deprecated
    public static class BookmarkRecipe {

        public String handlerName = "";
        public List<ItemStack> result = new ArrayList<>();
        public List<ItemStack> ingredients = new ArrayList<>();
        public BookmarkRecipeId recipeId = null;

        public BookmarkRecipe(ItemStack... result) {
            this.result.addAll(Arrays.asList(result));
        }

        public BookmarkRecipeId getRecipeId() {

            if (!handlerName.isEmpty() && !ingredients.isEmpty() && recipeId == null) {
                recipeId = new BookmarkRecipeId(handlerName, ingredients);
            }

            return recipeId;
        }

        public Recipe getRecipe() {
            String handlerName = this.handlerName;

            if (handlerName.isEmpty() && recipeId != null) {
                handlerName = recipeId.handlerName;
            }

            return Recipe.of(this.result, handlerName, this.ingredients);
        }
    }

    public enum BookmarkViewMode {
        DEFAULT,
        TODO_LIST
    }

    protected BookmarkStorage storage = new BookmarkStorage();

    public SortableItem sortableItem;
    public SortableGroup sortableGroup;
    public GroupingItem groupingItem;

    protected AcceptsFollowingTooltipLineHandler acceptsFollowingTooltipLineHandler;
    protected RecipeChainTooltipLineHandler recipeChainTooltipLineHandler;

    public Button namespacePrev;
    public Button namespaceNext;
    public Button pullBookmarkedItems;
    public Label namespaceLabel;

    public BookmarkPanel() {
        this.grid = new BookmarkGrid();
    }

    @Override
    public void update() {
        this.grid = this.storage.getActiveGrid();
    }

    @Override
    public void init() {
        super.init();

        namespaceLabel = new Label("1", true);

        namespacePrev = new Button("<") {

            public boolean onButtonPress(boolean rightclick) {

                if (inEditingState() || rightclick) {
                    return false;
                }

                return storage.prevNamespace();
            }
        };

        namespaceNext = new Button(">") {

            public boolean onButtonPress(boolean rightclick) {
                if (inEditingState() || rightclick) {
                    return false;
                }

                return storage.nextNamespace();
            }

        };

        pullBookmarkedItems = new Button("P") {

            public boolean onButtonPress(boolean rightclick) {
                if (rightclick) {
                    return false;
                }
                return pullBookmarkItems(BookmarkGrid.DEFAULT_GROUP_ID, NEIClientUtils.shiftKey());
            }

            @Override
            public String getButtonTip() {
                return NEIClientUtils.translate("bookmark.pullBookmarkedItems.tip");
            }
        };

    }

    @Override
    public BookmarksGridSlot getSlotMouseOver(int mousex, int mousey) {
        return this.grid.getSlotMouseOver(mousex, mousey);
    }

    @Override
    public String getLabelText() {

        if (this.grid.isCraftingMode(BookmarkGrid.DEFAULT_GROUP_ID)) {
            return String.format("§2[§r%d/%d§2]§r", getPage(), Math.max(1, getNumPages()));
        }

        return String.format("%d/%d", getPage(), Math.max(1, getNumPages()));
    }

    public boolean inEditingState() {
        return this.sortableItem != null || this.sortableGroup != null
                || this.draggedStack != null
                || this.groupingItem != null && this.groupingItem.hasEndRow();
    }

    public boolean addItem(ItemStack stack) {
        return addItem(stack, null, BookmarkGrid.DEFAULT_GROUP_ID);
    }

    public boolean addItem(ItemStack itemStack, RecipeId recipeId, int groupId) {

        if (itemStack != null && this.grid.indexOf(groupId, itemStack, recipeId, false) == -1) {
            final boolean existsRecipe = recipeId != null
                    && this.grid.existsRecipe(recipeId, BookmarkGrid.DEFAULT_GROUP_ID);
            final NBTTagCompound nbTag = StackInfo.itemStackToNBT(itemStack);
            long count = nbTag.getInteger("Count");
            long factor = existsRecipe ? Math.max(count, 1) : 1;

            if (existsRecipe) {
                for (int i = 0; i < this.grid.size(); i++) {
                    if (this.grid.getBookmarkItem(i).equalsRecipe(recipeId, groupId)) {
                        count *= this.grid.getBookmarkItem(i).getMultiplier();
                        break;
                    }
                }
            }

            this.grid.addItem(
                    BookmarkItem.of(
                            groupId,
                            StackInfo.withAmount(itemStack, count),
                            factor,
                            recipeId,
                            existsRecipe ? BookmarkItemType.RESULT : BookmarkItemType.ITEM),
                    true);
            return true;
        }

        return false;
    }

    public boolean addRecipe(Recipe recipe, int multiplier, int groupId) {
        final RecipeId recipeId = recipe.getRecipeId();

        if (recipe != null && this.grid.getGroup(groupId) != null && !this.grid.existsRecipe(recipeId, groupId)) {
            this.grid.addRecipe(recipe, multiplier, groupId);
            return true;
        }

        return false;
    }

    public boolean addGroup(List<?> items, BookmarkViewMode viewMode, boolean crafting) {
        if (items.isEmpty()) return false;

        if (viewMode == null) {
            viewMode = this.grid.getViewMode(BookmarkGrid.DEFAULT_GROUP_ID);
        }

        final int groupId = this.grid.addGroup(new BookmarkGroup(viewMode, crafting));
        final ItemStackSet uniqueStack = new ItemStackSet();
        final Set<RecipeId> uniqueRecipe = new HashSet<>();

        for (Object item : items) {

            if (item instanceof Recipe recipe) {

                if (uniqueRecipe.add(recipe.getRecipeId())) {
                    this.grid.addRecipe(recipe, 1, groupId);
                }

            } else if (item instanceof RecipeId recipeId) {
                ItemStack stack = recipeId.getResult();

                if (stack != null && uniqueRecipe.add(recipeId)) {
                    this.grid.addItem(
                            BookmarkItem
                                    .of(groupId, stack, StackInfo.getAmount(stack), recipeId, BookmarkItemType.ITEM),
                            true);
                    uniqueStack.add(stack);
                }

            } else {
                ItemStack stack = extractItem(item);

                if (stack != null && !uniqueStack.contains(stack)) {
                    this.grid.addItem(BookmarkItem.of(groupId, stack), true);
                    uniqueStack.add(stack);
                }

            }

        }

        return true;
    }

    protected static ItemStack extractItem(Object item) {

        if (item instanceof PositionedStack positionedStack) {
            return positionedStack.item;
        }

        if (item instanceof NBTTagCompound nbTag) {
            return StackInfo.loadFromNBT(nbTag);
        }

        if (item instanceof RecipeIngredient ingr) {
            return ingr.getItemStack();
        }

        if (item instanceof ItemStack stack) {
            return stack;
        }

        return null;
    }

    public boolean removeItem(ItemStack itemStack, RecipeId recipeId, int groupId) {
        final int itemIndex = this.grid.indexOf(groupId, itemStack, recipeId, false);

        if (itemIndex != -1) {
            this.grid.removeRecipe(itemIndex, false);
            return true;
        }

        return false;
    }

    public boolean removeSlot(int mousex, int mousey, boolean removeFullRecipe) {
        final BookmarksGridSlot slot = getSlotMouseOver(mousex, mousey);

        if (slot == null) {
            return false;
        }

        final int groupId = slot.getGroupId();
        final RecipeId recipeId = slot.getType() == BookmarkItemType.ITEM ? null : slot.getRecipeId();
        final BookmarkGroup group = slot.getGroup();

        if (group.crafting != null && group.collapsed) {

            if (!removeFullRecipe) {
                return true;
            }

            this.grid.removeGroup(groupId);
            return true;
        }

        if (recipeId != null && group.crafting != null) {
            Set<RecipeId> recipes = group.crafting.recipeRelations.getOrDefault(recipeId, Collections.emptySet());

            if (recipes.isEmpty()) {
                for (Map.Entry<RecipeId, Set<RecipeId>> entry : group.crafting.recipeRelations.entrySet()) {
                    if (entry.getValue().contains(recipeId)) {
                        recipes = entry.getValue();
                        break;
                    }
                }
            }

            if (!recipes.isEmpty()) {

                if (!removeFullRecipe) {
                    return true;
                }

                boolean removed = false;
                for (RecipeId relRecipeId : recipes) {
                    removed = this.grid.removeRecipe(relRecipeId, groupId) || removed;
                }

                return removed;
            }
        }

        if (recipeId == null || !removeFullRecipe) {
            this.grid.removeRecipe(slot.itemIndex, removeFullRecipe);
            return true;
        }

        return this.grid.removeRecipe(recipeId, groupId);
    }

    public boolean removeRecipe(RecipeId recipeId, int groupId) {
        return this.grid.removeRecipe(recipeId, groupId);
    }

    public boolean removeGroup(int groupId) {

        if (this.grid.getGroup(groupId) != null) {
            this.grid.removeGroup(groupId);
            return true;
        }

        return false;
    }

    public RecipeId getRecipeId(int itemIndex) {
        BookmarkItem meta = this.grid.getCalculatedItem(itemIndex);
        return meta.type == BookmarkItemType.INGREDIENT ? null : meta.recipeId;
    }

    public boolean existsRecipe(RecipeId recipeId, int groupId) {
        return this.grid.existsRecipe(recipeId, groupId);
    }

    public int getHoveredGroupId(boolean groupPanel) {
        final int overRowIndex = this.grid.getHoveredRowIndex(groupPanel);

        if (groupPanel && overRowIndex == -1 && !this.grid.isEmpty()) {
            final Point mouse = GuiDraw.getMousePosition();

            if (new Rectangle4i(pagePrev.x + pagePrev.w, pagePrev.y, pageNext.x - (pagePrev.x + pagePrev.w), pagePrev.h)
                    .contains(mouse.x, mouse.y)) {
                return BookmarkGrid.DEFAULT_GROUP_ID;
            }
        }

        if (overRowIndex >= 0) {
            int groupId = this.grid.getRowGroupId(overRowIndex);
            return groupId == BookmarkGrid.DEFAULT_GROUP_ID ? -1 : groupId;
        }

        return -1;
    }

    protected String getNamespaceLabelText(boolean shortFormat) {
        String activePage = String.valueOf(this.storage.getActiveIndex() + 1);

        return shortFormat ? activePage : (activePage + "/" + this.storage.getNamespaceSize());
    }

    public void load() {
        String worldPath = "global";

        if (NEIClientConfig.getBooleanSetting("inventory.bookmarks.worldSpecific")) {
            worldPath = NEIClientConfig.getWorldPath();
        }

        final File dir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/" + worldPath);

        if (!dir.exists()) {
            dir.mkdirs();
        }

        final File bookmarkFile = new File(dir, "bookmarks.ini");

        if (!bookmarkFile.exists()) {
            final File globalBookmarks = new File(CommonUtils.getMinecraftDir(), "saves/NEI/global/bookmarks.ini");
            final File configBookmarks = new File(NEIClientConfig.configDir, "bookmarks.ini");
            final File defaultBookmarks = configBookmarks.exists() ? configBookmarks : globalBookmarks;

            if (defaultBookmarks.exists()) {

                try {
                    if (bookmarkFile.createNewFile()) {
                        InputStream src = new FileInputStream(defaultBookmarks);
                        OutputStream dst = new FileOutputStream(bookmarkFile);

                        IOUtils.copy(src, dst);

                        src.close();
                        dst.close();
                    }
                } catch (IOException e) {}
            }
        }

        this.storage.load(bookmarkFile);
    }

    public void save() {
        this.storage.save();
    }

    @Override
    protected int resizeHeader(GuiContainer gui) {
        final LayoutStyleMinecraft layout = (LayoutStyleMinecraft) LayoutManager.getLayoutStyle();
        final int rows = (int) Math.ceil((double) layout.buttonCount / layout.numButtons);
        final int diff = rows * 19 + PADDING - y;

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
        int labelWidth;

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

        if (grid.getPerPage() > 0 && !this.storage.isEmpty()) {
            LayoutManager.addWidget(namespacePrev);
            LayoutManager.addWidget(namespaceNext);
            LayoutManager.addWidget(namespaceLabel);

            if (!grid.isEmpty()
                    && BookmarkContainerInfo.getBookmarkContainerHandler(NEIClientUtils.getGuiContainer()) != null) {
                LayoutManager.addWidget(pullBookmarkedItems);
            }
        }
    }

    @Override
    public Rectangle4i calculateBounds() {
        final GuiContainer gui = NEIClientUtils.getGuiContainer();
        final int maxWidth = (gui.width - 176) / 2 - PADDING * 2;
        final int maxHeight = gui.height - PADDING * 2;
        final int freeSpace = (gui.width - gui.xSize) / 2 - PADDING * 2;

        final int paddingLeft = (int) Math
                .ceil(maxWidth * NEIClientConfig.getSetting("world.panels.bookmarks.left").getIntValue() / 100000.0);
        final int paddingTop = (int) Math
                .ceil(maxHeight * NEIClientConfig.getSetting("world.panels.bookmarks.top").getIntValue() / 100000.0);
        final int paddingRight = (int) Math
                .ceil(maxWidth * NEIClientConfig.getSetting("world.panels.bookmarks.right").getIntValue() / 100000.0);
        final int paddingBottom = (int) Math
                .ceil(maxHeight * NEIClientConfig.getSetting("world.panels.bookmarks.bottom").getIntValue() / 100000.0);

        final int width = Math.max(
                ItemsGrid.SLOT_SIZE,
                snapDownToMultiple(
                        Math.min(freeSpace - paddingLeft, maxWidth - paddingLeft - paddingRight),
                        ItemsGrid.SLOT_SIZE));
        final int height = Math.max(ItemsGrid.SLOT_SIZE, maxHeight - paddingTop - paddingBottom);

        return new Rectangle4i(paddingLeft + PADDING, paddingTop + PADDING, width, height);
    }

    protected ItemStack getDraggedStackWithQuantity(ItemStack itemStack) {
        return ItemQuantityField.prepareStackWithQuantity(itemStack, StackInfo.getAmount(itemStack));
    }

    @Override
    public void mouseDragged(int mousex, int mousey, int button, long heldTime) {
        if (this.grid.getSlotBySlotIndex(this.mouseDownSlot) == null) {
            this.mouseDownSlot = -1;
        }

        if (this.groupingItem != null) {
            final int overRowIndex = grid.getRowIndex(mousey);

            if (this.groupingItem.hasEndRow() || overRowIndex != this.groupingItem.getStartSlotIndex()
                    || heldTime > 250) {
                this.groupingItem.setEndPoint(Math.max(0, Math.min(overRowIndex, grid.getLastRowIndex())));
            }

            return;
        } else if (this.sortableGroup != null) {
            final int overRowIndex = grid.getRowIndex(mousey);

            if (overRowIndex >= 0 && this.sortableGroup.groupId != this.grid.getRowGroupId(overRowIndex)) {
                this.grid.moveGroup(this.sortableGroup, overRowIndex);
            }

            return;
        } else if (this.sortableItem != null) {

            if (!this.sortableItem.isEmpty()) {
                this.sortableItem.mouseDragged(mousex, mousey);
            }

            return;
        }

        if (button == 0 && NEIClientUtils.shiftKey() && this.mouseDownSlot >= 0 && this.sortableItem == null) {
            final BookmarksGridSlot mouseOverSlot = getSlotMouseOver(mousex, mousey);

            if (mouseOverSlot == null || mouseOverSlot.slotIndex != this.mouseDownSlot || heldTime > 250) {
                final BookmarksGridSlot slot = this.grid.getSlotBySlotIndex(this.mouseDownSlot);

                if (slot != null && slot.itemIndex >= 0) {
                    this.sortableItem = new SortableItem(this.grid, slot.getBookmarkItem());
                    this.grid.onGridChanged();
                }
            }

            return;
        }

        super.mouseDragged(mousex, mousey, button, heldTime);
    }

    private int getNextSlot() {
        final int columns = this.grid.getColumns();
        final int perPage = this.grid.getRows() * columns;
        final boolean line = this.grid.getViewMode(BookmarkGrid.DEFAULT_GROUP_ID) == BookmarkViewMode.TODO_LIST;
        final List<BookmarksGridSlot> mask = this.grid.getMask();
        int slotIndex = mask.isEmpty() ? 0 : mask.get(mask.size() - 1).slotIndex + 1;

        while (slotIndex < perPage) {

            if ((!line || (slotIndex % columns) == 0) && !grid.isInvalidSlot(slotIndex)) {
                return slotIndex;
            }

            slotIndex++;
        }

        return -1;
    }

    @Override
    public void postDraw(int mousex, int mousey) {

        if (this.sortableItem != null) {
            this.sortableItem.postDraw(mousex, mousey);
        }

        if (this.sortableGroup != null) {
            this.sortableGroup.postDraw(mousex, mousey);
        }

        if (ItemPanels.itemPanel.draggedStack != null && contains(mousex, mousey)) {
            final int slotIndex = getNextSlot();

            if (slotIndex >= 0) {
                Rectangle4i rect = this.grid.getSlotRect(slotIndex);
                NEIClientUtils.drawRect(rect.x, rect.y, rect.w, rect.h, ItemsGrid.HIGHLIGHT_COLOR);
            }
        }

        super.postDraw(mousex, mousey);
    }

    @Override
    public boolean handleClickExt(int mousex, int mousey, int button) {

        if (button == 0 && NEIClientUtils.shiftKey()) {
            final int overRowIndex = this.grid.getHoveredRowIndex(true);
            final int groupId = overRowIndex != -1 ? this.grid.getRowGroupId(overRowIndex)
                    : BookmarkGrid.DEFAULT_GROUP_ID;

            if (groupId != BookmarkGrid.DEFAULT_GROUP_ID) {
                int rowIndex = overRowIndex;

                while (rowIndex >= 0 && groupId == this.grid.getRowGroupId(rowIndex)) {
                    rowIndex--;
                }

                Rectangle4i rect = this.grid.getSlotRect(rowIndex + 1, 0);
                this.sortableGroup = new SortableGroup(this.grid, groupId);
                this.sortableGroup.shiftX = rect.x - mousex;
                this.sortableGroup.shiftY = rect.y - mousey;

                return true;
            }
        }

        if (!NEIClientUtils.shiftKey() && (button == 0 || button == 1)) {
            final int overRowIndex = this.grid.getHoveredRowIndex(true);

            if (overRowIndex != -1) {
                int groupId = this.grid.getRowGroupId(overRowIndex);

                if (button == 1) {
                    groupId = BookmarkGrid.DEFAULT_GROUP_ID;
                } else if (groupId == BookmarkGrid.DEFAULT_GROUP_ID) {
                    groupId = Integer.MIN_VALUE;
                }

                this.groupingItem = new GroupingItem(this.grid, groupId, overRowIndex);

                if (this.groupingItem.startPoint.topRowIndex == -1
                        || this.groupingItem.startPoint.bottomRowIndex == -1) {
                    this.groupingItem = null;
                }

                return true;
            }
        }

        if (new Rectangle4i(pagePrev.x + pagePrev.w, pagePrev.y, pageNext.x - (pagePrev.x + pagePrev.w), pagePrev.h)
                .contains(mousex, mousey)) {

            if (NEIClientUtils.altKey() && button == 0) {
                this.grid.toggleCollapsedState(BookmarkGrid.DEFAULT_GROUP_ID);
                NEIClientUtils.playClickSound();
            } else if (button == 0) {
                this.grid.toggleViewMode(BookmarkGrid.DEFAULT_GROUP_ID);
                NEIClientUtils.playClickSound();
            } else if (button == 1) {
                this.grid.toggleCraftingMode(BookmarkGrid.DEFAULT_GROUP_ID);
                NEIClientUtils.playClickSound();
            }

            return true;
        } else {
            return super.handleClickExt(mousex, mousey, button);
        }
    }

    @Override
    public List<String> handleTooltip(int mousex, int mousey, List<String> currenttip) {

        if (this.grid.getPerPage() == 0 || this.grid.isEmpty()) {
            return new ArrayList<>();
        }

        final int overRowIndex = this.grid.getHoveredRowIndex(true);

        if (overRowIndex != -1) {
            final int groupId = this.grid.getRowGroupId(overRowIndex);

            currenttip.add(NEIClientUtils.translate("bookmark.group"));

            if (groupId != BookmarkGrid.DEFAULT_GROUP_ID) {
                currenttip = recipeChainTooltip(groupId, currenttip);
            } else {
                this.recipeChainTooltipLineHandler = null;
            }
        } else
            if (new Rectangle4i(pagePrev.x + pagePrev.w, pagePrev.y, pageNext.x - (pagePrev.x + pagePrev.w), pagePrev.h)
                    .contains(mousex, mousey)) {
                        currenttip.add(NEIClientUtils.translate("bookmark.group"));
                        currenttip = recipeChainTooltip(BookmarkGrid.DEFAULT_GROUP_ID, currenttip);
                    } else {
                        this.recipeChainTooltipLineHandler = null;
                    }

        return super.handleTooltip(mousex, mousey, currenttip);
    }

    @Override
    public boolean handleClick(int mousex, int mousey, int button) {
        final BookmarksGridSlot hoverSlot = getSlotMouseOver(mousex, mousey);

        if (hoverSlot != null && button == 0 && NEIClientUtils.altKey()) {
            final BookmarkGroup group = hoverSlot.getGroup();

            if (group.collapsed) {
                group.collapsed = false;
                NEIClientUtils.playClickSound();
                grid.onItemsChanged();
            } else if (group.crafting != null && hoverSlot.getRecipeId() != null) {
                grid.toggleCollapsedRecipe(hoverSlot.getGroupId(), hoverSlot.getRecipeId());
                NEIClientUtils.playClickSound();
            }

            return true;
        }

        return super.handleClick(mousex, mousey, button);
    }

    @Override
    public Map<String, String> handleHotkeys(int mousex, int mousey, Map<String, String> hotkeys) {
        final int overRowIndex = this.grid.getHoveredRowIndex(true);
        final BookmarksGridSlot slot = getSlotMouseOver(mousex, mousey);

        if (this.grid.getPerPage() == 0 || this.grid.isEmpty()) {
            return hotkeys;
        }

        if (new Rectangle4i(pagePrev.x + pagePrev.w, pagePrev.y, pageNext.x - (pagePrev.x + pagePrev.w), pagePrev.h)
                .contains(mousex, mousey)) {

            hotkeys.put(
                    NEIClientUtils.getKeyName(
                            NEIClientUtils.CTRL_HASH,
                            NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                    NEIClientUtils.translate("bookmark.change_quantity"));

            hotkeys.put(
                    NEIClientUtils.getKeyName(
                            NEIClientUtils.CTRL_HASH + NEIClientUtils.ALT_HASH,
                            NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                    NEIClientUtils.translate("bookmark.change_quantity_step"));

            hotkeys.put(
                    NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_LMB),
                    NEIClientUtils.translate("bookmark.group.toggle_mode"));
            hotkeys.put(
                    NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_RMB),
                    NEIClientUtils.translate("bookmark.group.toggle_crafting_chain"));
            hotkeys.put(
                    NEIClientConfig.getKeyName("gui.remove_recipe"),
                    NEIClientUtils.translate("bookmark.group.remove_recipe"));

            if (BookmarkContainerInfo.getBookmarkContainerHandler(NEIClientUtils.getGuiContainer()) != null) {
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.bookmark_pull_items"),
                        NEIClientUtils.translate("bookmark.group.pull_items"));
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.bookmark_pull_items", NEIClientUtils.SHIFT_HASH),
                        NEIClientUtils.translate("bookmark.group.pull_items_shift"));
            }

            hotkeys.put(
                    NEIClientUtils.getKeyName(NEIClientUtils.ALT_HASH, NEIMouseUtils.MOUSE_BTN_LMB),
                    NEIClientUtils.translate("bookmark.group.toggle_collapse"));
        }

        if (overRowIndex != -1) {
            final int groupId = this.grid.getRowGroupId(overRowIndex);

            hotkeys.put(
                    NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_LMB + NEIMouseUtils.MOUSE_DRAG),
                    NEIClientUtils.translate("bookmark.group.include_group"));

            if (groupId != BookmarkGrid.DEFAULT_GROUP_ID) {

                hotkeys.put(
                        NEIClientUtils.getKeyName(
                                NEIClientUtils.CTRL_HASH,
                                NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                        NEIClientUtils.translate("bookmark.change_quantity"));

                hotkeys.put(
                        NEIClientUtils.getKeyName(
                                NEIClientUtils.CTRL_HASH + NEIClientUtils.ALT_HASH,
                                NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                        NEIClientUtils.translate("bookmark.change_quantity_step"));

                hotkeys.put(
                        NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_RMB + NEIMouseUtils.MOUSE_DRAG),
                        NEIClientUtils.translate("bookmark.group.exclude_group"));
                hotkeys.put(
                        NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_LMB),
                        NEIClientUtils.translate("bookmark.group.toggle_mode"));
                hotkeys.put(
                        NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_RMB),
                        NEIClientUtils.translate("bookmark.group.toggle_crafting_chain"));

                hotkeys.put(
                        NEIClientUtils.getKeyName(
                                NEIClientUtils.SHIFT_HASH,
                                NEIMouseUtils.MOUSE_BTN_LMB + NEIMouseUtils.MOUSE_DRAG),
                        NEIClientUtils.translate("bookmark.group.sorting"));

                hotkeys.put(
                        NEIClientUtils.getKeyName(NEIClientUtils.ALT_HASH, NEIMouseUtils.MOUSE_BTN_LMB),
                        NEIClientUtils.translate("bookmark.group.toggle_collapse"));
            }
        }

        if (slot != null) {
            final BookmarkGroup group = slot.getGroup();

            hotkeys.put(
                    NEIClientUtils.getKeyName(
                            NEIClientUtils.CTRL_HASH,
                            NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                    NEIClientUtils.translate("bookmark.change_quantity"));

            hotkeys.put(
                    NEIClientUtils.getKeyName(
                            NEIClientUtils.CTRL_HASH + NEIClientUtils.ALT_HASH,
                            NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                    NEIClientUtils.translate("bookmark.change_quantity_step"));

            hotkeys.put(
                    NEIClientUtils.getKeyName(
                            NEIClientUtils.SHIFT_HASH,
                            NEIMouseUtils.MOUSE_BTN_LMB + NEIMouseUtils.MOUSE_DRAG),
                    NEIClientUtils.translate("bookmark.item.sorting"));

            if (group.collapsed) {
                hotkeys.put(
                        NEIClientUtils.getKeyName(NEIClientUtils.ALT_HASH, NEIMouseUtils.MOUSE_BTN_LMB),
                        NEIClientUtils.translate("bookmark.group.toggle_collapse"));
            } else if (group.crafting != null) {
                hotkeys.put(
                        NEIClientUtils.getKeyName(NEIClientUtils.ALT_HASH, NEIMouseUtils.MOUSE_BTN_LMB),
                        NEIClientUtils.translate("bookmark.recipe.toggle_collapse"));
            }

            if (this.acceptsFollowingTooltipLineHandler != null) {
                hotkeys.put(
                        NEIClientUtils.getKeyName(
                                NEIClientUtils.SHIFT_HASH,
                                NEIMouseUtils.MOUSE_BTN_NONE + NEIMouseUtils.MOUSE_SCROLL),
                        NEIClientUtils.translate("recipe.accepts.scroll"));
            }
        }

        return super.handleHotkeys(mousex, mousey, hotkeys);
    }

    private List<String> recipeChainTooltip(int groupId, List<String> currenttip) {
        boolean crafting = this.grid.getGroup(groupId).crafting != null;

        if (this.recipeChainTooltipLineHandler == null || this.recipeChainTooltipLineHandler.groupId != groupId
                || this.recipeChainTooltipLineHandler.crafting != crafting) {
            this.recipeChainTooltipLineHandler = new RecipeChainTooltipLineHandler(
                    groupId,
                    crafting,
                    this.grid.createRecipeChainMath(groupId));
        }

        if (this.recipeChainTooltipLineHandler != null) {
            currenttip.add(GuiDraw.TOOLTIP_HANDLER + GuiDraw.getTipLineId(this.recipeChainTooltipLineHandler));
        }

        return currenttip;
    }

    @Override
    public boolean contains(int px, int py) {

        if (new Rectangle4i(pagePrev.x + pagePrev.w, pagePrev.y, pageNext.x - (pagePrev.x + pagePrev.w), pagePrev.h)
                .contains(px, py)) {
            return true;
        }

        if (this.grid.getHoveredRowIndex(true) != -1) {
            return true;
        }

        return super.contains(px, py);
    }

    @Override
    public List<String> handleItemTooltip(ItemStack itemstack, int mousex, int mousey, List<String> currenttip) {
        final BookmarksGridSlot slot = this.grid.getSlotMouseOver(mousex, mousey);

        if (slot != null) {

            if (slot.getType() == BookmarkItemType.INGREDIENT && slot.getRecipeId() != null
                    && slot.getBookmarkItem().permutations.size() > 1) {
                RecipeId recipeId = slot.getRecipeId();

                if (recipeId == null) {
                    this.acceptsFollowingTooltipLineHandler = null;
                } else if (this.acceptsFollowingTooltipLineHandler == null
                        || ((int) this.acceptsFollowingTooltipLineHandler.tooltipGUID) != slot.itemIndex) {
                            this.acceptsFollowingTooltipLineHandler = AcceptsFollowingTooltipLineHandler.of(
                                    slot.itemIndex,
                                    new ArrayList<>(slot.getBookmarkItem().permutations.values()),
                                    slot.getItemStack(),
                                    2);
                        }

                if (this.acceptsFollowingTooltipLineHandler != null) {
                    currenttip.add(
                            GuiDraw.TOOLTIP_HANDLER + GuiDraw.getTipLineId(this.acceptsFollowingTooltipLineHandler));
                }
            } else {
                this.acceptsFollowingTooltipLineHandler = null;
            }

        } else {
            this.acceptsFollowingTooltipLineHandler = null;
        }

        return currenttip;
    }

    @Override
    public void mouseUp(int mousex, int mousey, int button) {

        if (this.sortableItem != null) {
            grid.onItemsChanged();
        } else if (this.sortableGroup != null) {
            grid.onItemsChanged();
        } else if (this.groupingItem != null && this.groupingItem.hasEndRow()) {
            this.grid.createGroup(this.groupingItem);
            this.grid.onItemsChanged();
        } else {
            int rowIndex = this.grid.getHoveredRowIndex(true);
            int groupId = rowIndex != -1 ? this.grid.getRowGroupId(rowIndex) : BookmarkGrid.DEFAULT_GROUP_ID;

            if (groupId != BookmarkGrid.DEFAULT_GROUP_ID) {
                if (button == 0 && NEIClientUtils.altKey()) {
                    this.grid.toggleCollapsedState(groupId);
                    NEIClientUtils.playClickSound();
                } else if (button == 0) {
                    this.grid.toggleViewMode(groupId);
                    NEIClientUtils.playClickSound();
                } else if (button == 1) {
                    this.grid.toggleCraftingMode(groupId);
                    NEIClientUtils.playClickSound();
                }

                this.grid.onItemsChanged();
            } else {
                super.mouseUp(mousex, mousey, button);
            }
        }

        this.sortableItem = null;
        this.sortableGroup = null;
        this.groupingItem = null;
        this.mouseDownSlot = -1;
    }

    @Override
    public boolean onMouseWheel(int shift, int mousex, int mousey) {

        if ((!inEditingState() || this.sortableGroup != null) && new Rectangle4i(
                namespacePrev.x,
                namespacePrev.y,
                namespaceNext.x + namespaceNext.w - namespacePrev.x,
                namespacePrev.h).contains(mousex, mousey)) {

            if (shift > 0) {
                this.storage.prevNamespace();
            } else {
                this.storage.nextNamespace();
            }

            return true;
        }

        if (!contains(mousex, mousey)) {
            return false;
        }

        if (!inEditingState() && NEIClientUtils.controlKey()) {
            final BookmarksGridSlot slot = this.grid.getSlotMouseOver(mousex, mousey);

            if (slot != null) {
                int multiplier = 1;

                if (NEIClientUtils.altKey()) {
                    multiplier = NEIClientConfig.showItemQuantityWidget() ? NEIClientConfig.getItemQuantity() : 0;
                    if (multiplier == 0) {
                        multiplier = StackInfo.itemStackToNBT(slot.getItemStack()).hasKey("gtFluidName") ? 144
                                : slot.getItemStack().getMaxStackSize();
                    }
                }

                this.grid.shiftItemAmount(slot.itemIndex, multiplier * shift);
                return true;
            } else {
                final int groupId = getHoveredGroupId(true);

                if (groupId != -1) {
                    int multiplier = 1;

                    if (NEIClientUtils.altKey()) {
                        multiplier = NEIClientConfig.showItemQuantityWidget() ? NEIClientConfig.getItemQuantity() : 0;
                        if (multiplier == 0) {
                            multiplier = 64;
                        }
                    }

                    this.grid.shiftGroupAmount(groupId, multiplier * shift);
                    return true;
                }
            }
        }

        if (!inEditingState() && NEIClientUtils.shiftKey()
                && this.grid.contains(mousex, mousey)
                && this.acceptsFollowingTooltipLineHandler != null) {
            int itemIndex = (int) this.acceptsFollowingTooltipLineHandler.tooltipGUID;
            final BookmarkItem item = this.grid.getCalculatedItem(itemIndex);

            if (item.permutations.size() > 1) {
                final List<ItemStack> items = acceptsFollowingTooltipLineHandler.items;
                ItemStack activeStack = this.acceptsFollowingTooltipLineHandler.getActiveStack();
                int stackIndex = 0;

                for (int i = 0; i < items.size(); i++) {
                    if (StackInfo.equalItemAndNBT(activeStack, items.get(i), true)) {
                        stackIndex = i;
                        break;
                    }
                }

                activeStack = items.get((items.size() - shift + stackIndex) % items.size());

                this.acceptsFollowingTooltipLineHandler.setActiveStack(activeStack);
                this.grid.getBookmarkItem(Math.abs(itemIndex)).itemStack = activeStack;
                this.grid.onItemsChanged();
            }

            return true;
        }

        if (super.onMouseWheel(shift, mousex, mousey)) {
            final int overRowIndex = grid.getRowIndex(mousey);

            if (this.sortableGroup != null && overRowIndex >= 0
                    && this.sortableGroup.groupId != this.grid.getRowGroupId(overRowIndex)) {
                this.grid.moveGroup(this.sortableGroup, overRowIndex);
            }

            if (this.groupingItem != null
                    && (this.groupingItem.hasEndRow() || overRowIndex != this.groupingItem.getStartSlotIndex())) {
                this.groupingItem.setEndPoint(Math.max(0, Math.min(overRowIndex, grid.getLastRowIndex())));
            }

            return true;
        }

        return false;
    }

    public boolean pullBookmarkItems(int groupId, boolean shift) {
        return pullBookmarkItems(this.grid.createRecipeChainMath(groupId), shift);
    }

    public boolean pullBookmarkItems(RecipeChainMath math, boolean shift) {
        final GuiContainer guiContainer = NEIClientUtils.getGuiContainer();
        final IBookmarkContainerHandler containerHandler = BookmarkContainerInfo
                .getBookmarkContainerHandler(guiContainer);

        if (containerHandler == null || math == null) {
            return false;
        }

        final ItemStackAmount pullItemStacks = new ItemStackAmount();
        final InventoryPlayer playerInventory = guiContainer.mc.thePlayer.inventory;
        final ItemStackAmount inventory = shift ? ItemStackAmount.of(Arrays.asList(playerInventory.mainInventory))
                : new ItemStackAmount();

        if (math.outputRecipes.isEmpty()) {

            for (BookmarkItem item : math.initialItems) {
                final long invStackSize = inventory.getOrDefault(item.itemStack, 0L);
                final long amount = item.amount - invStackSize * item.fluidCellAmount;
                if (amount > 0) {
                    pullItemStacks.add(item.getItemStack(amount));
                }
            }

        } else {
            final ItemStackAmount containerStorage = ItemStackAmount
                    .of(containerHandler.getStorageStacks(guiContainer));
            final List<BookmarkItem> initialItems = new ArrayList<>();

            for (BookmarkItem item : math.initialItems) {
                final long invStackSize = inventory.getOrDefault(item.itemStack, 0L);
                final long strStackSize = containerStorage.getOrDefault(item.itemStack, 0L);
                final long amount = item.amount - invStackSize * item.fluidCellAmount
                        - strStackSize * item.fluidCellAmount;

                if (amount > 0) {
                    initialItems.add(item.copyWithAmount(amount));
                }
            }

            math.initialItems.clear();
            math.initialItems.addAll(initialItems);

            for (ItemStack stack : inventory.values()) {
                math.initialItems.add(BookmarkItem.of(-1, stack));
            }

            for (ItemStack stack : containerStorage.values()) {
                math.initialItems.add(BookmarkItem.of(-2, stack));
            }

            math.refresh();

            for (BookmarkItem item : math.initialItems) {
                if (item.groupId == -2 && math.requiredAmount.getOrDefault(item, -1L) > 0) {
                    pullItemStacks.add(item.getItemStack(math.requiredAmount.get(item)));
                }
            }

        }

        if (pullItemStacks.isEmpty()) {
            return false;
        }

        ArrayList<ItemStack> items = new ArrayList<>();
        int freeSlots = 0;

        for (ItemStack stack : playerInventory.mainInventory) {
            if (stack == null || stack.stackSize < stack.getMaxStackSize() && pullItemStacks.contains(stack)) {
                freeSlots++;
            }
        }

        if (freeSlots == 0) {
            return false;
        }

        for (ItemStack stack : pullItemStacks.values()) {
            stack.stackSize = Math.min(stack.stackSize, freeSlots * stack.getMaxStackSize());
            if (stack.stackSize > 0) {
                items.add(stack);
            }
        }

        if (!items.isEmpty()) {
            final int helmStackIndex = FastTransferManager.dropHeldItem(guiContainer);

            if (guiContainer.mc.thePlayer.inventory.getItemStack() == null) {
                containerHandler.pullBookmarkItemsFromContainer(guiContainer, items);

                if (helmStackIndex != -1) {
                    FastTransferManager.clickSlot(guiContainer, helmStackIndex, 0, 0);
                }
            }
        }

        return true;
    }

    public void clearTooltips() {
        this.acceptsFollowingTooltipLineHandler = null;
        this.recipeChainTooltipLineHandler = null;
    }

    @Deprecated
    public void addItem(ItemStack stack, boolean saveSize) {
        addItem(saveSize ? stack : StackInfo.withAmount(stack, 0));
    }

    @Deprecated
    public void addOrRemoveItem(ItemStack stack) {
        if (!removeItem(stack, null, BookmarkGrid.DEFAULT_GROUP_ID)) {
            addItem(stack);
        }
    }

    @Deprecated
    public void addOrRemoveItem(ItemStack stack, String handlerName, List<PositionedStack> ingredients,
            boolean saveIngredients, boolean saveSize) {

        if (!removeItem(stack, null, BookmarkGrid.DEFAULT_GROUP_ID)) {
            if (saveIngredients && handlerName != null
                    && !handlerName.isEmpty()
                    && ingredients != null
                    && !ingredients.isEmpty()) {
                addRecipe(
                        Recipe.of(Arrays.asList(stack), handlerName, ingredients),
                        saveSize ? 1 : 0,
                        BookmarkGrid.DEFAULT_GROUP_ID);
            } else {
                addItem(stack, saveSize);
            }
        }
    }

    @Deprecated
    public void addRecipe(BookmarkRecipe recipe, boolean saveSize) {
        addRecipe(recipe, saveSize, BookmarkGrid.DEFAULT_GROUP_ID);
    }

    @Deprecated
    public void addRecipe(BookmarkRecipe recipe, boolean saveSize, int groupId) {
        addRecipe(recipe.getRecipe(), saveSize ? 1 : 0, groupId);
    }

    @Deprecated
    public void addBookmarkGroup(List<ItemStack> items, BookmarkViewMode viewMode) {
        addGroup(items, viewMode, false);
    }

    @Deprecated
    public void addBookmarkGroup(List<BookmarkRecipe> recipes, BookmarkViewMode viewMode, boolean crafting) {
        List<Recipe> items = new ArrayList<>();

        for (BookmarkRecipe recipe : recipes) {
            items.add(recipe.getRecipe());
        }

        addGroup(items, viewMode, crafting);
    }

    @Deprecated
    public BookmarkRecipeId getBookmarkRecipeId(int itemIndex) {
        BookmarkItem item = this.grid.getCalculatedItem(itemIndex);
        return item == null || item.type == BookmarkItemType.INGREDIENT || item.recipeId == null ? null
                : new BookmarkRecipeId(item.recipeId.getHandleName(), item.recipeId.getIngredients());
    }

    @Deprecated
    public BookmarkRecipeId getBookmarkRecipeId(ItemStack stackA) {
        BookmarkRecipeId recipeId = null;

        for (int itemIndex = 0; itemIndex < this.grid.size(); itemIndex++) {
            if (StackInfo.equalItemAndNBT(stackA, this.grid.getBookmarkItem(itemIndex).itemStack, true)
                    && (recipeId = getBookmarkRecipeId(itemIndex)) != null) {
                return recipeId;
            }
        }

        return null;
    }

    @Deprecated
    public boolean removeBookmarkRecipeId(BookmarkRecipeId recipeId) {
        return false;
    }
}
