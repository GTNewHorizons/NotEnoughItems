package codechicken.nei.recipe;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.api.ShortcutInputHandler;

public class ContainerRecipe extends Container {

    private ItemStack activeStack = null;

    @Override
    public ItemStack slotClick(int slotId, int clickedButton, int mode, EntityPlayer player) {
        ShortcutInputHandler.handleMouseClick(this.activeStack);
        return null;
    }

    public void setActiveStack(ItemStack activeStack) {
        this.inventorySlots.clear();
        this.inventoryItemStacks.clear();
        this.activeStack = activeStack;
    }

    @Override
    public boolean canInteractWith(EntityPlayer entityplayer) {
        return true;
    }

    @Override
    public void putStackInSlot(int par1, ItemStack par2ItemStack) {
        // Server side updates do nothing!
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer par1EntityPlayer, int par2) {
        return null; // no shift clicking (scrolling...)
    }

    @Deprecated
    public Slot getSlotWithStack(PositionedStack stack, int recipex, int recipey) {
        return null;
    }
}
