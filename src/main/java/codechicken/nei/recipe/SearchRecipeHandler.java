package codechicken.nei.recipe;

import java.util.concurrent.ExecutionException;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import codechicken.nei.ItemList;
import codechicken.nei.api.IRecipeFilter;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

class SearchRecipeHandler<H extends IRecipeHandler> {

    public H original;

    private IntList filteredRecipes;

    private IntList searchRecipes;

    public SearchRecipeHandler(H handler) {
        this.original = handler;

        if (this.original.numRecipes() == 0) {
            this.filteredRecipes = new IntArrayList();
        } else {
            final Stream<Integer> items = IntStream.range(0, this.original.numRecipes()).boxed();
            final IRecipeFilter filter = this.searchingAvailable() ? GuiRecipe.getRecipeListFilter() : null;

            if (filter == null) {
                this.filteredRecipes = items.collect(Collectors.toCollection(IntArrayList::new));
            } else {
                this.filteredRecipes = items.filter(recipe -> mathRecipe(this.original, recipe, filter))
                        .collect(Collectors.toCollection(IntArrayList::new));
            }
        }
    }

    protected static boolean mathRecipe(IRecipeHandler handler, int recipe, IRecipeFilter filter) {
        return filter.matches(
                handler,
                handler.getIngredientStacks(recipe),
                handler.getResultStack(recipe),
                handler.getOtherStacks(recipe));
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

    @Nullable
    public IntArrayList getSearchResult(IRecipeFilter filter) {

        if (filteredRecipes.isEmpty() || !this.searchingAvailable()) {
            return null;
        }

        IntArrayList filtered = null;
        final IntArrayList recipes = IntStream.range(0, filteredRecipes.size()).boxed()
                .collect(Collectors.toCollection(IntArrayList::new));

        try {
            filtered = ItemList.forkJoinPool.submit(
                    () -> recipes.parallelStream()
                            .filter(recipe -> mathRecipe(this.original, filteredRecipes.getInt(recipe), filter))
                            .collect(Collectors.toCollection(IntArrayList::new)))
                    .get();

            filtered.sort((a, b) -> a - b);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();

        }

        return filtered;
    }

    public void setSearchIndices(IntList searchRecipes) {
        this.searchRecipes = searchRecipes;
    }

    public int ref(int index) {

        if (searchRecipes != null) {
            index = searchRecipes.getInt(index);
        }

        return filteredRecipes.getInt(index);
    }

    public int numRecipes() {

        if (searchRecipes != null) {
            return searchRecipes.size();
        }

        return filteredRecipes.size();
    }

}
