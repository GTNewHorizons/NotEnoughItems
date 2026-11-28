package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

    private volatile List<Integer> searchRecipes;

    private volatile int searchVersion;

    public SearchRecipeHandler(H handler, IRecipeFilter filter) {
        this.original = handler;

        if (this.original.numRecipes() == 0) {
            this.filteredRecipes = Collections.emptyList();
        } else {
            final Stream<Integer> items = IntStream.range(0, this.original.numRecipes()).boxed();

            if (filter == null || !this.searchingAvailable()) {
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

        for (int recipeIndex = 0; recipeIndex < handler.numRecipes(); recipeIndex++) {
            if ((filter == null || mathRecipe(handler, recipeIndex, filter)) && predicate.test(recipeIndex)) {
                return recipeIndex;
            }
        }

        return -1;
    }

    public List<Integer> getSearchResult(IRecipeFilter filter) {

        if (this.filteredRecipes.isEmpty() || !this.searchingAvailable()) {
            return null;
        }

        final List<Integer> positions = IntStream.range(0, this.filteredRecipes.size()).boxed()
                .collect(Collectors.toList());

        try {
            return ItemList.forkJoinPool.submit(
                    () -> positions.parallelStream()
                            .filter(position -> mathRecipe(this.original, this.filteredRecipes.get(position), filter))
                            .collect(Collectors.toList()))
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void setSearchIndices(List<Integer> searchRecipes) {
        if (Objects.equals(this.searchRecipes, searchRecipes)) return;
        this.searchRecipes = searchRecipes;
        this.searchVersion++;
    }

    public int getSearchVersion() {
        return this.searchVersion;
    }

    public List<Integer> getFilteredRecipes() {
        final List<Integer> search = this.searchRecipes;

        if (search == null) {
            return this.filteredRecipes;
        }

        final List<Integer> resolved = new ArrayList<>(search.size());

        for (int idx : search) {
            resolved.add(this.filteredRecipes.get(idx));
        }

        return resolved;
    }

    public int ref(int index) {
        final List<Integer> search = this.searchRecipes;

        if (search != null) {
            index = search.get(index);
        }

        return this.filteredRecipes.get(index);
    }

    public int numRecipes() {
        final List<Integer> search = this.searchRecipes;
        return search != null ? search.size() : this.filteredRecipes.size();
    }

}
