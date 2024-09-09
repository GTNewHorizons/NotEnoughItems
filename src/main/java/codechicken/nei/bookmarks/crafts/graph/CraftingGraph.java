package codechicken.nei.bookmarks.crafts.graph;

import static codechicken.nei.recipe.StackInfo.getItemStackGUID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.NEIServerUtils;
import codechicken.nei.bookmarks.crafts.ItemStackWithMetadata;
import codechicken.nei.recipe.BookmarkRecipeId;
import codechicken.nei.recipe.StackInfo;

public class CraftingGraph {

    private final Map<String, CraftingGraphNode> nodes = new HashMap<>();
    private final Map<String, List<CraftingGraphNode>> allNodes = new HashMap<>();
    private final List<FluidConversionGraphNode> fluidConversionNodes = new ArrayList<>();

    private final Map<String, Integer> requestedItems = new HashMap<>();

    private final Map<String, ItemStack> itemStackMapping = new HashMap<>();

    // For showing numbers on top of stacks
    private final Map<Integer, ItemStack> calculatedItems = new LinkedHashMap<>();
    private final Map<Integer, ItemStack> calculatedRemainders = new LinkedHashMap<>();
    private final Map<Integer, Integer> calculatedCraftCounts = new LinkedHashMap<>();

    // For coloring stack backgrounds
    private final Set<Integer> inputSlots = new HashSet<>();
    private final Set<Integer> conflictingSlots = new HashSet<>();
    private final Set<Integer> outputSlots = new HashSet<>();

    // For the tooltip
    private final List<ItemStack> inputStacks = new ArrayList<>();
    private final List<ItemStack> outputStacks = new ArrayList<>();
    private final List<ItemStack> remainingStacks = new ArrayList<>();

    public void addNode(ItemStackWithMetadata output, CraftingGraphNode node) {
        String key = StackInfo.getItemStackGUID(output.getStack());
        if (!nodes.containsKey(key) || !(nodes.get(key) instanceof RecipeGraphNode)) {
            nodes.put(key, node);
            int requestedAmount = output.getMeta().requestedAmount;
            if (requestedAmount != 0) {
                requestedItems.put(key, requestedAmount);
            }
            if (node instanceof RecipeGraphNode recipeNode && requestedAmount > 0) {
                recipeNode.setChainOutputs(key, requestedAmount);
            }
        } else {
            conflictingSlots.add(output.getGridIdx());
        }
        if (node instanceof RecipeGraphNode || node instanceof FluidConversionGraphNode) {
            if (!allNodes.containsKey(key)) {
                allNodes.put(key, new ArrayList<>());
            }
            allNodes.get(key).add(node);
        }
    }

    public void runAll() {
        preProcess();
        for (Map.Entry<String, Integer> entry : requestedItems.entrySet()) {
            if (nodes.containsKey(entry.getKey())) {
                CraftingGraphNode node = nodes.get(entry.getKey());
                if (entry.getValue() < 0) {
                    node.addToRemainders(entry.getKey(), -entry.getValue());
                }
            }
        }
        for (Map.Entry<String, Integer> entry : requestedItems.entrySet()) {
            if (entry.getValue() > 0) {
                dfs(entry.getKey(), entry.getValue(), new HashSet<>());
            }
        }
        postProcess();
    }

