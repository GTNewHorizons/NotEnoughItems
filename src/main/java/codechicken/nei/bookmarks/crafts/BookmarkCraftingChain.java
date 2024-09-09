package codechicken.nei.bookmarks.crafts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import codechicken.nei.BookmarkPanel;
import codechicken.nei.bookmarks.crafts.graph.CraftingGraph;
import codechicken.nei.bookmarks.crafts.graph.ItemGraphNode;
import codechicken.nei.bookmarks.crafts.graph.RecipeGraphNode;
import codechicken.nei.recipe.BookmarkRecipeId;
import codechicken.nei.recipe.StackInfo;

public class BookmarkCraftingChain {

    private CraftingGraph graph;

    public void refresh(ArrayList<ItemStackWithMetadata> groupItems, boolean skipCalculation) {
        if (groupItems.isEmpty()) {
            return;
        }
        this.graph = new CraftingGraph();

        LinkedHashMap<BookmarkRecipeId, List<ItemStackWithMetadata>> groupedByRecipe = new LinkedHashMap<>();
        LinkedHashMap<BookmarkRecipeId, BookmarkPanel.BookmarkRecipe> recipes = new LinkedHashMap<>();
        List<ItemStackWithMetadata> itemsWithoutRecipe = new ArrayList<>();

        for (ItemStackWithMetadata groupItem : groupItems) {
            BookmarkRecipeId recipeId = groupItem.getMeta().recipeId;
            if (recipeId != null) {
                groupedByRecipe.computeIfAbsent(recipeId, k -> new ArrayList<>()).add(groupItem);
                BookmarkPanel.BookmarkRecipe fullRecipe = groupItem.getMeta().getFullRecipe(groupItem.getStack());
                if (fullRecipe != null) {
                    recipes.putIfAbsent(recipeId, fullRecipe);
                }
            } else {
                itemsWithoutRecipe.add(groupItem);
            }
        }

        for (Map.Entry<BookmarkRecipeId, List<ItemStackWithMetadata>> entry : groupedByRecipe.entrySet()) {
            if (entry.getKey() != null) {
                List<ItemStackWithMetadata> inputs = new ArrayList<>();
                List<ItemStackWithMetadata> outputs = new ArrayList<>();
                for (ItemStackWithMetadata it : entry.getValue()) {
                    if (it.getMeta().ingredient) {
                        inputs.add(it);
                    } else {
                        outputs.add(it);
                    }
                }

                if (!outputs.isEmpty()) {
                    if (inputs.isEmpty()) {
                        itemsWithoutRecipe.addAll(outputs);
                    } else {
                        BookmarkPanel.BookmarkRecipe recipe = recipes.get(entry.getKey());
                        // Fallback to pinned items
                        if (recipe == null) {
                            recipe = new BookmarkPanel.BookmarkRecipe();
                            recipe.ingredients = inputs.stream().map(it -> {
                                NBTTagCompound tagCompound = StackInfo.itemStackToNBT(it.getStack());
                                return StackInfo.loadFromNBT(tagCompound, it.getMeta().factor);
                            }).collect(Collectors.toList());

                            recipe.allIngredients = recipe.ingredients.stream().map(Collections::singletonList)
                                    .collect(Collectors.toList());

                            recipe.result = outputs.stream().map(it -> {
                                NBTTagCompound tagCompound = StackInfo.itemStackToNBT(it.getStack());
                                return StackInfo.loadFromNBT(tagCompound, it.getMeta().factor);
                            }).collect(Collectors.toList());
                            recipe.recipeId = entry.getKey();
                        }

                        RecipeGraphNode node = new RecipeGraphNode(recipe, inputs, outputs);
                        for (ItemStackWithMetadata output : outputs) {
                            graph.addNode(output, node);
                        }
                    }
                }
            }
        }

        for (ItemStackWithMetadata itemWithoutRecipe : itemsWithoutRecipe) {
            ItemGraphNode node = new ItemGraphNode(itemWithoutRecipe);
            graph.addNode(itemWithoutRecipe, node);
        }
        if (skipCalculation) {
            this.graph.postProcess();
        } else {
            this.graph.runAll();
        }
    }

    public Map<Integer, ItemStack> getCalculatedItems() {
        if (this.graph == null) return Collections.emptyMap();
        return this.graph.getCalculatedItems();
    }

    public Map<Integer, ItemStack> getCalculatedRemainders() {
        if (this.graph == null) return Collections.emptyMap();
        return this.graph.getCalculatedRemainders();
    }

    public Map<Integer, Integer> getCalculatedCraftCounts() {
        if (this.graph == null) return Collections.emptyMap();
        return this.graph.getCalculatedCraftCounts();
    }

    public Set<Integer> getInputSlots() {
        if (this.graph == null) return Collections.emptySet();
        return this.graph.getInputSlots();
    }

    public Set<Integer> getOutputSlots() {
        if (this.graph == null) return Collections.emptySet();
        return this.graph.getCraftedOutputSlots();
    }

    public Set<Integer> getConflictingSlots() {
        if (this.graph == null) return Collections.emptySet();
        return this.graph.getConflictingSlots();
    }

    public List<ItemStack> getInputStacks() {
        if (this.graph == null) return Collections.emptyList();
        return this.graph.getInputStacks();
    }

    public List<ItemStack> getOutputStacks() {
        if (this.graph == null) return Collections.emptyList();
        return this.graph.getOutputStacks();
    }

    public List<ItemStack> getRemainingStacks() {
        if (this.graph == null) return Collections.emptyList();
        return this.graph.getRemainingStacks();
    }
}
