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
    public List<ItemStack> ingredients = new ArrayList<>();

    public BookmarkRecipeId(String handlerName, List<PositionedStack> stacks, int recipetype, int position)
    {
        this.handlerName = handlerName;

        for (PositionedStack pStack : stacks) {
            ingredients.add(getItemStackWithMinimumDamage(pStack.items));
        }

        this.ingredients = ingredients;
        this.recipetype = recipetype;
        this.position = position;
    }

    public BookmarkRecipeId(String handlerName, List<PositionedStack> stacks)
    {
        this(handlerName, stacks, -1, -1);
    }

    public BookmarkRecipeId(JsonObject json)
    {

        if (json.get("handlerName") != null) {
            handlerName = json.get("handlerName").getAsString();
        }

        if (json.get("ingredients") != null) {
            JsonArray arr = (JsonArray) json.get("ingredients");
            List<ItemStack> itemStacks = convertJsonArrayToIngredients(arr);

            if (itemStacks != null) {
                ingredients = itemStacks;
            }
    
        }

    }

    public boolean equalsIngredients(List<PositionedStack> stacks)
    {

        if (ingredients.size() != stacks.size()) {
            return false;
        }

        Short idx = 0;

        for (PositionedStack pStack : stacks) {
            final ItemStack stackA = getItemStackWithMinimumDamage(pStack.items);
            final ItemStack stackB = ingredients.get(idx);

            if (stackB == null || !StackInfo.equalItemAndNBT(stackA, stackB, true)) {
                return false;
            }

            idx++;
        }

        return true;
    }

    protected ItemStack getItemStackWithMinimumDamage(ItemStack[] stacks)
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

    protected List<ItemStack> convertJsonArrayToIngredients(JsonArray arr)
    {
        List<ItemStack> ingredients = new ArrayList<>();

        for (JsonElement elem : arr) {
            final ItemStack stack = StackInfo.loadFromNBT((NBTTagCompound) NBTJson.toNbt(elem));

            if (stack == null) {
                return null;
            }

            ingredients.add(stack);
        }

        return ingredients;
    }

    protected JsonArray convertIngredientsToJsonArray(List<ItemStack> ingredients)
    {
        JsonArray arr = new JsonArray();

        for (ItemStack stack : ingredients) {
            final NBTTagCompound nbTag = StackInfo.itemStackToNBT(stack);

            if (nbTag == null) {
                return null;
            }

            arr.add(NBTJson.toJsonObject(nbTag));
        }

        return arr;
    }

}
