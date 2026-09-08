package codechicken.nei.recipe.stackinfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.api.IStackStringifyHandler;
import codechicken.nei.item.ItemFluidDisplay;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class GTFluidStackStringifyHandler implements IStackStringifyHandler {

    protected static Class<?> itemFluidDisplay = null;
    protected static Method getFluidFromDisplayStack = null;
    protected static Class<?> gtMetaGeneratedTool = null;
    protected static Field playSound = null;

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

            gtMetaGeneratedTool = ReflectionHelper.getClass(
                    loader,
                    "gregtech.api.items.MetaGeneratedTool",
                    "gregtech.api.items.GT_MetaGenerated_Tool");
            playSound = gtMetaGeneratedTool.getDeclaredField("playSound");
        } catch (Exception ignored) {
            /* Do nothing */
        }
    }

    @Override
    public NBTTagCompound convertItemStackToNBT(ItemStack stack, boolean saveStackSize) {

        if (stack != null && isFluidDisplayItem(stack)) {
            final FluidStack fluidStack = getFluid(stack);

            if (fluidStack != null) {
                final NBTTagCompound nbTag = new NBTTagCompound();
                nbTag.setString(FluidDisplayStackStringifyHandler.NBT_FLUID_NAME, fluidStack.getFluid().getName());
                nbTag.setInteger("Count", saveStackSize ? fluidStack.amount : 144);
                return nbTag;
            }
        }

        return null;
    }

    @Override
    public ItemStack convertNBTToItemStack(NBTTagCompound nbtTag) {

        if (nbtTag.hasKey("gtFluidName")) {
            final String fluidName = nbtTag.getString("gtFluidName");
            final Fluid fluid = FluidRegistry.getFluid(fluidName);
            final int amount = nbtTag.getInteger("Count");

            if (fluid != null) {
                return ItemFluidDisplay.createStack(new FluidStack(fluid, amount));
            }
        }

        return null;
    }

    @Override
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

    @Override
    public ItemStack normalizeRecipeQueryStack(ItemStack stack) {

        if (stack.getItem() == GameRegistry.findItem("ae2fc", "fluid_drop")
                || stack.getItem() == GameRegistry.findItem("ae2fc", "fluid_packet")) {
            final FluidStack fluidStack = getFluid(stack);

            if (fluidStack != null) {
                return ItemFluidDisplay.createStack(fluidStack);
            }
        }

        return null;
    }

    @Override
    public void pauseItemDamageSound(boolean pause) {
        if (playSound != null) {
            try {
                playSound.setBoolean(null, !pause);
            } catch (Exception e) {}
        }

    }

    @Override
    public boolean isFluidDisplayItem(ItemStack stack) {
        final Item item = stack.getItem();
        return (itemFluidDisplay != null && itemFluidDisplay.isInstance(item))
                || item == GameRegistry.findItem("ae2fc", "fluid_packet");
    }

}
