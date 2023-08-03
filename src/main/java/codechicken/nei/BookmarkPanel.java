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
import java.util.*;

import javax.annotation.Nullable;

import codechicken.nei.api.IBookmarkContainerHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import codechicken.core.CommonUtils;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.ItemPanel.ItemPanelSlot;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.recipe.BookmarkRecipeId;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;
import codechicken.nei.util.ReadableNumberConverter;

public class BookmarkPanel extends PanelWidget {

    protected File bookmarkFile;
    protected boolean bookmarksIsLoaded = false;
    public int sortedStackIndex = -1;
    public int sortedNamespaceIndex = -1;

    public Button namespacePrev;
    public Button namespaceNext;
    public Button pullBookmarkedItems;
    public Label namespaceLabel;

    protected List<BookmarkGrid> namespaces = new ArrayList<>();
    protected int activeNamespaceIndex = 0;

    protected static class BookmarkStackMeta {

        public int factor;
        public BookmarkRecipeId recipeId;
        public boolean ingredient = false;
        public boolean fluidDisplay = false;

        public BookmarkStackMeta(BookmarkRecipeId recipeId, int count, boolean ingredient, boolean fluidDisplay) {
            this.recipeId = recipeId;
            this.factor = count;
            this.ingredient = ingredient;
            this.fluidDisplay = fluidDisplay;
        }
    }

    public static enum BookmarkViewMode {
        DEFAULT,
        TODO_LIST
    }

    public static class BookmarkGrid extends ItemsGrid {

        protected static final float SCALE_SPEED = 0.1f;

        protected List<List<Integer>> gridMask;
        protected List<BookmarkStackMeta> metadata = new ArrayList<>();
        protected WeakHashMap<ItemStack, Float> animation = new WeakHashMap<>();

        protected BookmarkViewMode viewMode = BookmarkViewMode.DEFAULT;
        protected boolean[] todoSpaces;
        protected int todoItemsCount = 0;

        public void setViewMode(BookmarkViewMode mode) {
            if (viewMode == mode) {
                return;
            }

            viewMode = mode;

            if (mode == BookmarkViewMode.DEFAULT) {
                sortIngredients(false);
                todoItemsCount = 0;
                gridMask = null;
            }

            onGridChanged();
        }

        public BookmarkViewMode getViewMode() {
            return viewMode;
        }

        @Override
        public int getNumPages() {
            if (gridMask != null) {
                return gridMask.size();
            }

            return super.getNumPages();
        }

        @Override
        protected List<Integer> getMask() {
            if (gridMask != null) {
                return gridMask.size() > page ? gridMask.get(page) : new ArrayList<>();
            }

            return super.getMask();
        }

        private void sortIngredients(final boolean forward) {
            BookmarkStackMeta meta;
            int dir = forward ? 1 : -1;

            for (int i = 0; i < realItems.size(); i++) {
                meta = metadata.get(i);

                if (meta.recipeId != null && !meta.ingredient) {
                    int shift = 0;

                    for (int j = i + 1; j < realItems.size(); j++) {
                        if (meta.recipeId.equals(metadata.get(j).recipeId)) {
                            if (i + shift + dir != j) {
                                realItems.add(i + shift + Math.max(dir, 0), realItems.remove(j));
                                metadata.add(i + shift + Math.max(dir, 0), metadata.remove(j));
                            }
                            shift++;
                        }
                    }

                    for (int j = i + shift - 1; j >= 0; j--) {
                        if (meta.recipeId.equals(metadata.get(j).recipeId)) {
                            if (i + shift + dir != j) {
                                realItems.add(i + shift + Math.min(dir, 0), realItems.remove(j));
                                metadata.add(i + shift + Math.min(dir, 0), metadata.remove(j));
                            }
                            shift--;
                        }
                    }

                    i += Math.max(0, shift);
                }
            }
        }