    public void preProcess() {
        for (CraftingGraphNode node : nodes.values()) {
            if (node instanceof RecipeGraphNode recipeNode) {
                for (ItemStackWithMetadata stack : recipeNode.getPinnedInputs()) {
                    itemStackMapping.put(getItemStackGUID(stack.getStack()), stack.getStack());
                }
                for (ItemStackWithMetadata stack : recipeNode.getPinnedOutputs()) {
                    itemStackMapping.put(getItemStackGUID(stack.getStack()), stack.getStack());
                }
            }
        }

        // Add fake fluid conversion recipes
        // Identity LinkedHashSet is intentional
        Map<String, LinkedHashSet<ItemStackWithMetadata>> recipesWithFluidOutputs = new HashMap<>();
        Map<String, LinkedHashSet<ItemStackWithMetadata>> recipesWithFluidInputs = new HashMap<>();

        for (CraftingGraphNode node : nodes.values()) {
            if (node instanceof RecipeGraphNode recipeNode) {
                for (ItemStackWithMetadata pinnedOutput : recipeNode.getPinnedOutputs()) {
                    tryAddFluid(pinnedOutput, recipesWithFluidOutputs);
                }
            }
        }
        for (CraftingGraphNode node : nodes.values()) {
            if (node instanceof RecipeGraphNode recipeNode) {
                for (ItemStackWithMetadata pinnedInput : recipeNode.getPinnedInputs()) {
                    tryAddFluid(pinnedInput, recipesWithFluidInputs);
                }
            }
        }
        Set<String> commonFluids = new HashSet<>(recipesWithFluidInputs.keySet());
        commonFluids.retainAll(recipesWithFluidOutputs.keySet());
        for (String commonFluid : commonFluids) {
            // inversion of input <-> output is intentional
            LinkedHashSet<ItemStackWithMetadata> nodeOutputs = recipesWithFluidInputs.get(commonFluid);
            LinkedHashSet<ItemStackWithMetadata> nodeInputs = recipesWithFluidOutputs.get(commonFluid);
            FluidConversionGraphNode node = new FluidConversionGraphNode(nodeInputs, nodeOutputs);
            for (ItemStackWithMetadata output : nodeOutputs) {
                String outputKey = getItemStackGUID(output.getStack());
                if (!nodes.containsKey(outputKey)) {
                    nodes.put(outputKey, node);
                }
                if (!allNodes.containsKey(outputKey)) {
                    allNodes.put(outputKey, new ArrayList<>());
                }
                allNodes.get(outputKey).add(node);
            }
            fluidConversionNodes.add(node);
        }
    }

    private void tryAddFluid(ItemStackWithMetadata pinnedItem,
            Map<String, LinkedHashSet<ItemStackWithMetadata>> recipesWithFluids) {
        FluidStack fluidStack = StackInfo.getFluid(pinnedItem.getStack());
        if (fluidStack == null) {
            return;
        }
        String fluidKey = getFluidKey(fluidStack);
        if (!recipesWithFluids.containsKey(fluidKey)) {
            recipesWithFluids.put(fluidKey, new LinkedHashSet<>());
        }
        recipesWithFluids.get(fluidKey).add(pinnedItem);
        ItemStack fluidDisplayStack = StackInfo.getFluidDisplayStack(fluidStack);
        if (fluidDisplayStack != null) {
            itemStackMapping.put(getItemStackGUID(fluidDisplayStack), fluidDisplayStack);
        }
    }

