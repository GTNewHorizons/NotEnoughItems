package codechicken.nei;

import codechicken.core.CommonUtils;
import codechicken.lib.vec.Rectangle4i;
import codechicken.nei.util.NBTJson;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.BookmarkRecipeId;
import codechicken.nei.ItemPanel.ItemPanelSlot;
import codechicken.nei.guihook.GuiContainerManager;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;

import static codechicken.lib.gui.GuiDraw.getMousePosition;
import static codechicken.lib.gui.GuiDraw.drawRect;

import java.awt.Point;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class BookmarkPanel extends PanelWidget
{

    protected File bookmarkFile;
    protected boolean bookmarksIsLoaded = false;
    public int sortedStackIndex = -1;
    public int sortedNamespaceIndex = -1;


    public Button namespacePrev;
    public Button namespaceNext;
    public Label namespaceLabel;


    protected ArrayList<BookmarkGrid> namespaces = new ArrayList<>();
    protected int activeNamespaceIndex = 0;


    protected static class BookmarkStackMeta
    {
        public int factor;
        public BookmarkRecipeId recipeId;
        public boolean ingredient = false;
        public boolean fluidDisplay = false;


        public BookmarkStackMeta(BookmarkRecipeId recipeId, int count, boolean ingredient, boolean fluidDisplay)
        {
            this.recipeId = recipeId;
            this.factor = count;
            this.ingredient = ingredient;
            this.fluidDisplay = fluidDisplay;
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

                if (recipeIdB != null && recipeIdB.equals(recipeIdA)) {
                    removeItem(slotIndex);
                }

            }

        }
        
        protected boolean removeItem(int idx)
        {
            realItems.remove(idx);
            metadata.remove(idx);
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
        }

        @Override
        protected void drawItem(Rectangle4i rect, int idx, ItemPanelSlot focus)
        {

            if (LayoutManager.bookmarkPanel.sortedNamespaceIndex == LayoutManager.bookmarkPanel.activeNamespaceIndex && LayoutManager.bookmarkPanel.sortedStackIndex == idx) {
                drawRect(rect.x, rect.y, rect.w, rect.h, 0xee555555);//highlight
            } else {
                final ItemStack stack = getItem(idx);
                final BookmarkStackMeta meta = getMetadata(idx);

                if (focus != null) {

                    if (
                            LayoutManager.bookmarkPanel.sortedStackIndex == -1 && //disabled when sorting
                            NEIClientUtils.shiftKey() &&  //show only with shift key
                            getRecipeId(focus.slotIndex) != null && getRecipeId(idx) != null && getRecipeId(focus.slotIndex).equals(getRecipeId(idx))//is some recipeId
                    ) {
                        drawRect(rect.x, rect.y, rect.w, rect.h, meta.ingredient? 0x88b3b300: 0x88009933);//highlight recipe
                    } else if (focus.slotIndex == idx) {
                        drawRect(rect.x, rect.y, rect.w, rect.h, 0xee555555);//highlight
                    }

                }

                GuiContainerManager.drawItem(rect.x + 1, rect.y + 1, stack, true, meta.factor < 0 || meta.fluidDisplay? "": String.valueOf(stack.stackSize));

                if (meta.recipeId != null && !meta.ingredient && NEIClientConfig.showRecipeMarker()) {
                    drawRecipeMarker(rect.x, rect.y, GuiContainerManager.getFontRenderer(stack));
                }

            }

        }

        protected void drawRecipeMarker(int offsetX, int offsetY, FontRenderer fontRenderer )
        {
            final float scaleFactor = fontRenderer.getUnicodeFlag() ? 0.85f : 0.5f;
            final float inverseScaleFactor = 1.0f / scaleFactor;
    
            GuiContainerManager.enable2DRender();
            GL11.glScaled(scaleFactor, scaleFactor, scaleFactor);
    
            final int X = (int) ( ( (float) offsetX + 1.0f) * inverseScaleFactor );
            final int Y = (int) ( ( (float) offsetY + 1.0f) * inverseScaleFactor );
            fontRenderer.drawStringWithShadow("R", X, Y, 0xA0A0A0);
            
            GL11.glScaled(inverseScaleFactor, inverseScaleFactor, inverseScaleFactor);
            GuiContainerManager.enable3DRender();
        }

    }


    public BookmarkPanel()
    {
        grid = new BookmarkGrid();
    }

    @Override
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
        return "(" + getPage() + "/" + Math.max(1, getNumPages()) + ") [" + grid.size() + "]";
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

        if (slot != null && StackInfo.equalItemAndNBT(slot.item, stackover, true)) {
            BGrid.removeRecipe(slot.slotIndex, addFullRecipe);
        } else {
            final boolean saveStackSize = NEIClientUtils.controlKey();
            final NBTTagCompound nbTagA = StackInfo.itemStackToNBT(stackover, saveStackSize);
            final ItemStack normalizedA = StackInfo.loadFromNBT(nbTagA);
            BookmarkRecipeId recipeId = null;
            
            if (NEIClientConfig.saveCurrentRecipeInBookmarksEnabled() && handlerName != "" && ingredients != null && !ingredients.isEmpty()) {
                recipeId = new BookmarkRecipeId(handlerName, ingredients);
            }

            final int idx = BGrid.indexOf(normalizedA, recipeId);

            if (idx != -1) {
                BGrid.removeRecipe(idx, addFullRecipe);
            } else {

                if (addFullRecipe && handlerName != "" && ingredients != null && !ingredients.isEmpty()) {
                    final Map<NBTTagCompound, Integer> unique = new HashMap<>();
                    final ArrayList<NBTTagCompound> sorted = new ArrayList<>();

                    BGrid.removeRecipe(recipeId);

                    for (PositionedStack stack : ingredients) { 
                        final NBTTagCompound nbTag = StackInfo.itemStackToNBT(stack.item, saveStackSize);

                        if (unique.get(nbTag) == null) {
                            unique.put(nbTag, 1);
                            sorted.add(nbTag);
                        } else if (saveStackSize) {
                            unique.put(nbTag, unique.get(nbTag) + 1);
                        }

                    }

                    for (NBTTagCompound nbTag : sorted) { 
                        nbTag.setInteger("Count", nbTag.getInteger("Count") * unique.get(nbTag));
                        BGrid.addItem(StackInfo.loadFromNBT(nbTag), new BookmarkStackMeta(recipeId, (saveStackSize ? 1: -1) * nbTag.getInteger("Count"), true, nbTag.hasKey("gtFluidName")));
                    }

                }

                BGrid.addItem(normalizedA, new BookmarkStackMeta(recipeId, (saveStackSize ? 1: -1) * nbTagA.getInteger("Count"), false, nbTagA.hasKey("gtFluidName")));
            }
            

        }

        fixCountOfNamespaces();
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

    public void setBookmarkFile(String worldPath)
    {

        final File dir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/" + worldPath);

        if (!dir.exists()) {
            dir.mkdirs();
        }

        bookmarkFile = new File(dir, "bookmarks.ini");

        if (!bookmarkFile.exists()) {
            final File globalBookmarks = new File(CommonUtils.getMinecraftDir(), "saves/NEI/global/bookmarks.ini");
            final File configBookmarks = new File(NEIClientConfig.configDir, "bookmarks.ini");
            final File defaultBookmarks = configBookmarks.exists()? configBookmarks: globalBookmarks;

            if (defaultBookmarks.exists()) {

                try {
                    bookmarkFile.createNewFile();

                    InputStream src = new FileInputStream(defaultBookmarks);
                    OutputStream dst = new FileOutputStream(bookmarkFile);

                    IOUtils.copy(src, dst);

                    src.close();
                    dst.close();

                } catch(IOException e) {}
                
            }

        }

        bookmarksIsLoaded = false;
    }

    public void saveBookmarks()
    {

        if (bookmarkFile == null) {
            return;
        }

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

        try (FileOutputStream output = new FileOutputStream(bookmarkFile)) {
            IOUtils.writeLines(strings, "\n", output, "UTF-8");
        } catch (IOException e) {
            NEIClientConfig.logger.error("Filed to save bookmarks list to file {}", bookmarkFile, e);
        }

    }

    public void loadBookmarksIfNeeded()
    {

        if (bookmarksIsLoaded) {
            return;
        }

        bookmarksIsLoaded = true;

        if (bookmarkFile == null || !bookmarkFile.exists()) {
            return;
        }

        List<String> itemStrings;
        try (FileReader reader = new FileReader(bookmarkFile)) {
            NEIClientConfig.logger.info("Loading bookmarks from file {}", bookmarkFile);
            itemStrings = IOUtils.readLines(reader);
        } catch (IOException e) {
            NEIClientConfig.logger.error("Failed to load bookmarks from file {}", bookmarkFile, e);
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
                        jsonObject.has("factor")? jsonObject.get("factor").getAsInt(): 0,
                        jsonObject.has("ingredient")? jsonObject.get("ingredient").getAsBoolean(): false,
                        itemStackNBT.hasKey("gtFluidName")
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
    }


    @Override
    protected int resizeHeader(GuiContainer gui)
    {
        final LayoutStyleMinecraft layout = (LayoutStyleMinecraft) LayoutManager.getLayoutStyle();
        final int rows = (int) Math.ceil((double) layout.buttonCount / layout.numButtons);
        final int diff = rows * 18 + getMarginTop(gui) - y;

        if (diff > 0) {
            y += diff;
            h -= diff;
        }

        return super.resizeHeader(gui);
    }

    @Override
    protected int resizeFooter(GuiContainer gui)
    {
        final int BUTTON_SIZE = 16;

        final ButtonCycled button = LayoutManager.bookmarksButton;
        final int leftBorder = y + h > button.y? button.x + button.w + 2: x;
        final int rightBorder = x + w;
        final int center = leftBorder + Math.max(0, (rightBorder - leftBorder) / 2);
        int labelWidth = 2;

        namespacePrev.h = namespaceNext.h = BUTTON_SIZE;
        namespacePrev.w = namespaceNext.w = BUTTON_SIZE;
        namespacePrev.y = namespaceNext.y = y + h - BUTTON_SIZE;


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

        return BUTTON_SIZE + 2;
    }

    @Override
    public void setVisible()
    {
        super.setVisible();

        if (grid.getPerPage() > 0) {
            LayoutManager.addWidget(namespacePrev);
            LayoutManager.addWidget(namespaceNext);
            LayoutManager.addWidget(namespaceLabel);
        }
    }

    protected String getPositioningSettingName()
    {
        return "world.panels.bookmarks";
    }

    public int getMarginLeft(GuiContainer gui)
    {
        return PADDING;
    }

    public int getMarginTop(GuiContainer gui)
    {
        return PADDING;
    }

    public int getWidth(GuiContainer gui)
    {
        return gui.width - (gui.xSize + gui.width) / 2 - PADDING * 2;
    }

    public int getHeight(GuiContainer gui)
    {
        return gui.height - getMarginTop(gui) - PADDING;
    }


    protected ItemStack getDraggedStackWithQuantity(int mouseDownSlot)
    {
        final ItemStack stack = grid.getItem(mouseDownSlot);
        
        if (stack == null) {
            return null;
        }

        final BookmarkStackMeta meta = ((BookmarkGrid) grid).getMetadata(mouseDownSlot);
        int amount = stack.stackSize;

        if (meta.factor < 0 && !meta.fluidDisplay) {
            amount = NEIClientConfig.getItemQuantity();

            if (amount == 0) {
                amount = stack.getMaxStackSize();
            }

        }

        return NEIServerUtils.copyStack(stack, amount);
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
    public void mouseUp(int mousex, int mousey, int button)
    {
        if (sortedStackIndex != -1) {
            draggedStack = null;
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

    protected void shiftStackSize(int slotIndex, int shift)
    {
        final BookmarkGrid BGrid = (BookmarkGrid)grid;
        final NBTTagCompound nbTag = StackInfo.itemStackToNBT(BGrid.getItem(slotIndex), true);
        final BookmarkStackMeta meta = BGrid.getMetadata(slotIndex);
        final int factor = Math.abs(meta.factor);

        if (nbTag.getInteger("Count") == factor && meta.factor < 0 && shift > 0) {
            meta.factor = factor;
        } else {
            meta.factor = nbTag.getInteger("Count") == factor && shift < 0? -1 * factor: factor;
            nbTag.setInteger("Count", Math.max(nbTag.getInteger("Count") + factor * shift, factor));
            BGrid.replaceItem(slotIndex, StackInfo.loadFromNBT(nbTag));
        }

    }

}