        private void generateToDoSpaces() {
            todoItemsCount = 0;
            gridMask = new ArrayList<>();

            if (rows * columns == 0) {
                return;
            }

            BookmarkStackMeta meta;
            int size = size();
            int idx = 0;

            while (idx < size) {
                ArrayList<Integer> pmask = new ArrayList<>();

                for (int i = 0, perPage = rows * columns; i < perPage && idx < size; i++) {
                    if (isInvalidSlot(i)) {
                        pmask.add(null);
                    } else {
                        meta = getMetadata(idx);

                        if (idx > 0 && ((i % columns) == 0) == (meta.recipeId != null && meta.ingredient
                                && meta.recipeId.equals(getRecipeId(idx - 1)))) {
                            pmask.add(null);
                        } else {
                            pmask.add(idx++);

                            if ((i % columns) == 0) {
                                todoItemsCount++;
                            }
                        }
                    }
                }

                gridMask.add(pmask);
            }
        }

        protected void onGridChanged() {
            super.onGridChanged();

            if (getViewMode() == BookmarkViewMode.TODO_LIST) {
                sortIngredients(true);
                generateToDoSpaces();
            }
        }

        public int indexOf(ItemStack stackA, BookmarkRecipeId recipeId) {
            final boolean useNBT = NEIClientConfig.useNBTInBookmarks();

            for (int idx = 0; idx < realItems.size(); idx++) {
                final BookmarkStackMeta meta = getMetadata(idx);
                if ((recipeId == null && meta.recipeId == null
                        || recipeId != null && meta.recipeId != null && recipeId.equals(meta.recipeId))
                        && StackInfo.equalItemAndNBT(stackA, getItem(idx), useNBT)) {
                    return idx;
                }
            }

            return -1;
        }

        public BookmarkStackMeta getMetadata(int idx) {
            return metadata.get(idx);
        }

        public void addItem(ItemStack stackA, BookmarkStackMeta meta) {
            addItem(stackA, meta, true);
        }

        public void addItem(ItemStack stackA, BookmarkStackMeta meta, boolean animate) {
            realItems.add(stackA);
            metadata.add(meta);

            if (animate && !NEIClientConfig.shouldCacheItemRendering() && NEIClientConfig.areBookmarksAnimated()) {
                animation.put(stackA, 0f);
            }

            onGridChanged();
        }

        public void replaceItem(int idx, ItemStack stack) {
            realItems.set(idx, stack);
            onGridChanged();
        }

        public void removeRecipe(int idx, boolean removeFullRecipe) {
            final BookmarkStackMeta meta = getMetadata(idx);

            if (meta.recipeId != null && (removeFullRecipe || !meta.ingredient)) {
                removeRecipe(meta.recipeId);
            } else {
                removeItem(idx);
            }
        }

        public void removeRecipe(BookmarkRecipeId recipeIdA) {
            BookmarkRecipeId recipeIdB;

            for (int slotIndex = metadata.size() - 1; slotIndex >= 0; slotIndex--) {
                recipeIdB = getRecipeId(slotIndex);

                if (recipeIdB != null && recipeIdB.equals(recipeIdA)) {
                    removeItem(slotIndex);
                }
            }
        }

        protected boolean removeItem(int idx) {
            animation.remove(getItem(idx));
            realItems.remove(idx);
            metadata.remove(idx);
            onGridChanged();
            return true;
        }

        public BookmarkRecipeId getRecipeId(int idx) {
            return getMetadata(idx).recipeId;
        }

        public BookmarkRecipeId findRecipeId(ItemStack stackA) {
            final boolean useNBT = NEIClientConfig.useNBTInBookmarks();

            for (int idx = 0; idx < realItems.size(); idx++) {
                if (StackInfo.equalItemAndNBT(stackA, getItem(idx), useNBT)) {
                    return getRecipeId(idx);
                }
            }

            return null;
        }

        public void moveItem(int src, int dst) {
            realItems.add(dst, realItems.remove(src));
            metadata.add(dst, metadata.remove(src));
            onGridChanged();
        }

