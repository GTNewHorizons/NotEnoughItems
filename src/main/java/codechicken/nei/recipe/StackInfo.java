package codechicken.nei.recipe;

import codechicken.nei.api.IStackStringifyHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;

public class StackInfo
{

    public static final ArrayList<IStackStringifyHandler> stackStringifyHandlers = new ArrayList<>();

    public static NBTTagCompound itemStackToNBT(ItemStack stack)
    {
        NBTTagCompound nbTag = null;
        ItemStack[] stacks = new ItemStack[]{stack};

        for (int i = stackStringifyHandlers.size() - 1; i >= 0 && nbTag == null; i--) {
            nbTag = stackStringifyHandlers.get(i).convertItemStackToNBT(stacks);
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

    public static ItemStack normalize(ItemStack item) {
        ItemStack copy = null;

        for (int i = stackStringifyHandlers.size() - 1; i >= 0 && copy == null; i--) {
            copy = stackStringifyHandlers.get(i).normalize(item);
        }

        return copy;
    }

    public static boolean equalItemAndNBT(ItemStack stackA, ItemStack stackB, boolean useNBT) 
    {
        if (stackA.isItemEqual(stackB)) {
            NBTTagCompound tagCompoundA = stackA.getTagCompound();
            NBTTagCompound tagCompoundB = stackB.getTagCompound();

            return !useNBT || (tagCompoundA == null && tagCompoundB == null) || (tagCompoundA != null && tagCompoundB != null && tagCompoundA.equals(tagCompoundB));
        }

        return false;
    }

}
