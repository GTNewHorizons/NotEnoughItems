package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;

import codechicken.lib.inventory.InventoryUtils;
import codechicken.nei.FastTransferManager;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.api.IOverlayHandler;
import codechicken.nei.util.NBTHelper;
import cpw.mods.fml.relauncher.ReflectionHelper;

public class DefaultOverlayHandler implements IOverlayHandler {

    private static Class<?> gtItem;

    static {
        try {
            final ClassLoader loader = DefaultOverlayHandler.class.getClassLoader();
            gtItem = ReflectionHelper
                    .getClass(loader, "gregtech.api.items.MetaBaseItem", "gregtech.api.items.GT_MetaBase_Item");
        } catch (Exception ignored) {
            /* Do nothing */
        }
    }

    public static class DistributedIngred {

        public DistributedIngred(ItemStack item) {
            stack = InventoryUtils.copyStack(item, 1);
        }

        public ItemStack stack;
        public int invAmount;
        public int distributed;
        public int numSlots;
        public int recipeAmount;
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

    public DefaultOverlayHandler(int x, int y) {
        offsetx = x;
        offsety = y;
    }

    public DefaultOverlayHandler() {
        this(5, 11);
    }

    public int offsetx;
    public int offsety;

    @Override
    public void overlayRecipe(GuiContainer gui, IRecipeHandler handler, int recipeIndex, boolean maxTransfer) {
        transferRecipe(gui, handler, recipeIndex, maxTransfer ? Integer.MAX_VALUE : 1);
    }

    @Override
    public int transferRecipe(GuiContainer gui, IRecipeHandler handler, int recipeIndex, int multiplier) {
        final List<PositionedStack> ingredients = handler.getIngredientStacks(recipeIndex);
        final List<DistributedIngred> ingredStacks = getPermutationIngredients(ingredients);

        if (!clearIngredients(gui)) return 0;

        findInventoryQuantities(gui, ingredStacks);

        final List<IngredientDistribution> assignedIngredients = assignIngredients(ingredients, ingredStacks);
        if (assignedIngredients == null) return 0;

        assignIngredSlots(gui, ingredients, assignedIngredients);
        multiplier = Math.min(multiplier == 0 ? 64 : multiplier, calculateRecipeQuantity(assignedIngredients));

        if (multiplier != 0) {
            moveIngredients(gui, assignedIngredients, (int) multiplier);
        }

        return multiplier;
    }

    @Override
    public boolean canFillCraftingGrid(GuiContainer firstGui, IRecipeHandler handler, int recipeIndex) {
        return presenceOverlay(firstGui, handler, recipeIndex).stream().allMatch(state -> state.isPresent());
    }

    @Override
    public boolean canCraft(GuiContainer firstGui, IRecipeHandler handler, int recipeIndex) {
        return canFillCraftingGrid(firstGui, handler, recipeIndex);
    }

    @Override
    public boolean craft(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex, int multiplier) {
        final EntityClientPlayerMP thePlayer = NEIClientUtils.mc().thePlayer;
        boolean craft = false;

        while (multiplier > 0) {
            final int transfer = transferRecipe(firstGui, recipe, recipeIndex, multiplier);

            if (transfer <= 0) {
                break;
            }

            multiplier -= transfer;

            for (Slot slot : firstGui.inventorySlots.inventorySlots) {
                if (slot.getHasStack() && slot instanceof SlotCrafting && slot.canTakeStack(thePlayer)) {
                    FastTransferManager.clickSlot(firstGui, slot.slotNumber, 0, 1);
                    craft = true;
                    break;
                }
            }

        }

        return craft;
    }

    private boolean clearIngredients(GuiContainer gui) {
        final EntityClientPlayerMP thePlayer = NEIClientUtils.mc().thePlayer;

        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot.getHasStack() && !canMoveFrom(slot, gui)
                    && !(slot instanceof SlotCrafting)
                    && slot.canTakeStack(thePlayer)) {
                FastTransferManager.clickSlot(gui, slot.slotNumber, 0, 1);
                if (slot.getHasStack()) return false;
            }
        }

