package codechicken.nei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import codechicken.nei.ItemList.AllMultiItemFilter;
import codechicken.nei.ItemList.AnyMultiItemFilter;
import codechicken.nei.ItemList.EverythingItemFilter;
import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.filter.AllMultiRecipeFilter;
import codechicken.nei.filter.AnyMultiRecipeFilter;
import codechicken.nei.filter.RecipeFilter;
import codechicken.nei.filter.RecipeFilter.FilterContext;
import codechicken.nei.search.ItemFilterVisitor;
import codechicken.nei.search.RecipeFilterVisitor;
import codechicken.nei.search.SearchExpressionUtils;
import codechicken.nei.search.SearchToken;

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

    public static class IsRegisteredItemFilter implements ItemFilter {

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

    public static final Pattern SPACE_PATTERN = Pattern.compile(" ");

    protected final LRUCache<String, ItemFilter> filtersCache = new LRUCache<>(20);
    protected final LRUCache<String, IRecipeFilter> recipesCache = new LRUCache<>(20);
    protected final List<ISearchParserProvider> searchProviders;
    protected final Set<Character> redefinedPrefixes = new HashSet<>();
    protected final ProvidersCache providersCache = new ProvidersCache();
    protected final Map<Character, Character> prefixRedefinitions = new HashMap<>();

    public SearchTokenParser(List<ISearchParserProvider> searchProviders) {
        this.searchProviders = searchProviders;
        updateRedefinedPrefixes();
    }

    public SearchTokenParser() {
        this(new ArrayList<>());
        updateRedefinedPrefixes();
    }

    public void addProvider(ISearchParserProvider provider) {
        this.searchProviders.add(provider);
        updateRedefinedPrefixes(provider);
        this.providersCache.clear();
        this.filtersCache.clear();
        this.recipesCache.clear();
    }

    public void clearCache() {
        this.filtersCache.clear();
        this.recipesCache.clear();
        updateRedefinedPrefixes();
    }

    public List<ISearchParserProvider> getProviders() {
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

    public boolean hasRedefinedPrefix(char ch) {
        return redefinedPrefixes.contains(ch);
    }

    public ISearchParserProvider getProvider(char ch) {
        if (!hasRedefinedPrefix(ch)) {
            return null;
        }
        for (ISearchParserProvider provider : getProviders()) {
            if (provider.getSearchMode() == SearchMode.PREFIX && getRedefinedPrefix(provider.getPrefix()) == ch) {
                return provider;
            }
        }
        return null;
    }

    public List<ItemFilter> getAlwaysProvidersFilters(String searchText) {
        final List<ItemFilter> filters = new ArrayList<>();
        for (ISearchParserProvider provider : getProviders()) {
            if (provider.getSearchMode() == SearchMode.ALWAYS) {
                filters.add(provider.getFilter(searchText));
            }
        }

        return filters;
    }

    public synchronized ItemFilter getFilter(String filterText) {
        filterText = EnumChatFormatting.getTextWithoutFormattingCodes(filterText).toLowerCase();

        if (filterText == null || filterText.isEmpty()) {
            return new EverythingItemFilter();
        }

        final int patternMode = NEIClientConfig.getIntSetting("inventory.search.patternMode");

        if (patternMode != 3) {
            return this.filtersCache.computeIfAbsent(filterText, text -> {
                final List<ItemFilter> searchTokens = new ArrayList<>();

                for (String[] subQuery : splitByDelimiters(text, "|", false)) {

                    if (subQuery[1].isEmpty()) {
                        continue;
                    }

                    final List<ItemFilter> tokens = new ArrayList<>();

                    for (SearchToken token : splitSearchText(subQuery[1])) {
                        final ItemFilter result = token.getFilter(this);

                        if (result != null) {
                            tokens.add(result);
                        }
                    }

                    if (tokens.size() == 1) {
                        searchTokens.add(tokens.get(0));
                    } else if (!tokens.isEmpty()) {
                        searchTokens.add(new AllMultiItemFilter(tokens));
                    }
                }

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
                final int spaceMode = NEIClientConfig.getIntSetting("inventory.search.spaceMode");

                if (spaceMode == 1) {
                    text = SearchTokenParser.SPACE_PATTERN.matcher(text).replaceAll("\\\\ ");
                }

                return new IsRegisteredItemFilter(
                        SearchExpressionUtils.visitSearchExpression(text, new ItemFilterVisitor(this)));
            });
        }

    }

    public synchronized IRecipeFilter getRecipeFilter(String filterText) {
        filterText = EnumChatFormatting.getTextWithoutFormattingCodes(filterText).toLowerCase();

        if (filterText == null || filterText.isEmpty()) {
            return new RecipeFilter(FilterContext.ANY, true, new EverythingItemFilter());
        }

        final int patternMode = NEIClientConfig.getIntSetting("inventory.search.patternMode");

        if (patternMode != 3) {
            return this.recipesCache.computeIfAbsent(filterText, input -> {
                final List<IRecipeFilter> searchTokens = new ArrayList<>();

                for (String[] orQueryPart : splitByDelimiters(input, "|", false)) {

                    if (orQueryPart[1].isEmpty()) {
                        continue;
                    }

                    final List<IRecipeFilter> contextList = new ArrayList<>();

                    for (String[] contextQuery : splitByDelimiters(orQueryPart[1], "<>", false)) {

                        if (contextQuery[1].isEmpty()) {
                            continue;
                        }

                        final List<IRecipeFilter> tokens = new ArrayList<>();
                        FilterContext context = FilterContext.ANY;

                        if (!contextQuery[0].isEmpty()) {
                            context = FilterContext.fromChar(contextQuery[0].charAt(0));
                            contextQuery[0] = contextQuery[0].substring(1);
                        }

                        for (SearchToken token : splitSearchText(contextQuery[0] + contextQuery[1])) {
                            final ItemFilter result = token.getFilter(this);
                            if (result != null) {
                                tokens.add(
                                        new RecipeFilter(context, token.ignore == null || token.ignore != '!', result));
                            }
                        }

                        if (tokens.size() == 1) {
                            contextList.add(tokens.get(0));
                        } else if (!tokens.isEmpty()) {
                            contextList.add(new AllMultiRecipeFilter(tokens));
                        }
                    }

                    if (contextList.size() == 1) {
                        searchTokens.add(contextList.get(0));
                    } else if (!contextList.isEmpty()) {
                        searchTokens.add(new AllMultiRecipeFilter(contextList));
                    }
                }

                if (searchTokens.isEmpty()) {
                    return new RecipeFilter(FilterContext.ANY, true, new EverythingItemFilter());
                } else if (searchTokens.size() == 1) {
                    return searchTokens.get(0);
                } else {
                    return new AnyMultiRecipeFilter(searchTokens);
                }
            });

        } else {
            return this.recipesCache.computeIfAbsent(filterText, text -> {
                final int spaceMode = NEIClientConfig.getIntSetting("inventory.search.spaceMode");

                if (spaceMode == 1) {
                    text = SearchTokenParser.SPACE_PATTERN.matcher(text).replaceAll("\\\\ ");
                }

                return SearchExpressionUtils.visitSearchExpression(text, new RecipeFilterVisitor(this));
            });
        }

    }

    public List<SearchToken> splitSearchText(String filterText) {
        if (filterText.isEmpty()) {
            return Collections.emptyList();
        }

        final List<SearchToken> tokens = new ArrayList<>();
        final int spaceMode = NEIClientConfig.getIntSetting("inventory.search.spaceMode");
        final String prefixes = getPrefixes();
        SearchToken lastToken = null;
        int lastEnd = 0;

        for (String[] part : splitByDelimiters(filterText, "-!" + prefixes, true)) {
            final SearchToken token = createToken(lastEnd, part, prefixes);
            final int length = part[0].length() + part[1].length();

            if (token.isSimpleFilter() && token.rawText.isEmpty()) {
                lastEnd += length;
                continue;
            }

            if (spaceMode == 0) { // and
                tokens.add(lastToken = token);
            } else if (spaceMode == 1) { // space

                if (lastToken != null && lastToken.isSimpleFilter() && token.isSimpleFilter()) {
                    lastToken.rawText = filterText.substring(lastToken.start, token.end).trim();
                    lastToken.words[0] = lastToken.rawText;
                    lastToken.end = token.end;
                } else {
                    tokens.add(lastToken = token);
                }

            } else if (spaceMode == 2) { // and-x

                if (lastToken != null && lastToken.isSimpleFilter() && token.isSimpleFilter()) {
                    final String[] newWords = new String[lastToken.words.length + token.words.length];

                    System.arraycopy(lastToken.words, 0, newWords, 0, lastToken.words.length);
                    System.arraycopy(token.words, 0, newWords, lastToken.words.length, token.words.length);

                    lastToken.words = newWords;
                    lastToken.rawText = filterText.substring(lastToken.start, token.end).trim();
                    lastToken.end = token.end;
                } else {
                    tokens.add(lastToken = token);
                }

            }

            lastEnd += length;
        }

        return tokens;
    }

    private SearchToken createToken(int lastEnd, String[] part, String prefixes) {
        final SearchToken token = new SearchToken();
        String pref = part[0];
        String query = part[1].trim();

        token.start = lastEnd;
        token.end = lastEnd + pref.length() + query.length();

        if (pref.startsWith("-")) {
            token.ignore = '-';
            pref = pref.substring(1);
        } else if (pref.startsWith("!")) {
            token.ignore = '!';
            pref = pref.substring(1);
        }

        if (!pref.isEmpty()) {
            char firstChar = pref.charAt(0);

            if (prefixes.indexOf(firstChar) >= 0) {
                token.firstChar = firstChar;
                pref = pref.substring(1);
            }
        }

        String text = pref + query;
        token.quotes = text.length() > 1 && text.startsWith("\"") && text.endsWith("\"");

        if (token.quotes) {
            text = text.substring(1, text.length() - 1);
        }

        token.rawText = text;
        token.words = new String[] { token.quotes ? text.replaceAll("\\\\\"", "\"") : text };
        return token;
    }

    public List<String[]> splitByDelimiters(String input, String delimiters, boolean space) {

        if (delimiters == null || delimiters.isEmpty()) {
            return Collections.singletonList(new String[] { "", input });
        }

        final List<String[]> tokens = new ArrayList<>();
        final int length = input.length();
        boolean insideQuotes = false;
        String token = "";
        int lastEnd = 0;
        int index = 0;

        while (index < length) {
            final char ch = input.charAt(index);

            if (!insideQuotes && delimiters.indexOf(ch) >= 0
                    && (!space || index == 0 || input.charAt(index - 1) == ' ')) {

                if (lastEnd == index) {
                    token += String.valueOf(ch);
                } else {
                    tokens.add(new String[] { token, input.substring(lastEnd, index) });
                    token = String.valueOf(ch);
                }

                lastEnd = index + 1;
            } else if (!insideQuotes && space && ch != ' ' && (index > 0 && input.charAt(index - 1) == ' ')) {
                tokens.add(new String[] { token, input.substring(lastEnd, index) });
                token = "";

                lastEnd = index;
            }

            if (ch == '"' && (index == 0 || input.charAt(index - 1) != '\\')) {
                insideQuotes = !insideQuotes;
            }

            index++;
        }

        tokens.add(new String[] { token, input.substring(lastEnd) });

        return tokens;
    }

    private String getPrefixes() {
        final StringBuilder builder = new StringBuilder();
        for (Character redefinedPrefix : redefinedPrefixes) {
            builder.append(redefinedPrefix);
        }
        return builder.toString();
    }

    public char getRedefinedPrefix(char prefix) {
        return this.prefixRedefinitions.getOrDefault(prefix, prefix);
    }

    public void updateRedefinedPrefixes() {
        this.redefinedPrefixes.clear();
        for (ISearchParserProvider provider : getProviders()) {
            if (provider.getSearchMode() == SearchTokenParser.SearchMode.PREFIX) {
                redefinedPrefixes.add(getRedefinedPrefix(provider.getPrefix()));
            }
        }
        this.redefinedPrefixes.add('\0');
    }

    public void updateRedefinedPrefixes(ISearchParserProvider provider) {
        if (provider.getSearchMode() == SearchMode.PREFIX) {
            this.redefinedPrefixes.add(getRedefinedPrefix(provider.getPrefix()));
        }
    }

}
