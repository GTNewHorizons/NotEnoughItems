package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;

import codechicken.nei.FastTransferManager;
import codechicken.nei.PositionedStack;

public class SmartOverlayHandler extends DefaultOverlayHandler {

    public static final SmartOverlayHandler INSTANCE = new SmartOverlayHandler();

    private static final int MAX_RECIPE_SLOTS = 12;
    private static final int MAX_COST_DELTA = 16 * 16;

    private SmartOverlayHandler() {}

    public static boolean canHandle(GuiContainer gui, IRecipeHandler handler, int recipeIndex) {
        return INSTANCE.resolveSlots(gui, handler.getIngredientStacks(recipeIndex)) != null;
    }

    @Override
    public int transferRecipe(GuiContainer gui, IRecipeHandler handler, int recipeIndex, int multiplier) {
        final List<PositionedStack> ingredients = handler.getIngredientStacks(recipeIndex);
        final List<DistributedIngred> ingredStacks = getPermutationIngredients(ingredients);
        final Slot[][] recipeSlots = resolveSlots(gui, ingredients);

        if (recipeSlots == null || !clearMappedSlots(gui, recipeSlots)) return 0;

        findInventoryQuantities(gui, ingredStacks);

        final List<IngredientDistribution> assignedIngredients = assignIngredients(ingredients, ingredStacks);
        if (assignedIngredients == null) return 0;

        assignIngredSlots(gui, ingredients, assignedIngredients);
        multiplier = Math.min(multiplier == 0 ? 64 : multiplier, calculateRecipeQuantity(assignedIngredients));

        moveIngredients(gui, assignedIngredients, Math.max(1, multiplier));

        return assignedIngredients.stream().anyMatch(distrib -> distrib.distrib.distributed == 0) ? 0 : multiplier;
    }

    @Override
    public boolean canFillCraftingGrid(GuiContainer firstGui, IRecipeHandler handler, int recipeIndex) {
        return canHandle(firstGui, handler, recipeIndex);
    }

    @Override
    protected Set<Slot> getCraftMatrixSlots(GuiContainer gui, IRecipeHandler handler) {
        return Collections.emptySet();
    }

    @Override
    public Slot[][] mapIngredSlots(GuiContainer gui, List<PositionedStack> ingredients) {
        Slot[][] exactSlots = super.mapIngredSlots(gui, ingredients);
        if (!hasEmptyMappings(exactSlots)) {
            return exactSlots;
        }

        Slot[][] smartSlots = resolveSlots(gui, ingredients);
        return smartSlots == null ? exactSlots : smartSlots;
    }

    private boolean clearMappedSlots(GuiContainer gui, Slot[][] recipeSlots) {
        Set<Slot> cleared = new HashSet<>();

        for (Slot[] slots : recipeSlots) {
            for (Slot slot : slots) {
                if (cleared.add(slot) && slot.getHasStack() && slot.canTakeStack(gui.mc.thePlayer)) {
                    FastTransferManager.clickSlot(gui, slot.slotNumber, 0, 1);
                    if (slot.getHasStack()) return false;
                }
            }
        }

        FastTransferManager.dropHeldItem(gui);
        return gui.mc.thePlayer.inventory.getItemStack() == null;
    }

    private Slot[][] resolveSlots(GuiContainer gui, List<PositionedStack> ingredients) {
        if (ingredients == null || ingredients.isEmpty() || ingredients.size() > MAX_RECIPE_SLOTS) {
            return null;
        }

        List<Slot> candidates = new ArrayList<>();
        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (isDestinationCandidate(slot)) {
                candidates.add(slot);
            }
        }

        if (candidates.size() < ingredients.size()) {
            return null;
        }

        Assignment best = new Assignment();
        Assignment secondBest = new Assignment();
        searchAssignments(
                ingredients,
                candidates,
                new boolean[candidates.size()],
                new Slot[ingredients.size()],
                0,
                0,
                best,
                secondBest);

        if (best.slots == null || secondBest.cost - best.cost <= MAX_COST_DELTA) {
            return null;
        }

        Slot[][] resolved = new Slot[ingredients.size()][];
        for (int i = 0; i < ingredients.size(); i++) {
            resolved[i] = new Slot[] { best.slots[i] };
        }
        return resolved;
    }

    private void searchAssignments(List<PositionedStack> ingredients, List<Slot> candidates, boolean[] used,
            Slot[] assigned, int ingredientIndex, int cost, Assignment best, Assignment secondBest) {
        if (cost >= secondBest.cost) {
            return;
        }

        if (ingredientIndex == ingredients.size()) {
            if (cost < best.cost) {
                secondBest.copyFrom(best);
                best.set(cost, assigned);
            } else if (cost < secondBest.cost) {
                secondBest.set(cost, assigned);
            }
            return;
        }

        PositionedStack ingredient = ingredients.get(ingredientIndex);
        for (int i = 0; i < candidates.size(); i++) {
            if (used[i]) continue;

            Slot slot = candidates.get(i);
            int slotCost = getSlotCost(ingredient, slot);
            if (slotCost < 0) continue;

            used[i] = true;
            assigned[ingredientIndex] = slot;
            searchAssignments(
                    ingredients,
                    candidates,
                    used,
                    assigned,
                    ingredientIndex + 1,
                    cost + slotCost,
                    best,
                    secondBest);
            assigned[ingredientIndex] = null;
            used[i] = false;
        }
    }

    private int getSlotCost(PositionedStack ingredient, Slot slot) {
        boolean acceptsAnyPermutation = false;
        for (ItemStack permutation : ingredient.items) {
            if (canUseSlot(slot, permutation)) {
                acceptsAnyPermutation = true;
                break;
            }
        }

        if (!acceptsAnyPermutation) {
            return -1;
        }

        int dx = slot.xDisplayPosition - (ingredient.relx + offsetx);
        int dy = slot.yDisplayPosition - (ingredient.rely + offsety);
        int cost = dx * dx + dy * dy;

        if (slot.getHasStack()) {
            cost += 10_000;
        }

        return cost;
    }

    private boolean canUseSlot(Slot slot, ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !slot.isItemValid(stack)) {
            return false;
        }

        if (!slot.inventory.isItemValidForSlot(slot.getSlotIndex(), stack)) {
            return false;
        }

        return !slot.getHasStack() || canStack(slot.getStack(), stack);
    }

    private boolean isDestinationCandidate(Slot slot) {
        return slot != null && !(slot.inventory instanceof InventoryPlayer) && !(slot instanceof SlotCrafting);
    }

    private boolean hasEmptyMappings(Slot[][] recipeSlots) {
        for (Slot[] recipeSlot : recipeSlots) {
            if (recipeSlot.length == 0) {
                return true;
            }
        }
        return false;
    }

    private static class Assignment {

        int cost = Integer.MAX_VALUE;
        Slot[] slots;

        void set(int cost, Slot[] slots) {
            this.cost = cost;
            this.slots = slots.clone();
        }

        void copyFrom(Assignment other) {
            this.cost = other.cost;
            this.slots = other.slots == null ? null : other.slots.clone();
        }
    }
}
