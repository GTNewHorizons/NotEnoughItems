package codechicken.nei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.BookmarkPanel.ItemStackMetadata;
import codechicken.nei.recipe.BookmarkRecipeId;
import codechicken.nei.recipe.StackInfo;

public class BookmarkCraftingChain {

    public HashMap<ItemStack, ItemStack> calculatedItems = new HashMap<>();
    public HashMap<ItemStack, Integer> multiplier = new HashMap<>();

    public HashMap<ItemStack, ItemStack> inputs = new HashMap<>();
    public HashMap<ItemStack, ItemStack> outputs = new HashMap<>();
    public HashMap<ItemStack, ItemStack> intermediate = new HashMap<>();

    protected static class CraftingChainItem {

        public String guid;
        public int stackIndex = 0;
        public int recipeIndex = 0;
        public int factor = 0;
        public int count = 0;
        public boolean ingredient = false;
        public boolean fluidCell = false;
        public ItemStack stack = null;

        public CraftingChainItem(ItemStack stack, ItemStackMetadata stackMetadata) {
            FluidStack fluid = StackInfo.getFluid(stack);
            this.recipeIndex = getRecipeIndex(stackMetadata.recipeId);
            this.ingredient = stackMetadata.ingredient;
            this.stack = stack;

            if (fluid != null) {
                this.stackIndex = getStackIndex(fluid);
                this.count = fluid.amount * Math.max(1, stack.stackSize);
                this.fluidCell = StackInfo.isFluidContainer(stack);
                this.factor = this.fluidCell ? fluid.amount : stackMetadata.factor;
            } else {
                this.stackIndex = getStackIndex(stack);
                this.count = StackInfo.itemStackToNBT(stack).getInteger("Count");
                this.factor = stackMetadata.factor;
            }

            this.guid = this.stackIndex + " " + this.recipeIndex;
        }

        public ItemStack getItemStack(long count) {
            return StackInfo.loadFromNBT(
                    StackInfo.itemStackToNBT(this.stack),
                    this.factor > 0 ? (this.fluidCell ? (count / this.factor) : count) : 0);
        }

        public static void clearStatic() {
            itemCache.clear();
            fluidCache.clear();
            recipeCache.clear();
        }

        private static HashMap<ItemStack, Integer> itemCache = new HashMap<>();

        private static int getStackIndex(ItemStack stackA) {

            if (!itemCache.containsKey(stackA)) {

                for (ItemStack item : itemCache.keySet()) {
                    if (StackInfo.equalItemAndNBT(stackA, item, true)) {
                        itemCache.put(stackA, itemCache.get(item));
                        return itemCache.get(stackA);
                    }
                }

                itemCache.put(stackA, itemCache.size() + fluidCache.size());
            }

            return itemCache.get(stackA);
        }

        private static HashMap<FluidStack, Integer> fluidCache = new HashMap<>();

        private static int getStackIndex(FluidStack fluidA) {

            if (!fluidCache.containsKey(fluidA)) {
                for (FluidStack item : fluidCache.keySet()) {
                    if (fluidA.isFluidEqual(item)) {
                        fluidCache.put(fluidA, fluidCache.get(item));
                        return fluidCache.get(fluidA);
                    }
                }

                fluidCache.put(fluidA, itemCache.size() + fluidCache.size());
            }

            return fluidCache.get(fluidA);
        }

        private static HashMap<BookmarkRecipeId, Integer> recipeCache = new HashMap<>();

        private static int getRecipeIndex(BookmarkRecipeId recipe) {

            if (recipe == null) {
                return -1;
            }

            if (!recipeCache.containsKey(recipe)) {
                for (BookmarkRecipeId item : recipeCache.keySet()) {
                    if (item.equals(recipe)) {
                        recipeCache.put(recipe, recipeCache.get(item));
                        return recipeCache.get(recipe);
                    }
                }

                recipeCache.put(recipe, recipeCache.size());
            }

            return recipeCache.get(recipe);
        }

    }

    protected static class CraftingChainRequest {

        public List<CraftingChainItem> items = new ArrayList<>();

        public HashMap<Integer, Integer> multiplier = new HashMap<>();
        public HashMap<String, Long> counts = new HashMap<>();

        public HashMap<Integer, HashSet<CraftingChainItem>> inputs = new HashMap<>();
        public HashMap<Integer, HashSet<CraftingChainItem>> outputs = new HashMap<>();

        public HashSet<Integer> initialItems = new HashSet<>();
        public HashSet<Integer> startedIngrs = new HashSet<>();