        return true;
    }

    private void moveIngredients(GuiContainer gui, List<IngredientDistribution> assignedIngredients, int multiplier) {
        for (IngredientDistribution distrib : assignedIngredients) {
            if (distrib.slots.length == 0) continue;

            ItemStack pstack = distrib.permutation;
            int transferCap = multiplier * pstack.stackSize;
            int transferred = 0;

            int destSlotIndex = 0;
            Slot dest = distrib.slots[0];
            int slotTransferred = 0;
            int slotTransferCap = pstack.getMaxStackSize();

            for (Slot slot : gui.inventorySlots.inventorySlots) {
                if (!slot.getHasStack() || !canMoveFrom(slot, gui)) continue;

                ItemStack stack = slot.getStack();
                if (!canStack(pstack, stack)) continue;

                int amount = Math.min(transferCap - transferred, stack.stackSize);
                FastTransferManager.clickSlot(gui, slot.slotNumber);
                for (int c = 0; c < amount; c++) {
                    FastTransferManager.clickSlot(gui, dest.slotNumber, 1);
                    transferred++;
                    slotTransferred++;
                    if (slotTransferred >= slotTransferCap) {
                        destSlotIndex++;
                        if (destSlotIndex == distrib.slots.length) {
                            dest = null;
                            break;
                        }
                        dest = distrib.slots[destSlotIndex];
                        slotTransferred = 0;
                    }
                }
                FastTransferManager.clickSlot(gui, slot.slotNumber);
                if (transferred >= transferCap || dest == null) break;
            }
        }
    }

    private int calculateRecipeQuantity(List<IngredientDistribution> assignedIngredients) {
        int quantity = Integer.MAX_VALUE;

        for (IngredientDistribution distrib : assignedIngredients) {
            DistributedIngred istack = distrib.distrib;
            if (istack.numSlots == 0) return 0;

            final int maxStackSize = istack.stack.getMaxStackSize();
            int allSlots = istack.invAmount;
            if (allSlots / istack.numSlots > maxStackSize) allSlots = istack.numSlots * maxStackSize;

            final int newQuantity = allSlots / istack.distributed;
            if (maxStackSize == 1) {
                // If non-stackable, fill up as much as possible of the other ingredients
                continue;
            }
            quantity = Math.min(quantity, newQuantity);
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

            if (biggestIngred == null) // not enough ingreds
                return null;

            biggestIngred.distributed += permutation.stackSize;
            assignedIngredients.add(new IngredientDistribution(biggestIngred, permutation));
        }

        return assignedIngredients;
    }

    private void findInventoryQuantities(GuiContainer gui, List<DistributedIngred> ingredStacks) {
        for (Slot slot : gui.inventorySlots.inventorySlots) /* work out how much we have to go round */ {
            if (slot.getHasStack() && canMoveFrom(slot, gui)) {
                ItemStack pstack = slot.getStack();
                DistributedIngred istack = findIngred(ingredStacks, pstack);
                if (istack != null) istack.invAmount += pstack.stackSize;
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
        if (NEIServerUtils.areStacksSameTypeCrafting(stack2, stack1)) {
            if (NBTHelper.matchTag(stack1.getTagCompound(), stack2.getTagCompound())) return true;

            // GT Items don't have any NBT set for the recipe, so if either of the stacks has a NULL nbt, and the other
            // doesn't, pretend they stack
            if (((gtItem != null && gtItem.isInstance(stack1.getItem()))
                    || (stack1.getMaxStackSize() == 1 && stack2.getMaxStackSize() == 1))
                    && (stack1.stackTagCompound == null ^ stack2.stackTagCompound == null))
                return true;
        }
        return false;
    }
}
