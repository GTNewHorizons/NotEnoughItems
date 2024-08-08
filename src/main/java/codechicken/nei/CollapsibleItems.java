package codechicken.nei;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;

import org.apache.commons.io.IOUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import codechicken.nei.ItemList.AllMultiItemFilter;
import codechicken.nei.ItemList.AnyMultiItemFilter;
import codechicken.nei.ItemList.EverythingItemFilter;
import codechicken.nei.ItemList.NegatedItemFilter;
import codechicken.nei.ItemList.NothingItemFilter;
import codechicken.nei.PresetsList.Preset;
import codechicken.nei.PresetsList.PresetMode;
import codechicken.nei.api.ItemFilter;
import cpw.mods.fml.common.registry.GameData;

public class CollapsibleItems {

    protected static interface ISearchParserProvider {

        public ItemFilter getFilter(String searchText);

        default Predicate<String> getMatcher(String searchText) {

            if (searchText.length() >= 3 && searchText.startsWith("r/") && searchText.endsWith("/")) {

                try {
                    Pattern pattern = Pattern.compile(
                            searchText.substring(2, searchText.length() - 1),
                            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                    return value -> pattern.matcher(value).find();
                } catch (PatternSyntaxException ignored) {}

            } else if (!searchText.isEmpty()) {
                return value -> value.toLowerCase().contains(searchText);
            }

            return null;
        }

    }

    protected static class GroupTokenParser {

        protected final HashMap<Character, ISearchParserProvider> searchProviders = new HashMap<>();

        public void addProvider(char prefix, ISearchParserProvider provider) {
            this.searchProviders.put(prefix, provider);
        }

        public ItemFilter getFilter(String filterText) {
            final String[] parts = filterText.toLowerCase().trim().split("\\|");
            final List<ItemFilter> searchTokens = Arrays.stream(parts).map(s -> parseSearchText(s))
                    .filter(s -> s != null && !s.filters.isEmpty()).collect(Collectors.toCollection(ArrayList::new));

            if (searchTokens.isEmpty()) {
                return new EverythingItemFilter();
            } else if (searchTokens.size() == 1) {
                return searchTokens.get(0);
            } else {
                return new AnyMultiItemFilter(searchTokens);
            }
        }

        private AllMultiItemFilter parseSearchText(String filterText) {

            if (filterText.isEmpty()) {
                return null;
            }

            final String[] tokens = filterText.split("\\s+");
            final AllMultiItemFilter searchTokens = new AllMultiItemFilter();

            for (String token : tokens) {
                token = token.trim();
                boolean ignore = token.startsWith("!");

                if (ignore) {
                    token = token.substring(1);
                }

                if (token.isEmpty()) {
                    continue;
                }

                ISearchParserProvider provider = this.searchProviders.get(token.charAt(0));

                if (provider != null) {
                    token = token.substring(1);
                } else {
                    provider = this.searchProviders.get('\0');
                }

                ItemFilter filter = parseToken(ignore ? "!" + token : token, provider);

                if (filter != null) {
                    searchTokens.filters.add(filter);
                }
            }

            return searchTokens;
        }

        private ItemFilter parseToken(String token, ISearchParserProvider provider) {
            final String[] parts = token.split(",");
            final AnyMultiItemFilter includeFilter = new AnyMultiItemFilter();
            final AnyMultiItemFilter expludeFilter = new AnyMultiItemFilter();
            final AllMultiItemFilter groupFilter = new AllMultiItemFilter();

            for (String part : parts) {
                boolean ignore = part.startsWith("!");

                if (ignore) {
                    part = part.substring(1);
                }

                ItemFilter filter = provider.getFilter(part);

                if (filter == null) {
                    continue;
                }

                if (ignore) {
                    expludeFilter.filters.add(filter);
                } else {
                    includeFilter.filters.add(filter);
                }
            }

            if (!includeFilter.filters.isEmpty()) {
                groupFilter.filters.add(includeFilter);
            }

            if (!expludeFilter.filters.isEmpty()) {
                groupFilter.filters.add(new NegatedItemFilter(expludeFilter));
            }

            return groupFilter.filters.isEmpty() ? null : groupFilter;
        }
    }

