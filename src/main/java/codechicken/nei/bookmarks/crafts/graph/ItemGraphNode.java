
package codechicken.nei.bookmarks.crafts.graph;

import java.util.Collections;
import java.util.Map;

import codechicken.nei.bookmarks.crafts.ItemStackWithMetadata;
import codechicken.nei.recipe.StackInfo;

public class ItemGraphNode implements CraftingGraphNode {

    private final ItemStackWithMetadata pinnedItem;

    private int requestedItems = 0;

    public ItemGraphNode(ItemStackWithMetadata pinnedItem) {
        this.pinnedItem = pinnedItem;
    }

    public ItemStackWithMetadata getPinnedItem() {
        return pinnedItem;
    }

    @Override
    public int addToRemainders(String itemKey, int remainder) {
        requestedItems -= remainder;
        return requestedItems;
    }

    @Override
    public Map<String, Integer> getRemainders() {
        return Collections.singletonMap(StackInfo.getItemStackGUID(pinnedItem.getStack()), -requestedItems);
    }

    @Override
    public int getRemainder(String itemKey) {
        return -requestedItems;
    }
}
