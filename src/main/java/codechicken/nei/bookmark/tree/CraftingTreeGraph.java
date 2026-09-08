package codechicken.nei.bookmark.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import codechicken.nei.recipe.Recipe.RecipeId;

public class CraftingTreeGraph {

    public final List<ItemTreeSlot> roots = new ArrayList<>();
    public final List<ItemTreeSlot> items = new ArrayList<>();
    public final Map<ItemTreeSlot, ItemTreeSlot> parentOf = new HashMap<>();
    public final Map<ItemTreeSlot, List<ItemTreeSlot>> childrenOf = new HashMap<>();

    public final Set<ItemTreeSlot> subtreeMissingCache = new HashSet<>();
    public final Set<ItemTreeSlot> childrenNeedCraftingCache = new HashSet<>();

    public void rebuild(CraftingTreeMath math) {
        this.subtreeMissingCache.clear();
        this.childrenNeedCraftingCache.clear();

        math.refresh();

        this.roots.clear();
        this.roots.addAll(math.rootSlots);
        this.items.clear();
        this.items.addAll(math.items);
        this.parentOf.clear();
        this.parentOf.putAll(math.parentOf);
        this.childrenOf.clear();
        this.childrenOf.putAll(math.childrenOf);

        for (ItemTreeSlot root : this.roots) {
            computeSubtreeMissing(root);
        }

        for (Map.Entry<ItemTreeSlot, List<ItemTreeSlot>> entry : this.childrenOf.entrySet()) {
            if (entry.getValue().stream().anyMatch(child -> child.leftAmount != 0)) {
                this.childrenNeedCraftingCache.add(entry.getKey());
            }
        }
    }

    public Set<RecipeId> collectUnusedRecipes(ItemTreeSlot node) {
        final Map<RecipeId, Set<RecipeId>> children = new HashMap<>();
        final Map<RecipeId, Set<RecipeId>> consumers = new HashMap<>();
        int rootCount = 0;

        for (ItemTreeSlot item : this.items) {
            if (item.prefItem != null) {
                children.computeIfAbsent(item.ingrItem.recipeId, k -> new HashSet<>()).add(item.prefItem.recipeId);
                consumers.computeIfAbsent(item.prefItem.recipeId, k -> new HashSet<>()).add(item.ingrItem.recipeId);

                if (item.ingrItem.recipeId.equals(node.prefItem.recipeId)) {
                    rootCount++;
                }
            }
        }

        final Set<RecipeId> unusedRecipes = new HashSet<>();
        final RecipeId rootRecipeId = node.prefItem.recipeId;

        if (rootCount <= 1) {
            unusedRecipes.add(rootRecipeId);
            collectSubtree(rootRecipeId, children, consumers, unusedRecipes);
        }

        return unusedRecipes;
    }

    private void collectSubtree(RecipeId recipeId, Map<RecipeId, Set<RecipeId>> children,
            Map<RecipeId, Set<RecipeId>> consumers, Set<RecipeId> unusedRecipes) {
        for (RecipeId child : children.getOrDefault(recipeId, Collections.emptySet())) {
            if (consumers.get(child).size() == 1 && unusedRecipes.add(child)) {
                collectSubtree(child, children, consumers, unusedRecipes);
            }
        }
    }

    private boolean computeSubtreeMissing(ItemTreeSlot node) {
        boolean hasMissing = !this.childrenOf.containsKey(node);

        for (ItemTreeSlot child : this.childrenOf.getOrDefault(node, Collections.emptyList())) {
            hasMissing |= child.leftAmount != 0 && computeSubtreeMissing(child);
        }

        if (hasMissing) {
            this.subtreeMissingCache.add(node);
        }

        return hasMissing;
    }

}
