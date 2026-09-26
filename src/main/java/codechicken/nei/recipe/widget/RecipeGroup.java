package codechicken.nei.recipe.widget;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;

import codechicken.nei.FavoriteRecipes;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;

public class RecipeGroup {

    private static final List<Integer> SINGLE_MEMBER = Collections.singletonList(0);

    private final IRecipeHandler handler;
    private final List<Integer> recipeIndices;
    private final HandlerInfo handlerInfo;
    private final PermutationsCycler<Point> slotPermutations;

    private List<Integer> allowedMembers = null;

    public RecipeGroup(IRecipeHandler handler, List<Integer> recipeIndices) {
        this.handler = handler;
        this.recipeIndices = new ArrayList<>(recipeIndices);
        this.handlerInfo = GuiRecipeTab.getHandlerInfo(handler);
        this.slotPermutations = new PermutationsCycler<Point>(
                new HashMap<>(),
                new HashMap<>(),
                this::collectSlotPermutations).onInvalidate(() -> this.allowedMembers = null);
    }

    public int size() {
        return this.recipeIndices.size();
    }

    public IRecipeHandler getHandler() {
        return this.handler;
    }

    public int getRecipeIndex(int member) {
        return this.recipeIndices.get(member);
    }

    public HandlerInfo getHandlerInfo() {
        return this.handlerInfo;
    }

    public boolean contains(int recipeIndex) {
        return this.recipeIndices.contains(recipeIndex);
    }

    public List<ItemStack> getPermutations(Point slotKey) {
        return this.slotPermutations.get(slotKey);
    }

    public List<PositionedStack> getInputs(int member) {
        return this.handler.getIngredientStacks(getRecipeIndex(member));
    }

    public List<PositionedStack> getOutputs(int member) {
        final int recipeIndex = getRecipeIndex(member);
        final PositionedStack pStackResult = this.handler.getResultStack(recipeIndex);
        return pStackResult != null ? Arrays.asList(pStackResult) : this.handler.getOtherStacks(recipeIndex);
    }

    public List<PositionedStack> getCatalysts(int member) {
        final int recipeIndex = getRecipeIndex(member);

        if (this.handler.getResultStack(recipeIndex) == null) {
            return Collections.emptyList();
        }

        return this.handler.getOtherStacks(recipeIndex);
    }

    public List<PositionedStack> getCyclingStacks(int member) {
        final List<PositionedStack> stacks = new ArrayList<>(getInputs(member));
        stacks.addAll(getCatalysts(member));
        return stacks;
    }

    public Point getSlotKey(PositionedStack pStack) {
        return new Point(pStack.relx, pStack.rely);
    }

    public PositionedStack getSlot(int member, Point slotKey) {

        for (PositionedStack pStack : getCyclingStacks(member)) {
            if (pStack.relx == slotKey.x && pStack.rely == slotKey.y) {
                return pStack;
            }
        }

        return null;
    }

    public PositionedStack getOutputSlot(int member, Point slotKey) {

        for (PositionedStack pStack : getOutputs(member)) {
            if (pStack.relx == slotKey.x && pStack.rely == slotKey.y) {
                return pStack;
            }
        }

        return null;
    }

    public List<ItemStack> getOutputVariants(Point slotKey) {
        final List<ItemStack> items = new ArrayList<>();

        for (int member = 0; member < size(); member++) {
            final PositionedStack slot = getOutputSlot(member, slotKey);

            if (slot != null && PermutationsCycler.indexOf(items, slot.item) == -1) {
                items.add(slot.item);
            }
        }

        return items;
    }

    public List<Integer> getAllowedMembers() {
        this.slotPermutations.validate();

        if (size() == 1) {
            return SINGLE_MEMBER;
        }

        if (this.allowedMembers == null) {
            final Set<Point> favoriteSlots = new HashSet<>();

            for (int member = 0; member < size(); member++) {
                for (PositionedStack pStack : getCyclingStacks(member)) {
                    final Point slotKey = getSlotKey(pStack);
                    final List<ItemStack> items = getPermutations(slotKey);

                    if (items.size() > 1 && items.stream().anyMatch(FavoriteRecipes::containsManual)) {
                        favoriteSlots.add(slotKey);
                    }
                }
            }

            this.allowedMembers = filterMembers(favoriteSlots);
        }

        return this.allowedMembers;
    }

    protected List<Integer> filterMembers(Set<Point> favoriteSlots) {
        final List<Integer> indices = new ArrayList<>();
        int minMisses = Integer.MAX_VALUE;

        for (int member = 0; member < size(); member++) {
            final int misses = countMissingFavorites(member, favoriteSlots);

            if (misses < minMisses) {
                minMisses = misses;
                indices.clear();
            }

            if (misses == minMisses) {
                indices.add(member);
            }
        }

        return indices;
    }

    protected int countMissingFavorites(int member, Set<Point> favoriteSlots) {
        int misses = 0;

        for (Point slotKey : favoriteSlots) {
            final PositionedStack slot = getSlot(member, slotKey);

            if (slot != null && slot.getFilteredPermutations().stream().noneMatch(FavoriteRecipes::containsManual)) {
                misses++;
            }
        }

        return misses;
    }

    protected List<ItemStack> collectSlotPermutations(Point slotKey) {
        final List<ItemStack> items = new ArrayList<>();

        for (int member = 0; member < size(); member++) {
            final PositionedStack slot = getSlot(member, slotKey);

            if (slot != null) {
                for (ItemStack stack : slot.getFilteredPermutations()) {
                    if (PermutationsCycler.indexOf(items, stack) == -1) {
                        items.add(stack);
                    }
                }
            }
        }

        return items;
    }

}
