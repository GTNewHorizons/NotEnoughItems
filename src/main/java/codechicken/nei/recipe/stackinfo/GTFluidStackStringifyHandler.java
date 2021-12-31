package codechicken.nei.recipe.stackinfo;

import codechicken.nei.api.IStackStringifyHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Method;

public class GTFluidStackStringifyHandler implements IStackStringifyHandler
{

    public static Class GTDisplayFluid = null;
    public static Method getFluidDisplayStack = null;
    public static Method getFluidFromDisplayStack = null;
    
    static {
        try {
            final Class gtUtility = Class.forName("gregtech.api.util.GT_Utility");

            GTDisplayFluid = Class.forName("gregtech.common.items.GT_FluidDisplayItem");
            getFluidFromDisplayStack = gtUtility.getMethod("getFluidFromDisplayStack", ItemStack.class);
            getFluidDisplayStack = gtUtility.getMethod("getFluidDisplayStack", FluidStack.class, boolean.class);

        } catch (Exception ignored) { /*Do nothing*/ }
    }

    public NBTTagCompound convertItemStackToNBT(ItemStack stack, boolean saveStackSize)
    {

        if (GTDisplayFluid != null && GTDisplayFluid.isInstance(stack.getItem())) {
            final NBTTagCompound nbTag = new NBTTagCompound();
            final FluidStack fluidStack = getFluid(stack);

            nbTag.setString("gtFluidName", fluidStack.getFluid().getName());
            nbTag.setInteger("Count", saveStackSize? fluidStack.amount: 1);
            return nbTag;
        }

        return null;
    }

    public ItemStack convertNBTToItemStack(NBTTagCompound nbtTag)
    {

        if (getFluidDisplayStack != null && nbtTag.hasKey("gtFluidName")) {
            final String fluidName = nbtTag.getString("gtFluidName");
            final Fluid fluid = FluidRegistry.getFluid(fluidName);
            final int amount = nbtTag.getInteger("Count");

            try {
                final Object obj = getFluidDisplayStack.invoke(null, new FluidStack(fluid, amount), true);

                if (obj != null) {
                    return (ItemStack) obj;
                }

            } catch (Exception e) {}

        }

        return null;
    }

    public FluidStack getFluid(ItemStack stack)
    {

        if (getFluidFromDisplayStack != null && GTDisplayFluid != null && GTDisplayFluid.isInstance(stack.getItem())) {
            try {
                final Object obj = getFluidFromDisplayStack.invoke(null, stack);
    
                if (obj != null) {
                    return (FluidStack) obj;
                }
    
            } catch (Exception e) {}
        }
        
        return null;
    }
    
}
