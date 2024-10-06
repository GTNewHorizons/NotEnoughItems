package codechicken.nei.bookmarks.crafts.graph;

import java.util.Map;

public interface CraftingGraphNode {

    int addToRemainders(String itemKey, int remainder);

    int getRemainder(String itemKey);

    Map<String, Integer> getRemainders();
}
