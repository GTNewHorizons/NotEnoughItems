package codechicken.nei.bookmark;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.apache.commons.io.IOUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;

public class BookmarkStorage {

    protected List<BookmarkGrid> namespaces = new ArrayList<>();
    protected int activeNamespaceIndex = 0;
    protected File bookmarkFile = null;

    public BookmarkStorage() {
        this.namespaces.add(new BookmarkGrid());
    }

    public boolean prevNamespace() {
        getNamespaceSize();
        removeEmptyNamespaces();

        if (this.activeNamespaceIndex == 0) {
            setNamespace(getNamespaceSize() - 1);
        } else {
            setNamespace(this.activeNamespaceIndex - 1);
        }

        return true;
    }

    public boolean nextNamespace() {

        if (removeEmptyNamespaces()) {
            return true;
        }

        if (this.activeNamespaceIndex == getNamespaceSize() - 1) {
            setNamespace(0);
        } else {
            setNamespace(this.activeNamespaceIndex + 1);
        }

        return true;
    }

    private void setNamespace(int namespaceIndex) {
        this.activeNamespaceIndex = Math.min(namespaceIndex, this.namespaces.size() - 1);
        BookmarkGrid grid = this.namespaces.get(this.activeNamespaceIndex);

        if (grid.size() == 0 && this.activeNamespaceIndex > 0) {
            grid.setViewMode(
                    BookmarkGrid.DEFAULT_GROUP_ID,
                    this.namespaces.get(this.activeNamespaceIndex - 1).getViewMode(BookmarkGrid.DEFAULT_GROUP_ID));
        }

    }

    public int getNamespaceSize() {

        if (this.namespaces.get(this.namespaces.size() - 1).size() > 0) {
            this.namespaces.add(new BookmarkGrid());
        } else if (this.activeNamespaceIndex == this.namespaces.size() - 2 && getActiveGrid().size() == 0) {
            this.namespaces.remove(this.namespaces.size() - 1);
        }

        return this.namespaces.size();
    }

    public boolean isEmpty() {
        return this.namespaces.size() <= 1 && getActiveGrid().isEmpty();
    }

    public int getActiveIndex() {
        return this.activeNamespaceIndex;
    }

    public BookmarkGrid getActiveGrid() {
        return this.namespaces.get(this.activeNamespaceIndex);
    }

    private boolean removeEmptyNamespaces() {

        if (this.activeNamespaceIndex != this.namespaces.size() - 1 && getActiveGrid().size() == 0) {
            this.namespaces.remove(this.activeNamespaceIndex);
            setNamespace(this.activeNamespaceIndex);
            return true;
        }

        return false;
    }

    public void load(File bookmarkFile) {

        if (bookmarkFile.equals(this.bookmarkFile)) {
            return;
        }

        List<String> itemStrings = Collections.emptyList();

        if (bookmarkFile.exists()) {
            try (FileInputStream reader = new FileInputStream(bookmarkFile)) {
                NEIClientConfig.logger.info("Loading bookmarks from file {}", bookmarkFile);
                itemStrings = IOUtils.readLines(reader, StandardCharsets.UTF_8);
            } catch (IOException e) {
                NEIClientConfig.logger.error("Failed to load bookmarks from file {}", bookmarkFile, e);
                return;
            }
        }

        final JsonParser parser = new JsonParser();
        final List<BookmarkGrid> namespaces = new ArrayList<>();
        NBTTagCompound navigation = new NBTTagCompound();
        namespaces.add(new BookmarkGrid());
        BookmarkGrid grid = namespaces.get(0);

        if (NEIClientConfig.world.nbt.hasKey("bookmark")) {
            navigation = NEIClientConfig.world.nbt.getCompoundTag("bookmark");
        }

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

                    if (navigation.hasKey("namespacePage." + (namespaces.size() - 1))) {
                        grid.setPage(navigation.getInteger("namespacePage." + (namespaces.size() - 1)));;
                    }

                    if (settings.get("viewmode") != null) {
                        grid.groups.get(BookmarkGrid.DEFAULT_GROUP_ID).viewMode = BookmarkViewMode
                                .valueOf(settings.get("viewmode").getAsString());
                    } else if (settings.get("groups") instanceof JsonObject jsonObject) {
                        BookmarkGroup group;

                        for (Map.Entry<String, JsonElement> jsonEntry : jsonObject.entrySet()) {
                            if (jsonEntry.getValue() instanceof JsonObject value) {
                                group = new BookmarkGroup(
                                        value.has("viewmode")
                                                ? BookmarkViewMode.valueOf(value.get("viewmode").getAsString())
                                                : BookmarkViewMode.DEFAULT,
                                        value.has("crafting") && value.get("crafting").getAsBoolean());

                                group.collapsed = value.has("collapsed") && value.get("collapsed").getAsBoolean();

                                if (value.has("collapsedRecipes")) {
                                    for (JsonElement recipeId : value.get("collapsedRecipes").getAsJsonArray()) {
                                        if (recipeId instanceof JsonObject item) {
                                            group.collapsedRecipes.add(RecipeId.of(item));
                                        }
                                    }
                                }

                                grid.groups.put(Integer.valueOf(jsonEntry.getKey()), group);
                            }
                        }
                    }

                    continue;
                }

