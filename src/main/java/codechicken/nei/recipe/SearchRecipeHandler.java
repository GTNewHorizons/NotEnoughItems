package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import codechicken.nei.ItemList;
import codechicken.nei.api.IRecipeFilter;

class SearchRecipeHandler<H extends IRecipeHandler> {

    public H original;

    private List<Integer> filteredRecipes;

    private List<Integer> searchRecipes;

    public SearchRecipeHandler(H handler) {
        this.original = handler;

        if (this.original.numRecipes() == 0) {
            this.filteredRecipes = Collections.emptyList();
        } else {
            final Stream<Integer> items = IntStream.range(0, this.original.numRecipes()).boxed();
            final IRecipeFilter filter = this.searchingAvailable() ? GuiRecipe.getRecipeListFilter() : null;

            if (filter == null) {
                this.filteredRecipes = items.collect(Collectors.toList());
            } else {
                this.filteredRecipes = items.filter(recipe -> mathRecipe(this.original, recipe, filter))
                        .collect(Collectors.toList());
            }
        }
    }

    protected static boolean mathRecipe(IRecipeHandler handler, int recipeIndex, IRecipeFilter filter) {
        return filter.matches(handler, recipeIndex);
    }

    public boolean searchingAvailable() {
        return SearchRecipeHandler.searchingAvailable(this.original);
    }

    private static boolean searchingAvailable(IRecipeHandler handler) {
        return handler instanceof TemplateRecipeHandler;
    }

    public static int findFirst(IRecipeHandler handler, IntPredicate predicate) {
        final IRecipeFilter filter = searchingAvailable(handler) ? GuiRecipe.getRecipeListFilter() : null;
        int refIndex = -1;

        for (int recipeIndex = 0; recipeIndex < handler.numRecipes(); recipeIndex++) {
            if (filter == null || mathRecipe(handler, recipeIndex, filter)) {
                refIndex++;

                if (predicate.test(recipeIndex)) {
                    return refIndex;
                }
            }
        }

        return -1;
    }

    public List<Integer> getSearchResult(IRecipeFilter filter) {

        if (filteredRecipes.isEmpty() || !this.searchingAvailable()) {
            return null;
        }

        List<Integer> filtered = null;
        final List<Integer> recipes = IntStream.range(0, filteredRecipes.size()).boxed()
                .collect(Collectors.toCollection(ArrayList::new));

        try {
            filtered = ItemList.forkJoinPool.submit(
                    () -> recipes.parallelStream()
                            .filter(recipe -> mathRecipe(this.original, filteredRecipes.get(recipe), filter))
                            .collect(Collectors.toCollection(ArrayList::new)))
                    .get();

            filtered.sort((a, b) -> a - b);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();

        }

        return filtered;
    }

    public void setSearchIndices(List<Integer> searchRecipes) {
        this.searchRecipes = searchRecipes;
    }

    public int ref(int index) {

        if (searchRecipes != null) {
            index = searchRecipes.get(index);
        }

        return filteredRecipes.get(index);
    }

    public int numRecipes() {

        if (searchRecipes != null) {
            return searchRecipes.size();
        }

        return filteredRecipes.size();
    }

}
