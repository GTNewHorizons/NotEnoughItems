package codechicken.nei.search;

import java.util.ArrayList;
import java.util.List;

import codechicken.nei.SearchTokenParser;
import codechicken.nei.SearchTokenParser.ISearchParserProvider;
import codechicken.nei.SearchTokenParser.SearchMode;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.filter.AllMultiItemFilter;
import codechicken.nei.filter.AnyMultiItemFilter;
import codechicken.nei.filter.NegatedItemFilter;
import codechicken.nei.filter.NothingItemFilter;

public class SearchToken {

    public Character ignore = null;
    public boolean quotes = false;
    public Character firstChar = null;

    public String[] words;

    public String rawText = "";
    public int start = 0;
    public int end = 0;

    public ItemFilter getFilter(SearchTokenParser parser) {
        if (this.words == null || this.words.length == 0) {
            return null;
        }

        if (!this.rawText.isEmpty()) {
            final ItemFilter result = parseToken(parser);

            if (this.ignore != null) {
                return new NegatedItemFilter(result);
            } else {
                return result;
            }
        } else if (this.ignore == null) {
            return new NothingItemFilter();
        }

        return null;
    }

    public boolean isSimpleFilter() {
        return this.firstChar == null && !this.quotes && this.ignore == null;
    }

    private ItemFilter parseToken(SearchTokenParser parser) {
        final ISearchParserProvider provider = this.firstChar == null ? null : parser.getProvider(this.firstChar);

        if (provider == null || provider.getSearchMode() == SearchMode.NEVER) {
            final List<ItemFilter> filters = new ArrayList<>();

            for (ISearchParserProvider _provider : parser.getProviders()) {
                if (_provider.getSearchMode() == SearchMode.ALWAYS) {
                    AllMultiItemFilter filter = generateFilters(_provider);

                    if (!filter.filters.isEmpty()) {
                        filters.add(filter);
                    }
                }
            }

            return filters.isEmpty() ? new NothingItemFilter() : new AnyMultiItemFilter(filters);
        } else {
            return generateFilters(provider);
        }
    }

    private AllMultiItemFilter generateFilters(ISearchParserProvider provider) {
        final AllMultiItemFilter filters = new AllMultiItemFilter();

        for (String work : this.words) {
            final ItemFilter filter = provider.getFilter(work);

            if (filter != null) {
                filters.filters.add(filter);
            }
        }

        return filters;
    }
}
