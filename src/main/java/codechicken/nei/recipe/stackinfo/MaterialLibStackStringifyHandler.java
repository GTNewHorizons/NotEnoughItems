package codechicken.nei.recipe.stackinfo;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.ruling_0.materiallib.api.StackResolver;

import codechicken.nei.api.IStackStringifyHandler;
import codechicken.nei.recipe.StackInfo;

/// Resolves config entries written in the MaterialLib reference form `ml:<MaterialName>:<shapeToken>` (see
/// [StackInfo#MATERIALLIB_PREFIX]) through [StackResolver], so an entry keeps naming the same item across sessions
/// that renumber item metadata. The resolved stack carries the metadata MaterialLib currently assigns, ignoring any
/// `Damage` on the entry. Entries in any other form are left to the other handlers.
///
/// Registered only behind a `Loader.isModLoaded("materiallib")` gate, so this class never classloads without
/// MaterialLib present.
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
