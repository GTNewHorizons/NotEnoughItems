package mezz.jei.search;

import java.util.Set;

import codechicken.nei.NEIClientConfig.SearchMode;

public interface ISearchable<T> {

    void getSearchResults(String token, Set<T> results);

    void getAllElements(Set<T> results);

    default SearchMode getMode() {
        return SearchMode.ENABLED;
    }

}
