package codechicken.nei;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.commons.io.IOUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import codechicken.core.CommonUtils;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.ItemsGrid.MouseContext;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;

public class ItemHistoryPanel extends AbstractSubpanel<ItemsGrid<ItemHistoryPanel.HistoryGridSlot, MouseContext>> {

    protected File historyFile = null;

    public static class HistoryGridSlot extends ItemsGridSlot {

        public HistoryGridSlot(int slotIndex, int itemIndex, ItemStack item) {
            super(slotIndex, itemIndex, item);
        }

        @Override
        public RecipeId getRecipeId() {
            return FavoriteRecipes.getFavorite(this.item);
        }
    }

    public ItemHistoryPanel() {
        this.grid = new ItemsGrid<>() {

            protected List<HistoryGridSlot> gridMask;

            @Override
            protected void onGridChanged() {
                this.gridMask = null;
                super.onGridChanged();
            }

            @Override
            public List<HistoryGridSlot> getMask() {

                if (this.gridMask == null) {
                    this.gridMask = new ArrayList<>();
                    int itemIndex = 0;
                    for (int slotIndex = 0; slotIndex < this.rows * this.columns && itemIndex < size(); slotIndex++) {
                        if (!isInvalidSlot(slotIndex)) {
                            this.gridMask.add(new HistoryGridSlot(slotIndex, itemIndex, getItem(itemIndex)));
                            itemIndex++;
                        }
                    }

                    ItemHistoryPanel.this.updateLinePadding();
                }

                return this.gridMask;
            }

            @Override
            protected MouseContext getMouseContext(int mousex, int mousey) {
                final HistoryGridSlot hovered = getSlotMouseOver(mousex, mousey);

                if (hovered != null) {
                    return new MouseContext(
                            hovered.slotIndex,
                            hovered.slotIndex / this.columns,
                            hovered.slotIndex % this.columns);
                }

                return null;
            }

        };
    }

    @Override
    protected ItemStack getDraggedStackWithQuantity(ItemStack itemStack) {
        return ItemQuantityField.prepareStackWithQuantity(itemStack, 0);
    }

    @Override
    public void draw(int mousex, int mousey) {
        if (this.grid.size() > 0) {
            super.draw(mousex, mousey);
        }
    }

    @Override
    public int setPanelWidth(int width) {
        final int columns = width / ItemsGrid.SLOT_SIZE;
        final int useRows = NEIClientConfig.getIntSetting("inventory.history.useRows");
        final int rows = (int) Math.min(Math.ceil(this.grid.size() * 1f / columns), useRows);

        this.w = width;
        this.h = 8 + ItemsGrid.SLOT_SIZE * Math.max(rows, 1);

        return rows;
    }

    public void update() {
        this.splittingLineColor = NEIClientConfig.getSetting("inventory.history.color").getHexValue();
        super.update();
    }

    public void addItem(ItemStack stack) {
        if (stack != null) {
            ItemStack is = StackInfo.withAmount(stack, 0);

            this.grid.realItems.removeIf(historyStack -> StackInfo.equalItemAndNBT(historyStack, stack, true));
            this.grid.realItems.add(0, is);

            if (this.grid.realItems.size() > Math.max(50, this.grid.rows * this.grid.columns)) {
                this.grid.realItems.remove(this.grid.realItems.size() - 1);
            }

            this.grid.onItemsChanged();
        }
    }

    public void load() {

        if (!NEIClientConfig.getBooleanSetting("inventory.history.save")) {
            return;
        }

        String worldPath = "global";

        if (NEIClientConfig.getBooleanSetting("inventory.history.worldSpecific")) {
            worldPath = NEIClientConfig.getWorldPath();
        }

        final File dir = new File(CommonUtils.getMinecraftDir(), "saves/NEI/" + worldPath);

        if (!dir.exists()) {
            dir.mkdirs();
        }

        final File historyFile = new File(dir, "history.ini");

        if (historyFile.equals(this.historyFile)) {
            return;
        }

        List<String> itemStrings = Collections.emptyList();

        if (historyFile.exists()) {
            try (FileInputStream reader = new FileInputStream(historyFile)) {
                NEIClientConfig.logger.info("Loading history from file {}", historyFile);
                itemStrings = IOUtils.readLines(reader, StandardCharsets.UTF_8);
            } catch (IOException e) {
                NEIClientConfig.logger.error("Failed to load history from file {}", historyFile, e);
                return;
            }
        }

        final JsonParser parser = new JsonParser();
        final int maxSize = Math.max(50, this.grid.rows * this.grid.columns);

        this.grid.realItems.clear();

        for (String itemStr : itemStrings) {

            if (itemStr.isEmpty()) {
                continue;
            }

            if (this.grid.realItems.size() >= maxSize) {
                break;
            }

            try {
                JsonObject row = parser.parse(itemStr).getAsJsonObject();
                NBTTagCompound itemStackNBT = (NBTTagCompound) NBTJson.toNbt(row.get("item"));
                ItemStack itemStack = StackInfo.loadFromNBT(itemStackNBT);

                if (itemStack != null) {
                    this.grid.realItems.add(itemStack);
                } else {
                    NEIClientConfig.logger.warn(
                            "Failed to load history ItemStack from json string, the item no longer exists:\n{}",
                            itemStr);
                }
            } catch (Exception e) {
                NEIClientConfig.logger.error("Failed to load history ItemStack from json string:\n{}", itemStr);
            }
        }

        this.grid.onItemsChanged();

        this.historyFile = historyFile;
    }

    public void save() {

        if (!NEIClientConfig.getBooleanSetting("inventory.history.save")) {
            return;
        }

        if (this.historyFile == null) {
            return;
        }

        final List<String> strings = new ArrayList<>();

        for (ItemStack stack : this.grid.realItems) {

            try {
                final JsonObject row = new JsonObject();
                row.add("item", NBTJson.toJsonObject(StackInfo.itemStackToNBT(stack)));
                strings.add(NBTJson.toJson(row));
            } catch (JsonSyntaxException e) {
                NEIClientConfig.logger.error("Failed to stringify history ItemStack to json string");
            }
        }

        try (FileOutputStream output = new FileOutputStream(this.historyFile)) {
            IOUtils.writeLines(strings, "\n", output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NEIClientConfig.logger.error("Failed to save history list to file {}", this.historyFile, e);
        }
    }

}
