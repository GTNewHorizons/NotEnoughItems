package codechicken.nei.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import codechicken.nei.util.NBTJson;
import codechicken.nei.PositionedStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BookmarkRecipeId
{

    public int position = -1;

    public int recipetype = -1;

    public String handlerName = null;

    public List<NBTTagCompound> ingredients = new ArrayList<>();

    
    public BookmarkRecipeId(String handlerName, List<PositionedStack> stacks)
    {
        this.handlerName = handlerName;

        for (PositionedStack pos : stacks) {
            final NBTTagCompound nbt = StackInfo.itemStackToNBT(getItemStackWithMinimumDamage(pos.items));
            if (nbt != null) {
                ingredients.add(nbt);
            }
        }

    }

    public BookmarkRecipeId(JsonObject json)
    {

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

    public BookmarkRecipeId()
    {

    }

    public boolean equalsIngredients(List<PositionedStack> stacks)
    {

        if (ingredients.size() != stacks.size()) {
            return false;
        }

        Short idx = 0;

        for (PositionedStack pStack : stacks) {
            final NBTTagCompound tagCompoundA = StackInfo.itemStackToNBT(getItemStackWithMinimumDamage(pStack.items));
            final NBTTagCompound tagCompoundB = ingredients.get(idx);

            if (tagCompoundB == null || !tagCompoundB.equals(tagCompoundA)) {
                return false;
            }

            idx++;
        }

        return true;
    }

    public ItemStack getItemStackWithMinimumDamage(ItemStack[] stacks)
    {
        int damage = Short.MAX_VALUE;
        ItemStack result = stacks[0];

        if (stacks.length > 1) {
            for (ItemStack stack : stacks) {
                if (stack.getItemDamage() < damage) {
                    damage = stack.getItemDamage();
                    result = stack;
                }
            }
        }

        return result.copy();
    }

    public JsonObject toJsonObject()
    {
        JsonObject json = new JsonObject();

        if (handlerName != null) {
            json.add("handlerName", new JsonPrimitive(handlerName));
            json.add("ingredients", convertIngredientsToJsonArray(ingredients));
        }

        return json;
    }

    protected List<NBTTagCompound> convertJsonArrayToIngredients(JsonArray arr)
    {
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

    protected JsonArray convertIngredientsToJsonArray(List<NBTTagCompound> ingredients)
    {
        JsonArray arr = new JsonArray();

        for (NBTTagCompound nbTag : ingredients) {
            arr.add(NBTJson.toJsonObject(nbTag));
        }

        return arr;
    }

    public boolean equals(Object anObject)
    {
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

    public BookmarkRecipeId copy()
    {
        BookmarkRecipeId recipeId = new BookmarkRecipeId();
        recipeId.handlerName = handlerName;
        recipeId.ingredients = ingredients;

        return recipeId;
    }

}
