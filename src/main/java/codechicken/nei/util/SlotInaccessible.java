package codechicken.nei.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

public class SlotInaccessible extends Slot {

    private ItemStack itemStack = null;

    public SlotInaccessible(ItemStack itemStack, int xDisplayPosition, int yDisplayPosition) {
        super(null, 0, xDisplayPosition, yDisplayPosition);
        this.itemStack = itemStack;
    }

    @Override
    public void onSlotChange(final ItemStack par1ItemStack, final ItemStack par2ItemStack) {}

    @Override
    public void onPickupFromSlot(final EntityPlayer par1EntityPlayer, final ItemStack par2ItemStack) {}

    @Override
    public boolean isItemValid(final ItemStack par1ItemStack) {
        return false;
    }

    @Override
    public ItemStack getStack() {
        return this.itemStack;
    }

    @Override
    public void putStack(final ItemStack par1ItemStack) {}

    @Override
    public void onSlotChanged() {}

    @Override
    public int getSlotStackLimit() {
        return 0;
    }

    @Override
    public ItemStack decrStackSize(final int par1) {
        return null;
    }

    @Override
    public boolean isSlotInInventory(final IInventory par1IInventory, final int par2) {
        return false;
    }

    @Override
    public boolean canTakeStack(final EntityPlayer par1EntityPlayer) {
        return false;
    }

    @Override
    public int getSlotIndex() {
        return 0;
    }
}