        private boolean isPartOfFocusedRecipe(ItemPanelSlot focused, int myIdx) {
            return NEIClientUtils.shiftKey() && focused != null
                    && LayoutManager.bookmarkPanel.sortedStackIndex == -1
                    && getRecipeId(focused.slotIndex) != null
                    && getRecipeId(myIdx) != null
                    && getRecipeId(focused.slotIndex).equals(getRecipeId(myIdx));
        }

        @Override
        protected void drawSlotOutline(@Nullable ItemPanelSlot focus, int idx, Rectangle4i rect) {

            if (LayoutManager.bookmarkPanel.sortedNamespaceIndex == LayoutManager.bookmarkPanel.activeNamespaceIndex
                    && LayoutManager.bookmarkPanel.sortedStackIndex == idx) {
                drawRect(rect.x, rect.y, rect.w, rect.h, 0xee555555); // highlight
            } else if (focus != null) {
                BookmarkStackMeta meta = getMetadata(idx);
                if (isPartOfFocusedRecipe(focus, idx)) {
                    drawRect(rect.x, rect.y, rect.w, rect.h, meta.ingredient ? 0x88b3b300 : 0x88009933); // highlight
                                                                                                         // recipe
                } else if (focus.slotIndex == idx) {
                    drawRect(rect.x, rect.y, rect.w, rect.h, 0xee555555); // highlight
                }
            }
        }

