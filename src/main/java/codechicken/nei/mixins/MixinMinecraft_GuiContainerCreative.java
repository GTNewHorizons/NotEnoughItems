package codechicken.nei.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0EPacketClickWindow;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import codechicken.nei.NEIClientUtils;

@Mixin(GuiContainerCreative.class)
public class MixinMinecraft_GuiContainerCreative {

    // Replace client side click handler with usual click handler
    @Redirect(
            method = "handleMouseClick",
            at = @At(
                    value = "INVOKE",
                    ordinal = 0,
                    target = "Lnet/minecraft/inventory/Container;slotClick(IIILnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack nei$cancelClientSlotClick(Container instance, int slotId, int clickedButton, int clickType,
            EntityPlayer player) {
        return Minecraft.getMinecraft().playerController
                .windowClick(instance.windowId, slotId, clickedButton, clickType, player);
    }

    // Creative tabs set slots
    @Redirect(
            method = "handleMouseClick",
            at = @At(
                    value = "INVOKE",
                    ordinal = 1,
                    target = "Lnet/minecraft/inventory/Container;slotClick(IIILnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack nei$forkSlotClick(Container instance, int slotId, int clickedButton, int clickType,
            EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiContainerCreative gcc) {
            // Client side
            short short1 = player.openContainer.getNextTransactionID(player.inventory);
            ItemStack itemstack = player.openContainer.slotClick(slotId, clickedButton, clickType, player);

            // Server side
            slotId = slotId != -999 ? slotId - gcc.inventorySlots.inventorySlots.size() + 9 + 36 : -999;
            ((MixinMinecraft_PlayerControllerMP) mc.playerController).nei$getNetClientHandler().addToSendQueue(
                    new C0EPacketClickWindow(instance.windowId, slotId, clickedButton, clickType, itemstack, short1));
        }

        return null;
    }

    // Move from creative tabs to hotbar by keyboard number keys
    @Redirect(
            method = "handleMouseClick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V"))
    private void nei$cancelClientSetInventory(InventoryPlayer instance, int index, ItemStack stack) {
        Minecraft.getMinecraft().playerController.sendSlotPacket(stack, 36 + index);

        // Because mc not allow sync packet from server for creative tabs gui
        instance.setInventorySlotContents(index, stack);
    }

    // Need when you take stack from creative tab and then drag move in inventory tab
    @Redirect(
            method = "handleMouseClick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;setItemStack(Lnet/minecraft/item/ItemStack;)V"))
    private void nei$setServerHandItem(InventoryPlayer instance, ItemStack itemStack, Slot slotIn, int slotId,
            int clickedButton, int clickType) {
        if (itemStack != null && clickType == 1) itemStack.stackSize = itemStack.getMaxStackSize();
        NEIClientUtils.setSlotContents(-999, itemStack, false);
    }

    // Use mixin for sync
    @Redirect(
            method = "handleMouseClick",
            at = @At(
                    value = "INVOKE",
                    ordinal = 2,
                    target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;sendSlotPacket(Lnet/minecraft/item/ItemStack;I)V"))
    private void nei$cancelClientToServerSetSlot(PlayerControllerMP instance, ItemStack itemStackIn, int slotId) {}

    // Disable client -> server slots sync
    @Redirect(
            method = "handleMouseClick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Container;detectAndSendChanges()V"))
    private void nei$cancelClientToServerSync(Container instance) {}
}
