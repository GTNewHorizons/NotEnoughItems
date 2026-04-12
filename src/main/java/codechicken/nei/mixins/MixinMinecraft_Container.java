package codechicken.nei.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.inventory.Container;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Container.class)
public class MixinMinecraft_Container {

    @Redirect(
            method = "slotClick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Container;detectAndSendChanges()V"))
    private void nei$cancelClientToServerSync(Container instance) {
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiContainerCreative)) instance.detectAndSendChanges();
    }
}
