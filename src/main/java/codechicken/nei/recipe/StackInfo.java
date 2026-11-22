package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import codechicken.nei.ClientHandler;
import codechicken.nei.ItemStackMap.StackMetaKey;
import codechicken.nei.LRUCache;
import codechicken.nei.api.IStackStringifyHandler;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.recipe.stackinfo.DefaultStackStringifyHandler;
import codechicken.nei.recipe.stackinfo.FluidDisplayStackStringifyHandler;
import codechicken.nei.recipe.stackinfo.GTFluidStackStringifyHandler;
import codechicken.nei.util.ItemStackKey;
import codechicken.nei.util.NBTHelper;

public class StackInfo {

    private static final FluidStack NULL_FLUID = new FluidStack(FluidRegistry.WATER, 0);
    public static final List<IStackStringifyHandler> stackStringifyHandlers = new ArrayList<>();
    private static final Map<String, HashMap<String, String[]>> guidfilters = new HashMap<>();
    private static final Map<Item, Map<StackMetaKey, String>> guidcache = new ConcurrentHashMap<>();
    private static final LRUCache<ItemStackKey, FluidStack> fluidcache = new LRUCache<>(200);
    private static boolean isPausedItemDamageSound = false;

    static {
        stackStringifyHandlers.add(new DefaultStackStringifyHandler());
        stackStringifyHandlers.add(new FluidDisplayStackStringifyHandler());
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
            nbtTag.setLong("Count", Math.max(customCount, 0));
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

    public static ItemStack normalizeRecipeQueryStack(ItemStack stack) {
        ItemStack result = null;

        for (int i = stackStringifyHandlers.size() - 1; i >= 0 && result == null; i--) {
            result = stackStringifyHandlers.get(i).normalizeRecipeQueryStack(stack);
        }

        return result == null ? stack : result;
    }

    public static boolean isFluidDisplayItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }

        for (int i = stackStringifyHandlers.size() - 1; i >= 0; i--) {
            if (stackStringifyHandlers.get(i).isFluidDisplayItem(stack)) {
                return true;
            }
        }
        return false;
    }

    public static void pauseItemDamageSound(boolean pause) {
        if (isPausedItemDamageSound != (isPausedItemDamageSound = pause)) {
            for (IStackStringifyHandler handler : stackStringifyHandlers) {
                handler.pauseItemDamageSound(pause);
            }
        }
    }

    public static boolean isPausedItemDamageSound() {
        return isPausedItemDamageSound;
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
        if (stackA == null || stackB == null) {
            return false;
        }

        if (isFluidDisplayItem(stackA)) {

            if (isFluidDisplayItem(stackB)) {
                final FluidStack fluidA = getFluid(stackA);
                final FluidStack fluidB = getFluid(stackB);
                return fluidA != null && fluidA.isFluidEqual(fluidB);
            }

            return false;
        }

        if (stackA.getItem() != stackB.getItem()) {
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
        if (stack == null) {
            return null;
        }

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
        return stack.getItem() instanceof IFluidContainerItem && !isFluidDisplayItem(stack)
                || FluidContainerRegistry.isContainer(stack);
    }

    public static String getItemStackGUID(ItemStack stack) {
        final Item stackItem = stack.getItem();
        final Map<StackMetaKey, String> itemGuids = stackItem != null ? guidcache.get(stackItem) : null;
        String guid = itemGuids != null ? itemGuids.get(new StackMetaKey(stack)) : null;

        if (guid != null) {
            return guid;
        }

        final NBTTagCompound nbTag = itemStackToNBT(stack, false);

        if (nbTag == null || stack.getItem() == null) {
            return "unknown";
        }

        nbTag.removeTag("Count");

        if (nbTag.getShort("Damage") == 0) {
            nbTag.removeTag("Damage");
        }

        if (nbTag.hasKey("tag") && nbTag.getCompoundTag("tag").hasNoTags()) {
            nbTag.removeTag("tag");
        }

        if (nbTag.hasKey("strId") && (guidfilters.containsKey(nbTag.getString("strId"))
                || guidfilters.containsKey(nbTag.getString("strId") + "&" + nbTag.getShort("Damage")))) {
            final String strId = nbTag.getString("strId")
                    + (guidfilters.containsKey(nbTag.getString("strId")) ? "" : "&" + nbTag.getShort("Damage"));
            final ArrayList<String> keys = new ArrayList<>();

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
                    keys.add(NBTHelper.toString(item));
                } else if (local != null) {
                    keys.add(String.valueOf(local));
                }
            });

            guid = keys.toString();
        } else {
            guid = NBTHelper.toString(nbTag);
        }

        guidcache.computeIfAbsent(stack.getItem(), item -> new ConcurrentHashMap<>())
                .put(new StackMetaKey(stack.copy()), guid);

        return guid;
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
        ItemStack result = stacks[0];

        for (int i = 1; i < stacks.length; i++) {
            final ItemStack stack = stacks[i];
            if (stack.getItem() != null && isLess(stack, result) && !ItemInfo.isHidden(stack)) {
                result = stack;
            }
        }

        return result.copy();
    }

    private static boolean isLess(ItemStack stack, ItemStack other) {
        if (stack.getItemDamage() != other.getItemDamage()) {
            return stack.getItemDamage() < other.getItemDamage();
        }

        final String name = getItemName(stack);
        if (name == null) return false;

        final String otherName = getItemName(other);
        return otherName == null || name.compareTo(otherName) < 0;
    }

    private static String getItemName(ItemStack stack) {
        final Item item = stack.getItem();
        return item == null ? null : Item.itemRegistry.getNameForObject(item);
    }
}
