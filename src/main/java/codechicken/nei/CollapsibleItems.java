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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.apache.commons.io.IOUtils;

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

        protected static int lastGroupIndex = 0;

        public final int groupIndex;
        public final ItemFilter filter;
        public boolean expanded = false;

        public GroupItem(ItemFilter filter) {
            this.groupIndex = lastGroupIndex++;
            this.filter = filter;
        }

        public boolean matches(ItemStack stack) {
            return this.filter.matches(stack);
        }
    }

    protected static final GroupTokenParser groupParser = new GroupTokenParser();
    protected final Map<Integer, GroupItem> groups = new ConcurrentHashMap<>();
    protected final Map<ItemStack, Integer> cache = new ConcurrentHashMap<>();

    static {
        groupParser.addProvider('\0', new IdentifierFilter());
        groupParser.addProvider('$', new OreDictionaryFilter());
    }

    public void reload() {
        GroupItem.lastGroupIndex = 0;
        this.groups.clear();
        this.cache.clear();

        for (int i = PresetsList.presets.size() - 1; i >= 0; i--) {
            Preset preset = PresetsList.presets.get(i);
            if (preset.enabled && preset.mode == PresetMode.GROUP) {
                addGroup(preset);
            }
        }

        loadCollapsibleItems();

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
            IOUtils.readLines(reader).stream().filter((line) -> !line.startsWith("#") && !line.trim().isEmpty())
                    .forEach(this::addGroup);
        } catch (IOException e) {
            NEIClientConfig.logger.error("Failed to load collapsible items from file {}", file, e);
        }
    }

    protected void addGroup(String filterText) {
        addGroup(CollapsibleItems.groupParser.getFilter(filterText.trim()));
    }

    protected void addGroup(ItemFilter filter) {
        if (filter == null || filter instanceof EverythingItemFilter || filter instanceof NothingItemFilter) return;
        GroupItem group = new GroupItem(filter);
        this.groups.put(group.groupIndex, group);
    }

    public ItemFilter getItemFilter() {
        AnyMultiItemFilter filter = new AnyMultiItemFilter();

        for (GroupItem group : this.groups.values()) {
            filter.filters.add(group.filter);
        }

        return filter;
    }

    public void updateCache(final List<ItemStack> items) {
        this.cache.clear();

        try {

            ItemList.forkJoinPool.submit(() -> items.parallelStream().forEach(stack -> {
                GroupItem group = this.groups.values().stream().filter(g -> g.matches(stack)).findFirst().orElse(null);

                if (group != null) {
                    this.cache.put(stack, group.groupIndex);
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

    public boolean isExpanded(int groupIndex) {

        if (this.groups.containsKey(groupIndex)) {
            return this.groups.get(groupIndex).expanded;
        }

        return true;
    }

    public void setExpanded(int groupIndex, boolean expanded) {

        if (this.groups.containsKey(groupIndex)) {
            this.groups.get(groupIndex).expanded = expanded;
        }

    }

    public void toggleGroups(Boolean expanded) {

        if (expanded == null) {
            expanded = !this.groups.values().stream().filter(g -> g.expanded).findAny().isPresent();
        }

        for (GroupItem group : this.groups.values()) {
            group.expanded = expanded;
        }

    }

}
