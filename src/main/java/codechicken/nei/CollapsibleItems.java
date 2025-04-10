package codechicken.nei;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import codechicken.nei.ItemList.AnyMultiItemFilter;
import codechicken.nei.ItemList.EverythingItemFilter;
import codechicken.nei.ItemList.NothingItemFilter;
import codechicken.nei.PresetsList.Preset;
import codechicken.nei.PresetsList.PresetMode;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.util.ItemStackFilterParser;

public class CollapsibleItems {

    protected static class GroupItem {

        public String guid;
        public ItemFilter filter;
        public boolean expanded = false;
        public String displayName = "";

        public void setFilter(String filter) {
            this.filter = ItemStackFilterParser.parse(filter.trim());
            this.guid = this.filter != null ? UUID.nameUUIDFromBytes(filter.getBytes()).toString() : "";
        }

        public void setFilter(ItemFilter filter, String guid) {
            this.filter = filter;
            this.guid = guid;
        }

        public boolean matches(ItemStack stack) {
            return this.filter.matches(stack);
        }
    }

    private static final String STATE_KEY = "collapsibleitems";

    protected static File statesFile;
    protected static final List<GroupItem> groups = new ArrayList<>();
    protected static final Map<ItemStack, Integer> cache = new ConcurrentHashMap<>();

    private CollapsibleItems() {}

    public static void load() {
        CollapsibleItems.groups.clear();
        CollapsibleItems.cache.clear();

        for (int i = PresetsList.presets.size() - 1; i >= 0; i--) {
            Preset preset = PresetsList.presets.get(i);
            if (preset.enabled && preset.mode == PresetMode.GROUP) {
                GroupItem group = new GroupItem();
                group.setFilter(preset, UUID.nameUUIDFromBytes(preset.items.toString().getBytes()).toString());
                group.displayName = preset.name;
                addGroup(group);
            }
        }

        if (NEIClientConfig.enableCollapsibleItems()) {
            ClientHandler.loadSettingsFile(
                    "collapsibleitems.cfg",
                    lines -> parseFile(lines.collect(Collectors.toCollection(ArrayList::new))));
        }

        loadStates();
    }

    private static void parseFile(List<String> itemStrings) {
        final JsonParser parser = new JsonParser();
        GroupItem group = new GroupItem();

        for (String itemStr : itemStrings) {
            try {

                if (itemStr.startsWith("; ")) {
                    JsonObject settings = parser.parse(itemStr.substring(2)).getAsJsonObject();

                    if (settings.get("displayName") != null) {
                        group.displayName = settings.get("displayName").getAsString();
                    }

                    if (settings.get("unlocalizedName") != null) {
                        String unlocalizedName = settings.get("unlocalizedName").getAsString();
                        String displayName = StatCollector.translateToLocal(unlocalizedName);

                        if (!displayName.equals(unlocalizedName)) {
                            group.displayName = displayName;
                        }
                    }

                    if (settings.get("expanded") != null) {
                        group.expanded = settings.get("expanded").getAsBoolean();
                    }

                } else {
                    group.setFilter(itemStr);
                }

                if (group != null && group.filter != null) {
                    addGroup(group);
                    group = new GroupItem();
                }

            } catch (IllegalArgumentException | JsonSyntaxException | IllegalStateException e) {
                NEIClientConfig.logger.error("Failed to load collapsible items from json string:\n{}", itemStr);
            }
        }
    }

    private static void addGroup(GroupItem group) {
        if (group == null || group.filter == null
                || group.filter instanceof EverythingItemFilter
                || group.filter instanceof NothingItemFilter)
            return;
        CollapsibleItems.groups.add(group);
    }

    public static boolean isEmpty() {
        return CollapsibleItems.groups.isEmpty();
    }

    public static ItemFilter getItemFilter() {
        AnyMultiItemFilter filter = new AnyMultiItemFilter();

        for (GroupItem group : CollapsibleItems.groups) {
            filter.filters.add(group.filter);
        }

        return filter;
    }

    public static void clearCache() {
        CollapsibleItems.cache.clear();
    }

    public static void putItem(ItemStack stack) {
        int groupIndex = -1;

        for (int i = 0; i < CollapsibleItems.groups.size() && groupIndex == -1; i++) {
            if (CollapsibleItems.groups.get(i).matches(stack)) {
                groupIndex = i;
            }
        }

        if (groupIndex != -1) {
            CollapsibleItems.cache.put(stack, groupIndex);
        }
    }

    public static int getGroupIndex(ItemStack stack) {

        if (stack == null) {
            return -1;
        }

        return CollapsibleItems.cache.getOrDefault(stack, -1);
    }

    public static String getDisplayName(int groupIndex) {

        if (groupIndex < CollapsibleItems.groups.size()) {
            return CollapsibleItems.groups.get(groupIndex).displayName;
        }

        return null;
    }

    public static boolean isExpanded(int groupIndex) {

        if (groupIndex < CollapsibleItems.groups.size()) {
            return CollapsibleItems.groups.get(groupIndex).expanded;
        }

        return true;
    }

    public static void setExpanded(int groupIndex, boolean expanded) {

        if (groupIndex < CollapsibleItems.groups.size()) {
            CollapsibleItems.groups.get(groupIndex).expanded = expanded;
        }
    }

    public static void toggleGroups(Boolean expanded) {

        if (expanded == null) {
            expanded = CollapsibleItems.groups.stream().noneMatch(g -> g.expanded);
        }

        for (GroupItem group : CollapsibleItems.groups) {
            group.expanded = expanded;
        }

    }

    public static void saveStates() {
        NBTTagCompound list = new NBTTagCompound();

        for (GroupItem group : CollapsibleItems.groups) {
            list.setBoolean(group.guid, group.expanded);
        }

        if (NEIClientConfig.world != null) {
            NEIClientConfig.world.nbt.setTag(STATE_KEY, list);
        }
    }

    private static void loadStates() {

        try {

            if (NEIClientConfig.world.nbt.hasKey(STATE_KEY)) {
                NBTTagCompound states = NEIClientConfig.world.nbt.getCompoundTag(STATE_KEY);
                @SuppressWarnings("unchecked")
                final Map<String, NBTPrimitive> list = (Map<String, NBTPrimitive>) states.tagMap;
                final Map<String, GroupItem> mapping = new HashMap<>();

                for (GroupItem group : CollapsibleItems.groups) {
                    mapping.put(group.guid, group);
                }

                for (Map.Entry<String, NBTPrimitive> nbtEntry : list.entrySet()) {
                    if (mapping.containsKey(nbtEntry.getKey())) {
                        mapping.get(nbtEntry.getKey()).expanded = nbtEntry.getValue().func_150290_f() == 1;
                    }
                }
            }

        } catch (Exception e) {
            NEIClientConfig.logger.error("Error loading collapsible items states", e);
        }

    }

}
