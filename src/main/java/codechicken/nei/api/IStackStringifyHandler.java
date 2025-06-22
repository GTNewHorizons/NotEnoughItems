package codechicken.nei.api;

import java.util.Optional;

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

    default Optional<ItemStack> getContainerItem(ItemStack stack) {
        return null;
    }

}
