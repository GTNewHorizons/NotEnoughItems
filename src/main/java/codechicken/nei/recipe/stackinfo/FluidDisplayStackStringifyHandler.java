package codechicken.nei.recipe.stackinfo;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.api.IStackStringifyHandler;
import codechicken.nei.item.ItemFluidDisplay;

public class FluidDisplayStackStringifyHandler implements IStackStringifyHandler {

    public static final String NBT_FLUID_NAME = "neiFluidName";

    @Override
    public boolean isFluidDisplayItem(ItemStack stack) {
        return stack.getItem() instanceof ItemFluidDisplay;
    }

    @Override
    public NBTTagCompound convertItemStackToNBT(ItemStack stack, boolean saveStackSize) {

        if (!(stack.getItem() instanceof ItemFluidDisplay)) {
            return null;
        }

        final FluidStack fluidStack = ((ItemFluidDisplay) stack.getItem()).getFluid(stack);

        if (fluidStack == null) {
            return null;
        }

        final NBTTagCompound nbTag = new NBTTagCompound();
        nbTag.setString(NBT_FLUID_NAME, fluidStack.getFluid().getName());
        nbTag.setInteger("Count", saveStackSize ? fluidStack.amount : 1);

        if (fluidStack.tag != null) {
            nbTag.setTag("FluidTag", fluidStack.tag.copy());
        }

        return nbTag;
    }

    @Override
    public ItemStack convertNBTToItemStack(NBTTagCompound nbtTag) {

        if (!nbtTag.hasKey(NBT_FLUID_NAME)) {
            return null;
        }

        final Fluid fluid = FluidRegistry.getFluid(nbtTag.getString(NBT_FLUID_NAME));

        if (fluid == null) {
            return null;
        }

        final FluidStack fluidStack = new FluidStack(fluid, nbtTag.getInteger("Count"));

        if (nbtTag.hasKey("FluidTag")) {
            fluidStack.tag = nbtTag.getCompoundTag("FluidTag");
        }

        return ItemFluidDisplay.createStack(fluidStack);
    }

    @Override
    public FluidStack getFluid(ItemStack stack) {

        if (stack.getItem() instanceof ItemFluidDisplay fluidDisplay) {
            return fluidDisplay.getFluid(stack);
        }

        return null;
    }

}
