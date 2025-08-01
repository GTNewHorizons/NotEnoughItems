package codechicken.nei.recipe;

public class RecipeWithAmount {

    private final Recipe recipe;
    private final int amount;

    public RecipeWithAmount(Recipe recipe, int amount) {
        this.recipe = recipe;
        this.amount = amount;
    }

    public Recipe getRecipe() {
        return recipe;
    }

    public int getAmount() {
        return amount;
    }
}
