package codechicken.nei.api;

import java.util.List;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;

public interface RecipeFilter {

    public static interface RecipeFilterProvider {

        public RecipeFilter getRecipeFilter();
    }

    public boolean matches(IRecipeHandler handler, List<PositionedStack> ingredients, PositionedStack result,
            List<PositionedStack> others);

}
