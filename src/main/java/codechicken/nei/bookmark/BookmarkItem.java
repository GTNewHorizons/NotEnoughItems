package codechicken.nei.bookmark;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.Recipe.RecipeIngredient;
import codechicken.nei.recipe.StackInfo;

public class BookmarkItem {

    public static class Builder {

        private static Map<ItemStack, String> fuzzyPermutations = new HashMap<>();

        private final int groupId;
        private final ItemStack stack;
        private long factor;
        private long multiplier = -1;
        private long chance = PositionedStack.CHANCE_FULL;
        private RecipeId recipeId = null;
        private Recipe recipe = null;
        private BookmarkItemType type = BookmarkItemType.ITEM;
        private Map<String, ItemStack> permutations = null;

        private Builder(int groupId, ItemStack stack) {
            final NBTTagCompound nbTag = StackInfo.itemStackToNBT(stack);

            this.groupId = groupId;
            this.stack = stack;
            this.factor = nbTag.hasKey("gtFluidName") ? Math.min(144, nbTag.getInteger("Count")) : 1;
        }

        public Builder factor(long factor) {
            this.factor = factor;
            return this;
        }

        public Builder multiplier(long multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public Builder chance(long chance) {
            this.chance = chance;
            return this;
        }

        public Builder recipe(Recipe recipe) {
            this.recipe = recipe;
            this.recipeId = null;
            return this;
        }

        public Builder recipeId(RecipeId recipeId) {
            this.recipeId = recipeId;
            this.recipe = null;
            return this;
        }

        public Builder type(BookmarkItemType type) {
            this.type = type;
            return this;
        }

        public Builder permutations(Map<String, ItemStack> permutations) {
            this.permutations = permutations;
            return this;
        }

        public BookmarkItem build() {
            final Map<String, ItemStack> perms;

            if (this.permutations != null) {
                perms = this.permutations;
            } else if (this.type == BookmarkItemType.INGREDIENT) {

                if (this.recipe != null) {
                    perms = Builder.generatePermutations(this.stack, this.recipe);
                } else if (this.recipeId != null) {
                    perms = Builder.generatePermutations(this.stack, Recipe.of(this.recipeId));
                } else {
                    perms = Builder.generatePermutations(this.stack);
                }

            } else {
                perms = Builder.generatePermutations(this.stack);
            }

            return new BookmarkItem(
                    this.groupId,
                    this.multiplier >= 0 ? this.multiplier : StackInfo.getAmount(this.stack) / this.factor,
                    this.factor,
                    this.chance,
                    this.stack,
                    perms,
                    this.recipeId != null ? this.recipeId : this.recipe != null ? this.recipe.getRecipeId() : null,
                    this.type);
        }

        public static Map<String, ItemStack> generatePermutations(ItemStack stack) {
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

            return generatePermutations(stack);
        }

        private static synchronized String getItemGUID(ItemStack stack) {
            final FluidStack fluidStack = StackInfo.getFluid(stack);

            if (fluidStack != null) {
                return fluidStack.getFluid().getName() + ":" + fluidStack.tag;
            } else {

                for (Map.Entry<ItemStack, String> entry : Builder.fuzzyPermutations.entrySet()) {
                    if (NEIClientUtils.areStacksSameTypeCraftingWithNBT(stack, entry.getKey())) {
                        Builder.fuzzyPermutations.put(stack, entry.getValue());
                        return entry.getValue();
                    }
                }

                final String stackGUID = StackInfo.getItemStackGUID(stack);
                Builder.fuzzyPermutations.put(stack, stackGUID);

                return stackGUID;
            }
        }

    }

    public enum BookmarkItemType {

        INGREDIENT,
        RESULT,
        ITEM;

        public static BookmarkItemType fromInt(int type) {
            return type == 0 ? ITEM : type == 1 ? RESULT : INGREDIENT;
        }

        public int toInt() {
            return this == ITEM ? 0 : this == RESULT ? 1 : 2;
        }
    }

    public int groupId;

    public ItemStack itemStack;
    public Map<String, ItemStack> permutations;

    public RecipeId recipeId;
    public BookmarkItemType type = BookmarkItemType.ITEM;

    public long multiplier = 1L;
    public final long factor;
    public final int fluidCellAmount;
    public final long chance;

    protected BookmarkItem(int groupId, long multiplier, long factor, long chance, ItemStack itemStack,
            Map<String, ItemStack> permutations, RecipeId recipeId, BookmarkItemType type) {
        final FluidStack fluidStack = StackInfo.getFluid(itemStack);

        if (fluidStack != null) {
            this.fluidCellAmount = Math.max(1, StackInfo.isFluidContainer(itemStack) ? fluidStack.amount : 1);
        } else {
            this.fluidCellAmount = 1;
        }

        this.groupId = groupId;

        this.itemStack = itemStack;
        this.permutations = permutations;

        this.recipeId = recipeId;
        this.type = type == null ? BookmarkItemType.ITEM : type;

        this.multiplier = multiplier;
        this.factor = factor;
        this.chance = chance;
    }

