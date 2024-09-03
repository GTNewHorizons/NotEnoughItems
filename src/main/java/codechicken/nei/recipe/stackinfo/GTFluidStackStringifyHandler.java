package codechicken.nei.recipe.stackinfo;

import java.lang.reflect.Method;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.api.IStackStringifyHandler;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class GTFluidStackStringifyHandler implements IStackStringifyHandler {

    protected static Class<?> itemFluidDisplay = null;
    protected static Method getFluidDisplayStack = null;
    protected static Method getFluidFromDisplayStack = null;
    public static boolean replaceAE2FCFluidDrop = false;

    static {
        try {
            final ClassLoader loader = GTFluidStackStringifyHandler.class.getClassLoader();
            final Class<?> gtUtility = ReflectionHelper
                    .getClass(loader, "gregtech.api.util.GTUtility", "gregtech.api.util.GT_Utility");

            itemFluidDisplay = ReflectionHelper.getClass(
                    loader,
                    "gregtech.common.items.ItemFluidDisplay",
                    "gregtech.common.items.GT_FluidDisplayItem");
            getFluidFromDisplayStack = gtUtility.getMethod("getFluidFromDisplayStack", ItemStack.class);
            getFluidDisplayStack = gtUtility.getMethod("getFluidDisplayStack", FluidStack.class, boolean.class);
        } catch (Exception ignored) {
            /* Do nothing */
        }
    }

    public NBTTagCompound convertItemStackToNBT(ItemStack stack, boolean saveStackSize) {

        if (replaceAE2FCFluidDrop || stack.getItem() != GameRegistry.findItem("ae2fc", "fluid_drop")) {
            final FluidStack fluidStack = getFluid(stack);

            if (fluidStack != null) {
                final NBTTagCompound nbTag = new NBTTagCompound();
                nbTag.setString("gtFluidName", fluidStack.getFluid().getName());
                nbTag.setInteger("Count", saveStackSize ? fluidStack.amount : 144);
                return nbTag;
            }
        }

        return null;
    }

    public ItemStack convertNBTToItemStack(NBTTagCompound nbtTag) {

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

    public FluidStack getFluid(ItemStack stack) {
        final Item item = stack.getItem();

        try {
            if (getFluidFromDisplayStack != null && itemFluidDisplay != null && itemFluidDisplay.isInstance(item)) {
                final Object obj = getFluidFromDisplayStack.invoke(null, stack);

                if (obj != null) {
                    return (FluidStack) obj;
                }

            } else if (item == GameRegistry.findItem("ae2fc", "fluid_packet")) {
                NBTTagCompound nbtTag = stack.getTagCompound();

                return FluidStack.loadFluidStackFromNBT((NBTTagCompound) nbtTag.getTag("FluidStack"));
            } else if (item == GameRegistry.findItem("ae2fc", "fluid_drop")) {
                NBTTagCompound nbtTag = stack.getTagCompound();
                Fluid fluid = FluidRegistry.getFluid(nbtTag.getString("Fluid").toLowerCase());

                if (fluid != null) {
                    FluidStack fluidStack = new FluidStack(fluid, stack.stackSize);
                    if (nbtTag.hasKey("FluidTag")) {
                        fluidStack.tag = nbtTag.getCompoundTag("FluidTag");
                    }
                    return fluidStack;
                }
            }
        } catch (Exception e) {}

        return null;
    }
}
