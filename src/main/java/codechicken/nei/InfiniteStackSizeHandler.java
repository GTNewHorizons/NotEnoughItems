package codechicken.nei;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import codechicken.lib.inventory.InventoryUtils;
import codechicken.nei.api.IInfiniteItemHandler;

public class InfiniteStackSizeHandler implements IInfiniteItemHandler {

    @Override
    public void onPickup(ItemStack heldItem) {
        heldItem.stackSize = 1;
    }

    @Override
    public void onPlaceInfinite(ItemStack heldItem) {
        heldItem.stackSize = 111;
    }

    @Override
    public boolean canHandleItem(ItemStack stack) {
        return !stack.isItemStackDamageable();
    }

    @Override
    public boolean isItemInfinite(ItemStack stack) {
        return stack.stackSize == -1 || stack.stackSize > 100;
    }

    @Override
    public void replenishInfiniteStack(InventoryPlayer inv, int slotNo) {
        inv.getStackInSlot(slotNo).stackSize = 111;
    }

    @Override
    public ItemStack getInfiniteItem(ItemStack typeStack) {
        return InventoryUtils.copyStack(typeStack, -1);
    }
}
