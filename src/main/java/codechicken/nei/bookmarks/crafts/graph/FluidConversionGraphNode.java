package codechicken.nei.bookmarks.crafts.graph;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.bookmarks.crafts.ItemStackWithMetadata;
import codechicken.nei.recipe.StackInfo;

public class FluidConversionGraphNode implements CraftingGraphNode {

    private final Map<String, Integer> conversionInputs = new HashMap<>();
    private final Map<String, Integer> conversionOutputs = new HashMap<>();

    private final Map<String, Integer> consumedEmptyContainers = new HashMap<>();
    private final Map<String, Integer> producedEmptyContainers = new HashMap<>();

    private final Map<String, String> keyToEmptyContainer = new HashMap<>();

    private final String displayStackGUID;
    private int fluidRemainder = 0;

    public FluidConversionGraphNode(Set<ItemStackWithMetadata> inputFluidStacks,
            Set<ItemStackWithMetadata> outputFluidStacks) {
        for (ItemStackWithMetadata inputFluidStack : inputFluidStacks) {
            ItemStack stack = inputFluidStack.getStack();
            FluidStack fluidStack = StackInfo.getFluid(stack);
            String key = StackInfo.getItemStackGUID(stack);
            conversionInputs.put(key, StackInfo.isFluidContainer(stack) ? fluidStack.amount : 1);

            ItemStack emptyContainer = FluidContainerRegistry.drainFluidContainer(stack);
            if (emptyContainer != null) {
                keyToEmptyContainer.put(key, StackInfo.getItemStackGUID(emptyContainer));
            }
        }

        for (ItemStackWithMetadata outputFluidStack : outputFluidStacks) {
            ItemStack stack = outputFluidStack.getStack();
            FluidStack fluidStack = StackInfo.getFluid(stack);
            String key = StackInfo.getItemStackGUID(stack);
            conversionOutputs.put(key, StackInfo.isFluidContainer(stack) ? fluidStack.amount : 1);

            ItemStack emptyContainer = FluidContainerRegistry.drainFluidContainer(stack);
            if (emptyContainer != null) {
                keyToEmptyContainer.put(key, StackInfo.getItemStackGUID(emptyContainer));
            }
        }

        FluidStack fluidStack = StackInfo.getFluid(outputFluidStacks.iterator().next().getStack());
        this.displayStackGUID = StackInfo.getItemStackGUID(StackInfo.getFluidDisplayStack(fluidStack));
    }

    @Override
    public int addToRemainders(String itemKey, int remainder) {
        this.fluidRemainder += remainder;
        return remainder;
    }

    @Override
    public int getRemainder(String itemKey) {
        return fluidRemainder;
    }

    @Override
    public Map<String, Integer> getRemainders() {
        return Collections.singletonMap(displayStackGUID, fluidRemainder);
    }

    public int calculateAmountToRequest(String rightKey, int rightRequestedAmount, String leftKey) {
        int rightFluidSize = conversionOutputs.get(rightKey);
        int leftFluidSize = conversionInputs.get(leftKey);
        int fluidAmountToRequest = rightRequestedAmount * rightFluidSize - fluidRemainder;
        return NEIServerUtils.divideCeil(fluidAmountToRequest, leftFluidSize);
    }

    public String getInputKey() {
        return conversionInputs.entrySet().stream().findFirst().get().getKey();
    }

    public int processResults(String rightKey, int rightAmount, String leftKey, int leftAmount) {
        int rightFluidSize = conversionOutputs.get(rightKey);
        int leftFluidSize = conversionInputs.get(leftKey);
        int leftFluidReturned = leftAmount * leftFluidSize;
        int rightFluidRequested = rightFluidSize * rightAmount;
        int rightAmountReturned = leftFluidReturned / rightFluidSize;

        this.fluidRemainder += Math.max(0, leftFluidReturned - rightFluidRequested);

        if (keyToEmptyContainer.containsKey(rightKey)) {
            String containerKey = keyToEmptyContainer.get(rightKey);
            consumedEmptyContainers.compute(containerKey, (k, v) -> (v == null ? 0 : v) + rightAmountReturned);
        }

        if (keyToEmptyContainer.containsKey(leftKey)) {
            String containerKey = keyToEmptyContainer.get(leftKey);
            producedEmptyContainers.compute(containerKey, (k, v) -> (v == null ? 0 : v) + leftAmount);
        }
        return rightAmountReturned;
    }

    public Map<String, Integer> getConsumedEmptyContainers() {
        return consumedEmptyContainers;
    }

    public Map<String, Integer> getProducedEmptyContainers() {
        return producedEmptyContainers;
    }

    public int collectRemainders(Map<String, List<CraftingGraphNode>> allNodes, String requestedKey,
            int requestedAmount) {
        int outputFluidAmount = conversionOutputs.get(requestedKey);
        int fluidAmount = requestedAmount * outputFluidAmount - this.fluidRemainder;
        for (Map.Entry<String, Integer> entry : this.conversionInputs.entrySet()) {
            String inputKey = entry.getKey();
            int inputFluidAmount = entry.getValue();
            for (CraftingGraphNode node : allNodes.get(inputKey)) {
                int neededItemRemainder = NEIServerUtils.divideCeil(fluidAmount, inputFluidAmount);
                int itemRemainder = node.getRemainder(inputKey);
                if (itemRemainder > 0) {
                    int satisfiedAmount = Math.min(neededItemRemainder, itemRemainder);
                    node.addToRemainders(inputKey, -satisfiedAmount);
                    fluidAmount -= satisfiedAmount * inputFluidAmount;
                    if (fluidAmount < 0) {
                        this.fluidRemainder = -fluidAmount;
                        return requestedAmount;
                    }
                }
            }
        }

        int unsatisfiedItemAmount = NEIServerUtils.divideCeil(fluidAmount, outputFluidAmount);
        this.fluidRemainder = unsatisfiedItemAmount * outputFluidAmount - fluidAmount;
        return requestedAmount - unsatisfiedItemAmount;
    }
}