    public int dfs(String requestedKey, int requestedAmount, HashSet<BookmarkRecipeId> history) {
        CraftingGraphNode node = this.nodes.get(requestedKey);
        if (node == null) {
            return 0;
        }

        // Handle item node
        if (node instanceof ItemGraphNode itemGraphNode) {
            itemGraphNode.addToRemainders(requestedKey, -requestedAmount);
            return requestedAmount;
        }

        // Handle fluid conversion
        if (node instanceof FluidConversionGraphNode fluidNode) {
            String leftKey = fluidNode.getInputKey();
            int leftAmountToRequest = fluidNode.calculateAmountToRequest(requestedKey, requestedAmount, leftKey);

            int returnedLeftAmount = dfs(leftKey, leftAmountToRequest, history);

            return fluidNode.processResults(requestedKey, requestedAmount, leftKey, returnedLeftAmount);
        }

        if (!(node instanceof RecipeGraphNode recipeNode)) {
            return 0;
        }

        int availableAmount = 0;

        // Collect remainders from all nodes first
        for (CraftingGraphNode passiveNode : allNodes.get(requestedKey)) {
            if (availableAmount >= requestedAmount) {
                break;
            }
            if (passiveNode instanceof FluidConversionGraphNode fluidNode) {
                availableAmount += fluidNode
                        .collectRemainders(allNodes, requestedKey, requestedAmount - availableAmount);
            } else {
                int remainder = passiveNode.getRemainder(requestedKey);
                if (remainder > 0) {
                    int min = Math.min(remainder, requestedAmount - availableAmount);
                    passiveNode.addToRemainders(requestedKey, -min);
                    availableAmount += min;
                }
            }
        }
        // Collect produced containers containers
        for (FluidConversionGraphNode fluidConversionGraphNode : fluidConversionNodes) {
            Map<String, Integer> producedEmptyContainers = fluidConversionGraphNode.getProducedEmptyContainers();
            Integer availableContainers = producedEmptyContainers.get(requestedKey);
            if (availableContainers != null) {
                int n = Math.min(availableContainers, requestedAmount - availableAmount);
                producedEmptyContainers.put(requestedKey, producedEmptyContainers.get(requestedKey) - n);
                availableAmount += n;
            }
        }
        int amountToRequest = requestedAmount - availableAmount;

        BookmarkRecipeId recipeId = recipeNode.getRecipeId();
        // Handle recursive recipes
        if (history.contains(recipeId)) {
            return availableAmount;
        }

        if (amountToRequest <= 0) {
            return requestedAmount;
        }

        // Calculate number of requested crafts
        int crafts = 0;
        for (Map.Entry<String, Integer> outputItemStack : recipeNode.getRecipeOutputs().entrySet()) {
            if (Objects.equals(outputItemStack.getKey(), requestedKey)) {
                crafts = NEIServerUtils.divideCeil(amountToRequest, outputItemStack.getValue());
                break;
            }
        }

        recipeNode.addToRemainders(requestedKey, -amountToRequest);
        for (Map.Entry<String, Integer> outputItemStack : recipeNode.getRecipeOutputs().entrySet()) {
            if (recipeNode.getPinnedOutputKeys().containsKey(outputItemStack.getKey())) {
                recipeNode.addToRemainders(outputItemStack.getKey(), outputItemStack.getValue() * crafts);
            }
        }

        recipeNode.addCrafts(crafts);
        final int finalCrafts = crafts;

        // Process inputs
        final Map<String, Integer> ingredientsToRequest = new LinkedHashMap<>();

        for (Map<String, Integer> ingredientCandidates : recipeNode.getRecipeIngredients()) {
            // Intersect current pinned inputs with recipe inputs
            Set<String> pinnedCurrentRecipeInputs = new HashSet<>(recipeNode.getPinnedInputKeys().keySet());
            pinnedCurrentRecipeInputs.retainAll(ingredientCandidates.keySet());
            // Intersect all pinned outputs with recipe inputs
            Set<String> pinnedRecipeOutputs = new HashSet<>(this.nodes.keySet());
            pinnedRecipeOutputs.retainAll(ingredientCandidates.keySet());

            // Nothing to do here
            if (pinnedCurrentRecipeInputs.isEmpty()) {
                continue;
            }

            String keyToRequest = pinnedCurrentRecipeInputs.iterator().next();
            if (this.nodes.containsKey(keyToRequest)) { // Try to request pinned item exactly or convert fluids;
                ingredientsToRequest.compute(
                        keyToRequest,
                        (k, v) -> (v == null ? 0 : v) + ingredientCandidates.get(keyToRequest) * finalCrafts);
            } else if (!pinnedRecipeOutputs.isEmpty()) { // Fallback to oredict
                String fallbackKey = pinnedRecipeOutputs.iterator().next();
                ingredientsToRequest.compute(
                        fallbackKey,
                        (k, v) -> (v == null ? 0 : v) + ingredientCandidates.get(fallbackKey) * finalCrafts);
            } else { // Otherwise add pinned item to crafting chain inputs
                String key = pinnedCurrentRecipeInputs.iterator().next();
                recipeNode.addToChainInputs(key, ingredientCandidates.get(key) * finalCrafts);
            }
        }

        for (Map.Entry<String, Integer> entry : ingredientsToRequest.entrySet()) {
            history.add(recipeId);
            int provided = dfs(entry.getKey(), entry.getValue(), history);
            int chainInputs = entry.getValue() - provided;
            recipeNode.addToChainInputs(entry.getKey(), chainInputs);
            history.remove(recipeId);
        }
        return requestedAmount;
    }

