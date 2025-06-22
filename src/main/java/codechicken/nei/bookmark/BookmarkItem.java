package codechicken.nei.bookmark;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.NEIClientUtils;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.Recipe.RecipeIngredient;
import codechicken.nei.recipe.StackInfo;

public class BookmarkItem {

    private static Map<ItemStack, String> fuzzyPermutations = new HashMap<>();

    public int groupId;

    public long amount = 0L;
    public final int fluidCellAmount;
    public ItemStack itemStack;
    public Map<String, ItemStack> permutations;

    public long factor;
    public RecipeId recipeId;
    public boolean isIngredient = false;

    protected BookmarkItem(int groupId, long amount, int fluidCellAmount, ItemStack itemStack,
            Map<String, ItemStack> permutations, long factor, RecipeId recipeId, boolean isIngredient) {
        this.groupId = groupId;

        this.amount = amount;
        this.fluidCellAmount = fluidCellAmount;
        this.itemStack = itemStack;
        this.permutations = permutations;

        this.factor = factor;
        this.recipeId = recipeId;
        this.isIngredient = isIngredient;
    }

    public static BookmarkItem of(int groupId, ItemStack stack, long factor, RecipeId recipeId, boolean isIngredient,
            Map<String, ItemStack> permutations) {
        final FluidStack fluidStack = StackInfo.getFluid(stack);
        int fluidCellAmount = 1;
        long amount;

        if (fluidStack != null) {
            amount = fluidStack.amount * (long) Math.max(0, stack.stackSize);
            fluidCellAmount = Math.max(1, StackInfo.isFluidContainer(stack) ? fluidStack.amount : 1);
        } else {
            amount = StackInfo.getAmount(stack);
        }

        return new BookmarkItem(
                groupId,
                amount,
                fluidCellAmount,
                stack,
                permutations,
                factor * fluidCellAmount,
                recipeId,
                isIngredient);
    }

    public static BookmarkItem of(int groupId, ItemStack stack, long factor, RecipeId recipeId, boolean isIngredient) {
        return of(groupId, stack, factor, recipeId, isIngredient, generatePermutations(stack, recipeId, isIngredient));
    }

    public static BookmarkItem of(int groupId, ItemStack stack) {
        final NBTTagCompound nbTag = StackInfo.itemStackToNBT(stack);
        final long factor = nbTag.hasKey("gtFluidName") ? Math.min(144, nbTag.getInteger("Count")) : 1;
        return BookmarkItem
                .of(groupId, stack, factor, null, false, Collections.singletonMap(getItemGUID(stack), stack));
    }

    public BookmarkItem copyWithAmount(long amount) {
        return new BookmarkItem(
                this.groupId,
                amount,
                this.fluidCellAmount,
                this.itemStack,
                this.permutations,
                this.factor,
                this.recipeId,
                this.isIngredient);
    }

    public BookmarkItem copy() {
        return copyWithAmount(this.amount);
    }

    public static Map<String, ItemStack> generatePermutations(ItemStack stack, RecipeId recipeId,
            boolean isIngredient) {

        if (isIngredient && recipeId != null) {
            return generatePermutations(stack, Recipe.of(recipeId));
        }

        return Collections.singletonMap(getItemGUID(stack), stack);
    }

    public static Map<String, ItemStack> generatePermutations(ItemStack stack, Recipe recipe) {

        if (recipe != null) {
            final RecipeIngredient ingr = recipe.getIngredients().stream()
                    .filter(ingredient -> ingredient.contains(stack)).findAny().orElse(null);

            if (ingr != null) {
                final Map<String, ItemStack> permutations = new HashMap<>();
                for (ItemStack ingrStack : ingr.getPermutations()) {
                    permutations.put(getItemGUID(ingrStack), ingrStack);
                }
                return permutations;
            }
        }

        return Collections.singletonMap(getItemGUID(stack), stack);
    }

    private static synchronized String getItemGUID(ItemStack stack) {
        final FluidStack fluidStack = StackInfo.getFluid(stack);

        if (fluidStack != null) {
            return fluidStack.getFluid().getName() + ":" + fluidStack.tag;
        } else {

            for (Map.Entry<ItemStack, String> entry : BookmarkItem.fuzzyPermutations.entrySet()) {
                if (NEIClientUtils.areStacksSameTypeCraftingWithNBT(stack, entry.getKey())) {
                    return entry.getValue();
                }
            }

            final String stackGUID = StackInfo.getItemStackGUID(stack);
            BookmarkItem.fuzzyPermutations.put(stack, stackGUID);

            return stackGUID;
        }
    }

    public boolean containsItems(BookmarkItem item) {
        return this.permutations.keySet().stream().anyMatch(item.permutations::containsKey);
    }

    public ItemStack getItemStack() {
        return getItemStack(this.amount);
    }

    public ItemStack getItemStack(long amount) {
        return StackInfo.withAmount(this.itemStack, getStackSize(amount));
    }

    public long getStackSize() {
        return getStackSize(this.amount);
    }

    public long getStackSize(long amount) {
        return (long) (this.factor > 0 ? Math.ceil(amount / (double) this.fluidCellAmount) : 0);
    }

    public long getMultiplier() {
        return getMultiplier(this.amount);
    }

    public long getMultiplier(long amount) {
        return (long) (this.factor > 0 ? Math.ceil(amount / (double) this.factor) : 0);
    }

    public long getFactor() {
        return this.factor / this.fluidCellAmount;
    }

    public boolean equalsRecipe(BookmarkItem meta) {
        return equalsRecipe(meta.recipeId, meta.groupId);
    }

    public boolean equalsRecipe(RecipeId recipeId, int groupId) {
        return groupId == this.groupId && recipeId != null && recipeId.equals(this.recipeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.groupId, this.fluidCellAmount, this.isIngredient, this.recipeId);
    }

    @Override
    public boolean equals(Object object) {

        if (object instanceof BookmarkItem item) {
            return this.groupId == item.groupId && this.isIngredient == item.isIngredient
                    && this.fluidCellAmount == item.fluidCellAmount
                    && StackInfo.equalItemAndNBT(this.itemStack, item.itemStack, true)
                    && (this.recipeId == item.recipeId || this.recipeId != null && this.recipeId.equals(item.recipeId));
        }

        return false;
    }

}