    protected static class IdentifierFilter implements ISearchParserProvider {

        @Override
        public ItemFilter getFilter(String searchText) {

            if (Pattern.matches("^\\d+(-\\d+)*$", searchText)) {
                final Predicate<Integer> filter = generateDamageFilter(searchText);
                return (stack) -> filter.test(stack.getItemDamage());
            }

            Predicate<String> matcher = getMatcher(searchText);

            if (matcher != null) {
                return stack -> matcher.test(getStringIdentifier(stack));
            }

            return null;
        }

        protected String getStringIdentifier(ItemStack stack) {
            String name = GameData.getItemRegistry().getNameForObject(stack.getItem());
            return name == null || name.isEmpty() ? "Unknown:Unknown" : name;
        }

        protected Predicate<Integer> generateDamageFilter(String searchText) {
            String[] range = searchText.split("-");

            if (range.length == 1) {
                final int damage = Integer.parseInt(range[0]);
                return (dmg) -> dmg == damage;
            } else {
                final int damageStart = Integer.parseInt(range[0]);
                final int damageEnd = Integer.parseInt(range[1]);
                return (dmg) -> dmg >= damageStart && dmg <= damageEnd;
            }
        }
    }

    protected static class OreDictionaryFilter implements ISearchParserProvider {

        @Override
        public ItemFilter getFilter(String searchText) {
            Predicate<String> matcher = getMatcher(searchText);

            if (matcher != null) {
                return stack -> matches(stack, matcher);
            }

            return null;
        }

        protected boolean matches(ItemStack stack, Predicate<String> matcher) {
            return IntStream.of(OreDictionary.getOreIDs(stack))
                    .anyMatch(id -> matcher.test(OreDictionary.getOreName(id)));
        }
    }

    protected static class GroupItem {

        public String guid;
        public ItemFilter filter;
        public boolean expanded = false;
        public String displayName = "";

        public GroupItem() {}

        public void setFilter(String filter) {
            this.filter = CollapsibleItems.groupParser.getFilter(filter.trim());
            this.guid = UUID.nameUUIDFromBytes(filter.getBytes()).toString();
        }

        public void setFilter(ItemFilter filter, String guid) {
            this.filter = filter;
            this.guid = guid;
        }

        public boolean matches(ItemStack stack) {
            return this.filter.matches(stack);
        }
    }

    protected File statesFile;
    protected static final GroupTokenParser groupParser = new GroupTokenParser();
    protected final List<GroupItem> groups = new ArrayList<>();
    protected final Map<ItemStack, Integer> cache = new ConcurrentHashMap<>();

    static {
        groupParser.addProvider('\0', new IdentifierFilter());
        groupParser.addProvider('$', new OreDictionaryFilter());
    }

    public void reload() {
        this.groups.clear();
        this.cache.clear();

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
            loadCollapsibleItems();
        }

        loadStates();

        if (ItemList.loadFinished) {
            LayoutManager.markItemsDirty();
        }
    }

    protected void loadCollapsibleItems() {
        File file = NEIClientConfig.collapsibleItemsFile;
        if (!file.exists()) {
            try (FileWriter writer = new FileWriter(file)) {
                NEIClientConfig.logger.info("Creating default collapsible items list {}", file);
                URL defaultCollapsibleItemsResource = ClientHandler.class
                        .getResource("/assets/nei/cfg/collapsibleitems.cfg");
                if (defaultCollapsibleItemsResource != null) {
                    IOUtils.copy(defaultCollapsibleItemsResource.openStream(), writer);
                }
            } catch (IOException e) {
                NEIClientConfig.logger.error("Failed to save default collapsible items list to file {}", file, e);
            }
        }

        try (FileReader reader = new FileReader(file)) {
            NEIClientConfig.logger.info("Loading collapsible items from file {}", file);
            parseFile(IOUtils.readLines(reader));
        } catch (IOException e) {
            NEIClientConfig.logger.error("Failed to load collapsible items from file {}", file, e);
        }
    }

