package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;

import codechicken.nei.ItemPanels;
import codechicken.nei.ItemStackAmount;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.RestartableTask;
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
            final List<BookmarkItem> initialItems = prepareInitialItems(math, getInventoryItems(guiContainer));
            boolean processed = false;
            boolean changed = false;

            StackInfo.pauseItemDamageSound(true);

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

                            craft = multiplier != entry.getValue();
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

            StackInfo.pauseItemDamageSound(false);

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
                final long amount = Math.max(0, item.amount - invStackSize * item.fluidCellAmount);
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

}
