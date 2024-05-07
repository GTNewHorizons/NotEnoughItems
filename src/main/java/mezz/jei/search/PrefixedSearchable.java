package mezz.jei.search;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import net.minecraft.item.ItemStack;

import codechicken.core.TaskProfiler;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientConfig.SearchMode;

public class PrefixedSearchable implements ISearchable<IIngredientListElement<ItemStack>>, IBuildable {

    protected final ISearchStorage<IIngredientListElement<ItemStack>> searchStorage;
    protected final PrefixInfo prefixInfo;

    protected TaskProfiler timer;

    private static boolean firstBuild = true;
    private static boolean rebuild = false;

    public PrefixedSearchable(ISearchStorage<IIngredientListElement<ItemStack>> searchStorage, PrefixInfo prefixInfo) {
        this.searchStorage = searchStorage;
        this.prefixInfo = prefixInfo;
    }

    public ISearchStorage<IIngredientListElement<ItemStack>> getSearchStorage() {
        return searchStorage;
    }

    public Collection<String> getStrings(IIngredientListElement<ItemStack> element) {
        return prefixInfo.getStrings(element);
    }

    @Override
    public SearchMode getMode() {
        return prefixInfo.getMode();
    }

    @Override
    public void submit(IIngredientListElement<ItemStack> ingredient) {
        if (prefixInfo.getMode() == NEIClientConfig.SearchMode.DISABLED) return;
        Collection<String> strings = prefixInfo.getStrings(ingredient);
        for (String string : strings) {
            searchStorage.put(string, ingredient);
        }
    }

    @Override
    public void submitAll(Collection<IIngredientListElement<ItemStack>> ingredients) {
        if (prefixInfo.getMode() == NEIClientConfig.SearchMode.DISABLED) return;
        if (firstBuild) {
            start();
            if (!rebuild) {
                long modNameCount = ingredients.stream().map(IIngredientListElement<ItemStack>::getModNameForSorting)
                        .distinct().count();
            }
            String currentModName = null;
            for (IIngredientListElement<ItemStack> ingredient : ingredients) {
                String modname = ingredient.getModNameForSorting();
                if (!Objects.equals(currentModName, modname)) {
                    currentModName = modname;
                }
                submit(ingredient);
            }
            firstBuild = false;
            stop();
        } else {
            for (IIngredientListElement<ItemStack> ingredient : ingredients) {
                submit(ingredient);
            }
        }
    }

    @Override
    public void getSearchResults(String token, Set<IIngredientListElement<ItemStack>> results) {
        searchStorage.getSearchResults(token, results);
    }

    @Override
    public void getAllElements(Set<IIngredientListElement<ItemStack>> results) {
        searchStorage.getAllElements(results);
    }

    @Override
    public void start() {
        this.timer = new TaskProfiler();
        this.timer.start("Building [" + prefixInfo.getDesc() + "] search tree");
    }

    @Override
    public void stop() {
        if (this.timer != null) {
            this.timer.end();
            this.timer = null;
        }
    }

}
