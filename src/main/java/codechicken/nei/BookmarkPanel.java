package codechicken.nei;

import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.util.NBTJson;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.BookmarkRecipeId;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.ItemsGrid.ItemPanelSlot;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.apache.commons.io.IOUtils;

import static codechicken.lib.gui.GuiDraw.getMousePosition;
import static codechicken.lib.gui.GuiDraw.drawRect;

import java.awt.Point;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class BookmarkPanel extends ItemPanel
{

    protected boolean bookmarksIsLoaded = false;
    public int sortedStackIndex = -1;
    public int sortedNamespaceIndex = -1;


    public Button namespacePrev;
    public Button namespaceNext;
    public Label namespaceLabel;


    protected ArrayList<BookmarkGrid> namespaces = new ArrayList<>();
    public int activeNamespaceIndex = 0;


    protected static class BookmarkStackMeta
    {
        public int factor;
        public BookmarkRecipeId recipeId;
        public boolean ingredient = false;


        public BookmarkStackMeta(BookmarkRecipeId recipeId, int count, boolean ingredient)
        {
            this.recipeId = recipeId;
            this.factor = count;
            this.ingredient = ingredient;
        }

    }

    protected static class BookmarkGrid extends ItemsGrid
    {
        protected List<BookmarkStackMeta> metadata = new ArrayList<>();

        public int indexOf(ItemStack stackA, BookmarkRecipeId recipeId)
        {
            final boolean useNBT = NEIClientConfig.useNBTInBookmarks();
        
            for (int idx = 0; idx < realItems.size(); idx++) {
                final BookmarkStackMeta meta = getMetadata(idx);
                if ((recipeId == null && meta.recipeId == null || recipeId != null && meta.recipeId != null && recipeId.equals(meta.recipeId)) && StackInfo.equalItemAndNBT(stackA, realItems.get(idx), useNBT)) {
                    return idx;
                }
            }
    
            return -1;
        }

        public BookmarkStackMeta getMetadata(int idx)
        {
            return metadata.get(idx);
        }
        
        public void addItem(ItemStack stackA, BookmarkStackMeta meta)
        {
            realItems.add(stackA);
            metadata.add(meta);
            needRefresh = true;
        }

        public void replaceItem(int idx, ItemStack stack)
        {
            realItems.set(idx, stack);
        }

        public void removeRecipe(int idx, boolean removeFullRecipe)
        {
            final BookmarkStackMeta meta = getMetadata(idx);

            if (removeFullRecipe && meta.recipeId != null) {
                removeRecipe(meta.recipeId);
            } else {
                removeItem(idx);
            }

        }

        public void removeRecipe(BookmarkRecipeId recipeIdA)
        {
            BookmarkRecipeId recipeIdB;

            for (int slotIndex = metadata.size() - 1; slotIndex >= 0; slotIndex--) {
                recipeIdB = getRecipeId(slotIndex);

                if (recipeIdB != null && recipeIdA.equals(recipeIdB)) {
                    removeItem(slotIndex);
                }

            }

        }
        
        protected boolean removeItem(int idx)
        {
            realItems.remove(idx);
            metadata.remove(idx);
            needRefresh = true;
            return true;
        }

        public BookmarkRecipeId getRecipeId(int idx)
        {
            return getMetadata(idx).recipeId;
        }

        public BookmarkRecipeId findRecipeId(ItemStack stackA)
        {
            final boolean useNBT = NEIClientConfig.useNBTInBookmarks();

            for (int idx = 0; idx < realItems.size(); idx++) {
                if (StackInfo.equalItemAndNBT(stackA, getItem(idx), useNBT)) {
                    return getRecipeId(idx);
                }
            }
            
            return null;
        }

        public void moveItem(int src, int dst)
        {
            realItems.add(dst, realItems.remove(src));
            metadata.add(dst, metadata.remove(src));
            needRefresh = true;
        }

        @Override
        protected void drawItem(Rectangle4i rect, int idx, ItemPanelSlot focus)
        {

            if (LayoutManager.bookmarkPanel.sortedNamespaceIndex == LayoutManager.bookmarkPanel.activeNamespaceIndex && LayoutManager.bookmarkPanel.sortedStackIndex == idx) {
                drawRect(rect.x, rect.y, rect.w, rect.h, 0xee555555);//highlight
            } else {

                if (focus != null) {

                    if (focus.slotIndex == idx) {
                        drawRect(rect.x, rect.y, rect.w, rect.h, 0xee555555);//highlight
                    } else if (
                            LayoutManager.bookmarkPanel.sortedStackIndex == -1 && //disabled when sorting
                            NEIClientUtils.shiftKey() &&  //show only with shift key
                            getRecipeId(focus.slotIndex) != null && getRecipeId(idx) != null && getRecipeId(focus.slotIndex).equals(getRecipeId(idx))//is some recipeId
                    ) {
                        drawRect(rect.x, rect.y, rect.w, rect.h, getMetadata(idx).ingredient? 0x88b3b300: 0x88009933);//highlight recipe
                    }
    
                }

                GuiContainerManager.drawItem(rect.x + 1, rect.y + 1, getItem(idx), true);
            }

        }

    }


    public BookmarkPanel()
    {
        grid = new BookmarkGrid();
    }

    public void init() 
    {
        super.init();

        namespaceLabel = new Label("1", true);

        namespacePrev = new Button("Prev")
        {
            public boolean onButtonPress(boolean rightclick)
            {

                if (rightclick) {
                    return false;
                }

                return prevNamespace();
            }

            @Override
            public String getRenderLabel()
            {
                return "<";
            }
        };

        namespaceNext = new Button("Next")
        {
            public boolean onButtonPress(boolean rightclick)
            {
                if (rightclick) {
                    return false;
                }

                return nextNamespace();
            }

            @Override
            public String getRenderLabel()
            {
                return ">";
            }
        };

        namespaces.add(new BookmarkGrid());
        grid = namespaces.get(activeNamespaceIndex);
    }

    @Override
    public String getLabelText()
    {
        return super.getLabelText() + " [" + grid.size() + "]";
    }

    public void addOrRemoveItem(ItemStack stackA)
    {
        addOrRemoveItem(stackA, "", null);
    }

    public void addOrRemoveItem(ItemStack stackover, String handlerName, List<PositionedStack> ingredients)
    {
        loadBookmarksIfNeeded();

        final Point mousePos =  getMousePosition();
        final ItemPanelSlot slot = getSlotMouseOver(mousePos.x, mousePos.y);
        final boolean addFullRecipe = NEIClientUtils.shiftKey();
        final BookmarkGrid BGrid = (BookmarkGrid) grid;

        if (slot != null) {
            BGrid.removeRecipe(slot.slotIndex, addFullRecipe);
        } else {
            final boolean saveStackSize = NEIClientUtils.controlKey();
            final NBTTagCompound nbTagA = StackInfo.itemStackToNBT(stackover, saveStackSize);
            final ItemStack normalizedA = StackInfo.loadFromNBT(nbTagA);
            BookmarkRecipeId recipeId = null;
            
            if (NEIClientConfig.saveCurrentRecipeInBookmarksEnabled() && handlerName != "" && ingredients != null && ingredients.size() > 0) {
                recipeId = new BookmarkRecipeId(handlerName, ingredients);
            }

            final int idx = BGrid.indexOf(normalizedA, recipeId);

            if (idx != -1) {
                BGrid.removeRecipe(idx, addFullRecipe);
            } else {

                if (addFullRecipe && handlerName != "" && ingredients != null) {
                    final Map<NBTTagCompound, Integer> unique = new HashMap<>();
                    final ArrayList<NBTTagCompound> sorted = new ArrayList<>();

                    BGrid.removeRecipe(recipeId);

                    for (PositionedStack stack : ingredients) { 
                        final NBTTagCompound nbTag = StackInfo.itemStackToNBT(stack.item, saveStackSize);

                        if (nbTag.getInteger("Count") > 0) {

                            if (unique.get(nbTag) == null) {
                                unique.put(nbTag, 1);
                                sorted.add(nbTag);
                            } else if (saveStackSize) {
                                unique.put(nbTag, unique.get(nbTag) + 1);
                            }

                        }

                    }

                    for (NBTTagCompound nbTag : sorted) { 
                        nbTag.setInteger("Count", nbTag.getInteger("Count") * unique.get(nbTag));
                        BGrid.addItem(StackInfo.loadFromNBT(nbTag), new BookmarkStackMeta(recipeId, nbTag.getInteger("Count"), true));
                    }

                }

                BGrid.addItem(normalizedA, new BookmarkStackMeta(recipeId, nbTagA.getInteger("Count"), false));
            }
            

        }

        saveBookmarks();
    }

    public BookmarkRecipeId getBookmarkRecipeId(ItemStack stackA)
    {

        final Point mousePos =  getMousePosition();
        final ItemPanelSlot slot = getSlotMouseOver(mousePos.x, mousePos.y);

        if (slot != null && StackInfo.equalItemAndNBT(slot.item, stackA, true)) {
            return ((BookmarkGrid) grid).getRecipeId(slot.slotIndex);
        }

        BookmarkRecipeId recipeId = ((BookmarkGrid) grid).findRecipeId(stackA);

        if (recipeId == null) {

            for (int idx = 0; idx < namespaces.size() && recipeId == null; idx++) {
                if (idx == activeNamespaceIndex) continue;
                recipeId = namespaces.get(idx).findRecipeId(stackA);
            }

        }

        return recipeId;
    }

    protected String getNamespaceLabelText(boolean shortFormat)
    {
        String activePage = String.valueOf(activeNamespaceIndex + 1);

        return shortFormat? activePage: (activePage + "/" + fixCountOfNamespaces());
    }

    protected int fixCountOfNamespaces()
    {

        if (namespaces.get(namespaces.size() - 1).size() > 0) {
            namespaces.add(new BookmarkGrid());
        } else if (activeNamespaceIndex == namespaces.size() - 2 && grid.size() == 0) {
            namespaces.remove(namespaces.size() - 1);
        }

        return namespaces.size();
    }

    protected boolean removeEmptyNamespaces()
    {
        
        if (activeNamespaceIndex != namespaces.size() - 1 && grid.size() == 0) {
            namespaces.remove(activeNamespaceIndex);
            grid = namespaces.get(activeNamespaceIndex);
            return true;
        }

        return false;
    }

    protected boolean prevNamespace()
    {
        if (!bookmarksIsLoaded) {
            return false;
        }
        
        fixCountOfNamespaces();
        removeEmptyNamespaces();

        if (activeNamespaceIndex == 0) {
            activeNamespaceIndex = namespaces.size() - 1;
        } else {
            activeNamespaceIndex --;
        }

        grid = namespaces.get(activeNamespaceIndex);

        return true;
    }

    protected boolean nextNamespace()
    {
        if (!bookmarksIsLoaded) {
            return false;
        }

        if (removeEmptyNamespaces()) {
            return true;
        }

        if (activeNamespaceIndex == fixCountOfNamespaces() - 1) {
            activeNamespaceIndex = 0;
        } else {
            activeNamespaceIndex ++;
        }

        grid = namespaces.get(activeNamespaceIndex);

        return true;
    }


    public void saveBookmarks()
    {

        ArrayList<String> strings = new ArrayList<>();

        for (int grpIdx = 0; grpIdx < namespaces.size() - 1; grpIdx++) {
            final BookmarkGrid grid = namespaces.get(grpIdx);

            if (grpIdx > 0) {
                strings.add("");//namespace separator; ignore first namespace
            }

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

        File file = NEIClientConfig.bookmarkFile;
        if (file != null) {
            try(FileWriter writer = new FileWriter(file)) {
                IOUtils.writeLines(strings, "\n", writer);
            } catch (IOException e) {
                NEIClientConfig.logger.error("Filed to save bookmarks list to file {}", file, e);
            }
        }

    }

    public void loadBookmarksIfNeeded()
    {

        if (bookmarksIsLoaded) {
            return;
        }

        bookmarksIsLoaded = true;

        File file = NEIClientConfig.bookmarkFile;
        if (file == null || !file.exists()) {
            return;
        }

        List<String> itemStrings;
        try (FileReader reader = new FileReader(file)) {
            NEIClientConfig.logger.info("Loading bookmarks from file {}", file);
            itemStrings = IOUtils.readLines(reader);
        } catch (IOException e) {
            NEIClientConfig.logger.error("Failed to load bookmarks from file {}", file, e);
            return;
        }

        namespaces.clear();
        namespaces.add(new BookmarkGrid());
        activeNamespaceIndex = 0;

        final JsonParser parser = new JsonParser();

        for (String itemStr: itemStrings) {
            try {

                if (itemStr.isEmpty()) {

                    if (namespaces.get(activeNamespaceIndex).size() > 0) {
                        //do not create empty namespaces
                        namespaces.add(new BookmarkGrid());
                        activeNamespaceIndex++;
                    }

                    continue;
                }

                JsonObject jsonObject = parser.parse(itemStr).getAsJsonObject();
                BookmarkRecipeId recipeId = null;
                NBTTagCompound itemStackNBT;

                if (jsonObject.get("item") != null) {
                    itemStackNBT = (NBTTagCompound) NBTJson.toNbt(jsonObject.get("item"));
                } else {// old format
                    itemStackNBT = (NBTTagCompound) NBTJson.toNbt(jsonObject);
                }

                if (jsonObject.get("recipeId") != null && jsonObject.get("recipeId") instanceof JsonObject) {
                    recipeId = new BookmarkRecipeId((JsonObject) jsonObject.get("recipeId"));
                }

                ItemStack itemStack = StackInfo.loadFromNBT(itemStackNBT);


                if (itemStack != null) {
                    namespaces.get(activeNamespaceIndex).addItem(itemStack, new BookmarkStackMeta(
                        recipeId,
                        jsonObject.has("factor")? jsonObject.get("factor").getAsInt(): 1,
                        jsonObject.has("ingredient")? jsonObject.get("ingredient").getAsBoolean(): false
                    ));
                } else {
                    NEIClientConfig.logger.warn("Failed to load bookmarked ItemStack from json string, the item no longer exists:\n{}", itemStr);
                }

            } catch (IllegalArgumentException | JsonSyntaxException e) {
                NEIClientConfig.logger.error("Failed to load bookmarked ItemStack from json string:\n{}", itemStr);
            }
        }

        if (namespaces.get(activeNamespaceIndex).size() == 0) {
            activeNamespaceIndex = Math.max(activeNamespaceIndex - 1, 0);
        }

        grid = namespaces.get(activeNamespaceIndex);
    }


    @Override
    public void resize(GuiContainer gui) 
    {
        loadBookmarksIfNeeded();
        super.resize(gui);

        final int buttonHeight = 16;
        final int buttonWidth = 16;

        final int buttonTop = gui.height - buttonHeight - 2;
        final int leftBorder = LayoutManager.bookmarksButton.x + LayoutManager.bookmarksButton.w + 2;
        final int rightBorder = x + w;
        final int center = leftBorder + (rightBorder - leftBorder) / 2;
        int labelWidth = 2;

        namespacePrev.h = namespaceNext.h = buttonHeight;
        namespacePrev.w = namespaceNext.w = buttonWidth;
        namespacePrev.y = namespaceNext.y = buttonTop;


        if (rightBorder - leftBorder >= 70) {
            labelWidth = 36;
            namespaceLabel.text = getNamespaceLabelText(false);
        } else {
            labelWidth = 18;
            namespaceLabel.text = getNamespaceLabelText(true);
        }

        namespaceLabel.y = buttonTop + 5;
        namespaceLabel.x = center;

        namespacePrev.x = center - labelWidth / 2 - 2 - namespacePrev.w;
        namespaceNext.x = center + labelWidth / 2 + 2;

    }

    @Override
    public void setVisible()
    {
        super.setVisible();

        LayoutManager.addWidget(namespacePrev);
        LayoutManager.addWidget(namespaceNext);
        LayoutManager.addWidget(namespaceLabel);
    }

    @Override
    public int getMarginLeft(GuiContainer gui)
    {
        return 5;
    }

    @Override
    public int getMarginTop(GuiContainer gui)
    {
        LayoutStyleMinecraft layout = (LayoutStyleMinecraft)LayoutManager.getLayoutStyle();
        return 2 + (((int)Math.ceil((double)layout.buttonCount / layout.numButtons)) * 18);
    }

    @Override
    public int getWidth(GuiContainer gui)
    {
        return LayoutManager.getLeftSize(gui) - ((gui.xSize + gui.width) / 2 + 3) - 16;
    }

    @Override
    public int getHeight(GuiContainer gui)
    {
        // - 19 = 22 + 1 -> input in bottom
        return gui.height - getMarginTop(gui) - 23;
    }


    @Override
    public void mouseDragged(int mousex, int mousey, int button, long heldTime)
    {

        if (button == 0 && NEIClientUtils.shiftKey() && mouseDownSlot >= 0) {
            ItemPanelSlot mouseOverSlot = getSlotMouseOver(mousex, mousey);

            if (sortedStackIndex == -1) {
                final ItemStack stack = grid.getItem(mouseDownSlot);

                if (stack != null && (mouseOverSlot == null || mouseOverSlot.slotIndex != mouseDownSlot || heldTime > 250)) {
                    sortedNamespaceIndex = activeNamespaceIndex;
                    sortedStackIndex = mouseDownSlot;
                }

            } else {

                if (mouseOverSlot == null && sortedNamespaceIndex != activeNamespaceIndex) {
                    mouseOverSlot = getNextSlot(mousex, mousey);
                }

                if (mouseOverSlot != null) {

                    if (sortedNamespaceIndex != activeNamespaceIndex) {
                        BookmarkGrid BGrid = namespaces.get(sortedNamespaceIndex);
                        BookmarkStackMeta meta = BGrid.getMetadata(sortedStackIndex);
                        ItemStack stack = BGrid.getItem(sortedStackIndex);
                        BGrid.removeItem(sortedStackIndex);
        
                        ((BookmarkGrid) grid).addItem(stack, meta);
        
                        sortedNamespaceIndex = activeNamespaceIndex;
                        sortedStackIndex = grid.size() - 1;
                    }
    
                    if (mouseOverSlot.slotIndex != sortedStackIndex) {
                        ((BookmarkGrid) grid).moveItem(sortedStackIndex, mouseOverSlot.slotIndex);
                        sortedStackIndex = mouseOverSlot.slotIndex;
                    }
                    
                }

            }

            return;
        }

        super.mouseDragged(mousex, mousey, button, heldTime);
    }

    private ItemPanelSlot getNextSlot(int mousex, int mousey)
    {
        int idx = grid.getPage() * grid.getPerPage() - grid.getPerPage();
        int gSize = grid.size();

        for (int i = 0; i < grid.getRows() * grid.getColumns() && idx <= gSize; i++) {

            if (!grid.isInvalidSlot(i)) {

                if (idx == gSize && grid.getSlotRect(i).contains(mousex, mousey)) {
                    return new ItemPanelSlot(idx, null);
                }

                idx++;
            }

        }

        return null;
    }

    @Override
    public void postDraw(int mousex, int mousey)
    {

        if (sortedStackIndex != -1) {
            GuiContainerManager.drawItems.zLevel += 100;
            GuiContainerManager.drawItem(mousex - 8, mousey - 8, namespaces.get(sortedNamespaceIndex).getItem(sortedStackIndex).copy(), true);
            GuiContainerManager.drawItems.zLevel -= 100;
        }

        super.postDraw(mousex, mousey);
    }

    @Override
    protected ItemStack getDraggedStackWithQuantity(int mouseDownSlot)
    {
        ItemStack stack = grid.getItem(mouseDownSlot);

        return stack != null? stack.copy(): null;
    }

    @Override
    public void mouseUp(int mousex, int mousey, int button)
    {
        if (sortedStackIndex != -1) {
            sortedNamespaceIndex = -1;
            sortedStackIndex = -1;
            mouseDownSlot = -1;
            saveBookmarks();
        } else {
            super.mouseUp(mousex, mousey, button);
        }
    }

    @Override
    public boolean onMouseWheel(int shift, int mousex, int mousey)
    {

        if (new Rectangle4i(namespacePrev.x, namespacePrev.y, namespaceNext.x + namespaceNext.w - namespacePrev.x, namespacePrev.h).contains(mousex, mousey)) {

            if (shift > 0) {
                prevNamespace();
            } else {
                nextNamespace();       
            }
            
            return true;
        }

        if (!contains(mousex, mousey)) {
            return false;
        }

        if (NEIClientUtils.controlKey()) {
            final ItemPanelSlot slot = getSlotMouseOver(mousex, mousey);

            if (slot != null) {
                final BookmarkGrid BGrid = (BookmarkGrid)grid;
                final BookmarkRecipeId recipeId = BGrid.getRecipeId(slot.slotIndex);
                final BookmarkStackMeta meta = BGrid.getMetadata(slot.slotIndex);
                final boolean addFullRecipe = NEIClientUtils.shiftKey();

                if (addFullRecipe) {
                    BookmarkStackMeta iMeta;

                    for (int slotIndex = grid.size() - 1; slotIndex >= 0; slotIndex--) {
                        iMeta = BGrid.getMetadata(slotIndex);
    
                        if (iMeta.recipeId != null && iMeta.recipeId.equals(recipeId)) {
                            NBTTagCompound nbTag = StackInfo.itemStackToNBT(BGrid.getItem(slotIndex), true);
                            nbTag.setInteger("Count", Math.max(nbTag.getInteger("Count") + iMeta.factor * shift, iMeta.factor));
                            BGrid.replaceItem(slotIndex, StackInfo.loadFromNBT(nbTag));
                        }
    
                    }

                }

                NBTTagCompound nbTag = StackInfo.itemStackToNBT(slot.item, true);
                nbTag.setInteger("Count", Math.max(nbTag.getInteger("Count") + meta.factor * shift, meta.factor));
                BGrid.replaceItem(slot.slotIndex, StackInfo.loadFromNBT(nbTag));

                saveBookmarks();
            }

        }

        return super.onMouseWheel(shift, mousex, mousey);
    }

}