    public void postProcess() {
        this.calculatedItems.clear();
        this.calculatedCraftCounts.clear();
        this.calculatedRemainders.clear();

        this.inputStacks.clear();
        this.remainingStacks.clear();

        Map<String, Integer> remainders = new HashMap<>();
        Map<String, Integer> inputs = new HashMap<>();
        Map<String, Integer> outputs = new HashMap<>();

        Map<String, Integer> consumedEmptyContainers = new HashMap<>();
        Map<String, Integer> producedEmptyContainers = new HashMap<>();

        IdentityHashMap<CraftingGraphNode, Void> distinctNodes = new IdentityHashMap<>();
        for (CraftingGraphNode value : nodes.values()) {
            distinctNodes.put(value, null);
        }

        for (CraftingGraphNode node : distinctNodes.keySet()) {
            if (node instanceof FluidConversionGraphNode fluidNode) {
                for (Map.Entry<String, Integer> containerEntry : fluidNode.getConsumedEmptyContainers().entrySet()) {
                    consumedEmptyContainers.compute(
                            containerEntry.getKey(),
                            (k, v) -> (v == null ? 0 : v) + containerEntry.getValue());
                }
                for (Map.Entry<String, Integer> containerEntry : fluidNode.getProducedEmptyContainers().entrySet()) {
                    producedEmptyContainers.compute(
                            containerEntry.getKey(),
                            (k, v) -> (v == null ? 0 : v) + containerEntry.getValue());
                }
            }
        }

        for (CraftingGraphNode node : distinctNodes.keySet()) {
            if (node instanceof RecipeGraphNode recipeNode) {
                for (ItemStackWithMetadata pinnedOutput : recipeNode.getPinnedOutputs()) {
                    String key = getItemStackGUID(pinnedOutput.getStack());
                    int calculatedCount = recipeNode.getRecipeOutputs().getOrDefault(key, 0) * recipeNode.getCrafts();
                    // Remove empty containers from results
                    if (consumedEmptyContainers.containsKey(key)) {
                        int toRemove = Math.min(recipeNode.getRemainder(key), consumedEmptyContainers.get(key));
                        consumedEmptyContainers.put(key, consumedEmptyContainers.get(key) - toRemove);
                        recipeNode.addToRemainders(key, -toRemove);
                    }

                    this.calculatedItems
                            .put(pinnedOutput.getGridIdx(), withStackSize(pinnedOutput.getStack(), calculatedCount));
                    this.calculatedRemainders.put(
                            pinnedOutput.getGridIdx(),
                            withStackSize(
                                    pinnedOutput.getStack(),
                                    recipeNode.getRemainder(key) + recipeNode.getChainOutput(key)));
                    this.calculatedCraftCounts.put(pinnedOutput.getGridIdx(), recipeNode.getCrafts());
                }

                for (ItemStackWithMetadata pinnedInput : recipeNode.getPinnedInputs()) {
                    String key = getItemStackGUID(pinnedInput.getStack());
                    int calculatedCount = recipeNode.getRecipeIngredients().stream()
                            .flatMap(it -> it.entrySet().stream())
                            .filter(it -> Objects.requireNonNull(it.getKey()).equals(key))
                            .mapToInt(it -> it.getValue() * recipeNode.getCrafts()).sum();
                    // Remove empty containers from results
                    if (producedEmptyContainers.containsKey(key)) {
                        int toRemove = Math.min(recipeNode.getChainInput(key), producedEmptyContainers.get(key));
                        producedEmptyContainers.put(key, producedEmptyContainers.get(key) - toRemove);
                        recipeNode.addToChainInputs(key, -toRemove);
                    }

                    this.calculatedItems
                            .put(pinnedInput.getGridIdx(), withStackSize(pinnedInput.getStack(), calculatedCount));
                    this.calculatedRemainders.put(
                            pinnedInput.getGridIdx(),
                            withStackSize(pinnedInput.getStack(), recipeNode.getChainInput(key)));
                }

                for (Map.Entry<String, Integer> entry : recipeNode.getRemainders().entrySet()) {
                    int slotIdx = recipeNode.getPinnedOutputKeys().get(entry.getKey());
                    this.outputSlots.add(slotIdx);
                    remainders.compute(entry.getKey(), (k, v) -> (v == null ? 0 : v) + entry.getValue());
                }

                for (Map.Entry<String, Integer> entry : recipeNode.getChainOutputs().entrySet()) {
                    int slotIdx = recipeNode.getPinnedOutputKeys().get(entry.getKey());
                    this.outputSlots.add(slotIdx);
                    outputs.compute(entry.getKey(), (k, v) -> (v == null ? 0 : v) + entry.getValue());
                }

                for (Map.Entry<String, Integer> entry : recipeNode.getChainInputs().entrySet()) {
                    int slotIdx = recipeNode.getPinnedInputKeys().get(entry.getKey());
                    this.inputSlots.add(slotIdx);
                    inputs.compute(entry.getKey(), (k, v) -> (v == null ? 0 : v) + entry.getValue());
                }
            } else if (node instanceof ItemGraphNode itemGraphNode) {
                ItemStackWithMetadata pinnedItem = itemGraphNode.getPinnedItem();
                String key = getItemStackGUID(pinnedItem.getStack());
                int amount = -itemGraphNode.getRemainder(key);
                ItemStack stack = withStackSize(pinnedItem.getStack(), amount);
                this.calculatedItems.put(pinnedItem.getGridIdx(), stack);
                this.calculatedRemainders.put(pinnedItem.getGridIdx(), stack);
                this.calculatedCraftCounts.put(pinnedItem.getGridIdx(), 0);
                this.inputSlots.add(pinnedItem.getGridIdx());
                inputs.compute(key, (k, v) -> (v == null ? 0 : v) + amount);
            } else if (node instanceof FluidConversionGraphNode fluidNode) {
                Map.Entry<String, Integer> entry = fluidNode.getRemainders().entrySet().iterator().next();
                if (entry.getKey() != null) {
                    remainders.compute(entry.getKey(), (k, v) -> (v == null ? 0 : v) + entry.getValue());
                }

                for (Map.Entry<String, Integer> containerEntry : fluidNode.getConsumedEmptyContainers().entrySet()) {
                    if (remainders.containsKey(containerEntry.getKey())) {
                        remainders.compute(
                                containerEntry.getKey(),
                                (k, v) -> (v == null ? 0 : v) - containerEntry.getValue());
                    }
                }
                for (Map.Entry<String, Integer> containerEntry : fluidNode.getProducedEmptyContainers().entrySet()) {
                    if (inputs.containsKey(containerEntry.getKey())) {
                        inputs.compute(
                                containerEntry.getKey(),
                                (k, v) -> (v == null ? 0 : v) - containerEntry.getValue());
                    }
                }
            }
        }

        convertMapToStacks(inputs, this.inputStacks);
        convertMapToStacks(remainders, this.remainingStacks);
        convertMapToStacks(outputs, this.outputStacks);
    }

