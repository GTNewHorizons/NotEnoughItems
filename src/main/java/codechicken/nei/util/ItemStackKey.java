package codechicken.nei.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class ItemStackKey {

    public final ItemStack stack;
    private int hashCode = 1;

    public ItemStackKey(ItemStack stack) {
        this.stack = stack;

        if (this.stack != null) {
            this.hashCode = 31 * this.hashCode + stack.stackSize;
            this.hashCode = 31 * this.hashCode + Item.getIdFromItem(stack.getItem());
            this.hashCode = 31 * this.hashCode + stack.getItemDamage();
            this.hashCode = 31 * this.hashCode + (!stack.hasTagCompound() ? 0 : stack.getTagCompound().hashCode());
        }
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ItemStackKey)) return false;
        return ItemStack.areItemStacksEqual(this.stack, ((ItemStackKey) o).stack);
    }
}