    public void parseFile(List<String> itemStrings) {
        final JsonParser parser = new JsonParser();
        GroupItem group = new GroupItem();

        for (String itemStr : itemStrings) {

            if (itemStr.startsWith("#") || itemStr.trim().isEmpty()) {
                continue;
            }

            try {

                if (itemStr.startsWith("; ")) {
                    JsonObject settings = parser.parse(itemStr.substring(2)).getAsJsonObject();

                    if (settings.get("displayName") != null) {
                        group.displayName = settings.get("displayName").getAsString();
                    }

                    if (settings.get("unlocalizedName") != null) {
                        String displayName = StatCollector
                                .translateToLocal(settings.get("unlocalizedName").getAsString());

                        if (!displayName.equals(settings.get("unlocalizedName").getAsString())) {
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

    protected void addGroup(GroupItem group) {
        if (group == null || group.filter == null
                || group.filter instanceof EverythingItemFilter
                || group.filter instanceof NothingItemFilter)
            return;
        this.groups.add(group);
    }

    public boolean isEmpty() {
        return this.groups.isEmpty();
    }

    public ItemFilter getItemFilter() {
        AnyMultiItemFilter filter = new AnyMultiItemFilter();

        for (GroupItem group : this.groups) {
            filter.filters.add(group.filter);
        }

        return filter;
    }

    public void updateCache(final List<ItemStack> items) {
        this.cache.clear();

        try {

            ItemList.forkJoinPool.submit(() -> items.parallelStream().forEach(stack -> {
                GroupItem group = this.groups.stream().filter(g -> g.matches(stack)).findFirst().orElse(null);

                if (group != null) {
                    this.cache.put(stack, this.groups.indexOf(group));
                }
            })).get();

        } catch (Exception e) {
            NEIClientConfig.logger.error("Error create collapsible items groups", e);
        }

    }

    public int getGroupIndex(ItemStack stack) {

        if (stack == null) {
            return -1;
        }

        return this.cache.getOrDefault(stack, -1);
    }

    public String getDisplayName(int groupIndex) {

        if (groupIndex < this.groups.size()) {
            return this.groups.get(groupIndex).displayName;
        }

        return null;
    }

    public boolean isExpanded(int groupIndex) {

        if (groupIndex < this.groups.size()) {
            return this.groups.get(groupIndex).expanded;
        }

        return true;
    }

    public void setExpanded(int groupIndex, boolean expanded) {

        if (groupIndex < this.groups.size()) {
            this.groups.get(groupIndex).expanded = expanded;
            saveStates();
        }
    }

    public void toggleGroups(Boolean expanded) {

        if (expanded == null) {
            expanded = !this.groups.stream().filter(g -> g.expanded).findAny().isPresent();
        }

        for (GroupItem group : this.groups) {
            group.expanded = expanded;
        }

        saveStates();
    }

    private void loadStates() {

        try {

            if (NEIClientConfig.world.nbt.hasKey("collapsibleitems")) {
                NBTTagCompound states = NEIClientConfig.world.nbt.getCompoundTag("collapsibleitems");
                @SuppressWarnings("unchecked")
                final Map<String, NBTPrimitive> list = (Map<String, NBTPrimitive>) states.tagMap;
                final Map<String, GroupItem> mapping = new HashMap<>();

                for (GroupItem group : this.groups) {
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

    private void saveStates() {
        NBTTagCompound list = new NBTTagCompound();

        for (GroupItem group : this.groups) {
            list.setBoolean(group.guid, group.expanded);
        }

        NEIClientConfig.world.nbt.setTag("collapsibleitems", list);
    }

}
