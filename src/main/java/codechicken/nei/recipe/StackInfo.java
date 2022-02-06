package codechicken.nei.recipe;

import codechicken.nei.api.IStackStringifyHandler;
import codechicken.nei.recipe.stackinfo.DefaultStackStringifyHandler;
import codechicken.nei.recipe.stackinfo.GTFluidStackStringifyHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;

public class StackInfo
{

    public static final ArrayList<IStackStringifyHandler> stackStringifyHandlers = new ArrayList<>();

    static {
        stackStringifyHandlers.add(new DefaultStackStringifyHandler());
        stackStringifyHandlers.add(new GTFluidStackStringifyHandler());
    }

    public static NBTTagCompound itemStackToNBT(ItemStack stack)
    {
        return itemStackToNBT(stack, true);
    }

    public static NBTTagCompound itemStackToNBT(ItemStack stack, boolean saveStackSize)
    {
        NBTTagCompound nbTag = null;

        for (int i = stackStringifyHandlers.size() - 1; i >= 0 && nbTag == null; i--) {
            nbTag = stackStringifyHandlers.get(i).convertItemStackToNBT(stack, saveStackSize);
        }

        return nbTag;
    }

    public static ItemStack loadFromNBT(NBTTagCompound nbtTag)
    {
        ItemStack stack = null;

        for (int i = stackStringifyHandlers.size() - 1; i >= 0 && stack == null; i--) {
            stack = stackStringifyHandlers.get(i).convertNBTToItemStack(nbtTag);
        }

        return stack;
    }

    public static boolean equalItemAndNBT(ItemStack stackA, ItemStack stackB, boolean useNBT) 
    {
        if (!stackA.isItemEqual(stackB)) {
            return false;
        }

        if (useNBT) {
            NBTTagCompound tagCompoundA = itemStackToNBT(stackA, false);
            NBTTagCompound tagCompoundB = itemStackToNBT(stackB, false);

            return tagCompoundA == null && tagCompoundB == null || tagCompoundA != null && tagCompoundB != null && tagCompoundA.equals(tagCompoundB);
        }

        return true;
    }

    public static FluidStack getFluid(ItemStack stack)
    {
        FluidStack fluid = null;

        for (int i = stackStringifyHandlers.size() - 1; i >= 0 && fluid == null; i--) {
            fluid = stackStringifyHandlers.get(i).getFluid(stack);
        }

        return fluid;
    }

}
