package codechicken.nei.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.inventory.GuiContainerCreative$ContainerCreative")
public class MixinMinecraft_GuiContainerCreative_ContainerCreative {

    // Fix double click grab
    @Inject(method = "func_94530_a", at = @At(value = "TAIL"), cancellable = true)
    private void nei$doubleClickGrab(ItemStack p_94530_1_, Slot p_94530_2_, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen instanceof GuiContainerCreative gcc) {
            if (gcc.func_147056_g() == CreativeTabs.tabInventory.getTabIndex()) cir.setReturnValue(true);
        }
    }
}
