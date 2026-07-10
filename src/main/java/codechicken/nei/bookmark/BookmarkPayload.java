package codechicken.nei.bookmark;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.google.gson.JsonObject;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemQuantityField;
import codechicken.nei.ItemStackSet;
import codechicken.nei.bookmark.BookmarkItem.BookmarkItemType;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.Recipe.RecipeIngredient;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.util.NBTJson;

public class BookmarkPayload {

    private String linkType;
    private NBTTagCompound target;

    private NBTTagCompound group;

    private NBTTagList items = new NBTTagList();
    private NBTTagList recipes = new NBTTagList();

    private BookmarkPayload(String linkType, ItemStack stack) {
        this.linkType = linkType;
        this.target = stack != null ? StackInfo.itemStackToNBT(stack) : null;
    }

    public static BookmarkPayload of(NBTTagCompound nbt) {
        final BookmarkPayload payload = new BookmarkPayload(nbt.getString("linkType"), null);

        if (nbt.hasKey("item")) {
            payload.target = nbt.getCompoundTag("item");
        }

        if (nbt.hasKey("group")) {
            payload.group = nbt.getCompoundTag("group");
        }

        payload.items = nbt.getTagList("items", 10);
        payload.recipes = nbt.getTagList("recipes", 10);

        return payload;
    }

    public static BookmarkPayload of(BookmarkItem item) {
        final BookmarkPayload payload = new BookmarkPayload("recipe", item.getItemStack());
        final BookmarkGrid grid = ItemPanels.bookmarkPanel.getGrid();
        final Map<RecipeId, Integer> recipesIndexes = new HashMap<>();
        final RecipeId recipeId = item.recipeId;
        final int groupId = item.groupId;

        payload.items = new NBTTagList();
        payload.recipes = new NBTTagList();

        if (recipeId != null) {

            for (int itemIndex = 0; itemIndex < grid.size(); itemIndex++) {
                final BookmarkItem currentItem = grid.getBookmarkItem(itemIndex);

                if (currentItem != null && currentItem.groupId == groupId && recipeId.equals(currentItem.recipeId)) {
                    addBookmarkItemToPayload(payload, currentItem, recipesIndexes);
                }

            }

        } else {
            addBookmarkItemToPayload(payload, item, recipesIndexes);
        }

        return payload;
    }

    public static BookmarkPayload of(Recipe recipe) {
        final BookmarkPayload payload = new BookmarkPayload("recipe", recipe.getResult());
        final Map<RecipeId, Integer> recipesIndexes = new HashMap<>();
        final ItemStackSet seenIngredients = new ItemStackSet();
        final ItemStackSet seenResults = new ItemStackSet();

        for (RecipeIngredient result : recipe.getResults()) {
            ItemStack stack = result.getItemStack();
            if (!seenResults.contains(stack)) {
                final BookmarkItem item = BookmarkItem.builder(-1, stack, recipe, BookmarkItemType.RESULT).build();
                addBookmarkItemToPayload(payload, item, recipesIndexes);
                seenResults.add(stack);
            }
        }

        for (RecipeIngredient ingr : recipe.getIngredients()) {
            ItemStack stack = ingr.getItemStack();
            if (!seenIngredients.contains(stack)) {
                final BookmarkItem item = BookmarkItem.builder(-1, stack, recipe, BookmarkItemType.INGREDIENT).build();
                addBookmarkItemToPayload(payload, item, recipesIndexes);
                seenIngredients.add(stack);
            }
        }

        return payload;
    }

    public static BookmarkPayload of(int groupId) {
        final BookmarkPayload payload = new BookmarkPayload("group", null);
        final Map<RecipeId, Integer> recipesIndexes = new HashMap<>();
        final BookmarkGrid grid = ItemPanels.bookmarkPanel.getGrid();

        for (int itemIndex = 0; itemIndex < grid.size(); itemIndex++) {
            final BookmarkItem item = grid.getBookmarkItem(itemIndex);

            if (item != null && item.groupId == groupId) {
                addBookmarkItemToPayload(payload, item, recipesIndexes);
            }
        }

        payload.group = generateGroup(grid.getGroup(groupId));

        return payload;
    }

    public static BookmarkPayload of(ItemStack stackover, RecipeId recipeId) {
        final BookmarkPayload payload = new BookmarkPayload("item", stackover);
        final Map<RecipeId, Integer> recipesIndexes = new HashMap<>();
        final Point mousePos = GuiDraw.getMousePosition();

        if (ItemPanels.itemPanel.containsWithSubpanels(mousePos.x, mousePos.y)) {
            stackover = ItemQuantityField.prepareStackWithQuantity(stackover, 0);
        }

        final BookmarkItem item = BookmarkItem.builder(-1, stackover).recipeId(recipeId).type(BookmarkItemType.ITEM)
                .build();

        addBookmarkItemToPayload(payload, item, recipesIndexes);

        return payload;
    }