        @Override
        protected void drawItem(Rectangle4i rect, int idx) {
            if (LayoutManager.bookmarkPanel.sortedNamespaceIndex != LayoutManager.bookmarkPanel.activeNamespaceIndex
                    || LayoutManager.bookmarkPanel.sortedStackIndex != idx) {
                final ItemStack stack = getItem(idx);
                final BookmarkStackMeta meta = getMetadata(idx);
                final String stackSize = meta.factor < 0 || meta.fluidDisplay ? ""
                        : ReadableNumberConverter.INSTANCE.toWideReadableForm(stack.stackSize);
                if (animation.containsKey(stack) && animation.get(stack) < 1) {
                    final float currentScale = animation.get(stack) + SCALE_SPEED;
                    animation.put(stack, currentScale);
                    drawPoppingItem(rect, stack, stackSize, currentScale);
                } else {
                    GuiContainerManager.drawItem(rect.x + 1, rect.y + 1, stack, true, stackSize);
                    if (meta.recipeId != null && !meta.ingredient && NEIClientConfig.showRecipeMarker()) {
                        drawRecipeMarker(rect.x, rect.y, GuiContainerManager.getFontRenderer(stack));
                    }
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

        protected void drawRecipeMarker(int offsetX, int offsetY, FontRenderer fontRenderer) {
            final float scaleFactor = fontRenderer.getUnicodeFlag() ? 0.85f : 0.5f;
            final float inverseScaleFactor = 1.0f / scaleFactor;

            GuiContainerManager.enable2DRender();
            GL11.glScaled(scaleFactor, scaleFactor, scaleFactor);

            final int X = (int) (((float) offsetX + 1.0f) * inverseScaleFactor);
            final int Y = (int) (((float) offsetY + 1.0f) * inverseScaleFactor);
            fontRenderer.drawStringWithShadow("R", X, Y, 0xA0A0A0);

            GL11.glScaled(inverseScaleFactor, inverseScaleFactor, inverseScaleFactor);
            GuiContainerManager.enable3DRender();
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

                if (rightclick) {
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
                if (rightclick) {
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
                IBookmarkContainerHandler containerHandler = new DefaultBookmarkContainerHandler();
                containerHandler.pullBookmarkItemsFromContainer(
                        getGuiContainer(),
                        ((BookmarkGrid) grid).realItems);
                return true;
            }

            @Override
            public String getRenderLabel() {
                return "P";
            }
        };

        namespaces.add(new BookmarkGrid());
        grid = namespaces.get(activeNamespaceIndex);
    }

    @Override
    public String getLabelText() {
        int size = grid.size();

        if (((BookmarkGrid) grid).getViewMode() == BookmarkViewMode.TODO_LIST) {
            size = ((BookmarkGrid) grid).todoItemsCount;
        }

        return String.format("(%d/%d) [%d]", getPage(), Math.max(1, getNumPages()), size);
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

    public int getNameSpaceSize() {
        return namespaces.size();
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
            final ItemStack normalizedA = StackInfo.loadFromNBT(nbTagA);
            BookmarkRecipeId recipeId = null;

            if (handlerName != "" && ingredients != null && !ingredients.isEmpty()) {
                recipeId = new BookmarkRecipeId(handlerName, ingredients);
            }

            final int idx = BGrid.indexOf(normalizedA, recipeId);

            if (idx != -1) {
                BGrid.removeRecipe(idx, saveIngredients);
            } else {

                if (saveIngredients && handlerName != "" && ingredients != null && !ingredients.isEmpty()) {
                    final Map<NBTTagCompound, Integer> unique = new HashMap<>();
                    final ArrayList<NBTTagCompound> sorted = new ArrayList<>();

                    BGrid.removeRecipe(recipeId);

                    for (PositionedStack stack : ingredients) {
                        final NBTTagCompound nbTag = StackInfo.itemStackToNBT(stack.item, saveStackSize);

                        if (unique.get(nbTag) == null) {
                            unique.put(nbTag, 1);
                            sorted.add(nbTag);
                        } else {
                            unique.put(nbTag, unique.get(nbTag) + 1);
                        }
                    }

                    for (NBTTagCompound nbTag : sorted) {
                        final int count = nbTag.getInteger("Count") * unique.get(nbTag);
                        nbTag.setInteger("Count", count);
                        BGrid.addItem(
                                StackInfo.loadFromNBT(nbTag),
                                new BookmarkStackMeta(
                                        recipeId != null ? recipeId.copy() : null,
                                        (saveStackSize ? 1 : -1) * count,
                                        true,
                                        nbTag.hasKey("gtFluidName")));
                    }
                }

                BGrid.addItem(
                        normalizedA,
                        new BookmarkStackMeta(
                                recipeId,
                                (saveStackSize ? 1 : -1) * nbTagA.getInteger("Count"),
                                false,
                                nbTagA.hasKey("gtFluidName")));
            }
        }

        fixCountOfNamespaces();
        saveBookmarks();
    }

    public BookmarkRecipeId getBookmarkRecipeId(int slotIndex) {
        return ((BookmarkGrid) grid).getRecipeId(slotIndex);
    }

    public BookmarkRecipeId getBookmarkRecipeId(ItemStack stackA) {
        BookmarkRecipeId recipeId = ((BookmarkGrid) grid).findRecipeId(stackA);

        if (recipeId == null) {
            for (int idx = 0; idx < getNameSpaceSize() && recipeId == null; idx++) {
                if (idx == activeNamespaceIndex) continue;
                recipeId = namespaces.get(idx).findRecipeId(stackA);
            }
        }

        return recipeId;
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
            grid = namespaces.get(activeNamespaceIndex);
            return true;
        }

        return false;
    }

    protected boolean prevNamespace() {
        if (!bookmarksIsLoaded) {
            return false;
        }

        fixCountOfNamespaces();
        removeEmptyNamespaces();

        if (activeNamespaceIndex == 0) {
            activeNamespaceIndex = getNameSpaceSize() - 1;
        } else {
            activeNamespaceIndex--;
        }

        grid = namespaces.get(activeNamespaceIndex);

        return true;
    }

    protected boolean nextNamespace() {
        if (!bookmarksIsLoaded) {
            return false;
        }

        if (removeEmptyNamespaces()) {
            return true;
        }

        if (activeNamespaceIndex == fixCountOfNamespaces() - 1) {
            activeNamespaceIndex = 0;
        } else {
            activeNamespaceIndex++;
        }

        grid = namespaces.get(activeNamespaceIndex);

        return true;
    }

    public void setBookmarkFile(String worldPath) {

        final File dir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/" + worldPath);

        if (!dir.exists()) {
            dir.mkdirs();
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

        bookmarksIsLoaded = false;
    }

    public void saveBookmarks() {

        if (bookmarkFile == null) {
            return;
        }

        ArrayList<String> strings = new ArrayList<>();

        for (int grpIdx = 0; grpIdx < getNameSpaceSize() - 1; grpIdx++) {
            final BookmarkGrid grid = namespaces.get(grpIdx);

            JsonObject settings = new JsonObject();
            settings.add("viewmode", new JsonPrimitive(grid.getViewMode().toString()));
            settings.add("active", new JsonPrimitive(activeNamespaceIndex == grpIdx));
            strings.add("; " + NBTJson.toJson(settings));

            for (int idx = 0; idx < grid.size(); idx++) {

                try {
                    final NBTTagCompound nbTag = StackInfo.itemStackToNBT(grid.getItem(idx));

                    if (nbTag != null) {
                        JsonObject row = new JsonObject();
                        BookmarkStackMeta meta = grid.getMetadata(idx);

                        row.add("item", NBTJson.toJsonObject(nbTag));
                        row.add("factor", new JsonPrimitive(meta.factor));
                        row.add("ingredient", new JsonPrimitive(meta.ingredient));

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

        if (bookmarksIsLoaded) {
            return;
        }

        bookmarksIsLoaded = true;

        if (bookmarkFile == null || !bookmarkFile.exists()) {
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

        namespaces.clear();
        namespaces.add(new BookmarkGrid());
        activeNamespaceIndex = -1;

        final JsonParser parser = new JsonParser();
        int namespaceIndex = 0;

        for (String itemStr : itemStrings) {

            try {

                if (itemStr.isEmpty()) {
                    itemStr = "; {}";
                }

                if (itemStr.startsWith("; ")) {
                    JsonObject settings = parser.parse(itemStr.substring(2)).getAsJsonObject();

                    if (namespaces.get(namespaceIndex).size() > 0) {
                        // do not create empty namespaces
                        namespaces.add(new BookmarkGrid());
                        namespaceIndex++;
                    }

                    if (settings.get("viewmode") != null) {
                        namespaces.get(namespaceIndex)
                                .setViewMode(BookmarkViewMode.valueOf(settings.get("viewmode").getAsString()));
                    }

                    if (settings.get("active") != null && settings.get("active").getAsBoolean()) {
                        activeNamespaceIndex = namespaceIndex;
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
                    namespaces.get(namespaceIndex).addItem(
                            itemStack,
                            new BookmarkStackMeta(
                                    recipeId,
                                    jsonObject.has("factor") ? jsonObject.get("factor").getAsInt() : 0,
                                    jsonObject.has("ingredient") ? jsonObject.get("ingredient").getAsBoolean() : false,
                                    itemStackNBT.hasKey("gtFluidName")),
                            false);
                } else {
                    NEIClientConfig.logger.warn(
                            "Failed to load bookmarked ItemStack from json string, the item no longer exists:\n{}",
                            itemStr);
                }

            } catch (IllegalArgumentException | JsonSyntaxException e) {
                NEIClientConfig.logger.error("Failed to load bookmarked ItemStack from json string:\n{}", itemStr);
            }
        }

        if (activeNamespaceIndex == -1) {
            activeNamespaceIndex = namespaceIndex;
        }

        grid = namespaces.get(activeNamespaceIndex);
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
            LayoutManager.addWidget(pullBookmarkedItems);
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

        final BookmarkStackMeta meta = ((BookmarkGrid) grid).getMetadata(mouseDownSlot);
        int amount = stack.stackSize;

        if (meta.factor < 0 && !meta.fluidDisplay) {
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

            if (sortedStackIndex == -1) {
                final ItemStack stack = grid.getItem(mouseDownSlot);

                if (stack != null
                        && (mouseOverSlot == null || mouseOverSlot.slotIndex != mouseDownSlot || heldTime > 250)) {
                    sortedNamespaceIndex = activeNamespaceIndex;
                    sortedStackIndex = mouseDownSlot;
                    grid.onGridChanged();
                }

            } else {

                if (sortedNamespaceIndex != activeNamespaceIndex) {
                    final BookmarkGrid hoverGrid = namespaces.get(activeNamespaceIndex);
                    final BookmarkGrid sortedGrid = namespaces.get(sortedNamespaceIndex);

                    if (mouseOverSlot != null || getNextSlot(new Point(mousex, mousey)) >= 0) {
                        final ItemStack stack = sortedGrid.getItem(sortedStackIndex);
                        final BookmarkStackMeta meta = sortedGrid.getMetadata(sortedStackIndex);

                        sortedGrid.removeItem(sortedStackIndex);
                        hoverGrid.addItem(stack, meta, false);

                        sortedNamespaceIndex = activeNamespaceIndex;
                        sortedStackIndex = hoverGrid.indexOf(stack, meta.recipeId);
                    }

                } else if (mouseOverSlot == null || mouseOverSlot.slotIndex != sortedStackIndex) {
                    final BookmarkGrid sortedGrid = namespaces.get(sortedNamespaceIndex);

                    if (mouseOverSlot != null && sortedGrid.getViewMode() == BookmarkViewMode.DEFAULT) {
                        sortedGrid.moveItem(sortedStackIndex, mouseOverSlot.slotIndex);
                        sortedStackIndex = mouseOverSlot.slotIndex;
                    } else if (sortedGrid.getViewMode() == BookmarkViewMode.TODO_LIST) {
                        final ItemStack stack = sortedGrid.getItem(sortedStackIndex);
                        final BookmarkStackMeta meta = sortedGrid.getMetadata(sortedStackIndex);
                        final boolean isIngredient = sortedStackIndex > 0 && meta.recipeId != null
                                && meta.ingredient
                                && meta.recipeId.equals(sortedGrid.getRecipeId(sortedStackIndex - 1));

                        if (!isIngredient) {
                            mouseOverSlot = getSlotMouseOver(grid.marginLeft + grid.paddingLeft, mousey);

                            if (mouseOverSlot != null && sortedStackIndex != mouseOverSlot.slotIndex) {
                                sortedGrid.moveItem(sortedStackIndex, mouseOverSlot.slotIndex);
                                sortedStackIndex = sortedGrid.indexOf(stack, meta.recipeId);
                            }

                        } else if (mouseOverSlot != null && meta.recipeId != null
                                && sortedGrid.getMetadata(mouseOverSlot.slotIndex).ingredient
                                && meta.recipeId.equals(sortedGrid.getRecipeId(mouseOverSlot.slotIndex))) {
                                    sortedGrid.moveItem(sortedStackIndex, mouseOverSlot.slotIndex);
                                    sortedStackIndex = sortedGrid.indexOf(stack, meta.recipeId);
                                }
                    }
                }
            }

            return;
        }

        super.mouseDragged(mousex, mousey, button, heldTime);
    }

    private int getNextSlot(final Point point) {
        final List<Integer> mask = grid.getMask();
        final int columns = grid.getColumns();
        final int perPage = grid.getRows() * columns;
        final boolean line = ((BookmarkGrid) grid).getViewMode() == BookmarkViewMode.TODO_LIST;

        for (int i = mask.size(); i < perPage; i++) {
            if (!grid.isInvalidSlot(i) && (!line || (i % columns) == 0)) {

                if (point == null || grid.getSlotRect(i).contains(point.x, point.y)) {
                    return i;
                }

                return -1;
            }
        }

        return -1;
    }

    @Override
    public void postDraw(int mousex, int mousey) {

        if (sortedStackIndex != -1) {
            GuiContainerManager.drawItems.zLevel += 100;
            GuiContainerManager.drawItem(
                    mousex - 8,
                    mousey - 8,
                    namespaces.get(sortedNamespaceIndex).getItem(sortedStackIndex).copy(),
                    true);
            GuiContainerManager.drawItems.zLevel -= 100;
        }

        if (ItemPanels.itemPanel.draggedStack != null && this.contains(mousex, mousey)) {
            final int idx = getNextSlot(null);

            if (idx >= 0) {
                Rectangle4i rect = grid.getSlotRect(idx);
                drawRect(rect.x, rect.y, rect.w, rect.h, 0xee555555); // highlight
            }
        }

        super.postDraw(mousex, mousey);
    }

    @Override
    public boolean handleClickExt(int mousex, int mousey, int button) {

        if (new Rectangle4i(pagePrev.x + pagePrev.w, pagePrev.y, pageNext.x - (pagePrev.x + pagePrev.w), pagePrev.h)
                .contains(mousex, mousey)) {
            final BookmarkGrid BGrid = (BookmarkGrid) grid;

            if (BGrid.getViewMode() == BookmarkViewMode.DEFAULT) {
                BGrid.setViewMode(BookmarkViewMode.TODO_LIST);
            } else {
                BGrid.setViewMode(BookmarkViewMode.DEFAULT);
            }

            saveBookmarks();
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
        if (new Rectangle4i(pullBookmarkedItems.x, pullBookmarkedItems.y, pullBookmarkedItems.w, pullBookmarkedItems.h).contains(mx, my)) {
            tooltip.add(translate("bookmark.pullBookmarkedItems.tip"));
        }

        return super.handleTooltip(mx, my, tooltip);
    }

    @Override
    public void mouseUp(int mousex, int mousey, int button) {
        if (sortedStackIndex != -1) {
            draggedStack = null;
            sortedNamespaceIndex = -1;
            sortedStackIndex = -1;
            mouseDownSlot = -1;
            grid.onGridChanged(); /* make sure grid redraws the new item */
            saveBookmarks();
        } else {
            super.mouseUp(mousex, mousey, button);
        }
    }

    @Override
    public boolean onMouseWheel(int shift, int mousex, int mousey) {

        if (new Rectangle4i(
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
                final BookmarkRecipeId recipeId = BGrid.getRecipeId(slot.slotIndex);
                final boolean addFullRecipe = NEIClientUtils.shiftKey();

                if (addFullRecipe) {
                    BookmarkStackMeta iMeta;

                    for (int slotIndex = grid.size() - 1; slotIndex >= 0; slotIndex--) {
                        iMeta = BGrid.getMetadata(slotIndex);

                        if (iMeta.recipeId != null && iMeta.recipeId.equals(recipeId) && slotIndex != slot.slotIndex) {
                            shiftStackSize(slotIndex, shift);
                        }
                    }
                }

                shiftStackSize(slot.slotIndex, shift);

                saveBookmarks();
                return true;
            }
        }

        return super.onMouseWheel(shift, mousex, mousey);
    }

    protected void shiftStackSize(int slotIndex, int shift) {
        final BookmarkGrid BGrid = (BookmarkGrid) grid;
        final NBTTagCompound nbTag = StackInfo.itemStackToNBT(BGrid.getItem(slotIndex), true);
        final BookmarkStackMeta meta = BGrid.getMetadata(slotIndex);
        final int factor = Math.abs(meta.factor);

        if (nbTag.getInteger("Count") == factor && meta.factor < 0 && shift > 0) {
            meta.factor = factor;
        } else {
            meta.factor = nbTag.getInteger("Count") == factor && shift < 0 ? -1 * factor : factor;
            if ((long) nbTag.getInteger("Count") + ((long) factor * shift) <= Integer.MAX_VALUE) {
                nbTag.setInteger("Count", Math.max(nbTag.getInteger("Count") + factor * shift, factor));
                BGrid.replaceItem(slotIndex, StackInfo.loadFromNBT(nbTag));
            }
        }
    }
}
