package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidContainerRegistry;

import com.google.common.base.Objects;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.util.NBTJson;

public class Recipe {

    public static class RecipeId {

        private static final String KEY_HANDLER_NAME = "handlerName";
        private static final String KEY_INGREDIENTS = "ingredients";
        private static final String KEY_RESULT = "result";

        private final String handlerName;
        private final List<ItemStack> ingredients;
        private final ItemStack result;

        protected RecipeId(ItemStack result, String handlerName, List<ItemStack> ingredients) {
            this.result = result != null ? StackInfo.withAmount(result, 0) : null;
            this.handlerName = handlerName;
            this.ingredients = ingredients;
        }

        public static RecipeId of(Object result, String handlerName, Iterable<?> ingredients) {
            return new RecipeId(extractItem(result), handlerName, extractItems(ingredients));
        }

        public static RecipeId of(JsonObject json) {
            String handlerName = null;
            List<ItemStack> ingredients = new ArrayList<>();
            ItemStack result = null;

            if (json.get(KEY_HANDLER_NAME) != null) {
                handlerName = json.get(KEY_HANDLER_NAME).getAsString();
            }

            if (json.get(KEY_INGREDIENTS) != null) {
                ingredients = convertJsonArrayToItems(json.getAsJsonArray(KEY_INGREDIENTS));
            }

            if (json.get(KEY_RESULT) != null) {
                result = StackInfo.loadFromNBT((NBTTagCompound) NBTJson.toNbt(json.getAsJsonObject(KEY_RESULT)));
            }

            return new RecipeId(result, handlerName, ingredients);
        }

        public String getHandleName() {
            return this.handlerName;
        }

        public ItemStack getResult() {
            return this.result;
        }

        public List<ItemStack> getIngredients() {
            return new ArrayList<>(this.ingredients);
        }

        protected static List<ItemStack> extractItems(Iterable<?> items) {
            final List<ItemStack> list = new ArrayList<>();

            for (Object item : items) {
                ItemStack stack = extractItem(item);

                if (stack != null) {
                    list.add(stack.copy());
                }
            }

            return list;
        }

        protected static ItemStack extractItem(Object item) {

            if (item instanceof PositionedStack positionedStack) {
                item = StackInfo.getItemStackWithMinimumDamage(positionedStack.items);
            }

            if (item instanceof RecipeIngredient ingr) {
                item = StackInfo.getItemStackWithMinimumDamage(ingr.getPermutations().toArray(new ItemStack[0]));
            }

            if (item instanceof NBTTagCompound nbTag) {
                return StackInfo.loadFromNBT(nbTag);
            }

            if (item instanceof ItemStack stack) {
                return stack;
            }

            return null;
        }

        public boolean equalsIngredients(List<PositionedStack> stacks) {

            if (ingredients.size() != stacks.size()) {
                return false;
            }

            for (int index = 0; index < stacks.size(); index++) {
                if (!stacks.get(index).containsWithNBT(ingredients.get(index))) {
                    return false;
                }
            }

            return true;
        }

        public JsonObject toJsonObject() {
            final JsonObject json = new JsonObject();

            if (this.handlerName != null && !"".equals(this.handlerName)) {
                json.add(KEY_HANDLER_NAME, new JsonPrimitive(this.handlerName));
                json.add(KEY_INGREDIENTS, convertItemsToJsonArray(this.ingredients));

                if (this.result != null) { // old format without result
                    json.add(KEY_RESULT, NBTJson.toJsonObject(StackInfo.itemStackToNBT(this.result)));
                }
            }

            return json;
        }

        protected JsonArray convertItemsToJsonArray(List<ItemStack> items) {
            final JsonArray arr = new JsonArray();

            for (ItemStack nbTag : items) {
                arr.add(NBTJson.toJsonObject(StackInfo.itemStackToNBT(nbTag)));
            }

            return arr;
        }

        protected static List<ItemStack> convertJsonArrayToItems(JsonArray arr) {
            final List<ItemStack> items = new ArrayList<>();

            for (JsonElement elem : arr) {
                final ItemStack nbt = StackInfo.loadFromNBT((NBTTagCompound) NBTJson.toNbt(elem));

                if (nbt == null) {
                    return new ArrayList<>();
                }

                items.add(nbt);
            }

            return items;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.handlerName, this.ingredients.size());
        }

