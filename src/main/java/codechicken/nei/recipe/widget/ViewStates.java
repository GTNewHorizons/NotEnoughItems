package codechicken.nei.recipe.widget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import codechicken.nei.recipe.IRecipeHandler;

public final class ViewStates {

    private final Map<Object, ViewState> groupStates = new HashMap<>();
    private final Map<Integer, ViewState> recipeStates = new HashMap<>();

    // a group keeps its state while a search hides some of its recipes, a search can't split or merge groups
    public ViewState get(IRecipeHandler handler, List<Integer> group) {
        final int firstRecipe = group.get(0);
        final Object groupId = handler.getRecipeGroupId(firstRecipe);

        return groupId != null ? this.groupStates.computeIfAbsent(groupId, k -> new ViewState())
                : this.recipeStates.computeIfAbsent(firstRecipe, k -> new ViewState());
    }
}
