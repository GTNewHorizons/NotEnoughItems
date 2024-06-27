package codechicken.nei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import codechicken.lib.config.ConfigTagParent;
import codechicken.nei.ItemList.ItemsLoadedCallback;
import codechicken.nei.api.API;
import codechicken.nei.api.ItemInfo;
import codechicken.nei.config.GuiItemSorter;
import codechicken.nei.config.OptionOpenGui;

public class ItemSorter implements Comparator<ItemStack>, ItemsLoadedCallback {

    public static class SortEntry {

        public final String name;
        public final Comparator<ItemStack> comparator;

        public SortEntry(String name, Comparator<ItemStack> comparator) {
            this.name = name;
            this.comparator = comparator;
        }

        public String getLocalisedName() {
            return StatCollector.translateToLocal(name);
        }

        public String getTooltip() {
            String tipname = name + ".tip";
            String tip = StatCollector.translateToLocal(tipname);
            return !tip.equals(tipname) ? tip : null;
        }
    }

    public static final ArrayList<SortEntry> entries = new ArrayList<>();
    public static ArrayList<SortEntry> list = new ArrayList<>();
    public static final ItemSorter instance = new ItemSorter();

    // optimisations
    public HashMap<ItemStack, Integer> ordering = null;

    public static void sort(ArrayList<ItemStack> items) {
        try {
            // items = (ArrayList<ItemStack>)
            // items.parallelStream().sorted(instance).collect(Collectors.toList());
            items.sort(instance);
        } catch (Exception e) {
            NEIClientConfig.logger.error("Exception sorting item list", e);
        }
    }

    @Override
    public int compare(ItemStack o1, ItemStack o2) {
        for (SortEntry e : list) {
            int c = e.comparator.compare(o1, o2);
            if (c != 0) return c;
        }
        return 0;
    }

    @Override
    public void itemsLoaded() {
        HashMap<ItemStack, Integer> newMap = new HashMap<>();
        int i = 0;
        for (ItemStack stack : ItemList.items) newMap.put(stack, i++);
        ordering = newMap;
    }

    public static SortEntry find(String name) {
        for (SortEntry e : entries) if (e.name.equals(name)) return e;

        return null;
    }

    public static int compareInt(int a, int b) {
        return Integer.compare(a, b);
    }

    public static void add(String name, Comparator<ItemStack> comparator) {
        SortEntry e = new SortEntry(name, comparator);
        entries.add(e);
        ArrayList<SortEntry> nlist = new ArrayList<>(list);
        nlist.add(e);
        list = nlist; // concurrency
    }

    public static void initConfig(ConfigTagParent tag) {
        // minecraft, mod, id, default, meta, name
        API.addSortOption("nei.itemsort.minecraft", (o1, o2) -> {
            boolean m1 = "minecraft".equals(ItemInfo.itemOwners.get(o1.getItem()));
            boolean m2 = "minecraft".equals(ItemInfo.itemOwners.get(o2.getItem()));
            return m1 == m2 ? 0 : m1 ? -1 : 1;
        });
        API.addSortOption("nei.itemsort.mod", (o1, o2) -> {
            String mod1 = ItemInfo.itemOwners.get(o1.getItem());
            String mod2 = ItemInfo.itemOwners.get(o2.getItem());
            if (mod1 == null) return mod2 == null ? 0 : 1;
            if (mod2 == null) return -1;
            return mod1.compareTo(mod2);
        });
        API.addSortOption("nei.itemsort.id", (o1, o2) -> {
            int id1 = Item.getIdFromItem(o1.getItem());
            int id2 = Item.getIdFromItem(o2.getItem());
            return compareInt(id1, id2);
        });
        API.addSortOption("nei.itemsort.default", (o1, o2) -> {
            Integer order1 = instance.ordering.get(o1);
            Integer order2 = instance.ordering.get(o2);
            if (order1 == null) return order2 == null ? 0 : 1;
            if (order2 == null) return -1;
            return compareInt(order1, order2);
        });
        API.addSortOption("nei.itemsort.damage", (o1, o2) -> {
            int id1 = o1.getItemDamage();
            int id2 = o2.getItemDamage();
            return compareInt(id1, id2);
        });
        API.addSortOption("nei.itemsort.name", (o1, o2) -> {
            String name1 = o1.getDisplayName();
            String name2 = o2.getDisplayName();
            return name1.compareTo(name2);
        });
        tag.getTag("inventory.itemsort").setDefaultValue(getSaveString(list));
        API.addOption(new OptionOpenGui("inventory.itemsort", GuiItemSorter.class) {

            @Override
            public void useGlobals() {
                super.useGlobals();
                list = fromSaveString(activeTag().getValue());
            }
        });
        ItemList.loadCallbacks.add(instance);
    }

    public static String getSaveString(List<SortEntry> list) {
        StringBuilder sb = new StringBuilder();
        for (SortEntry e : list) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.name);
        }
        return sb.toString();
    }

    public static ArrayList<SortEntry> fromSaveString(String s) {
        if (s == null) return new ArrayList<>(entries);

        ArrayList<SortEntry> list = new ArrayList<>();
        for (String s2 : s.split(",")) {
            SortEntry e = find(s2.trim());
            if (e != null) list.add(e);
        }
        for (SortEntry e : entries) if (!list.contains(e)) list.add(e);

        return list;
    }

    public static void loadConfig() {
        list = fromSaveString(NEIClientConfig.getStringSetting("inventory.itemsort"));
    }
}
