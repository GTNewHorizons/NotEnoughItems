package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import codechicken.nei.PositionedStack;
import codechicken.nei.util.NBTJson;

public class BookmarkRecipeId {

    public String handlerName = null;

    public List<NBTTagCompound> ingredients = new ArrayList<>();

    public BookmarkRecipeId(String handlerName, List<?> stacks) {
        this.handlerName = handlerName;

        for (Object pos : stacks) {

            if (pos instanceof PositionedStack item) {
                pos = StackInfo.getItemStackWithMinimumDamage(item.items);
            }

            if (pos instanceof ItemStack item) {
                pos = StackInfo.itemStackToNBT(item);
            }

            if (pos instanceof NBTTagCompound item) {
                ingredients.add(item);
            }
        }
    }

    public BookmarkRecipeId(JsonObject json) {

        if (json.get("handlerName") != null) {
            handlerName = json.get("handlerName").getAsString();
        }

        if (json.get("ingredients") != null) {
            JsonArray arr = (JsonArray) json.get("ingredients");
            List<NBTTagCompound> ingredients = convertJsonArrayToIngredients(arr);

            if (ingredients != null) {
                this.ingredients = ingredients;
            }
        }
    }

    public BookmarkRecipeId() {}

    public boolean equalsIngredients(List<PositionedStack> stacks) {

        if (ingredients.size() != stacks.size()) {
            return false;
        }

        Short idx = 0;

        for (PositionedStack pStack : stacks) {
            final NBTTagCompound tagCompoundA = StackInfo
                    .itemStackToNBT(StackInfo.getItemStackWithMinimumDamage(pStack.items));
            final NBTTagCompound tagCompoundB = ingredients.get(idx);

            if (tagCompoundB == null || !tagCompoundB.equals(tagCompoundA)) {
                return false;
            }

            idx++;
        }

        return true;
    }

    public JsonObject toJsonObject() {
        JsonObject json = new JsonObject();

        if (handlerName != null) {
            json.add("handlerName", new JsonPrimitive(handlerName));
            json.add("ingredients", convertIngredientsToJsonArray(ingredients));
        }

        return json;
    }

    protected List<NBTTagCompound> convertJsonArrayToIngredients(JsonArray arr) {
        List<NBTTagCompound> ingredients = new ArrayList<>();

        for (JsonElement elem : arr) {
            final NBTTagCompound nbt = (NBTTagCompound) NBTJson.toNbt(elem);

            if (nbt == null) {
                return null;
            }

            ingredients.add(nbt);
        }

        return ingredients;
    }

    protected JsonArray convertIngredientsToJsonArray(List<NBTTagCompound> ingredients) {
        JsonArray arr = new JsonArray();

        for (NBTTagCompound nbTag : ingredients) {
            arr.add(NBTJson.toJsonObject(nbTag));
        }

        return arr;
    }

    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }

        if (anObject instanceof BookmarkRecipeId) {
            final BookmarkRecipeId anRecipeId = (BookmarkRecipeId) anObject;

            if (!handlerName.equals(anRecipeId.handlerName)) {
                return false;
            }

            if (ingredients.size() != anRecipeId.ingredients.size()) {
                return false;
            }

            for (int idx = 0; idx < ingredients.size(); idx++) {
                if (!ingredients.get(idx).equals(anRecipeId.ingredients.get(idx))) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    public BookmarkRecipeId copy() {
        return new BookmarkRecipeId(handlerName, ingredients);
    }
}
