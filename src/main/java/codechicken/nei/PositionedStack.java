package codechicken.nei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import codechicken.nei.api.ItemFilter;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.recipe.GuiRecipe;

/**
 * Simply an {@link ItemStack} with position. Mainly used in the recipe handlers.
 */
public class PositionedStack {

    public int relx;
    public int rely;
    public ItemStack[] items;
    // compatibility dummy
    public ItemStack item;

    private boolean permutated = false;

    public PositionedStack(Object object, int x, int y, boolean genPerms) {
        items = NEIServerUtils.extractRecipeItems(object);
        relx = x;
        rely = y;

        if (genPerms) {
            generatePermutations();
        } else {
            setPermutationToRender(0);
        }
    }

    public PositionedStack(Object object, int x, int y) {
        this(object, x, y, true);
    }

    public void generatePermutations() {
        if (permutated) return;

        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getItem() == null) continue;

            if (item.getItemDamage() == Short.MAX_VALUE) {
                List<ItemStack> permutations = ItemList.itemMap.get(item.getItem());
                if (!permutations.isEmpty()) {
                    for (ItemStack stack : permutations) {
                        ItemStack toAdd = stack.copy();
                        toAdd.stackSize = item.stackSize;
                        stacks.add(toAdd);
                    }
                } else {
                    ItemStack base = new ItemStack(item.getItem(), item.stackSize);
                    base.stackTagCompound = item.stackTagCompound;
                    stacks.add(base);
                }
                continue;
            }

            stacks.add(item.copy());
        }
        items = stacks.toArray(new ItemStack[0]);

        if (items.length == 0) items = new ItemStack[] { new ItemStack(Blocks.fire) };

        permutated = true;
        setPermutationToRender(0);
    }

    public void setMaxSize(int i) {
        for (ItemStack item : items) if (item.stackSize > i) item.stackSize = i;
    }

    public PositionedStack copy() {
        PositionedStack pStack = new PositionedStack(
                Arrays.stream(this.items).map(ItemStack::copy).toArray(ItemStack[]::new),
                relx,
                rely,
                false);
        pStack.permutated = this.permutated;
        return pStack;
    }

    public List<ItemStack> getFilteredPermutations() {
        return getFilteredPermutations(null);
    }

    public List<ItemStack> getFilteredPermutations(ItemFilter additionalFilter) {
        List<ItemStack> items = Arrays.asList(this.items);

        items = filteringPermutations(items, item -> !ItemInfo.isHidden(item));
        items = filteringPermutations(items, PresetsList.getItemFilter());
        items = filteringPermutations(items, GuiRecipe.getSearchItemFilter());
        items = filteringPermutations(items, additionalFilter);

        items.sort(Comparator.comparing(FavoriteRecipes::contains).reversed());
        return items;
    }

    private List<ItemStack> filteringPermutations(List<ItemStack> items, ItemFilter filter) {
        if (filter == null) return items;
        final List<ItemStack> filteredItems = items.stream().filter(filter::matches).collect(Collectors.toList());
        return filteredItems.isEmpty() ? items : filteredItems;
    }

    public boolean setPermutationToRender(ItemStack ingredient) {

        for (int index = 0; index < this.items.length; index++) {
            if (NEIServerUtils.areStacksSameTypeCraftingWithNBT(this.items[index], ingredient)) {
                setPermutationToRender(index);
                return true;
            }
        }

        return false;
    }

    public void setPermutationToRender(int index) {
        this.item = this.items[index].copy();

        if (this.item.getItem() == null) {
            this.item = new ItemStack(Blocks.fire);
        } else if (this.item.getItemDamage() == OreDictionary.WILDCARD_VALUE && this.item.getItem().isRepairable()) {
            this.item.setItemDamage(0);
        }
    }

    public boolean contains(ItemStack ingredient) {
        for (ItemStack item : items) if (NEIServerUtils.areStacksSameTypeCrafting(item, ingredient)) return true;

        return false;
    }

    /**
     * NBT-friendly version of {@link #contains(ItemStack)}
     */
    public boolean containsWithNBT(ItemStack ingredient) {
        for (ItemStack item : items) if (NEIServerUtils.areStacksSameTypeCraftingWithNBT(item, ingredient)) return true;

        return false;
    }

    public boolean contains(Item ingred) {
        for (ItemStack item : items) if (item.getItem() == ingred) return true;

        return false;
    }

    @Override
    public String toString() {
        return "PositionedStack(output='" + item.toString() + "')";
    }
}
