package codechicken.nei.api;

import java.util.List;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;

public interface IRecipeFilter {

    public static interface RecipeFilterProvider {

        public IRecipeFilter getRecipeFilter();
    }

    public boolean matches(IRecipeHandler handler, List<PositionedStack> ingredients, PositionedStack result,
            List<PositionedStack> others);

}
