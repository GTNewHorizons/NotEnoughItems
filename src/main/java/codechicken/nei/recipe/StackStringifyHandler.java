package codechicken.nei.recipe;

import codechicken.nei.api.IStackStringifyHandler;
import cpw.mods.fml.common.registry.GameData;
import net.minecraft.nbt.NBTTagCompound;
import codechicken.nei.NEIClientConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;

public class StackStringifyHandler implements IStackStringifyHandler
{

    public NBTTagCompound convertItemStackToNBT(ItemStack[] stacks)
    {
        ItemStack stack = getItemStackWithMinimumDamage(stacks);
        NBTTagCompound nbTag = new NBTTagCompound();
        String strId = Item.itemRegistry.getNameForObject(stack.getItem());

        nbTag.setString("strId", strId);
        nbTag.setShort("Damage", (short)stack.getItemDamage());

        if (stack.hasTagCompound()) {
            nbTag.setTag("tag", stack.getTagCompound());
        }

        return nbTag;
    }

    public ItemStack convertNBTToItemStack(NBTTagCompound nbtTag)
    {

        if (!nbtTag.hasKey("strId")) {
            return null;
        }

        String strId = nbtTag.getString("strId");

        nbtTag = (NBTTagCompound)nbtTag.copy();
        nbtTag.setByte("Count", (byte)1);
        nbtTag.setShort("id", (short)GameData.getItemRegistry().getId(strId));

        return ItemStack.loadItemStackFromNBT(nbtTag);
    }

    public ItemStack normalize(ItemStack item)
    {
        ItemStack copy = item.copy();
        copy.stackSize = 1;
        return copy;
    }

    protected static ItemStack getItemStackWithMinimumDamage(ItemStack[] stacks)
    {
        int damage = Short.MAX_VALUE;
        ItemStack result = stacks[0];

        if (stacks.length > 1) {
            for (ItemStack stack : stacks) {
                if (stack.getItemDamage() < damage) {
                    damage = stack.getItemDamage();
                    result = stack;
                }
            }
        }

        return result;
    }

}