package codechicken.nei.util;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagEnd;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nullable;
import java.util.Set;

public class NBTHelper {

    /*
     * Taken from Botania r1.11-379 by Vazkii under the Botania License
     * You can obtain one at https://botaniamod.net/license.php
     */

    /**
     * Returns true if the `target` tag contains all of the tags and values present in the `template` tag. Recurses into
     * compound tags and matches all template keys and values; recurses into list tags and matches the template against
     * the first elements of target. Empty lists and compounds in the template will match target lists and compounds of
     * any size.
     */
    public static boolean matchTag(@Nullable NBTBase template, @Nullable NBTBase target) {
        if (template instanceof NBTTagCompound && target instanceof NBTTagCompound) {
            return matchTagCompound((NBTTagCompound) template, (NBTTagCompound) target);
        } else if (template instanceof NBTTagList && target instanceof NBTTagList) {
            return matchTagList((NBTTagList) template, (NBTTagList) target);
        } else {
            return template == null || (target != null && target.equals(template));
        }
    }

    private static boolean matchTagCompound(NBTTagCompound template, NBTTagCompound target) {
        if (template.tagMap.size() > target.tagMap.size()) return false;

        //noinspection unchecked
        for (String key : (Set<String>) template.func_150296_c()) {
            if (!matchTag(template.getTag(key), target.getTag(key))) return false;
        }

        return true;
    }

    private static boolean matchTagList(NBTTagList template, NBTTagList target) {
        if (template.tagCount() > target.tagCount()) return false;

        for (int i = 0; i < template.tagCount(); i++) {
            if (!matchTag(get(template, i), get(target, i))) return false;
        }

        return true;
    }

    private static NBTBase get(NBTTagList tag, int idx)
    {
        return idx >= 0 && idx < tag.tagList.size() ? (NBTBase)tag.tagList.get(idx) : new NBTTagEnd();
    }
}