                if (!addItemToGrid(grid, parser.parse(itemStr).getAsJsonObject())) {
                    NEIClientConfig.logger.warn(
                            "Failed to load bookmarked ItemStack from json string, the item no longer exists:\n{}",
                            itemStr);
                }

            } catch (IllegalArgumentException | JsonSyntaxException | IllegalStateException e) {
                NEIClientConfig.logger.error("Failed to load bookmarked ItemStack from json string:\n{}", itemStr);
            }
        }

        prepareOldRecipeFormat(namespaces);

        for (BookmarkGrid gr : namespaces) {
            gr.onItemsChanged();
        }

        this.namespaces = namespaces;

        if (navigation.hasKey("namespaceIndex")) {
            setNamespace(navigation.getInteger("namespaceIndex"));
        } else {
            setNamespace(0);
        }

        this.bookmarkFile = bookmarkFile;
    }

    private boolean addItemToGrid(BookmarkGrid grid, JsonObject jsonObject) {
        NBTTagCompound itemStackNBT;

        if (jsonObject.get("item") != null) {
            itemStackNBT = (NBTTagCompound) NBTJson.toNbt(jsonObject.get("item"));
        } else { // old format
            itemStackNBT = (NBTTagCompound) NBTJson.toNbt(jsonObject);
        }

        ItemStack itemStack = StackInfo.loadFromNBT(itemStackNBT);

        if (itemStack != null) {
            RecipeId recipeId = null;
            boolean isFluid = itemStackNBT.hasKey("gtFluidName");
            int groupId = jsonObject.has("groupId") ? jsonObject.get("groupId").getAsInt()
                    : BookmarkGrid.DEFAULT_GROUP_ID;
            int factor = jsonObject.has("factor") ? Math.abs(jsonObject.get("factor").getAsInt()) : (isFluid ? 144 : 1);
            boolean isIngredient = jsonObject.has("ingredient") && jsonObject.get("ingredient").getAsBoolean();

            if (jsonObject.get("recipeId") instanceof JsonObject recipeJson) {
                recipeId = RecipeId.of(recipeJson);
            }

            if (!grid.groups.containsKey(groupId)) {
                groupId = BookmarkGrid.DEFAULT_GROUP_ID;
            }

            grid.addItem(BookmarkItem.of(groupId, itemStack, factor, recipeId, isIngredient), false);
        }

        return itemStack != null;
    }

    private void prepareOldRecipeFormat(List<BookmarkGrid> namespaces) {

        for (BookmarkGrid grid : namespaces) {
            if (grid.bookmarkItems.stream()
                    .noneMatch(item -> item.recipeId != null && item.recipeId.getResult() == null)) {
                continue;
            }

            final Map<String, RecipeId> recipes = new HashMap<>();

            for (BookmarkItem item : grid.bookmarkItems) {
                if (item.recipeId != null && !item.isIngredient && item.recipeId.getResult() == null) {
                    final JsonObject recipeJson = item.recipeId.toJsonObject();
                    final String recipeGUID = item.groupId + ":" + NBTJson.toJson(recipeJson);

                    if (!recipes.containsKey(recipeGUID)) {
                        recipeJson.add("result", NBTJson.toJsonObject(StackInfo.itemStackToNBT(item.itemStack)));
                        recipes.put(recipeGUID, item.recipeId = RecipeId.of(recipeJson));
                    }
                }
            }

            for (BookmarkItem item : grid.bookmarkItems) {
                if (item.recipeId != null && item.recipeId.getResult() == null) {
                    final JsonObject recipeJson = item.recipeId.toJsonObject();
                    final String recipeGUID = item.groupId + ":" + NBTJson.toJson(recipeJson);

                    if (recipes.containsKey(recipeGUID)) {
                        item.recipeId = recipes.get(recipeGUID).copy();
                        item.permutations = BookmarkItem
                                .generatePermutations(item.itemStack, item.recipeId, item.isIngredient);
                    } else {
                        item.recipeId = null;
                    }
                }
            }
        }
    }

    public void save() {

        if (this.bookmarkFile == null) {
            return;
        }

        List<String> strings = new ArrayList<>();
        NBTTagCompound navigation = new NBTTagCompound();
        navigation.setInteger("namespaceIndex", this.activeNamespaceIndex);

        for (int grpIdx = 0; grpIdx < namespaces.size(); grpIdx++) {
            BookmarkGrid grid = namespaces.get(grpIdx);
            JsonObject settings = new JsonObject();
            JsonObject groups = new JsonObject();

            for (int groupId : grid.groups.keySet()) {
                BookmarkGroup group = grid.groups.get(groupId);
                JsonObject groupJson = new JsonObject();
                groupJson.add("viewmode", new JsonPrimitive(group.viewMode.toString()));
                groupJson.add("crafting", new JsonPrimitive(group.crafting != null));
                groupJson.add("collapsed", new JsonPrimitive(group.collapsed));

                if (!group.collapsedRecipes.isEmpty()) {
                    JsonArray collapsedRecipes = new JsonArray();
                    for (RecipeId recipeId : group.collapsedRecipes) {
                        collapsedRecipes.add(recipeId.toJsonObject());
                    }
                    groupJson.add("collapsedRecipes", collapsedRecipes);
                }

                groups.add(String.valueOf(groupId), groupJson);
            }

            settings.add("groups", groups);
            strings.add("; " + NBTJson.toJson(settings));

            navigation.setInteger("namespacePage." + grpIdx, grid.getPage() - 1);

            for (BookmarkItem item : grid.bookmarkItems) {
                try {
                    JsonObject row = new JsonObject();

                    row.add("item", NBTJson.toJsonObject(StackInfo.itemStackToNBT(item.getItemStack())));
                    row.add("factor", new JsonPrimitive(item.getFactor()));
                    row.add("ingredient", new JsonPrimitive(item.isIngredient));

                    if (item.groupId != BookmarkGrid.DEFAULT_GROUP_ID) {
                        row.add("groupId", new JsonPrimitive(item.groupId));
                    }

                    if (item.recipeId != null) {
                        row.add("recipeId", item.recipeId.toJsonObject());
                    }

                    strings.add(NBTJson.toJson(row));
                } catch (JsonSyntaxException e) {
                    NEIClientConfig.logger.error("Failed to stringify bookmarked ItemStack to json string");
                }
            }
        }

        try (FileOutputStream output = new FileOutputStream(bookmarkFile)) {
            IOUtils.writeLines(strings, "\n", output, StandardCharsets.UTF_8);
            NEIClientConfig.world.nbt.setTag("bookmark", navigation);
        } catch (IOException e) {
            NEIClientConfig.logger.error("Filed to save bookmarks list to file {}", bookmarkFile, e);
        }
    }

}
