package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import codechicken.lib.inventory.InventoryUtils;
import codechicken.nei.FastTransferManager;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;

public class DefaultOverlayHandler implements IOverlayHandler {

    public static class DistributedIngred {

        public DistributedIngred(ItemStack item) {
            stack = InventoryUtils.copyStack(item, 1);
        }

        public ItemStack stack;
        public int invAmount;
        public int distributed;
        public int numSlots;
        public int recipeAmount;
        public boolean isContainerItem = false;
    }

    public static class IngredientDistribution {

        public IngredientDistribution(DistributedIngred distrib, ItemStack permutation) {
            this.distrib = distrib;
            this.permutation = permutation;
        }

        public DistributedIngred distrib;
        public ItemStack permutation;
        public Slot[] slots;
    }

    public int offsetx;
    public int offsety;

    public DefaultOverlayHandler() {
        this(5, 11);
    }

    public DefaultOverlayHandler(int x, int y) {
        this.offsetx = x;
        this.offsety = y;
    }

    @Override
    public void overlayRecipe(GuiContainer gui, IRecipeHandler handler, int recipeIndex, boolean maxTransfer) {
        transferRecipe(gui, handler, recipeIndex, maxTransfer ? Integer.MAX_VALUE : 1);
    }

    @Override
    public int transferRecipe(GuiContainer gui, IRecipeHandler handler, int recipeIndex, int multiplier) {
        final List<PositionedStack> ingredients = handler.getIngredientStacks(recipeIndex);
        final List<DistributedIngred> ingredStacks = getPermutationIngredients(ingredients);

        if (!clearIngredients(gui, handler)) return 0;

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
        return true;
    }

    @Override
    public boolean canCraft(GuiContainer firstGui, IRecipeHandler handler, int recipeIndex) {
        return canFillCraftingGrid(firstGui, handler, recipeIndex)
                && presenceOverlay(firstGui, handler, recipeIndex).stream().allMatch(state -> state.isPresent());
    }

    @Override
    public boolean craft(GuiContainer firstGui, IRecipeHandler handler, int recipeIndex, int multiplier) {
        final Slot craftingSlot = firstGui.inventorySlots.inventorySlots.stream()
                .filter(slot -> slot instanceof SlotCrafting).findFirst().orElse(null);

        if (craftingSlot == null) {
            return false;
        }

        boolean craft = false;
        while (multiplier > 0) {
            final int transfer = transferRecipe(firstGui, handler, recipeIndex, multiplier);

            if (transfer <= 0) {
                break;
            }

            multiplier -= transfer;

            if (craftingSlot.getHasStack() && craftingSlot.canTakeStack(firstGui.mc.thePlayer)) {
                FastTransferManager.clickSlot(firstGui, craftingSlot.slotNumber, 0, 1);
                craft = true;
            }

        }

        clearIngredients(firstGui, handler);

        return craft;
    }

    protected boolean clearIngredients(GuiContainer gui, IRecipeHandler handler) {

        for (Slot slot : getCraftMatrixSlots(gui, handler)) {
            if (slot.canTakeStack(gui.mc.thePlayer)) {
                FastTransferManager.clickSlot(gui, slot.slotNumber, 0, 1);
                if (slot.getHasStack()) return false;
            }
        }

        FastTransferManager.dropHeldItem(gui);

        return gui.mc.thePlayer.inventory.getItemStack() == null;
    }

    /**
     * Gets the slots in the crafting matrix for the given GUI and recipe handler.
     * @param gui
     * @param handler
     * @return
     */
    protected Set<Slot> getCraftMatrixSlots(GuiContainer gui, IRecipeHandler handler) {
        final PositionedStack firstIngr = handler.getIngredientStacks(0).get(0);
        final Map<Integer, Slot> grid = new HashMap<>();
        final Set<Slot> ingredSlots = new HashSet<>();

        for (Slot slot : gui.inventorySlots.inventorySlots) {
            final int key = (slot.xDisplayPosition - this.offsetx) * 10_000 + (slot.yDisplayPosition - this.offsety);
            grid.put(key, slot);
        }

        int left = firstIngr.relx;
        int top = firstIngr.rely;
        int right = left;
        int bottom = top;

        while (grid.containsKey((left - 18) * 10_000 + firstIngr.rely)) {
            left -= 18;
        }

        while (grid.containsKey((right + 18) * 10_000 + firstIngr.rely)) {
            right += 18;
        }

        while (grid.containsKey(firstIngr.relx * 10_000 + (top - 18))) {
            top -= 18;
        }

        while (grid.containsKey(firstIngr.relx * 10_000 + (bottom + 18))) {
            bottom += 18;
        }

        for (int x = left; x <= right; x += 18) {
            for (int y = top; y <= bottom; y += 18) {
                final Slot slot = grid.get(x * 10_000 + y);
                if (slot != null && !(slot.inventory instanceof InventoryPlayer)) {
                    ingredSlots.add(slot);
                }
            }
        }

        return ingredSlots;
    }

