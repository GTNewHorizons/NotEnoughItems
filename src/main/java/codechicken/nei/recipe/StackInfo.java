package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import codechicken.nei.ClientHandler;
import codechicken.nei.ItemStackMap;
import codechicken.nei.LRUCache;
import codechicken.nei.api.IStackStringifyHandler;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.recipe.stackinfo.DefaultStackStringifyHandler;
import codechicken.nei.recipe.stackinfo.GTFluidStackStringifyHandler;
import codechicken.nei.util.ItemStackKey;

public class StackInfo {

    private static final FluidStack NULL_FLUID = new FluidStack(FluidRegistry.WATER, 0);
    public static final List<IStackStringifyHandler> stackStringifyHandlers = new ArrayList<>();
    private static final Map<String, HashMap<String, String[]>> guidfilters = new HashMap<>();
    private static final ItemStackMap<String> guidcache = new ItemStackMap<>();
    private static final LRUCache<ItemStackKey, FluidStack> fluidcache = new LRUCache<>(200);

    static {
        stackStringifyHandlers.add(new DefaultStackStringifyHandler());
        stackStringifyHandlers.add(new GTFluidStackStringifyHandler());
    }

    public static NBTTagCompound itemStackToNBT(ItemStack stack) {
        return itemStackToNBT(stack, true);
    }

    public static NBTTagCompound itemStackToNBT(ItemStack stack, boolean saveStackSize) {
        NBTTagCompound nbTag = null;

        for (int i = stackStringifyHandlers.size() - 1; i >= 0 && nbTag == null; i--) {
            nbTag = stackStringifyHandlers.get(i).convertItemStackToNBT(stack, saveStackSize);
        }

        return nbTag;
    }

    public static ItemStack loadFromNBT(NBTTagCompound nbtTag, long customCount) {

        if (nbtTag != null) {
            nbtTag = (NBTTagCompound) nbtTag.copy();
            nbtTag.setInteger("Count", (int) Math.max(Math.min(customCount, Integer.MAX_VALUE), 0));
            return loadFromNBT(nbtTag);
        }

        return null;
    }

    public static ItemStack loadFromNBT(NBTTagCompound nbtTag) {
        ItemStack stack = null;

        if (nbtTag != null) {
            for (int i = stackStringifyHandlers.size() - 1; i >= 0 && stack == null; i--) {
                stack = stackStringifyHandlers.get(i).convertNBTToItemStack(nbtTag);
            }
        }

        return stack;
    }

    public static Optional<ItemStack> getContainerItem(ItemStack stack) {
        Optional<ItemStack> result = null;

        for (int i = stackStringifyHandlers.size() - 1; i >= 0 && result == null; i--) {
            result = stackStringifyHandlers.get(i).getContainerItem(stack);
        }

        return result;
    }

    public static void playItemDamageSound(boolean enabled) {
        for (IStackStringifyHandler handler : stackStringifyHandlers) {
            handler.playItemDamageSound(enabled);
        }
    }

    public static ItemStack withAmount(ItemStack stack, long customCount) {
        if (stack == null) return null;
        final NBTTagCompound nbTag = StackInfo.itemStackToNBT(stack);
        return nbTag != null ? loadFromNBT(nbTag, customCount) : null;
    }

    public static int getAmount(ItemStack stack) {
        if (stack == null) return 0;
        final NBTTagCompound nbTag = StackInfo.itemStackToNBT(stack);
        return nbTag != null ? nbTag.getInteger("Count") : 0;
    }

    public static boolean equalItemAndNBT(ItemStack stackA, ItemStack stackB, boolean useNBT) {
        if (stackA == null || stackB == null || stackA.getItem() != stackB.getItem()) {
            return false;
        }

        if (useNBT && stackA != stackB) {
            NBTTagCompound tagCompoundA = itemStackToNBT(stackA, false);
            NBTTagCompound tagCompoundB = itemStackToNBT(stackB, false);

            return tagCompoundA == null && tagCompoundB == null
                    || tagCompoundA != null && tagCompoundB != null && tagCompoundA.equals(tagCompoundB);
        }

        return true;
    }

    public static synchronized FluidStack getFluid(ItemStack stack) {
        ItemStackKey key = new ItemStackKey(stack);
        FluidStack fluid = fluidcache.get(key);

        if (fluid == null) {

            for (int i = stackStringifyHandlers.size() - 1; i >= 0 && fluid == null; i--) {
                fluid = stackStringifyHandlers.get(i).getFluid(stack);
            }

            fluidcache.put(key, fluid == null ? NULL_FLUID : fluid);
        }

        return fluid == NULL_FLUID ? null : fluid;
    }

    public static boolean isFluidContainer(ItemStack stack) {
        return stack.getItem() instanceof IFluidContainerItem || FluidContainerRegistry.isContainer(stack);
    }

    public static String getItemStackGUID(ItemStack stack) {
        String guid = guidcache.get(stack);

        if (guid != null) {
            return guid;
        }

        final NBTTagCompound nbTag = itemStackToNBT(stack, false);

        if (nbTag == null) {
            return null;
        }

        nbTag.removeTag("Count");

        if (nbTag.getShort("Damage") == 0) {
            nbTag.removeTag("Damage");
        }

        if (nbTag.hasKey("tag") && nbTag.getCompoundTag("tag").hasNoTags()) {
            nbTag.removeTag("tag");
        }

        if (nbTag.hasKey("strId") && guidfilters.containsKey(nbTag.getString("strId"))) {
            final ArrayList<String> keys = new ArrayList<>();
            final String strId = nbTag.getString("strId");

            keys.add(strId);

            guidfilters.get(strId).forEach((key, rule) -> {
                Object local = nbTag;

                for (int i = 0; i < rule.length; i++) {

                    try {

                        if (local instanceof NBTTagCompound item) {
                            local = item.getTag(rule[i]);
                        } else if (local instanceof NBTTagList item) {
                            local = item.tagList.get(Integer.parseInt(rule[i]));
                        } else {
                            break;
                        }

                    } catch (Throwable e) {
                        break;
                    }
                }

                if (local instanceof NBTBase item) {
                    keys.add(item.toString());
                } else if (local != null) {
                    keys.add(String.valueOf(local));
                }
            });

            synchronized (guidcache) {
                guidcache.put(stack, keys.toString());
            }
        } else {
            synchronized (guidcache) {
                guidcache.put(stack, nbTag.toString());
            }
        }

        return guidcache.get(stack);
    }

    public static void loadGuidFilters() {
        guidfilters.clear();

        ClientHandler.loadSettingsFile("guidfilters.cfg", lines -> lines.forEach(guidStr -> {
            final String[] parts = guidStr.split(",");
            final HashMap<String, String[]> rules = new HashMap<>();

            for (int j = 1; j < parts.length; j++) {
                rules.put(parts[j], parts[j].split("\\."));
            }

            guidfilters.put(parts[0], rules);
        }));
    }

    public static ItemStack getItemStackWithMinimumDamage(ItemStack[] stacks) {
        int damage = Short.MAX_VALUE;
        ItemStack result = stacks[0];

        if (stacks.length > 1) {
            for (ItemStack stack : stacks) {
                if (stack.getItem() != null && !ItemInfo.isHidden(stack) && stack.getItemDamage() < damage) {
                    damage = stack.getItemDamage();
                    result = stack;
                }
            }
        }

        return result.copy();
    }
}
