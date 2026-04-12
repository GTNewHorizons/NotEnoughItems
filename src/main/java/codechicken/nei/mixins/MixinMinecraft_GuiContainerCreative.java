package codechicken.nei.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import codechicken.nei.NEICPH;
import codechicken.nei.NEIClientUtils;

@Mixin(GuiContainerCreative.class)
public class MixinMinecraft_GuiContainerCreative {

    @Mixin(targets = "net.minecraft.client.gui.inventory.GuiContainerCreative$ContainerCreative")
    public static class MixinMinecraft_GuiContainerCreative_ContainerCreative {

        // Fix double click grab
        @Inject(method = "func_94530_a", at = @At(value = "TAIL"), cancellable = true)
        public void nei$doubleClickGrab(ItemStack p_94530_1_, Slot p_94530_2_, CallbackInfoReturnable<Boolean> cir) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.currentScreen instanceof GuiContainerCreative gcc) {
                if (gcc.func_147056_g() == CreativeTabs.tabInventory.getTabIndex()) cir.setReturnValue(true);
            }
        }
    }

    // Replace client side click handler with usual click handler
    @Redirect(
            method = "handleMouseClick",
            at = @At(
                    value = "INVOKE",
                    ordinal = 0,
                    target = "Lnet/minecraft/inventory/Container;slotClick(IIILnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;"))
    public ItemStack nei$cancelClientSlotClick(Container instance, int slotId, int clickedButton, int clickType,
            EntityPlayer player) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiContainerCreative) {
            mc.playerController.windowClick(instance.windowId, slotId, clickedButton, clickType, player);
        }

        return null;
    }

    // Move from creative tabs to hotbar by keyboard number keys
    @Redirect(
            method = "handleMouseClick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;setInventorySlotContents(ILnet/minecraft/item/ItemStack;)V"))
    public void nei$cancelClientSetInventory(InventoryPlayer instance, int index, ItemStack stack) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiContainerCreative) {
            mc.playerController.sendSlotPacket(stack, 36 + index);
        }

        // Because mc not allow sync packet from server for creative tabs gui
        instance.setInventorySlotContents(index, stack);
    }

    // Need when you take stack from creative tab and then drag move in inventory tab
    @Redirect(
            method = "handleMouseClick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/InventoryPlayer;setItemStack(Lnet/minecraft/item/ItemStack;)V"))
    public void nei$setServerHandItem(InventoryPlayer instance, ItemStack itemStack) {
        if (itemStack != null && GuiScreen.isShiftKeyDown()) itemStack.stackSize = itemStack.getMaxStackSize();
        NEIClientUtils.setSlotContents(-999, itemStack, false);
    }

    // Empty server hand after stack grabbed from creative tab sent to server
    @Redirect(
            method = "handleMouseClick",
            at = @At(
                    value = "INVOKE",
                    ordinal = 2,
                    target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;sendSlotPacket(Lnet/minecraft/item/ItemStack;I)V"))
    public void nei$resetPlayerHandOnServer(PlayerControllerMP instance, ItemStack itemStackIn, int slotId) {
        instance.sendSlotPacket(itemStackIn, slotId);
        NEICPH.sendSetSlot(-999, null, false);
    }

    // Disable client -> server slots sync
    @Redirect(
            method = "handleMouseClick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Container;detectAndSendChanges()V"))
    public void nei$cancelClientToServerSync(Container instance) {}

}