        @Override
        public boolean equals(Object anObject) {
            if (this == anObject) {
                return true;
            }

            if (anObject instanceof RecipeId anRecipeId) {

                if (!this.handlerName.equals(anRecipeId.handlerName)) {
                    return false;
                }

                if (this.ingredients.size() != anRecipeId.ingredients.size()) {
                    return false;
                }

                if (this.result != null && anRecipeId.result != null
                        && !StackInfo.equalItemAndNBT(this.result, anRecipeId.result, true)) {
                    return false;
                }

                for (int idx = 0; idx < this.ingredients.size(); idx++) {
                    if (!StackInfo.equalItemAndNBT(this.ingredients.get(idx), anRecipeId.ingredients.get(idx), true)) {
                        return false;
                    }
                }

                return true;
            }

            return false;
        }

        public RecipeId copy() {
            return new RecipeId(this.result, this.handlerName, this.ingredients);
        }

    }

    public static class RecipeIngredient {

        protected final ItemStack[] items;
        protected int activeIndex = 0;
        protected int amount = 0;

        protected int relx;
        protected int rely;

        public RecipeIngredient(int relx, int rely, List<ItemStack> items, int activeIndex) {
            this.items = items.stream().map(ItemStack::copy).toArray(size -> new ItemStack[size]);
            this.amount = StackInfo.getAmount(this.items[activeIndex]);
            this.activeIndex = activeIndex;
            this.relx = relx;
            this.rely = rely;
        }

        public static RecipeIngredient of(PositionedStack positionedStack) {
            final List<ItemStack> stacks = Arrays.asList(positionedStack.items).stream()
                    .filter(item -> !ItemInfo.isHidden(item)).collect(Collectors.toList());
            int activeIndex = 0;

            if (stacks.isEmpty()) {
                stacks.addAll(Arrays.asList(positionedStack.items));
            }

            for (int i = 0; i < stacks.size(); i++) {
                if (NEIServerUtils.areStacksSameTypeCraftingWithNBT(positionedStack.item, stacks.get(i))) {
                    activeIndex = i;
                    break;
                }
            }

            return of(positionedStack.relx, positionedStack.rely, stacks, activeIndex);
        }

        public static RecipeIngredient of(int relx, int rely, List<ItemStack> stacks, int activeIndex) {
            return new RecipeIngredient(relx, rely, stacks, activeIndex);
        }

        public int getAmount() {
            return this.amount;
        }

        public ItemStack getItemStack() {
            return this.items[this.activeIndex];
        }

        public void setActiveIndex(int activeIndex) {
            this.activeIndex = Math.max(0, Math.min(activeIndex, this.items.length - 1));
        }

        public boolean contains(ItemStack stackA) {
            return getPermutations().stream().anyMatch(stackB -> StackInfo.equalItemAndNBT(stackA, stackB, true));
        }

        public List<ItemStack> getPermutations() {
            return Arrays.asList(this.items);
        }

        public RecipeIngredient setAmount(int amount) {

            for (int index = 0; index < this.items.length; index++) {
                this.items[index] = StackInfo.withAmount(this.items[index], amount);
            }

            return this;
        }

        public RecipeIngredient copy() {
            return new RecipeIngredient(
                    this.relx,
                    this.rely,
                    getPermutations().stream().map(ItemStack::copy).collect(Collectors.toList()),
                    this.activeIndex);
        }
    }

    private final String handlerName;
    private final RecipeIngredient[] results;
    private final RecipeIngredient[] ingredients;
    private RecipeId recipeId = null;

    private Recipe(String handlerName, List<RecipeIngredient> ingredients, List<RecipeIngredient> results) {
        this.handlerName = handlerName;
        this.ingredients = ingredients.toArray(new RecipeIngredient[ingredients.size()]);
        this.results = results.toArray(new RecipeIngredient[results.size()]);
    }

    public static Recipe of(IRecipeHandler handler, int recipeIndex) {
        final String handlerName = GuiRecipeTab.getHandlerInfo(handler).getHandlerName();
        final List<RecipeIngredient> ingredients = new ArrayList<>();
        final List<RecipeIngredient> results = new ArrayList<>();

        for (PositionedStack positionedStack : handler.getIngredientStacks(recipeIndex)) {
            ingredients.add(RecipeIngredient.of(positionedStack));
        }

        if (handler.getResultStack(recipeIndex) != null) {
            results.add(RecipeIngredient.of(handler.getResultStack(recipeIndex)));
        } else {
            for (PositionedStack positionedStack : handler.getOtherStacks(recipeIndex)) {
                results.add(RecipeIngredient.of(positionedStack));
            }
        }

        return new Recipe(handlerName, ingredients, results);
    }

    public static Recipe of(List<?> results, String handlerName, List<?> ingredients) {
        return new Recipe(handlerName, extractItems(ingredients), extractItems(results));
    }

    public static Recipe of(RecipeHandlerRef handlerRef) {
        return Recipe.of(handlerRef.handler, handlerRef.recipeIndex);
    }

    public static Recipe of(RecipeId recipeId) {
        if (recipeId == null) {
            return null;
        }

        final RecipeHandlerRef handlerRef = RecipeHandlerRef.of(recipeId);

        if (handlerRef != null) {
            return Recipe.of(handlerRef);
        }

        return null;
    }

    protected static List<RecipeIngredient> extractItems(List<?> items) {
        final List<RecipeIngredient> list = new ArrayList<>();

        for (Object item : items) {
            RecipeIngredient ingr = extractItem(item);

            if (ingr != null) {
                list.add(ingr);
            }
        }

        return list;
    }

    protected static RecipeIngredient extractItem(Object item) {

        if (item instanceof PositionedStack positionedStack) {
            return RecipeIngredient.of(positionedStack);
        }

        if (item instanceof NBTTagCompound nbTag) {
            item = StackInfo.loadFromNBT(nbTag);
        }

        if (item instanceof ItemStack stack) {
            return RecipeIngredient.of(0, 0, Arrays.asList(stack), 0);
        }

        if (item instanceof RecipeIngredient ingr) {
            return ingr.copy();
        }

        return null;
    }

    public String getHandlerName() {
        return this.handlerName;
    }

    public List<RecipeIngredient> getResults() {
        return Arrays.asList(this.results);
    }

    public List<RecipeIngredient> getIngredients() {
        return Arrays.asList(this.ingredients);
    }

    public ItemStack getResult() {
        return getResult(null);
    }

    public ItemStack getResult(ItemStack stack) {

        if (this.results.length == 0) {
            return null;
        }

        if (stack == null && this.results.length > 1) {
            for (RecipeIngredient ingr : this.results) {
                if (!FluidContainerRegistry.isContainer(ingr.getItemStack())
                        || StackInfo.getFluid(ingr.getItemStack()) != null) {
                    stack = ingr.getItemStack();
                    break;
                }
            }
        }

        if (stack == null) {
            stack = this.results[0].getItemStack();
        }

        int stackSize = 0;

        for (RecipeIngredient result : this.results) {
            if (result.contains(stack)) {
                stackSize += result.getAmount();
            }
        }

        return StackInfo.withAmount(stack, stackSize);
    }

    public RecipeId getRecipeId() {

        if (this.recipeId == null) {
            this.recipeId = RecipeId.of(getResult(), this.handlerName, getIngredients());
        }

        return this.recipeId;
    }

    public void setCustomRecipeId(RecipeId recipeId) {
        this.recipeId = recipeId;
    }

    public Recipe copy() {
        final List<RecipeIngredient> ingredients = new ArrayList<>();
        final List<RecipeIngredient> results = new ArrayList<>();

        for (RecipeIngredient ingr : this.ingredients) {
            ingredients.add(ingr.copy());
        }

        for (RecipeIngredient res : this.results) {
            results.add(res.copy());
        }

        Recipe recipe = new Recipe(this.handlerName, ingredients, results);
        recipe.recipeId = this.recipeId;

        return recipe;
    }

}
