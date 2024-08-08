package codechicken.nei.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;

public interface IOverlayHandler {

    void overlayRecipe(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, boolean shift);

    default List<Boolean> presenceOverlay(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex) {
        final List<Boolean> itemPresenceSlots = new ArrayList<>();
        final List<PositionedStack> ingredients = recipe.getIngredientStacks(recipeIndex);
        @SuppressWarnings("unchecked")
        final List<ItemStack> invStacks = ((List<Slot>) firstGui.inventorySlots.inventorySlots).stream()
                .filter(
                        s -> s != null && s.getStack() != null
                                && s.getStack().stackSize > 0
                                && s.isItemValid(s.getStack())
                                && s.canTakeStack(firstGui.mc.thePlayer))
                .map(s -> s.getStack().copy()).collect(Collectors.toCollection(ArrayList::new));

        for (PositionedStack stack : ingredients) {
            Optional<ItemStack> used = invStacks.stream().filter(is -> is.stackSize > 0 && stack.contains(is))
                    .findFirst();

            itemPresenceSlots.add(used.isPresent());

            if (used.isPresent()) {
                ItemStack is = used.get();
                is.stackSize -= 1;
            }
        }

        return itemPresenceSlots;
    }
}