    public static Builder builder(int groupId, ItemStack stack) {
        return new Builder(groupId, stack);
    }

    public static Builder builder(BookmarkItem item) {
        return new Builder(item.groupId, item.itemStack).factor(item.factor).multiplier(item.multiplier)
                .chance(item.chance).recipeId(item.recipeId).type(item.type).permutations(item.permutations);
    }

    public static Builder builder(int groupId, ItemStack stack, Recipe recipe, BookmarkItemType type) {
        final List<RecipeIngredient> items = type == BookmarkItemType.RESULT ? recipe.getResults()
                : recipe.getIngredients();
        final Builder builder = builder(groupId, stack).type(type).recipe(recipe);
        long amount = 0;

        for (RecipeIngredient res : items) {
            if (res.contains(stack)) {
                amount += 1L * res.getAmount() * res.getChance();
            }
        }

        if ((amount % PositionedStack.CHANCE_FULL) == 0) {
            builder.factor(amount / PositionedStack.CHANCE_FULL);
        } else {
            builder.factor(1).chance(amount);
        }

        return builder;
    }

    public static BookmarkItem of(int groupId, ItemStack stack) {
        return builder(groupId, stack).build();
    }

    public BookmarkItem copyWithMultiplier(long multiplier) {
        return new BookmarkItem(
                this.groupId,
                multiplier,
                this.factor,
                this.chance,
                this.itemStack,
                this.permutations,
                this.recipeId,
                this.type);
    }

    public BookmarkItem copyWithAmount(long amount) {
        return copyWithMultiplier(getMultiplierFromAmount(amount));
    }

    public BookmarkItem copy() {
        return copyWithMultiplier(this.multiplier);
    }

    public boolean containsItems(BookmarkItem item) {
        return this.permutations.keySet().stream().anyMatch(item.permutations::containsKey);
    }

    public long getAmount() {
        return getAmount(this.multiplier);
    }

    public long getAmount(long multiplier) {
        long amount = this.factor * multiplier;

        if (this.fluidCellAmount > 1) {
            amount = amount * this.fluidCellAmount;
        }

        if (this.chance != PositionedStack.CHANCE_FULL) {

            if (this.type == BookmarkItemType.INGREDIENT) {
                // For ingredients, round up the expected amount to be conservative.
                amount = (amount * this.chance + PositionedStack.CHANCE_FULL - 1) / PositionedStack.CHANCE_FULL;
            } else {
                // For results, round down the expected amount to be conservative.
                amount = (amount * this.chance) / PositionedStack.CHANCE_FULL;
            }

        }

        return amount;
    }

    public ItemStack getItemStack() {
        return getItemStack(getAmount());
    }

    public ItemStack getItemStack(long amount) {
        return StackInfo.withAmount(this.itemStack, getStackSize(amount));
    }

    public long getStackSize() {
        return getStackSize(getAmount());
    }

    public long getStackSize(long amount) {

        if (this.fluidCellAmount > 1) {
            amount = (amount + this.fluidCellAmount - 1) / this.fluidCellAmount;
        }

        return amount;
    }

    public long getMultiplierFromAmount(long amount) {
        if (this.factor <= 0 || this.chance <= 0) return 0;

        if (this.fluidCellAmount > 1) {
            amount = (amount + this.fluidCellAmount - 1) / this.fluidCellAmount;
        }

        if (this.chance == PositionedStack.CHANCE_FULL) {
            return (amount + this.factor - 1) / this.factor;
        }

        return (amount * PositionedStack.CHANCE_FULL + this.factor * this.chance - 1) / (this.factor * this.chance);
    }

    public long getMultiplier() {
        return this.multiplier;
    }

    public boolean equalsRecipe(BookmarkItem meta) {
        return equalsRecipe(meta.recipeId, meta.groupId);
    }

    public boolean equalsRecipe(RecipeId recipeId, int groupId) {
        return groupId == this.groupId && recipeId != null && recipeId.equals(this.recipeId);
    }

    public boolean emptyFactor() {
        return this.factor <= 0 || this.chance <= 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.groupId, this.fluidCellAmount, this.type.toInt(), this.recipeId);
    }

    @Override
    public boolean equals(Object object) {

        if (object instanceof BookmarkItem item) {
            return this.groupId == item.groupId && this.type == item.type
                    && this.fluidCellAmount == item.fluidCellAmount
                    && StackInfo.equalItemAndNBT(this.itemStack, item.itemStack, true)
                    && (this.recipeId == item.recipeId || this.recipeId != null && this.recipeId.equals(item.recipeId));
        }

        return false;
    }

}
