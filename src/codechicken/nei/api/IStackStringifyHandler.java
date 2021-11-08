package codechicken.nei.api;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.item.ItemStack;

public interface IStackStringifyHandler
{

    public NBTTagCompound convertItemStackToNBT(ItemStack[] stacks);

    public ItemStack convertNBTToItemStack(NBTTagCompound nbtTag);

    public String getItemStackId(ItemStack[] stacks);

    public ItemStack normalize(ItemStack item);

}