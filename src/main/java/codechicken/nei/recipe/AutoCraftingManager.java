package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

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

        private static final ItemStack ROOT_ITEM = new ItemStack(Blocks.fire);
        private static final RecipeId ROOT_RECIPE_ID = RecipeId
                .of(ROOT_ITEM, "recipe-autocrafting", Collections.emptyList());

        @Override
        public void execute() {
            final GuiContainer guiContainer = NEIClientUtils.getGuiContainer();
            final InventoryPlayer playerInventory = guiContainer.mc.thePlayer.inventory;
            final ItemStackAmount inventory = ItemStackAmount.of(Arrays.asList(playerInventory.mainInventory));
            final RecipeChainMath math = createMasterRoot(AutoCraftingManager.math);
            final List<BookmarkItem> initialItems = prepareInitialItems(math, inventory);
            boolean processed = false;
            boolean changed = false;

            do {
                changed = false;

                RecipeChainIterator iterator = new RecipeChainIterator(math, initialItems);
                iterator.updateInventory(playerInventory.mainInventory);

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
                        iterator.updateInventory(playerInventory.mainInventory);
                    }
                }

            } while (changed && !interrupted(guiContainer));

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

        private RecipeChainMath createMasterRoot(RecipeChainMath math) {
            final List<BookmarkItem> rootIngredients = new ArrayList<>();

            for (BookmarkItem item : math.recipeResults) {
                if (math.outputRecipes.containsKey(item.recipeId)) {
                    long amount = item.factor * math.outputRecipes.get(item.recipeId);
                    rootIngredients.add(
                            BookmarkItem.of(
                                    -1,
                                    item.getItemStack(amount),
                                    item.getStackSize(amount),
                                    ROOT_RECIPE_ID,
                                    true,
                                    BookmarkItem.generatePermutations(item.itemStack, null)));
                }
            }

            math.outputRecipes.clear();
            math.outputRecipes.put(ROOT_RECIPE_ID, 1L);
            math.recipeResults.add(BookmarkItem.of(-1, ROOT_ITEM, 1, ROOT_RECIPE_ID, false));
            math.recipeIngredients.addAll(rootIngredients);

            return math;
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
            task.restart();
        }
    }

    public static boolean processing() {
        return AutoCraftingManager.math != null && !task.interrupted();
    }

}