    private void moveIngredients(GuiContainer gui, List<IngredientDistribution> assignedIngredients, int multiplier) {

        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot instanceof SlotCrafting || !slot.getHasStack()
                    || !canMoveFrom(slot, gui)
                    || !slot.canTakeStack(gui.mc.thePlayer))
                continue;
            ItemStack stack = slot.getStack();
            int slotTransferCap = stack.getMaxStackSize();

            for (IngredientDistribution distrib : assignedIngredients) {
                if (distrib.slots.length == 0 || !slot.getHasStack() || !canStack(distrib.permutation, stack)) continue;
                int transferCap = Math.min(slotTransferCap, multiplier * distrib.permutation.stackSize);
                int stackSize = slot.getStack().stackSize;
                boolean pickup = false;

                for (Slot dest : distrib.slots) {
                    int amount = Math
                            .min(transferCap - (dest.getHasStack() ? dest.getStack().stackSize : 0), stackSize);

                    if (stackSize <= amount) {

                        if (!pickup) {
                            FastTransferManager.clickSlot(gui, slot.slotNumber);
                        }

                        FastTransferManager.clickSlot(gui, dest.slotNumber);
                        break;
                    } else {

                        for (int c = 0; c < amount; c++) {

                            if (pickup != (pickup = true)) {
                                FastTransferManager.clickSlot(gui, slot.slotNumber);
                            }

                            FastTransferManager.clickSlot(gui, dest.slotNumber, 1);
                            stackSize--;
                        }
                    }

                }

                if (gui.mc.thePlayer.inventory.getItemStack() != null) {
                    FastTransferManager.clickSlot(gui, slot.slotNumber);
                }
            }

        }
    }

    private int calculateRecipeQuantity(List<IngredientDistribution> assignedIngredients) {
        int quantity = Integer.MAX_VALUE;

        for (IngredientDistribution distrib : assignedIngredients) {
            final DistributedIngred istack = distrib.distrib;
            if (istack.distributed == 0) continue;
            if (istack.numSlots == 0) return 0;

            final int maxStackSize = istack.stack.getMaxStackSize();
            if (maxStackSize == 1 && istack.isContainerItem) {
                // If non-stackable, fill up as much as possible of the other ingredients
                continue;
            }

            final int allSlots = Math.min(istack.invAmount, istack.numSlots * maxStackSize);
            quantity = Math.min(quantity, allSlots / istack.distributed);
        }

        if (quantity == Integer.MAX_VALUE) {
            // Only possible if all ingredients were non-stackable
            quantity = 1;
        }

        return quantity;
    }

    private Slot[][] assignIngredSlots(GuiContainer gui, List<PositionedStack> ingredients,
            List<IngredientDistribution> assignedIngredients) {
        Slot[][] recipeSlots = mapIngredSlots(gui, ingredients); // setup the slot map

        HashMap<Slot, Integer> distribution = new HashMap<>();
        for (Slot[] recipeSlot : recipeSlots)
            for (Slot slot : recipeSlot) if (!distribution.containsKey(slot)) distribution.put(slot, -1);

        HashSet<Slot> avaliableSlots = new HashSet<>(distribution.keySet());
        HashSet<Integer> remainingIngreds = new HashSet<>();
        ArrayList<LinkedList<Slot>> assignedSlots = new ArrayList<>();
        for (int i = 0; i < ingredients.size(); i++) {
            remainingIngreds.add(i);
            assignedSlots.add(new LinkedList<>());
        }

        while (!avaliableSlots.isEmpty() && !remainingIngreds.isEmpty()) {
            for (Iterator<Integer> iterator = remainingIngreds.iterator(); iterator.hasNext();) {
                int i = iterator.next();
                boolean assigned = false;
                DistributedIngred istack = assignedIngredients.get(i).distrib;

                for (Slot slot : recipeSlots[i]) {
                    if (avaliableSlots.contains(slot)) {
                        avaliableSlots.remove(slot);
                        if (slot.getHasStack()) continue;

                        istack.numSlots++;
                        assignedSlots.get(i).add(slot);
                        assigned = true;
                        break;
                    }
                }

                if (!assigned || istack.numSlots * istack.stack.getMaxStackSize() >= istack.invAmount) {
                    iterator.remove();
                }
            }
        }

        for (int i = 0; i < ingredients.size(); i++)
            assignedIngredients.get(i).slots = assignedSlots.get(i).toArray(new Slot[0]);
        return recipeSlots;
    }

    private List<IngredientDistribution> assignIngredients(List<PositionedStack> ingredients,
            List<DistributedIngred> ingredStacks) {
        ArrayList<IngredientDistribution> assignedIngredients = new ArrayList<>();
        for (PositionedStack posstack : ingredients) // assign what we need and have
        {
            DistributedIngred biggestIngred = null;
            ItemStack permutation = null;
            int biggestSize = 0;
            for (ItemStack pstack : posstack.items) {
                for (DistributedIngred istack : ingredStacks) {
                    if (!canStack(pstack, istack.stack) || istack.invAmount - istack.distributed < pstack.stackSize
                            || istack.recipeAmount == 0
                            || pstack.stackSize == 0)
                        continue;

                    int relsize = (istack.invAmount - istack.invAmount / istack.recipeAmount * istack.distributed)
                            / pstack.stackSize;
                    if (relsize > biggestSize) {
                        biggestSize = relsize;
                        biggestIngred = istack;
                        permutation = pstack;
                        break;
                    }
                }
            }

            if (biggestIngred == null) {
                biggestIngred = new DistributedIngred(posstack.item);
                permutation = InventoryUtils.copyStack(posstack.item, 0);
            }

            biggestIngred.distributed += permutation.stackSize;
            assignedIngredients.add(new IngredientDistribution(biggestIngred, permutation));
        }

        return assignedIngredients;
    }

    private void findInventoryQuantities(GuiContainer gui, List<DistributedIngred> ingredStacks) {
        for (Slot slot : gui.inventorySlots.inventorySlots) /* work out how much we have to go round */ {
            if (slot.getHasStack() && canMoveFrom(slot, gui)) {
                final ItemStack pstack = slot.getStack();
                final DistributedIngred istack = findIngred(ingredStacks, pstack);

                if (istack != null) {
                    istack.invAmount += pstack.stackSize;

                    if (!istack.isContainerItem && pstack.getMaxStackSize() == 1
                            && pstack.getItem().hasContainerItem(pstack)) {
                        final NBTTagCompound tagCompound = pstack.getTagCompound();

                        if (tagCompound != null && tagCompound.hasKey("GT.ToolStats")) {
                            istack.isContainerItem = true;
                        } else {
                            final boolean isPausedItemDamageSound = StackInfo.isPausedItemDamageSound();
                            StackInfo.pauseItemDamageSound(true);

                            final ItemStack containerItem = pstack.getItem().getContainerItem(pstack);
                            if (containerItem != null) {
                                istack.isContainerItem = pstack.getItem() == containerItem.getItem();
                            }

                            StackInfo.pauseItemDamageSound(isPausedItemDamageSound);
                        }
                    }
                }
            }
        }
    }

    private List<DistributedIngred> getPermutationIngredients(List<PositionedStack> ingredients) {
        ArrayList<DistributedIngred> ingredStacks = new ArrayList<>();
        for (PositionedStack posstack : ingredients) /* work out what we need */ {
            for (ItemStack pstack : posstack.items) {
                DistributedIngred istack = findIngred(ingredStacks, pstack);
                if (istack == null) ingredStacks.add(istack = new DistributedIngred(pstack));
                istack.recipeAmount += pstack.stackSize;
            }
        }
        return ingredStacks;
    }

    public boolean canMoveFrom(Slot slot, GuiContainer gui) {
        return slot.inventory instanceof InventoryPlayer;
    }

    public Slot[][] mapIngredSlots(GuiContainer gui, List<PositionedStack> ingredients) {
        Slot[][] recipeSlotList = new Slot[ingredients.size()][];
        for (int i = 0; i < ingredients.size(); i++) /* identify slots */ {
            LinkedList<Slot> recipeSlots = new LinkedList<>();
            PositionedStack pstack = ingredients.get(i);
            for (Slot slot : gui.inventorySlots.inventorySlots) {
                if (slot.xDisplayPosition == pstack.relx + offsetx && slot.yDisplayPosition == pstack.rely + offsety) {
                    recipeSlots.add(slot);
                    break;
                }
            }
            recipeSlotList[i] = recipeSlots.toArray(new Slot[0]);
        }
        return recipeSlotList;
    }

    public DistributedIngred findIngred(List<DistributedIngred> ingredStacks, ItemStack pstack) {
        for (DistributedIngred istack : ingredStacks) if (canStack(istack.stack, pstack)) return istack;
        return null;
    }

    protected boolean canStack(ItemStack stack1, ItemStack stack2) {
        if (stack1 == null || stack2 == null) return true;
        return NEIClientUtils.areStacksSameTypeCraftingWithNBT(stack1, stack2);
    }
}
