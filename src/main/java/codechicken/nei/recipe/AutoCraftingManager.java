package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;

import codechicken.nei.FastTransferManager;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.NEIServerUtils;
import codechicken.nei.RestartableTask;
import codechicken.nei.ServerSync;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.chain.RecipeChainIterator;
import codechicken.nei.recipe.chain.RecipeChainMath;

public class AutoCraftingManager {

    private static RecipeChainMath math;

    private static final RestartableTask task = new RestartableTask("NEI Bookmark AutoCraft Processing") {

        @Override
        public void execute() {
            final GuiContainer guiContainer = NEIClientUtils.getGuiContainer();
            final ItemStack[] layout = snapshotLayout(guiContainer);
            final List<BookmarkItem> initialItems = prepareInitialItems(math, getInventoryItems(guiContainer));
            boolean processed = false;
            boolean changed = false;

            StackInfo.pauseItemDamageSound(true);

            try {
                do {
                    changed = false;

                    RecipeChainIterator iterator = new RecipeChainIterator(math, initialItems);
                    iterator.updateInventory(getInventoryItems(guiContainer));

                    while (iterator.hasNext() && !interrupted(guiContainer)) {
                        final Map<RecipeId, Long> recipes = iterator.next();
                        boolean craft = false;

                        for (Map.Entry<RecipeId, Long> entry : recipes.entrySet()) {
                            final RecipeHandlerRef handler = RecipeHandlerRef.of(entry.getKey());

                            if (handler != null && handler.canCraft(guiContainer)) {
                                long multiplier = entry.getValue();

                                while (multiplier > 0 && !interrupted(guiContainer)
                                        && handler.craft(guiContainer, (int) Math.min(64, multiplier))) {
                                    multiplier -= 64;
                                }

                                craft = craft || multiplier != entry.getValue();
                            }

                            if (interrupted(guiContainer)) break;
                        }

                        if (craft) {
                            changed = true;
                            processed = true;
                            iterator.updateInventory(getInventoryItems(guiContainer));
                        }
                    }

                } while (changed && !interrupted(guiContainer));

                if (!interrupted(guiContainer)) {
                    restoreLayout(guiContainer, layout);
                    ServerSync.request();
                }

            } finally {
                StackInfo.pauseItemDamageSound(false);
            }

            if (processed && !changed && !interrupted(guiContainer)) {
                NEIClientUtils.playClickSound();
            }

        }

        @Override
        public void clearTasks() {
            super.clearTasks();
            AutoCraftingManager.math = null;
        }

        private boolean interrupted(GuiContainer guiContainer) {
            return interrupted() || guiContainer != NEIClientUtils.getGuiContainer();
        }

        private List<BookmarkItem> prepareInitialItems(RecipeChainMath math, ItemStackAmount inventory) {
            final List<BookmarkItem> initialItems = new ArrayList<>();

            for (BookmarkItem item : math.initialItems) {
                final long invStackSize = inventory.getOrDefault(item.itemStack, 0L);
                final long amount = Math.max(0, item.getAmount() - invStackSize * item.fluidCellAmount);
                if (amount > 0) {
                    initialItems.add(item.copyWithAmount(amount));
                }
            }

            return initialItems;
        }

    };

    private AutoCraftingManager() {}

    public static void runProcessing(RecipeChainMath math) {
        task.stop();
        AutoCraftingManager.math = math;

        if (AutoCraftingManager.math != null) {
            ItemPanels.bookmarkPanel.clearTooltips();
            math.createMasterRoot();
            task.restart();
        }
    }

    public static boolean processing() {
        return AutoCraftingManager.math != null && !task.interrupted();
    }

    public static ItemStackAmount getInventoryItems(GuiContainer guiContainer) {
        final ItemStackAmount inventory = new ItemStackAmount();

        for (Slot slot : guiContainer.inventorySlots.inventorySlots) {
            if (slot.getHasStack() && !(slot instanceof SlotCrafting) && slot.canTakeStack(guiContainer.mc.thePlayer)) {
                inventory.add(slot.getStack());
            }
        }

        return inventory;
    }

    private static ItemStack[] snapshotLayout(GuiContainer guiContainer) {
        final List<Slot> slots = guiContainer.inventorySlots.inventorySlots;
        final ItemStack[] layout = new ItemStack[slots.size()];

        for (int index = 0; index < slots.size(); index++) {
            final Slot slot = slots.get(index);

            if (slot.getHasStack() && !(slot instanceof SlotCrafting)) {
                layout[index] = slot.getStack().copy();
            }
        }

        return layout;
    }

    private static void restoreLayout(GuiContainer guiContainer, ItemStack[] layout) {
        final List<Slot> slots = guiContainer.inventorySlots.inventorySlots;
        final EntityPlayer player = guiContainer.mc.thePlayer;

        for (int index = 0; index < slots.size() && player.inventory.getItemStack() == null
                && guiContainer.mc.currentScreen == guiContainer; index++) {
            final ItemStack expected = layout[index];
            final Slot target = slots.get(index);

            if (expected == null || inPlace(slots, layout, index)
                    || !target.isItemValid(expected)
                    || target.getHasStack() && !target.canTakeStack(player)) {
                continue;
            }

            final Slot source = findMisplaced(slots, layout, expected, player);
            if (source == null) continue;

            FastTransferManager.clickSlot(guiContainer, source.slotNumber);
            FastTransferManager.clickSlot(guiContainer, target.slotNumber);

            if (player.inventory.getItemStack() != null) {
                FastTransferManager.clickSlot(guiContainer, source.slotNumber);
            }
        }

        FastTransferManager.dropHeldItem(guiContainer);
    }

    private static boolean inPlace(List<Slot> slots, ItemStack[] layout, int index) {
        final ItemStack stack = slots.get(index).getStack();
        return stack != null && layout[index] != null
                && NEIServerUtils.areStacksSameTypeCraftingWithNBT(layout[index], stack);
    }

    private static Slot findMisplaced(List<Slot> slots, ItemStack[] layout, ItemStack expected, EntityPlayer player) {

        for (int index = 0; index < slots.size(); index++) {
            final Slot slot = slots.get(index);

            if (slot.getHasStack() && !(slot instanceof SlotCrafting)
                    && !inPlace(slots, layout, index)
                    && slot.canTakeStack(player)
                    && NEIServerUtils.areStacksSameTypeCraftingWithNBT(expected, slot.getStack())) {
                return slot;
            }
        }

        return null;
    }

}
