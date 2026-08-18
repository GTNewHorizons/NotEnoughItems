package codechicken.nei.recipe.stackinfo;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.ruling_0.materiallib.api.StackResolver;

import codechicken.nei.api.IStackStringifyHandler;
import codechicken.nei.recipe.StackInfo;

/// Reads config entries written in the stable MaterialLib reference form
/// `ml:<MaterialName>:<shapeToken>` (see [StackInfo#MATERIALLIB_PREFIX]) and resolves them against the registries,
/// so an entry keeps naming the same item across sessions that hand out different metadata.
///
/// The resolved stack carries the metadata MaterialLib currently assigns, overriding any `Damage` the entry
/// carries alongside the id.
///
/// This is the only class in NotEnoughItems that references the MaterialLib API, and every reference to it is
/// gated on `Loader.isModLoaded("materiallib")` so it never classloads without MaterialLib present.
public class MaterialLibStackStringifyHandler implements IStackStringifyHandler {

    @Override
    public ItemStack convertNBTToItemStack(NBTTagCompound nbtTag) {
        final String strId = nbtTag.getString("strId");

        if (!strId.startsWith(StackInfo.MATERIALLIB_PREFIX)) {
            return null;
        }

        final String[] parts = strId.split(":");

        if (parts.length != 3) {
            return null;
        }

        final ItemStack stack = StackResolver.getStack(parts[1], parts[2], nbtTag.getInteger("Count"));

        if (stack != null && nbtTag.hasKey("tag")) {
            stack.setTagCompound((NBTTagCompound) nbtTag.getCompoundTag("tag").copy());
        }

        return stack;
    }

}
