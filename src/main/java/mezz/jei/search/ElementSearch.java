package mezz.jei.search;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientConfig.SearchMode;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class ElementSearch implements IElementSearch {

    private final Map<PrefixInfo, PrefixedSearchable> prefixedSearchables = new Reference2ObjectArrayMap<>();
    private final CombinedSearchables<IIngredientListElement<ItemStack>> combinedSearchables = new CombinedSearchables<>();

    private boolean loggedStatistics = false;

    public ElementSearch() {
        AsyncPrefixedSearchable.startService();

        ISearchStorage<IIngredientListElement<ItemStack>> storage = PrefixInfo.NO_PREFIX.createStorage();
        PrefixedSearchable searchable = new PrefixedSearchable(storage, PrefixInfo.NO_PREFIX);
        this.prefixedSearchables.put(PrefixInfo.NO_PREFIX, searchable);
        this.combinedSearchables.addSearchable(searchable);

        for (PrefixInfo prefixInfo : PrefixInfo.all()) {
            storage = prefixInfo.createStorage();
            searchable = true && prefixInfo.isAsyncable() ? new AsyncPrefixedSearchable(storage, prefixInfo)
                    : new PrefixedSearchable(storage, prefixInfo);
            this.prefixedSearchables.put(prefixInfo, searchable);
            this.combinedSearchables.addSearchable(searchable);
        }
    }

    public void block() {
        AsyncPrefixedSearchable.endService();
        for (PrefixedSearchable prefixedSearchable : this.prefixedSearchables.values()) {
            prefixedSearchable.stop();
        }
        // if (!this.loggedStatistics && CoreModManager.deob) {
        // this.loggedStatistics = true;
        // this.logStatistics();
        // }
    }

    @Override
    public Set<IIngredientListElement<ItemStack>> getSearchResults(TokenInfo tokenInfo) {
        String token = tokenInfo.token;
        if (token.isEmpty()) {
            return Collections.emptySet();
        }
        Set<IIngredientListElement<ItemStack>> results = new ReferenceOpenHashSet<>();
        PrefixInfo prefixInfo = tokenInfo.prefixInfo;
        if (prefixInfo == PrefixInfo.NO_PREFIX) {
            combinedSearchables.getSearchResults(token, results);
            return results;
        }
        final ISearchable<IIngredientListElement<ItemStack>> searchable = this.prefixedSearchables.get(prefixInfo);
        if (searchable == null || searchable.getMode() == SearchMode.DISABLED) {
            combinedSearchables.getSearchResults(token, results);
            return results;
        }
        searchable.getSearchResults(token, results);
        return results;
    }

    @Override
    public void add(IIngredientListElement<ItemStack> ingredient) {
        for (PrefixedSearchable prefixedSearchable : this.prefixedSearchables.values()) {
            prefixedSearchable.submit(ingredient);
        }
    }

    @Override
    public void addAll(Collection<IIngredientListElement<ItemStack>> ingredients) {
        for (PrefixedSearchable prefixedSearchable : this.prefixedSearchables.values()) {
            prefixedSearchable.submitAll(ingredients);
        }
    }

    @Override
    public Set<IIngredientListElement<ItemStack>> getAllIngredients() {
        Set<IIngredientListElement<ItemStack>> results = new ReferenceOpenHashSet<>();
        this.prefixedSearchables.get(PrefixInfo.NO_PREFIX).getAllElements(results);
        return results;
    }

    @Override
    public void logStatistics() {
        for (Map.Entry<PrefixInfo, PrefixedSearchable> entry : this.prefixedSearchables.entrySet()) {
            PrefixInfo prefixInfo = entry.getKey();
            if (prefixInfo.getMode() != NEIClientConfig.SearchMode.DISABLED) {
                ISearchStorage<IIngredientListElement<ItemStack>> storage = entry.getValue().getSearchStorage();
                NEIClientConfig.logger.info("ElementSearch {} Storage Stats: {}", prefixInfo, storage.statistics());
                try {
                    FileWriter fileWriter = new FileWriter("GeneralizedSuffixTree-" + prefixInfo + ".dot");
                    try (PrintWriter out = new PrintWriter(fileWriter)) {
                        storage.printTree(out, false);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
