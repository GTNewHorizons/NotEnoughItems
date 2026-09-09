package codechicken.nei.bookmark.tree;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.item.ItemStack;

import codechicken.nei.NEIClientUtils;

class SearchResult {

    public final Object searchGUID;
    public ItemTreeSlot highlightNode;
    public final Set<ItemTreeSlot> searchMatches = new LinkedHashSet<>();

    public SearchResult(Object searchGUID) {
        this.searchGUID = searchGUID;
    }

    public boolean matchesGUID(Object guid) {
        if (guid instanceof ItemStack stack && this.searchGUID instanceof ItemStack searchStack) {
            return NEIClientUtils.areStacksSameTypeCraftingWithNBT(stack, searchStack);
        }

        return this.searchGUID.equals(guid);
    }

}