    private void convertMapToStacks(Map<String, Integer> map, List<ItemStack> stacks) {
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            ItemStack outputStack = this.itemStackMapping.get(entry.getKey());
            if (outputStack != null && entry.getValue() > 0) {
                stacks.add(withStackSize(outputStack, entry.getValue()));
            }
        }
    }

    private static String getFluidKey(FluidStack fluidStack) {
        NBTTagCompound nbTag = new NBTTagCompound();
        nbTag.setBoolean("fluidStack", true);
        nbTag.setString("gtFluidName", fluidStack.getFluid().getName());
        return nbTag.toString();
    }

    public Map<Integer, ItemStack> getCalculatedItems() {
        return calculatedItems;
    }

    public Map<Integer, ItemStack> getCalculatedRemainders() {
        return calculatedRemainders;
    }

    public Map<Integer, Integer> getCalculatedCraftCounts() {
        return calculatedCraftCounts;
    }

    public Set<Integer> getInputSlots() {
        return inputSlots;
    }

    public Set<Integer> getCraftedOutputSlots() {
        return outputSlots;
    }

    public Set<Integer> getConflictingSlots() {
        return conflictingSlots;
    }

    public List<ItemStack> getInputStacks() {
        return inputStacks;
    }

    public List<ItemStack> getOutputStacks() {
        return outputStacks;
    }

    public List<ItemStack> getRemainingStacks() {
        return remainingStacks;
    }

    private static ItemStack withStackSize(ItemStack itemStack, int stackSize) {
        NBTTagCompound tagCompound = StackInfo.itemStackToNBT(itemStack);
        return StackInfo.loadFromNBT(tagCompound, stackSize);
    }

    public static int getStackSize(ItemStack itemStack) {
        if (itemStack == null) {
            return 0;
        }
        return StackInfo.itemStackToNBT(itemStack).getInteger("Count");
    }
}
