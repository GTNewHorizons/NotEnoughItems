package codechicken.nei.recipe.widget;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import codechicken.nei.FavoriteRecipes;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.StackInfo;

public class RecipeGroup {

    // position and items of a stack at load time, the handler may change the stack later
    protected static class StackState {

        public final int relx;
        public final int rely;
        public final ItemStack[] items;

        public StackState(PositionedStack pStack) {
            this.relx = pStack.relx;
            this.rely = pStack.rely;
            this.items = pStack.items;
        }
    }

    // filtered permutations of a slot, favorites first, with their indices in PositionedStack.items
    protected static class FilteredSlot {

        public final List<ItemStack> items;
        public final int[] indices;
        public final int favoriteCount;

        public FilteredSlot(PositionedStack pStack) {
            this.items = pStack.getFilteredPermutations();
            this.indices = new int[this.items.size()];

            // filtering keeps the original instances, so an identity lookup finds the source index
            for (int i = 0; i < this.indices.length; i++) {
                this.indices[i] = indexOfInstance(pStack.items, this.items.get(i));
            }

            int favoriteCount = 0;

            while (this.items.size() > 1 && favoriteCount < this.items.size()
                    && FavoriteRecipes.containsManual(this.items.get(favoriteCount))) {
                favoriteCount++;
            }

            this.favoriteCount = favoriteCount;
        }

        private static int indexOfInstance(ItemStack[] items, ItemStack stack) {
            for (int i = 0; i < items.length; i++) {
                if (items[i] == stack) {
                    return i;
                }
            }
            return 0;
        }
    }

    protected static class MemberStacks {

        public final List<PositionedStack> inputs;
        public final List<PositionedStack> outputs;
        public final List<PositionedStack> auxiliaries;
        public final List<PositionedStack> cycling;
        public final List<StackState> states = new ArrayList<>();
        public final Map<Point, PositionedStack> slots = new HashMap<>();
        public final Map<Point, FilteredSlot> filtered = new HashMap<>();

        public MemberStacks(List<PositionedStack> inputs, List<PositionedStack> outputs,
                List<PositionedStack> auxiliaries) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.auxiliaries = auxiliaries;

            if (auxiliaries.isEmpty()) {
                this.cycling = inputs;
            } else {
                this.cycling = new ArrayList<>(inputs);
                this.cycling.addAll(auxiliaries);
            }

            for (PositionedStack pStack : this.cycling) {
                this.slots.putIfAbsent(new Point(pStack.relx, pStack.rely), pStack);
            }

