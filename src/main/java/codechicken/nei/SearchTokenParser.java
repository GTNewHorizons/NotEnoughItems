package codechicken.nei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import codechicken.nei.ItemList.AllMultiItemFilter;
import codechicken.nei.ItemList.AnyMultiItemFilter;
import codechicken.nei.ItemList.EverythingItemFilter;
import codechicken.nei.ItemList.NegatedItemFilter;
import codechicken.nei.ItemList.NothingItemFilter;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.search.SearchExpressionErrorListener;
import codechicken.nei.search.SearchExpressionFilterVisitor;
import codechicken.nei.search.SearchExpressionLexer;
import codechicken.nei.search.SearchExpressionParser;

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

    public static class SearchToken {

        public boolean ignore = false;
        public boolean quotes = false;
        public Character firstChar = null;

        public String[] words;

        public String rawText = "";
        public int start = 0;
        public int end = 0;

    }

    public static interface ISearchParserProvider {

        public ItemFilter getFilter(String searchText);

        public ItemFilter getFilter(Pattern pattern);

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

    protected final LRUCache<String, ItemFilter> filtersCache = new LRUCache<>(20);
    protected final List<ISearchParserProvider> searchProviders;
    protected final ProvidersCache providersCache = new ProvidersCache();
    protected final Map<Character, Character> prefixRedefinitions = new HashMap<>();

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
            providersCache.providers = new ArrayList<>();
            providersCache.languageCode = currentLanguage.getLanguageCode();

            Map<Character, ISearchParserProvider> providers = new HashMap<>();

            for (int index = this.searchProviders.size() - 1; index >= 0; index--) {
                ISearchParserProvider provider = this.searchProviders.get(index);

                if (provider.getMatchingLanguages().contains(currentLanguage)) {
                    if (provider.getSearchMode() == SearchMode.PREFIX && !providers.containsKey(provider.getPrefix())) {
                        providers.put(provider.getPrefix(), provider);
                    } else if (provider.getSearchMode() == SearchMode.ALWAYS) {
                        providersCache.providers.add(provider);
                    }
                }
            }

            providersCache.providers.addAll(providers.values());
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

    public ISearchParserProvider getProviderForDefaultPrefix(char ch) {
        return getProviders().stream()
                .filter(provider -> provider.getSearchMode() == SearchMode.PREFIX && provider.getPrefix() == ch)
                .findFirst().orElse(null);
    }

    public synchronized ItemFilter getFilter(String filterText) {
        filterText = EnumChatFormatting.getTextWithoutFormattingCodes(filterText).toLowerCase();
        if (filterText == null || filterText.isEmpty()) {
            return new EverythingItemFilter();
        }
        int spaceModeEnabled = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
        int patternMode = NEIClientConfig.getIntSetting("inventory.search.patternMode");

        if (spaceModeEnabled == 1 && patternMode == 3) {
            filterText = filterText.replaceAll(" ", "\\\\ ");
        }

        if (patternMode != 3) {
            return this.filtersCache.computeIfAbsent(filterText, text -> {
                final String[] parts = text.split("\\|");
                final List<ItemFilter> searchTokens = Arrays.stream(parts).map(this::parseSearchText)
                        .filter(s -> s != null).collect(Collectors.toList());

                if (searchTokens.isEmpty()) {
                    return new EverythingItemFilter();
                } else if (searchTokens.size() == 1) {
                    return new IsRegisteredItemFilter(searchTokens.get(0));
                } else {
                    return new IsRegisteredItemFilter(new AnyMultiItemFilter(searchTokens));
                }
            });
        } else {
            return this.filtersCache.computeIfAbsent(filterText, text -> {
                final CharStream inputStream = CharStreams.fromString(text);
                final SearchExpressionErrorListener errorListener = new SearchExpressionErrorListener(true);
                final SearchExpressionLexer lexer = new SearchExpressionLexer(inputStream);
                lexer.removeErrorListeners();
                lexer.addErrorListener(errorListener);
                final CommonTokenStream tokenStream = new CommonTokenStream(lexer);
                final SearchExpressionParser parser = new SearchExpressionParser(tokenStream);
                parser.removeErrorListeners();
                parser.addErrorListener(errorListener);
                final SearchExpressionFilterVisitor visitor = new SearchExpressionFilterVisitor(this, true);
                final ItemFilter searchToken = visitor.visitSearchExpression(parser.searchExpression());

                return new IsRegisteredItemFilter(searchToken);
            });
        }

    }

    public List<SearchToken> splitSearchText(String filterText) {

        if (filterText.isEmpty()) {
            return Collections.emptyList();
        }

        final List<SearchToken> tokens = new ArrayList<>();
        final String prefixes = getPrefixes();
        final int spaceMode = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
        // The regular expression first tries to match a string that starts with a space or the beginning of the string,
        // followed by a sequence of characters that do not contain special characters -"@#$%&, and ends with a space
        // and the characters -"@#$%& or the end of the string.
        final String patternPart1 = "(?<tokenA>(^|\\s+)(?:[^" + Pattern.quote(" -\"" + prefixes)
                + "]).+?(?=\\s+["
                + Pattern.quote("-\"" + prefixes)
                + "]|$))";
        // If the first condition is not met, it tries to match a sequence of - characters, followed by @#$%&
        // characters, then either a quoted string or non-space characters.
        final String patternPart2 = "((?<ignore>-*)(?<firstChar>[" + Pattern.quote(prefixes)
                + "]*)(?<tokenB>\\\".*?(?:\\\"|$)|\\S+\\s*))";
        final Pattern pattern = Pattern.compile(spaceMode == 0 ? patternPart2 : (patternPart1 + "|" + patternPart2));
        final Matcher filterMatcher = pattern.matcher(filterText);

        while (filterMatcher.find()) {
            String firstChar = filterMatcher.group("firstChar");
            SearchToken token = new SearchToken();
            token.start = filterMatcher.start();
            token.end = filterMatcher.end();
            token.ignore = "-".equals(filterMatcher.group("ignore"));
            token.rawText = spaceMode == 0 ? null : filterMatcher.group("tokenA");

            if (firstChar != null && !firstChar.isEmpty()) {
                token.firstChar = firstChar.charAt(0);
            }

            if (token.rawText == null) { // spaceMode == 0
                token.rawText = filterMatcher.group("tokenB");
                token.quotes = token.rawText.length() > 1 && token.rawText.startsWith("\"")
                        && token.rawText.endsWith("\"");

                if (token.quotes) {
                    token.rawText = token.rawText.substring(1, token.rawText.length() - 1);
                }

                token.words = new String[] { token.rawText.trim() };
            } else if (spaceMode == 2) {
                token.words = token.rawText.trim().split("\\s+");
            } else {
                token.words = new String[] { token.rawText.trim() };
            }

            tokens.add(token);
        }

        return tokens;
    }

    private String getPrefixes() {
        StringBuilder prefixes = new StringBuilder().append('\0');

        for (ISearchParserProvider provider : getProviders()) {
            if (provider.getSearchMode() == SearchMode.PREFIX) {
                prefixes.append(getRedefinedPrefix(provider.getPrefix()));
            }
        }

        return prefixes.toString();
    }

    public char getRedefinedPrefix(char prefix) {
        return this.prefixRedefinitions.getOrDefault(prefix, prefix);
    }

    private ItemFilter parseSearchText(String filterText) {

        if (filterText.isEmpty()) {
            return null;
        }

        final AllMultiItemFilter searchTokens = new AllMultiItemFilter();
        final List<SearchToken> tokens = splitSearchText(filterText);

        for (SearchToken token : tokens) {
            if (!token.rawText.isEmpty()) {
                ItemFilter result = parseToken(token);

                if (token.ignore) {
                    searchTokens.filters.add(new NegatedItemFilter(result));
                } else {
                    searchTokens.filters.add(result);
                }
            } else if (!token.ignore) {
                searchTokens.filters.add(new NothingItemFilter());
            }
        }

        return searchTokens;
    }

    private ItemFilter parseToken(SearchToken token) {
        final ISearchParserProvider provider = token.firstChar == null ? null : this.getProvider(token.firstChar);

        if (provider == null || provider.getSearchMode() == SearchMode.NEVER) {
            final List<ItemFilter> filters = new ArrayList<>();

            for (ISearchParserProvider _provider : getProviders()) {
                if (_provider.getSearchMode() == SearchMode.ALWAYS) {
                    AllMultiItemFilter filter = generateFilters(_provider, token.words);

                    if (!filter.filters.isEmpty()) {
                        filters.add(filter);
                    }
                }
            }

            return filters.isEmpty() ? new NothingItemFilter() : new AnyMultiItemFilter(filters);
        } else {
            return generateFilters(provider, token.words);
        }
    }

    private AllMultiItemFilter generateFilters(ISearchParserProvider provider, String[] words) {
        final AllMultiItemFilter filters = new AllMultiItemFilter();

        for (String work : words) {
            final ItemFilter filter = provider.getFilter(work);

            if (filter != null) {
                filters.filters.add(filter);
            }
        }

        return filters;
    }
}
