package codechicken.nei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import codechicken.nei.ItemList.AllMultiItemFilter;
import codechicken.nei.ItemList.AnyMultiItemFilter;
import codechicken.nei.ItemList.EverythingItemFilter;
import codechicken.nei.ItemList.NegatedItemFilter;
import codechicken.nei.ItemList.NothingItemFilter;
import codechicken.nei.api.ItemFilter;
import gnu.trove.map.TCharCharMap;
import gnu.trove.map.hash.TCharCharHashMap;

public class SearchTokenParser {

    public enum SearchMode {

        ALWAYS,
        PREFIX,
        NEVER;

        public static SearchMode fromInt(int value) {
            return switch (value) {
                case 0 -> ALWAYS;
                case 1 -> PREFIX;
                default -> NEVER;
            };
        }
    }

    public static interface ISearchParserProvider {

        public ItemFilter getFilter(String searchText);

        public static List<Language> getAllLanguages() {
            return new ArrayList<>(Minecraft.getMinecraft().getLanguageManager().getLanguages());
        }

        default List<Language> getMatchingLanguages() {
            return getAllLanguages();
        }

        public char getPrefix();

        public EnumChatFormatting getHighlightedColor();

        public SearchMode getSearchMode();
    }

    private static class ProvidersCache {

        public String languageCode = null;
        public List<ISearchParserProvider> providers = new ArrayList<>();

        public void clear() {
            this.languageCode = null;
            this.providers.clear();
        }
    }

    private static class IsRegisteredItemFilter implements ItemFilter {

        public ItemFilter filter;

        public IsRegisteredItemFilter(ItemFilter filter) {
            this.filter = filter;
        }

        @Override
        public boolean matches(ItemStack item) {
            return item != null && item.getItem() != null
                    && item.getItem().delegate.name() != null
                    && this.filter.matches(item);
        }
    }

    private static class ItemFilterCache implements ItemFilter {

        private final Map<ItemStack, Boolean> states;
        public ItemFilter filter;

        public ItemFilterCache(ItemFilter filter) {
            this.states = Collections.synchronizedMap(new WeakHashMap<>());
            this.filter = filter;
        }

        @Override
        public boolean matches(ItemStack item) {
            return states.computeIfAbsent(item, stack -> this.filter.matches(stack));
        }
    }

    private final LinkedHashMap<String, ItemFilter> filtersCache = new LinkedHashMap<>() {

        private static final long serialVersionUID = 1042213947848622164L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemFilter> eldest) {
            return size() > 20;
        }
    };

    protected final List<ISearchParserProvider> searchProviders;
    protected final ProvidersCache providersCache = new ProvidersCache();
    protected final TCharCharMap prefixRedefinitions = new TCharCharHashMap();

    public SearchTokenParser(List<ISearchParserProvider> searchProviders) {
        this.searchProviders = searchProviders;
    }

    public SearchTokenParser() {
        this(new ArrayList<>());
    }

    public void addProvider(ISearchParserProvider provider) {
        this.searchProviders.add(provider);
        this.providersCache.clear();
        this.filtersCache.clear();
    }

    public void clearCache() {
        this.filtersCache.clear();
    }

    protected List<ISearchParserProvider> getProviders() {
        Language currentLanguage = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage();

        if (!currentLanguage.getLanguageCode().equals(providersCache.languageCode)) {
            Map<Character, ISearchParserProvider> providers = new HashMap<>();

            for (int index = this.searchProviders.size() - 1; index >= 0; index--) {
                ISearchParserProvider provider = this.searchProviders.get(index);

                if (!providers.containsKey(provider.getPrefix())
                        && provider.getMatchingLanguages().contains(currentLanguage)) {
                    providers.put(provider.getPrefix(), provider);
                }
            }

            providersCache.providers = new ArrayList<>(providers.values());
            providersCache.languageCode = currentLanguage.getLanguageCode();
        }

        return providersCache.providers;
    }

    public ISearchParserProvider getProvider(char ch) {
        return getProviders().stream()
                .filter(
                        provider -> provider.getSearchMode() == SearchMode.PREFIX
                                && getRedefinedPrefix(provider.getPrefix()) == ch)
                .findFirst().orElse(null);
    }

    public synchronized ItemFilter getFilter(String filterText) {
        filterText = EnumChatFormatting.getTextWithoutFormattingCodes(filterText).toLowerCase();

        return this.filtersCache.computeIfAbsent(filterText, text -> {
            final String[] parts = text.split("\\|");
            final List<ItemFilter> searchTokens = Arrays.stream(parts).map(this::parseSearchText).filter(s -> s != null)
                    .collect(Collectors.toCollection(ArrayList::new));

            if (searchTokens.isEmpty()) {
                return new EverythingItemFilter();
            } else if (searchTokens.size() == 1) {
                return new IsRegisteredItemFilter(new ItemFilterCache(searchTokens.get(0)));
            } else {
                return new IsRegisteredItemFilter(new ItemFilterCache(new AnyMultiItemFilter(searchTokens)));
            }

        });

    }

    public Pattern getSplitPattern() {
        StringBuilder prefixes = new StringBuilder().append('\0');

        for (ISearchParserProvider provider : getProviders()) {
            if (provider.getSearchMode() == SearchMode.PREFIX) {
                prefixes.append(getRedefinedPrefix(provider.getPrefix()));
            }
        }

        return Pattern.compile("((-*)([" + Pattern.quote(prefixes.toString()) + "]*)(\\\".*?(?:\\\"|$)|\\S+))");
    }

    public char getRedefinedPrefix(char prefix) {
        if (this.prefixRedefinitions.containsKey(prefix)) {
            return this.prefixRedefinitions.get(prefix);
        }
        return prefix;
    }

    private ItemFilter parseSearchText(String filterText) {

        if (filterText.isEmpty()) {
            return null;
        }

        final Matcher filterMatcher = getSplitPattern().matcher(filterText);
        final AllMultiItemFilter searchTokens = new AllMultiItemFilter();

        while (filterMatcher.find()) {
            boolean ignore = "-".equals(filterMatcher.group(2));
            String firstChar = filterMatcher.group(3);
            String token = filterMatcher.group(4);
            boolean quotes = token.length() > 1 && token.startsWith("\"") && token.endsWith("\"");

            if (quotes) {
                token = token.substring(1, token.length() - 1);
            }

            if (!token.isEmpty()) {
                ItemFilter result = parseToken(firstChar, token);

                if (ignore) {
                    searchTokens.filters.add(new NegatedItemFilter(result));
                } else {
                    searchTokens.filters.add(result);
                }
            } else if (!ignore) {
                searchTokens.filters.add(new NothingItemFilter());
            }
        }

        return searchTokens;
    }

    private ItemFilter parseToken(String firstChar, String token) {
        final ISearchParserProvider provider = firstChar.isEmpty() ? null : this.getProvider(firstChar.charAt(0));

        if (provider == null || provider.getSearchMode() == SearchMode.NEVER) {
            final List<ItemFilter> filters = new ArrayList<>();

            for (ISearchParserProvider _provider : getProviders()) {
                if (_provider.getSearchMode() == SearchMode.ALWAYS) {
                    ItemFilter filter = _provider.getFilter(token);

                    if (filter != null) {
                        filters.add(filter);
                    }
                }
            }

            return filters.isEmpty() ? new NothingItemFilter() : new AnyMultiItemFilter(filters);
        } else {
            return provider.getFilter(token);
        }
    }
}
