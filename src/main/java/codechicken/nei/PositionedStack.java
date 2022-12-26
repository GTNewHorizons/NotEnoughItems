package codechicken.nei;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Simply an {@link ItemStack} with position.
 * Mainly used in the recipe handlers.
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

        if (genPerms) generatePermutations();
        else setPermutationToRender(0);
    }

    public PositionedStack(Object object, int x, int y) {
        this(object, x, y, true);
    }

    public void generatePermutations() {
        if (permutated) return;

        ArrayList<ItemStack> stacks = new ArrayList<>();
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

        if (items.length == 0) items = new ItemStack[] {new ItemStack(Blocks.fire)};

        permutated = true;
        setPermutationToRender(0);
    }

    public void setMaxSize(int i) {
        for (ItemStack item : items) if (item.stackSize > i) item.stackSize = i;
    }

    public PositionedStack copy() {
        return new PositionedStack(items, relx, rely);
    }

    public void setPermutationToRender(int index) {
        item = items[index].copy();
        if (item.getItem() == null) item = new ItemStack(Blocks.fire);
        else if (item.getItemDamage() == OreDictionary.WILDCARD_VALUE
                && item.getItem() != null
                && item.getItem().isRepairable()) item.setItemDamage(0);
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
