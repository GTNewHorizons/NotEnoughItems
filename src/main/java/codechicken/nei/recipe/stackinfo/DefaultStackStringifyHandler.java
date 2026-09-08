package codechicken.nei.recipe.stackinfo;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import codechicken.nei.api.IStackStringifyHandler;
import cpw.mods.fml.common.registry.GameData;

public class DefaultStackStringifyHandler implements IStackStringifyHandler {

    public NBTTagCompound convertItemStackToNBT(ItemStack stack, boolean saveStackSize) {
        final String strId = Item.itemRegistry.getNameForObject(stack.getItem());

        if (strId == null) {
            return null;
        }

        final NBTTagCompound nbTag = new NBTTagCompound();
        nbTag.setString("strId", strId);
        nbTag.setInteger("Count", saveStackSize ? stack.stackSize : 1);
        nbTag.setInteger("Damage", stack.getItemDamage());

        if (stack.hasTagCompound() && !stack.getTagCompound().hasNoTags()) {
            nbTag.setTag("tag", stack.getTagCompound().copy());
        }

        return nbTag;
    }

    public ItemStack convertNBTToItemStack(NBTTagCompound nbtTag) {
        final String strId = nbtTag.getString("strId");

        nbtTag = (NBTTagCompound) nbtTag.copy();
        nbtTag.setInteger("id", GameData.getItemRegistry().getId(strId)); // getObject

        final ItemStack stack = ItemStack.loadItemStackFromNBT(nbtTag);

        if (stack != null) {
            stack.stackSize = nbtTag.getInteger("Count");
        }

        return stack;
    }

    public FluidStack getFluid(ItemStack stack) {
        FluidStack fluidStack = FluidContainerRegistry.getFluidForFilledItem(stack);

        if (fluidStack == null && stack.getItem() instanceof IFluidContainerItem container) {
            fluidStack = container.getFluid(stack);
        }

        if (fluidStack == null && Block.getBlockFromItem(stack.getItem()) instanceof BlockLiquid liquidBlock) {
            Fluid fluid = FluidRegistry.lookupFluidForBlock(liquidBlock);

            if (fluid == null) {
                if (liquidBlock.getMaterial() == Material.water) {
                    fluid = FluidRegistry.WATER;
                } else if (liquidBlock.getMaterial() == Material.lava) {
                    fluid = FluidRegistry.LAVA;
                }
            }

            if (fluid != null) {
                fluidStack = new FluidStack(fluid, FluidContainerRegistry.BUCKET_VOLUME);
            }
        }

        return fluidStack;
    }

}
