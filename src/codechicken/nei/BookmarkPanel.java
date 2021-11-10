package codechicken.nei;

import codechicken.nei.util.NBTJson;
import codechicken.nei.recipe.StackInfo;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import cpw.mods.fml.common.registry.GameData;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/*
 * TODO:
 *   1) Bookmark button to toggle visibility
 */
public class BookmarkPanel extends ItemPanel {

    protected ArrayList<String> _recipes = new ArrayList<>();
    protected boolean bookmarksIsLoaded = false;
    public int sortedStackIndex = -1;

    public void init() {
        super.init();
    }

    @Override
    protected void setItems() {
        realItems = _items;
    }

    public String getLabelText() {
        return super.getLabelText() + " [" + realItems.size() + "]";
    }

    public void addOrRemoveItem(ItemStack item) {
        addOrRemoveItem(item, "");
    }

    public void addOrRemoveItem(ItemStack item, String recipeId)
    {
        loadBookmarksIfNeeded();

        ItemStack normalized = StackInfo.normalize(item);

        if (normalized != null && !remove(normalized)) {
            _items.add(normalized);
            _recipes.add(recipeId);
        }

        saveBookmarks();
    }

    public String getRecipeId(ItemStack item)
    {
        int index = findStackIndex(item);

        if (index >= 0) {
            return  _recipes.get(index);
        }

        return "";
    }

    public int findStackIndex(ItemStack stack1)
    {
        boolean useNBT = NEIClientConfig.useNBTInBookmarks();
        int i = 0;

        for (ItemStack stack2 : _items) {

            if (stack1 == stack2 || stack1.isItemEqual(stack2) && (!useNBT || ItemStack.areItemStackTagsEqual(stack1, stack2))) {
                return i;
            }

            i++;
        }

        return -1;
    }

    public void saveBookmarks() {
        List<String> strings = new ArrayList<>();
        int index = 0;

        for (ItemStack item: _items) {
            NBTTagCompound nbTag = StackInfo.itemStackToNBT(item);

            if (nbTag != null) {
                nbTag.setString("recipeId", _recipes.get(index));
                strings.add(NBTJson.toJson(nbTag));
            }

            index++;
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

        if (bookmarksIsLoaded == true) {
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
        _items.clear();
        JsonParser parser = new JsonParser();
        for (String itemStr: itemStrings) {
            try {
                NBTTagCompound itemStackNBT = (NBTTagCompound) NBTJson.toNbt(parser.parse(itemStr));
                ItemStack itemStack = StackInfo.loadFromNBT(itemStackNBT);

                if (itemStack != null) {
                    String recipeId = "";

                    if (itemStackNBT.hasKey("recipeId")) {
                        recipeId = itemStackNBT.getString("recipeId");
                    }

                    if (itemStack != null) {
                        _items.add(itemStack);
                        _recipes.add(recipeId);
                    } else {
                        NEIClientConfig.logger.warn("Failed to load bookmarked ItemStack from json string, the item no longer exists:\n{}", itemStr);
                    }

                }

            } catch (IllegalArgumentException | JsonSyntaxException e) {
                NEIClientConfig.logger.error("Failed to load bookmarked ItemStack from json string:\n{}", itemStr);
            }
        }
    }

    @Override
    public void draw(int mousex, int mousey)
    {
        loadBookmarksIfNeeded();
        super.draw(mousex, mousey);
    }

    private boolean remove(ItemStack item) 
    {
        int index = findStackIndex(item);

        if (index >= 0) {
            _items.remove(index);
            _recipes.remove(index);
            return true; 
        }

        return false;
    }

    @Override
    public int getX(GuiContainer gui) {
        return 5;
    }

    @Override
    public int getMarginLeft() {
        return 5;
    }


    @Override
    public int getButtonTop() {
        LayoutStyleMinecraft layout = (LayoutStyleMinecraft)LayoutManager.getLayoutStyle();
        return 2 + (((int)Math.ceil((double)layout.buttonCount / layout.numButtons)) * 18);
    }

    @Override
    public int getNextX(GuiContainer gui) {
        return gui.width - prev.w - 2 - ((gui.xSize + gui.width) /2 + 2) - 16;
    }

    @Override
    public int getPrevX(GuiContainer gui) {
        return 2;
    }

    @Override
    public int getPageX(GuiContainer gui) {
        return gui.guiLeft * 3 / 2 + gui.xSize + 1 - ((gui.xSize + gui.width) / 2 + 2) - 16;
    }

    public int getHightAdjustment() {
        return 0;
    }

    public int getWidth(GuiContainer gui) {
        return LayoutManager.getLeftSize(gui) - ( (gui.xSize + gui.width) / 2 + 3 ) - 16;
    }

    @Override
    public void mouseDragged(int mousex, int mousey, int button, long heldTime)
    {

        if (button == 0 && NEIClientUtils.shiftKey() && mouseDownSlot >= 0) {
            ItemPanelSlot mouseOverSlot = getSlotMouseOver(mousex, mousey);

            if (mouseOverSlot == null) {
                return;
            }

            if (sortedStackIndex == -1) {
                ItemStack stack = _items.get(mouseDownSlot);

                if (stack != null && (mouseOverSlot == null || mouseOverSlot.slotIndex != mouseDownSlot || heldTime > 500)) {
                    sortedStackIndex = mouseDownSlot;
                }

            } else if (mouseOverSlot != null && mouseOverSlot.slotIndex != sortedStackIndex) {

                int maxStackIndex = Math.max(mouseOverSlot.slotIndex, sortedStackIndex);
                int slotIndex = mouseOverSlot.slotIndex;

                _items.add(slotIndex, _items.remove(sortedStackIndex));
                _recipes.add(slotIndex, _recipes.remove(sortedStackIndex));

                sortedStackIndex = mouseOverSlot.slotIndex;
            }

            return;
        }

        super.mouseDragged(mousex, mousey, button, heldTime);
    }

    @Override
    public void mouseUp(int mousex, int mousey, int button)
    {
        if (sortedStackIndex >= 0) {
            sortedStackIndex = -1;
            mouseDownSlot = -1;
            saveBookmarks();
        } else {
            super.mouseUp(mousex, mousey, button);
        }
    }

}
