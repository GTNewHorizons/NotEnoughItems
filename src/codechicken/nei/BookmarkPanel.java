package codechicken.nei;

import codechicken.nei.util.NBTJson;
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
        ItemStack normalized = normalize(item);
        if(!remove(normalized)) {
            _items.add(normalized);
        }
        saveBookmarks();
    }

    public static NBTTagCompound itemStackToNBT(ItemStack stack, NBTTagCompound nbTag)
    {
        String strId = Item.itemRegistry.getNameForObject(stack.getItem());
        nbTag.setString("strId", strId);
        nbTag.setByte("Count", (byte)stack.stackSize);
        nbTag.setShort("Damage", (short)stack.getItemDamage());

        if (stack.stackTagCompound != null)
        {
            nbTag.setTag("tag", stack.stackTagCompound);
        }

        return nbTag;
    }

    public static ItemStack loadFromNBT(NBTTagCompound nbtTag)
    {
        if (!nbtTag.hasKey("id")) {
            final short id = (short)GameData.getItemRegistry().getId(nbtTag.getString("strId"));
             nbtTag.setShort("id", id);
        }
        ItemStack stack = ItemStack.loadItemStackFromNBT(nbtTag);
        return stack;
    }

    public void saveBookmarks() {
        List<String> strings = new ArrayList<>();
        for (ItemStack item:_items) {
            strings.add(NBTJson.toJson(itemStackToNBT(item, new NBTTagCompound())));
        }
        File file = NEIClientConfig.bookmarkFile;
        if(file != null) {
            try(FileWriter writer = new FileWriter(file)) {
                IOUtils.writeLines(strings, "\n", writer);
            } catch (IOException e) {
                NEIClientConfig.logger.error("Filed to save bookmarks list to file {}", file, e);
            }
        }
    }

    public void loadBookmarks() {
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
                ItemStack itemStack = loadFromNBT(itemStackNBT);
                if (itemStack != null) {
                    _items.add(itemStack);
                } else {
                    NEIClientConfig.logger.warn("Failed to load bookmarked ItemStack from json string, the item no longer exists:\n{}", itemStr);
                }
            } catch (IllegalArgumentException | JsonSyntaxException e) {
                NEIClientConfig.logger.error("Failed to load bookmarked ItemStack from json string:\n{}", itemStr);
            }
        }
    }

    protected ItemStack normalize(ItemStack item) {
        ItemStack copy = item.copy();
        copy.stackSize = 1;
        return copy;
    }
    private boolean remove(ItemStack item) {
        int i = 0;
        for (ItemStack existing : _items) {
            if (existing == item || existing.isItemEqual(item)) {
                _items.remove(i);
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
                ItemStack cacheItem = _items.get(sortedStackIndex);
                String recipeId = _recipes.get(sortedStackIndex);

                if (sortedStackIndex == maxStackIndex) {

                    for (int i = sortedStackIndex - 1; i >= slotIndex; --i) {
                        _items.set(i + 1, _items.get(i));
                        _recipes.set(i + 1, _recipes.get(i));
                    }

                } else {

                    for (int i = sortedStackIndex + 1; i <= slotIndex; ++i) {
                        _items.set(i - 1, _items.get(i));
                        _recipes.set(i - 1, _recipes.get(i));
                    }

                }

                _items.set(slotIndex, cacheItem);
                _recipes.set(slotIndex, recipeId);
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
