package codechicken.nei.recipe.widget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.item.ItemStack;

import codechicken.nei.FavoriteRecipes;
import codechicken.nei.NEIServerUtils;

public class PermutationsCycler<K> {

    protected final Map<K, List<ItemStack>> permutations;
    protected final Map<K, Integer> favoriteCounts;
    protected final Function<K, List<ItemStack>> loader;
    protected Runnable onInvalidate = null;

    protected int favoriteRevision = -1;

    public PermutationsCycler(Map<K, List<ItemStack>> storage, Map<K, Integer> favoriteStorage,
            Function<K, List<ItemStack>> loader) {
        this.permutations = storage;
        this.favoriteCounts = favoriteStorage;
        this.loader = loader;
    }

    public PermutationsCycler<K> onInvalidate(Runnable callback) {
        this.onInvalidate = callback;
        return this;
    }

    public boolean validate() {
        final int rev = FavoriteRecipes.getRevision();

        if (this.favoriteRevision != rev) {
            this.favoriteRevision = rev;
            this.permutations.clear();
            this.favoriteCounts.clear();

            if (this.onInvalidate != null) {
                this.onInvalidate.run();
            }

            return true;
        }

        return false;
    }

    public boolean contains(K key) {
        validate();
        return this.permutations.containsKey(key);
    }

    public List<ItemStack> get(K key) {
        validate();
        List<ItemStack> items = this.permutations.get(key);

        if (items == null) {
            items = new ArrayList<>(this.loader.apply(key));
            items.sort(Comparator.comparing(FavoriteRecipes::containsManual).reversed());

            int favoriteCount = 0;

            if (items.size() > 1) {
                for (ItemStack stack : items) {
                    if (!FavoriteRecipes.containsManual(stack)) break;
                    favoriteCount++;
                }
            }

            this.permutations.put(key, items);
            this.favoriteCounts.put(key, favoriteCount);
        }

        return items;
    }

    public ItemStack getCycled(K key, int cycle) {
        final List<ItemStack> items = get(key);
        final int favoriteCount = this.favoriteCounts.getOrDefault(key, 0);
        final int size = favoriteCount > 0 ? favoriteCount : items.size();

        return size > 0 ? items.get(cycle % size) : null;
    }

    public static int indexOf(List<ItemStack> list, ItemStack stack) {
        for (int i = 0; i < list.size(); i++) {
            if (NEIServerUtils.areStacksSameType(list.get(i), stack)) {
                return i;
            }
        }
        return -1;
    }

}
