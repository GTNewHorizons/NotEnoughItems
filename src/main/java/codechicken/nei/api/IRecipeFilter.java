package codechicken.nei.api;

import codechicken.nei.recipe.IRecipeHandler;

public interface IRecipeFilter {

    public static interface IRecipeFilterProvider {

        public IRecipeFilter getRecipeFilter();
    }

    public boolean matches(IRecipeHandler handler, int recipeIndex);

}
