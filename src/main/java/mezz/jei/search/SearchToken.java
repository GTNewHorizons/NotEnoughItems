package mezz.jei.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class SearchToken {

    public static final Pattern QUOTE_PATTERN = Pattern.compile("\"");
    public static final Pattern FILTER_SPLIT_PATTERN = Pattern.compile("(-?\".*?(?:\"|$)|\\S+)");

    public static final SearchToken EMPTY = new SearchToken(Collections.emptyList(), Collections.emptyList());

    public static SearchToken parseSearchToken(String filterText) {
        if (filterText.isEmpty()) {
            return EMPTY;
        }
        SearchToken searchTokens = new SearchToken(new ArrayList<>(), new ArrayList<>());
        Matcher filterMatcher = FILTER_SPLIT_PATTERN.matcher(filterText);
        while (filterMatcher.find()) {
            String string = filterMatcher.group(1);
            final boolean remove = string.startsWith("-");
            if (remove) {
                string = string.substring(1);
            }
            string = QUOTE_PATTERN.matcher(string).replaceAll("");
            if (string.isEmpty()) {
                continue;
            }
            TokenInfo token = TokenInfo.parseRawToken(string);
            if (token != null) {
                if (remove) {
                    searchTokens.remove.add(token);
                } else {
                    searchTokens.search.add(token);
                }
            }
        }
        return searchTokens;
    }

    public final List<TokenInfo> search, remove;

    public SearchToken(List<TokenInfo> search, List<TokenInfo> remove) {
        this.search = search;
        this.remove = remove;
    }

    public Set<IIngredientListElement<ItemStack>> getSearchResults(IElementSearch elementSearch) {
        Set<IIngredientListElement<ItemStack>> results = intersection(
                search.stream().map(elementSearch::getSearchResults));
        if (!results.isEmpty() && !remove.isEmpty()) {
            for (TokenInfo tokenInfo : remove) {
                Set<IIngredientListElement<ItemStack>> resultsToRemove = elementSearch.getSearchResults(tokenInfo);
                results.removeAll(resultsToRemove);
                if (results.isEmpty()) {
                    break;
                }
            }
        }
        return results;
    }

    private <T> Set<T> intersection(Stream<Set<T>> stream) {
        List<Set<T>> sets = stream.collect(Collectors.toList());
        Set<T> smallestSet = sets.stream().min(Comparator.comparing(Set::size)).orElseGet(Collections::emptySet);
        Set<T> results = new ReferenceOpenHashSet<>(smallestSet);
        for (Set<T> set : sets) {
            if (set == smallestSet) {
                continue;
            }
            if (results.retainAll(set) && results.isEmpty()) {
                break;
            }
        }
        return results;
    }

}