        public CraftingChainRequest(List<ItemStack> items, List<ItemStackMetadata> metadata) {
            HashSet<Integer> inputs = new HashSet<>();
            HashSet<Integer> outputs = new HashSet<>();

            for (int index = 0; index < items.size(); index++) {
                CraftingChainItem item = new CraftingChainItem(items.get(index), metadata.get(index));
                this.items.add(item);

                if (this.multiplier.get(item.recipeIndex) == null) {
                    this.multiplier.put(item.recipeIndex, 0);
                }

                this.counts.put(item.guid, this.counts.getOrDefault(item.guid, 0L) + item.count);

                if (item.ingredient) {
                    inputs.add(item.recipeIndex);

                    if (this.inputs.get(item.stackIndex) == null) {
                        this.inputs.put(item.stackIndex, new HashSet<>());
                    }

                    this.inputs.get(item.stackIndex).add(item);
                } else {
                    outputs.add(item.recipeIndex);

                    if (this.outputs.get(item.stackIndex) == null) {
                        this.outputs.put(item.stackIndex, new HashSet<>());
                    }

                    this.outputs.get(item.stackIndex).add(item);
                }

            }

            for (int recipeIndex : this.multiplier.keySet()) {
                if (recipeIndex == -1 || !inputs.contains(recipeIndex) || !outputs.contains(recipeIndex)) {
                    this.initialItems.add(recipeIndex);
                }
            }

            for (CraftingChainItem item : this.items) {
                if (item.ingredient == false && !this.initialItems.contains(item.recipeIndex)) {
                    this.startedIngrs.add(item.stackIndex);
                }
            }

            CraftingChainItem.clearStatic();
        }

    }

    public void refresh(List<ItemStack> items, List<ItemStackMetadata> metadata) {
        CraftingChainRequest request = new CraftingChainRequest(items, metadata);
        HashMap<Integer, Long> iShift = new HashMap<>();
        HashMap<Integer, Long> oShift = new HashMap<>();
        boolean change = true;
        int iteration = 100;

        while (--iteration > 0 && change) {
            change = false;
            for (int stackIndex : request.startedIngrs) {
                change = calculateShift(request, stackIndex) || change;
            }
        }

        this.inputs.clear();
        this.outputs.clear();
        this.intermediate.clear();
        this.calculatedItems.clear();
        this.multiplier.clear();

        for (CraftingChainItem item : request.items) {
            if (request.initialItems.contains(item.recipeIndex)) {
                long iCount = calculateCount(request, request.inputs.get(item.stackIndex))
                        - iShift.getOrDefault(item.stackIndex, 0L);
                this.calculatedItems.put(item.stack, item.stack);

                if (iCount == 0) {
                    this.inputs.put(item.stack, item.getItemStack(0));
                } else {
                    long count = Math.min(iCount, item.count);
                    this.inputs.put(item.stack, item.getItemStack(count));
                    iShift.put(item.stackIndex, iShift.getOrDefault(item.stackIndex, 0L) + count);
                }

            }
        }

        for (CraftingChainItem item : request.items) {
            if (!request.initialItems.contains(item.recipeIndex)) {
                long count = item.count + item.factor * request.multiplier.get(item.recipeIndex);
                this.calculatedItems.put(item.stack, item.getItemStack(count));

                if (item.ingredient) { // input
                    long oCount = calculateCount(request, request.outputs.get(item.stackIndex))
                            - oShift.getOrDefault(item.stackIndex, 0L);

                    if (oCount < count) {
                        this.inputs.put(item.stack, item.getItemStack(count - oCount));
                        oShift.put(item.stackIndex, oShift.getOrDefault(item.stackIndex, 0L) + oCount);
                    } else if (oCount >= count) {
                        this.intermediate.put(item.stack, item.getItemStack(0));
                        oShift.put(item.stackIndex, oShift.getOrDefault(item.stackIndex, 0L) + count);
                    }

                } else { // output
                    long iCount = calculateCount(request, request.inputs.get(item.stackIndex))
                            - iShift.getOrDefault(item.stackIndex, 0L);
                    this.multiplier.put(item.stack, item.factor > 0 ? (int) (count / item.factor) : 0);

                    if (iCount < count) {
                        this.outputs.put(item.stack, item.getItemStack(count - iCount));
                        iShift.put(item.stackIndex, iShift.getOrDefault(item.stackIndex, 0L) + iCount);
                    } else if (iCount >= count) {
                        this.intermediate.put(item.stack, item.getItemStack(0));
                        iShift.put(item.stackIndex, iShift.getOrDefault(item.stackIndex, 0L) + count);
                    }

                }

            }
        }

    }

    private boolean calculateShift(CraftingChainRequest request, int stackIndex) {

        if (!request.outputs.containsKey(stackIndex)) {
            return false;
        }

        int shift = 0;
        int minRecipeIndex = 0;
        int minShift = 0x7fffff;

        for (CraftingChainItem item : request.outputs.get(stackIndex)) {
            if (!request.initialItems.contains(item.recipeIndex) && item.factor > 0) {
                long ingrCount = calculateCount(request, request.inputs.get(item.stackIndex));
                long outputCount = calculateCount(request, request.outputs.get(item.stackIndex));

                while ((outputCount + item.factor * shift) < ingrCount && shift < minShift) {
                    shift++;
                }

                if (shift < minShift) {
                    minShift = shift;
                    minRecipeIndex = item.recipeIndex;
                }
            }
        }

        if (minShift < 0x7fffff) {
            request.multiplier.put(minRecipeIndex, request.multiplier.get(minRecipeIndex) + minShift);
            return true;
        }

        return false;
    }

    private long calculateCount(CraftingChainRequest request, HashSet<CraftingChainItem> items) {
        long count = 0L;

        if (items != null) {
            for (CraftingChainItem item : items) {
                count += request.counts.get(item.guid) + item.factor * request.multiplier.get(item.recipeIndex);
            }
        }

        return count;
    }

}
