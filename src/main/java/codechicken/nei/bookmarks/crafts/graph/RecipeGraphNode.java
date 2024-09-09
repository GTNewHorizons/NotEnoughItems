package codechicken.nei.bookmarks.crafts.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import codechicken.nei.BookmarkPanel.BookmarkRecipe;
import codechicken.nei.bookmarks.crafts.ItemStackWithMetadata;
import codechicken.nei.recipe.BookmarkRecipeId;
import codechicken.nei.recipe.StackInfo;

public class RecipeGraphNode implements CraftingGraphNode {

    private final List<ItemStackWithMetadata> pinnedInputs;
    private final List<ItemStackWithMetadata> pinnedOutputs;
    private final Map<String, Integer> pinnedInputKeys = new HashMap<>();
    private final Map<String, Integer> pinnedOutputKeys = new HashMap<>();
    private final BookmarkRecipe recipe;

    private final List<Map<String, Integer>> recipeIngredients = new ArrayList<>();
    private final Map<String, Integer> recipeOutputs = new LinkedHashMap<>();

    private int crafts = 0;
    private final Map<String, Integer> remainders = new HashMap<>();
    private final Map<String, Integer> chainInputs = new HashMap<>();
    private final Map<String, Integer> chainOutputs = new HashMap<>();

    public RecipeGraphNode(BookmarkRecipe recipe, List<ItemStackWithMetadata> pinnedInputs,
            List<ItemStackWithMetadata> pinnedOutputs) {
        this.pinnedInputs = pinnedInputs;
        this.pinnedOutputs = pinnedOutputs;
        this.recipe = recipe;
        for (ItemStackWithMetadata item : pinnedInputs) {
            pinnedInputKeys.put(StackInfo.getItemStackGUID(item.getStack()), item.getGridIdx());
        }
        for (ItemStackWithMetadata item : pinnedOutputs) {
            pinnedOutputKeys.put(StackInfo.getItemStackGUID(item.getStack()), item.getGridIdx());
        }

        for (List<ItemStack> slotIngredients : recipe.allIngredients) {
            Map<String, Integer> ingredientMap = new HashMap<>();
            for (ItemStack ingredient : slotIngredients) {
                String key = StackInfo.getItemStackGUID(ingredient);
                int size = CraftingGraph.getStackSize(ingredient);
                ingredientMap.compute(key, (k, v) -> v == null ? size : v + size);
            }
            recipeIngredients.add(ingredientMap);
        }

        for (ItemStack output : recipe.result) {
            String key = StackInfo.getItemStackGUID(output);
            int size = CraftingGraph.getStackSize(output);
            recipeOutputs.compute(key, (k, v) -> v == null ? size : v + size);
        }
    }

    public List<ItemStackWithMetadata> getPinnedInputs() {
        return pinnedInputs;
    }

    public List<ItemStackWithMetadata> getPinnedOutputs() {
        return pinnedOutputs;
    }

    public Map<String, Integer> getPinnedInputKeys() {
        return pinnedInputKeys;
    }

    public Map<String, Integer> getPinnedOutputKeys() {
        return pinnedOutputKeys;
    }

    public List<Map<String, Integer>> getRecipeIngredients() {
        return recipeIngredients;
    }

    public Map<String, Integer> getRecipeOutputs() {
        return recipeOutputs;
    }

    public BookmarkRecipeId getRecipeId() {
        return recipe.getRecipeId();
    }

    public int getCrafts() {
        return crafts;
    }

    public void addCrafts(int crafts) {
        this.crafts += crafts;
    }

    @Override
    public int addToRemainders(String itemKey, int remainder) {
        remainders.compute(itemKey, (k, v) -> {
            int result = (v == null ? 0 : v) + remainder;
            if (result == 0) return null;
            return result;
        });
        return remainders.getOrDefault(itemKey, 0);
    }

    @Override
    public int getRemainder(String itemKey) {
        return remainders.getOrDefault(itemKey, 0);
    }

    public int getChainInput(String itemKey) {
        return chainInputs.getOrDefault(itemKey, 0);
    }

    public void addToChainInputs(String itemKey, int size) {
        chainInputs.compute(itemKey, (k, v) -> {
            int result = (v == null ? 0 : v) + size;
            if (result == 0) return null;
            return result;
        });
    }

    public Map<String, Integer> getChainOutputs() {
        return chainOutputs;
    }

    public int getChainOutput(String itemKey) {
        return chainOutputs.getOrDefault(itemKey, 0);
    }

    public void setChainOutputs(String itemKey, int size) {
        chainOutputs.put(itemKey, size);
    }

    public Map<String, Integer> getChainInputs() {
        return chainInputs;
    }

    @Override
    public Map<String, Integer> getRemainders() {
        return remainders;
    }
}
