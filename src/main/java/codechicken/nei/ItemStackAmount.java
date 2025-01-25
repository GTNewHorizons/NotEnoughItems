package codechicken.nei;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import codechicken.nei.recipe.StackInfo;

public class ItemStackAmount {

    private final Map<NBTTagCompound, Long> itemMap = new LinkedHashMap<>();

    public void putAll(ItemStackAmount amounts) {
        for (Map.Entry<NBTTagCompound, Long> entry : amounts.itemMap.entrySet()) {
            this.itemMap.put(entry.getKey(), entry.getValue() + this.itemMap.getOrDefault(entry.getKey(), 0L));
        }
    }

    public void add(ItemStack item) {
        add(item, (long) StackInfo.getAmount(item));
    }

    public void add(ItemStack stack, Long value) {
        if (stack == null || stack.getItem() == null) return;
        final NBTTagCompound key = StackInfo.itemStackToNBT(stack, false);

        this.itemMap.put(key, value + this.itemMap.getOrDefault(key, 0L));
    }

    public Long get(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        final NBTTagCompound key = StackInfo.itemStackToNBT(stack, false);

        return this.itemMap.get(key);
    }

    public void put(ItemStack stack, long value) {
        if (stack == null || stack.getItem() == null) return;
        final NBTTagCompound key = StackInfo.itemStackToNBT(stack, false);

        this.itemMap.put(key, value);
    }

    public long getOrDefault(ItemStack stack, long defaultAmount) {
        final Long e = get(stack);

        return e == null ? defaultAmount : e;
    }

    public void clear() {
        this.itemMap.clear();
    }

    public Long remove(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        final NBTTagCompound key = StackInfo.itemStackToNBT(stack, false);

        return this.itemMap.remove(key);
    }

    public boolean removeIf(Predicate<Map.Entry<NBTTagCompound, Long>> predicate) {
        return this.itemMap.entrySet().removeIf(predicate);
    }

    public Set<Map.Entry<NBTTagCompound, Long>> entrySet() {
        return this.itemMap.entrySet();
    }

    public List<ItemStack> values() {
        List<ItemStack> list = new ArrayList<>();

        for (Map.Entry<NBTTagCompound, Long> entry : this.itemMap.entrySet()) {
            list.add(StackInfo.loadFromNBT(entry.getKey(), Math.max(0, entry.getValue())));
        }

        return list;
    }

    public int size() {
        return this.itemMap.size();
    }

    public boolean isEmpty() {
        return this.itemMap.isEmpty();
    }

    public static ItemStackAmount of(ItemStackAmount map) {
        ItemStackAmount result = new ItemStackAmount();
        result.itemMap.putAll(map.itemMap);
        return result;
    }

    public static ItemStackAmount of(ItemStackMap<Long> map) {
        ItemStackAmount result = new ItemStackAmount();

        for (ItemStackMap.Entry<Long> entry : map.entries()) {
            result.put(entry.key, entry.value);
        }

        return result;
    }

    public static ItemStackAmount of(Iterable<ItemStack> items) {
        ItemStackAmount result = new ItemStackAmount();

        for (ItemStack stack : items) {
            result.add(stack);
        }

        return result;
    }

}