            for (PositionedStack pStack : inputs) this.states.add(new StackState(pStack));
            for (PositionedStack pStack : outputs) this.states.add(new StackState(pStack));
            for (PositionedStack pStack : auxiliaries) this.states.add(new StackState(pStack));
        }
    }

    private static final List<Integer> SINGLE_MEMBER = Collections.singletonList(0);

    private final IRecipeHandler handler;
    private final List<Integer> recipeIndices;
    private final HandlerInfo handlerInfo;
    private final PermutationsCycler<Point> slotPermutations;

    private final MemberStacks[] memberStacks;
    private Map<Point, PositionedStack[]> slotStacks = null;
    private final Set<Point> sharedSlots = new HashSet<>();
    private List<Integer> allowedMembers = null;
    private Boolean cycling = null;

    public RecipeGroup(IRecipeHandler handler, List<Integer> recipeIndices) {
        this.handler = handler;
        this.recipeIndices = new ArrayList<>(recipeIndices);
        this.handlerInfo = GuiRecipeTab.getHandlerInfo(handler);
        this.memberStacks = new MemberStacks[this.recipeIndices.size()];
        this.slotPermutations = new PermutationsCycler<Point>(
                new HashMap<>(),
                new HashMap<>(),
                this::collectSlotPermutations).onInvalidate(() -> {
                    this.allowedMembers = null;
                    this.cycling = null;
                    this.slotStacks = null;

                    for (MemberStacks stacks : this.memberStacks) {
                        if (stacks != null) {
                            stacks.filtered.clear();
                        }
                    }

                    this.sharedSlots.clear();
                });
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

    public int indexOf(int recipeIndex) {
        return this.recipeIndices.indexOf(recipeIndex);
    }

    public List<ItemStack> getPermutations(Point slotKey) {
        return this.slotPermutations.get(slotKey);
    }

    public boolean hasPermutation(int member, Point slotKey, ItemStack stack) {
        this.slotPermutations.validate();

        if (stack == null) {
            return false;
        }

        final List<ItemStack> permutations = getFilteredPermutations(member, slotKey);
        return permutations != null && PermutationsCycler.indexOf(permutations, stack) != -1;
    }

    // the given member if it has the item in the slot, otherwise the next member that has it
    public int findMember(Point slotKey, ItemStack stack, int from) {

        for (int step = 0; step < size(); step++) {
            final int member = (from + step) % size();

            if (hasPermutation(member, slotKey, stack)) {
                return member;
            }
        }

        return -1;
    }

    public List<PositionedStack> getInputs(int member) {
        return getStacks(member).inputs;
    }

    public List<PositionedStack> getOutputs(int member) {
        return getStacks(member).outputs;
    }

    public List<PositionedStack> getCyclingStacks(int member) {
        return getStacks(member).cycling;
    }

    public Point getSlotKey(PositionedStack pStack) {
        return new Point(pStack.relx, pStack.rely);
    }

    public PositionedStack getSlot(int member, Point slotKey) {
        return getStacks(member).slots.get(slotKey);
    }

    protected MemberStacks getStacks(int member) {

        if (this.memberStacks[member] == null) {
            this.memberStacks[member] = loadStacks(member);
        }

        return this.memberStacks[member];
    }

    protected MemberStacks loadStacks(int member) {
        final int recipeIndex = getRecipeIndex(member);
        final List<PositionedStack> inputs = new ArrayList<>(this.handler.getIngredientStacks(recipeIndex));
        final PositionedStack pStackResult = this.handler.getResultStack(recipeIndex);
        final List<PositionedStack> others = new ArrayList<>(this.handler.getOtherStacks(recipeIndex));

        if (pStackResult != null) {
            return new MemberStacks(inputs, Collections.singletonList(pStackResult), others);
        }

        return new MemberStacks(inputs, others, Collections.emptyList());
    }

    // re-reads the member stacks from the handler, keeps the cached instances when nothing changed
    public void refresh(int member) {

        if (this.memberStacks[member] == null) {
            return;
        }

        final MemberStacks stacks = loadStacks(member);

        if (!isSameStacks(this.memberStacks[member], stacks)) {
            this.memberStacks[member] = stacks;
            this.slotPermutations.invalidate();
        }
    }

    // index into PositionedStack.items of the permutation to render, -1 when the slot is unknown
    public int getCycledPermutationIndex(int member, Point slotKey, int index) {
        this.slotPermutations.validate();

        final FilteredSlot filtered = getFilteredSlot(member, slotKey);

        if (filtered == null || filtered.indices.length == 0) {
            return -1;
        }

        final int size = filtered.favoriteCount > 0 ? filtered.favoriteCount : filtered.indices.length;
        return filtered.indices[index % size];
    }

    protected boolean isSameStacks(MemberStacks stacksA, MemberStacks stacksB) {

        if (stacksA.inputs.size() != stacksB.inputs.size() || stacksA.outputs.size() != stacksB.outputs.size()
                || stacksA.auxiliaries.size() != stacksB.auxiliaries.size()) {
            return false;
        }

        for (int i = 0; i < stacksA.states.size(); i++) {
            final StackState stateA = stacksA.states.get(i);
            final StackState stateB = stacksB.states.get(i);

            if (stateA.relx != stateB.relx || stateA.rely != stateB.rely || !isSameItems(stateA.items, stateB.items)) {
                return false;
            }
        }

        return true;
    }

    protected boolean isSameItems(ItemStack[] itemsA, ItemStack[] itemsB) {

        if (itemsA == itemsB) {
            return true;
        }

        if (itemsA.length != itemsB.length) {
            return false;
        }

        for (int i = 0; i < itemsA.length; i++) {
            if (!isSameStack(itemsA[i], itemsB[i])) {
                return false;
            }
        }

        return true;
    }

    // true when the group has something to cycle: several members or a slot with several permutations
    public boolean canCycle() {
        this.slotPermutations.validate();

        if (this.cycling == null) {
            this.cycling = size() > 1 || getStacks(0).slots.keySet().stream()
                    .anyMatch(slotKey -> getFilteredPermutations(0, slotKey).size() > 1);
        }

        return this.cycling;
    }

    public List<Integer> getAllowedMembers() {
        this.slotPermutations.validate();

        if (size() == 1) {
            return SINGLE_MEMBER;
        }

        if (this.allowedMembers == null && !FavoriteRecipes.hasManual()) {
            this.allowedMembers = new ArrayList<>(size());

            for (int member = 0; member < size(); member++) {
                this.allowedMembers.add(member);
            }

        } else if (this.allowedMembers == null) {
            final Set<Point> favoriteSlots = new HashSet<>();

            for (Point slotKey : getSlotStacks().keySet()) {
                // a shared slot gives every member the same misses, so it can't affect the filter
                if (!this.sharedSlots.contains(slotKey) && this.slotPermutations.getFavoriteCount(slotKey) > 0) {
                    favoriteSlots.add(slotKey);
                }
            }

            this.allowedMembers = filterMembers(favoriteSlots);
        }

        return this.allowedMembers;
    }

    protected Map<Point, PositionedStack[]> getSlotStacks() {

        if (this.slotStacks == null) {
            this.slotStacks = new LinkedHashMap<>();

            for (int member = 0; member < size(); member++) {
                for (PositionedStack pStack : getCyclingStacks(member)) {
                    final PositionedStack[] stacks = this.slotStacks
                            .computeIfAbsent(getSlotKey(pStack), key -> new PositionedStack[size()]);

                    if (stacks[member] == null) {
                        stacks[member] = pStack;
                    }
                }
            }

            for (Map.Entry<Point, PositionedStack[]> entry : this.slotStacks.entrySet()) {
                if (isSharedSlot(entry.getValue())) {
                    this.sharedSlots.add(entry.getKey());
                }
            }
        }

        return this.slotStacks;
    }

    // all members hold the same permutations in this slot
    protected boolean isSharedSlot(PositionedStack[] stacks) {
        ItemStack[] first = null;

        for (PositionedStack pStack : stacks) {
            if (pStack == null) {
                continue;
            }

            if (first == null) {
                first = pStack.items;
            } else if (!isSameItems(first, pStack.items)) {
                return false;
            }
        }

        return true;
    }

    protected boolean isSameStack(ItemStack stackA, ItemStack stackB) {
        return stackA == stackB || NEIServerUtils.areStacksSameType(stackA, stackB)
                && StackInfo.getAmount(stackA) == StackInfo.getAmount(stackB);
    }

    protected List<ItemStack> getFilteredPermutations(int member, Point slotKey) {
        final FilteredSlot filtered = getFilteredSlot(member, slotKey);
        return filtered != null ? filtered.items : null;
    }

    protected FilteredSlot getFilteredSlot(int member, Point slotKey) {
        final MemberStacks stacks = getStacks(member);
        final PositionedStack slot = stacks.slots.get(slotKey);

        if (slot == null) {
            return null;
        }

        return stacks.filtered.computeIfAbsent(slotKey, key -> new FilteredSlot(slot));
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
            final List<ItemStack> permutations = getFilteredPermutations(member, slotKey);

            if (permutations != null && permutations.stream().noneMatch(FavoriteRecipes::containsManual)) {
                misses++;
            }
        }

        return misses;
    }

    protected List<ItemStack> collectSlotPermutations(Point slotKey) {

        if (!getSlotStacks().containsKey(slotKey)) {
            return Collections.emptyList();
        }

        final Map<Object, ItemStack> items = new LinkedHashMap<>();
        final boolean shared = this.sharedSlots.contains(slotKey);

        for (int member = 0; member < size(); member++) {
            final List<ItemStack> permutations = getFilteredPermutations(member, slotKey);

            if (permutations != null) {
                for (ItemStack stack : permutations) {
                    if (stack != null) {
                        items.putIfAbsent(PermutationsCycler.getStackKey(stack), stack);
                    }
                }

                if (shared) {
                    break;
                }
            }
        }

        return new ArrayList<>(items.values());
    }

}
