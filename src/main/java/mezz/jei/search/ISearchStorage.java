package mezz.jei.search;

import java.io.PrintWriter;
import java.util.Set;

public interface ISearchStorage<T> {

    void getSearchResults(String token, Set<T> results);

    void getAllElements(Set<T> results);

    void put(String key, T value);

    String statistics();

    void printTree(PrintWriter out, boolean includeSuffixLinks);
}
