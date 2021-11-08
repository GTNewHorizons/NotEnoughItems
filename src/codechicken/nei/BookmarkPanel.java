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
        int i = 0;

        for (ItemStack existing : _items) {
            if (existing == item || existing.isItemEqual(item)) {
                return _recipes.get(i);
            }
            i++;
        }

        return "";
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

    private boolean remove(ItemStack item) {
        int i = 0;
        for (ItemStack existing : _items) {
            if (existing == item || existing.isItemEqual(item)) {
                _items.remove(i);
                _recipes.remove(i);
                return true;
            }
            i++;
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

}