    private static NBTTagCompound generateGroup(BookmarkGroup group) {
        final NBTTagCompound groupTag = new NBTTagCompound();
        final NBTTagList collapsedRecipes = new NBTTagList();

        groupTag.setString("viewmode", group.viewMode.toString());
        groupTag.setBoolean("crafting", group.crafting != null);
        groupTag.setBoolean("collapsed", group.collapsed);

        for (RecipeId recipeId : group.collapsedRecipes) {
            collapsedRecipes.appendTag(NBTJson.toNbt(recipeId.toJsonObject()));
        }

        groupTag.setTag("collapsedRecipes", collapsedRecipes);

        return groupTag;
    }

    private static void addBookmarkItemToPayload(BookmarkPayload payload, BookmarkItem item,
            Map<RecipeId, Integer> recipesIndexes) {
        final JsonObject itemTag = item.toJsonObject();
        itemTag.remove("groupId");

        if (item.recipeId != null) {
            final int recipeIndex = recipesIndexes
                    .computeIfAbsent(item.recipeId, recipeId -> addRecipeToPayload(payload, recipeId, recipesIndexes));
            itemTag.addProperty("recipeId", recipeIndex);
        }

        payload.items.appendTag(NBTJson.toNbt(itemTag));
    }

    private static int addRecipeToPayload(BookmarkPayload payload, RecipeId recipeId,
            Map<RecipeId, Integer> recipesIndexes) {
        final NBTTagCompound recipeTag = new NBTTagCompound();
        final int recipeIndex = payload.recipes.tagCount();

        recipeTag.setTag("recipeId", NBTJson.toNbt(recipeId.toJsonObject()));
        recipeTag.setInteger("recipeIndex", recipeIndex);
        payload.recipes.appendTag(recipeTag);

        return recipeIndex;
    }

    public NBTTagCompound toNBT() {
        final NBTTagCompound payloadTag = new NBTTagCompound();
        payloadTag.setString("linkType", linkType);

        if (target != null) {
            payloadTag.setTag("item", target);
        }

        if (group != null) {
            payloadTag.setTag("group", group);
        }

        payloadTag.setTag("items", items);
        payloadTag.setTag("recipes", recipes);

        return payloadTag;
    }

    public String getLinkType() {
        return this.linkType;
    }

    public ItemStack getTargetStack() {
        return this.target != null ? StackInfo.loadFromNBT(this.target) : null;
    }

    public BookmarkGroup getGroup() {
        if (this.group == null) {
            return null;
        }

        final boolean crafting = this.group.hasKey("crafting") && this.group.getBoolean("crafting");
        final BookmarkViewMode viewMode;

        if (this.group.hasKey("viewmode")) {
            viewMode = BookmarkViewMode.valueOf(this.group.getString("viewmode"));
        } else {
            viewMode = BookmarkViewMode.TODO_LIST;
        }

        final BookmarkGroup group = new BookmarkGroup(viewMode, crafting);

        if (this.group.hasKey("collapsed")) {
            group.collapsed = this.group.getBoolean("collapsed");
        }

        if (this.group.hasKey("collapsedRecipes")) {
            final NBTTagList collapsedRecipes = this.group.getTagList("collapsedRecipes", 10);

            for (int i = 0; i < collapsedRecipes.tagCount(); i++) {
                final NBTTagCompound recipeTag = collapsedRecipes.getCompoundTagAt(i);
                final RecipeId recipeId = RecipeId.of((JsonObject) NBTJson.toJsonObject(recipeTag));
                group.collapsedRecipes.add(recipeId);
            }
        }

        return group;
    }

    public List<BookmarkItem> getBookmarkItems(int groupId) {
        final List<BookmarkItem> bookmarkItems = new ArrayList<>();
        final Map<Integer, RecipeId> recipesMap = new HashMap<>();

        for (int i = 0; i < recipes.tagCount(); i++) {
            final NBTTagCompound recipeTag = recipes.getCompoundTagAt(i);
            final RecipeId recipeId = RecipeId
                    .of((JsonObject) NBTJson.toJsonObject(recipeTag.getCompoundTag("recipeId")));
            recipesMap.put(recipeTag.getInteger("recipeIndex"), recipeId);
        }

        for (int i = 0; i < items.tagCount(); i++) {
            final NBTTagCompound itemTag = items.getCompoundTagAt(i);
            RecipeId recipeId = null;

            if (itemTag.hasKey("recipeId")) {
                recipeId = recipesMap.get(itemTag.getInteger("recipeId"));
                itemTag.removeTag("recipeId");
            }

            final BookmarkItem item = BookmarkItem.of((JsonObject) NBTJson.toJsonObject(itemTag));

            if (item != null) {
                item.groupId = groupId;
                item.recipeId = recipeId;
                bookmarkItems.add(item);
            }
        }

        return bookmarkItems;
    }

}
