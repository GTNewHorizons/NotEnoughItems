package codechicken.nei.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.StackInfo;

public class FavoriteStorage {

    protected final Map<NBTTagCompound, RecipeId> itemStackToRecipeId = new HashMap<>();
    protected final Map<RecipeId, NBTTagCompound> recipeIdToItemStack = new HashMap<>();
    protected final Map<Fluid, RecipeId> fluidToRecipeId = new IdentityHashMap<>();

    protected final Map<Long, RecipeId> itemHashToRecipeId = new HashMap<>();
    protected final Map<RecipeId, Long> recipeIdToItemHash = new HashMap<>();
    protected final Map<Long, ItemStack> itemHashToItemStack = new HashMap<>();

    public void clear() {
        this.itemStackToRecipeId.clear();
        this.fluidToRecipeId.clear();
        this.recipeIdToItemStack.clear();
        this.itemHashToRecipeId.clear();
        this.recipeIdToItemHash.clear();
        this.itemHashToItemStack.clear();
    }

    public int size() {
        return this.itemStackToRecipeId.size() + this.itemHashToRecipeId.size();
    }

    public void removeItemStack(ItemStack stack) {
        final RecipeId recipeId = getRecipeId(stack);

        if (recipeId != null) {
            removeRecipeId(recipeId);
        }
    }

    public void removeRecipeId(RecipeId recipeId) {
        this.recipeIdToItemStack.remove(recipeId);
        this.fluidToRecipeId.values().removeIf(recipeId::equals);
        this.itemStackToRecipeId.values().removeIf(recipeId::equals);
        this.itemHashToRecipeId.values().removeIf(recipeId::equals);
        this.itemHashToItemStack.remove(this.recipeIdToItemHash.get(recipeId));
        this.recipeIdToItemHash.remove(recipeId);
    }

    public void add(ItemStack stack, RecipeId recipeId) {

        if (!stack.hasTagCompound() || NEIServerUtils.isItemTool(stack)) {
            long key = getItemKey(stack);
            this.itemHashToRecipeId.put(key, recipeId);
            this.recipeIdToItemHash.put(recipeId, key);
            this.itemHashToItemStack.put(key, stack);
        } else {
            final NBTTagCompound itemStackNBT = StackInfo.itemStackToNBT(stack, false);
            final Fluid fluidKey = getFluidKey(stack);

            if (fluidKey != null) {
                this.fluidToRecipeId.put(fluidKey, recipeId);
            }

            this.itemStackToRecipeId.put(itemStackNBT, recipeId);
            this.recipeIdToItemStack.put(recipeId, itemStackNBT);
        }

    }

    private long getItemKey(ItemStack stack) {
        return ((long) Item.getIdFromItem(stack.getItem()) << 32) | (stack.getItemDamage() & 0xFFFFFFFFL);
    }

    private Fluid getFluidKey(ItemStack stack) {
        final FluidStack fluid = StackInfo.getFluid(stack);

        if (fluid != null) {
            return fluid.getFluid();
        }

        return null;
    }

    public RecipeId getRecipeId(ItemStack stack) {
        RecipeId recipeId = this.itemHashToRecipeId.get(getItemKey(stack));

        if (recipeId == null) {
            recipeId = this.itemStackToRecipeId.get(StackInfo.itemStackToNBT(stack, false));
        }

        if (recipeId == null) {
            recipeId = this.fluidToRecipeId.get(getFluidKey(stack));
        }

        return recipeId;
    }

    public ItemStack getItemStack(RecipeId recipeId) {
        NBTTagCompound itemStackNBT = this.recipeIdToItemStack.get(recipeId);

        if (itemStackNBT == null) {
            final Long key = this.recipeIdToItemHash.get(recipeId);
            if (key != null) {
                return this.itemHashToItemStack.get(key);
            }
        }

        if (itemStackNBT != null) {
            return StackInfo.loadFromNBT(itemStackNBT);
        }

        return null;
    }

    public boolean contains(ItemStack stack) {
        return getRecipeId(stack) != null;
    }

    public List<Map.Entry<NBTTagCompound, RecipeId>> getAllFavorites() {
        final List<Map.Entry<NBTTagCompound, RecipeId>> entries = new ArrayList<>(this.itemStackToRecipeId.entrySet());

        for (Map.Entry<Long, RecipeId> entry : this.itemHashToRecipeId.entrySet()) {
            final ItemStack stack = this.itemHashToItemStack.get(entry.getKey());
            if (stack != null) {
                entries.add(new HashMap.SimpleEntry<>(StackInfo.itemStackToNBT(stack, false), entry.getValue()));
            }
        }

        return entries;
    }
}
