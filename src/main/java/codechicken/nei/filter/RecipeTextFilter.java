package codechicken.nei.filter;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import codechicken.nei.api.IRecipeFilter;
import codechicken.nei.recipe.IRecipeHandler;

public class RecipeTextFilter implements IRecipeFilter {

    private final Predicate<List<String>> lines;

    public RecipeTextFilter(Predicate<List<String>> lines) {
        this.lines = lines;
    }

    public static Predicate<List<String>> matching(Pattern pattern) {
        return lines -> {
            for (String line : lines) {
                if (pattern.matcher(line).find()) return true;
            }
            return false;
        };
    }

    @Override
    public boolean matches(IRecipeHandler handler, int recipeIndex) {
        final List<String> text = handler.getSearchText(recipeIndex);
        return !text.isEmpty() && this.lines.test(text);
    }
}
