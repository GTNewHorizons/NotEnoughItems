package codechicken.nei.api;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

public interface IStackStringifyHandler {

    default NBTTagCompound convertItemStackToNBT(ItemStack stack, boolean saveStackSize) {
        return null;
    }

    default ItemStack convertNBTToItemStack(NBTTagCompound nbtTag) {
        return null;
    }

    default FluidStack getFluid(ItemStack stack) {
        return null;
    }

    default ItemStack normalizeRecipeQueryStack(ItemStack stack) {
        return null;
    }

    default void pauseItemDamageSound(boolean pause) {}

}
