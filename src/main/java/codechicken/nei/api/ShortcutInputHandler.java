package codechicken.nei.api;

import java.awt.Point;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.input.Mouse;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.BookmarkContainerInfo;
import codechicken.nei.FavoriteRecipes;
import codechicken.nei.ItemPanels;
import codechicken.nei.ItemQuantityField;
import codechicken.nei.ItemsGrid.ItemsGridSlot;
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.SearchField;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkGroup;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.BookmarkItem.BookmarkItemType;
import codechicken.nei.bookmark.BookmarksGridSlot;
import codechicken.nei.recipe.AutoCraftingManager;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiFavoriteButton;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.chain.RecipeChainMath;
import codechicken.nei.util.NEIMouseUtils;

public abstract class ShortcutInputHandler {

    private static class HotkeysCache {

        public int mousex;
        public int mousey;
        public ItemStack stack;
        public int groupId;
        public Map<String, String> hotkeys = new HashMap<>();

        public boolean matches(int mousex, int mousey, ItemStack stack, int groupId) {
            return Math.abs(this.mousex - mousex) < 16 && Math.abs(this.mousey - mousey) < 16
                    && (this.stack == null ? stack == null && this.groupId == groupId
                            : stack != null && NEIClientUtils.areStacksSameTypeWithNBT(stack, this.stack));
        }

        public void update(int mousex, int mousey, ItemStack stack, int groupId, Map<String, String> hotkeys) {
            this.mousex = mousex;
            this.mousey = mousey;
            this.stack = stack != null ? stack.copy() : null;
            this.groupId = groupId;
            this.hotkeys = hotkeys;
        }
    }

    private static HotkeysCache hotkeysCache = new HotkeysCache();

    public static boolean handleKeyEvent(ItemStack stackover) {

        if (!NEIClientConfig.isLoaded()) {
            return false;
        }

        if (NEIClientConfig.isKeyHashDown("gui.overlay") && stackover == null
                && LayoutManager.overlayRenderer != null) {
            LayoutManager.overlayRenderer = null;
            return true;
        }

        if (stackover == null) {
            final int groupId = ItemPanels.bookmarkPanel.getHoveredGroupId(true);

            if (groupId != -1) {

                if (NEIClientConfig.isKeyHashDown("gui.remove_recipe")) {
                    ItemPanels.bookmarkPanel.removeGroup(groupId);
                    return true;
                }

                if (NEIClientConfig.isKeyHashDown("gui.bookmark_pull_items")) {
                    return ItemPanels.bookmarkPanel.pullBookmarkItems(groupId, NEIClientUtils.shiftKey());
                }

                if (NEIClientConfig.autocraftingEnabled() && NEIClientConfig.isKeyHashDown("gui.craft_items")
                        && NEIClientUtils.shiftKey()
                        && ItemPanels.bookmarkPanel.getGrid().isCraftingMode(groupId)) {
                    final RecipeChainMath math = ItemPanels.bookmarkPanel.getGrid().createRecipeChainMath(groupId);

                    if (math != null) {

                        if (!NEIClientUtils.controlKey()) {
                            autocraftingIgnoreInventory(math);
                        }

                        AutoCraftingManager.runProcessing(math);
                        return true;
                    }
                }

            }

            return false;
        }

        stackover = stackover.copy();

        if (NEIClientConfig.isKeyHashDown("gui.overlay")) {
            return openOverlayRecipe(stackover);
        }

        if (NEIClientConfig.isKeyHashDown("gui.copy_name")) {
            return copyItemStackName(stackover);
        }
        if (NEIClientConfig.isKeyHashDown("gui.copy_id")) {
            return copyItemStackID(stackover);
        }

        if (NEIClientConfig.isKeyHashDown("gui.copy_oredict")) {
            return copyItemStackOreDictionary(stackover);
        }

        if (NEIClientConfig.isKeyHashDown("gui.chat_link_item")) {
            return sendItemStackChatLink(stackover);
        }

        if (NEIClientConfig.isKeyHashDown("gui.recipe")) {
            return GuiCraftingRecipe.openRecipeGui("item", stackover);
        }

        if (NEIClientConfig.isKeyHashDown("gui.usage")) {
            return GuiUsageRecipe.openRecipeGui("item", stackover);
        }

        if (NEIClientConfig.isKeyHashDown("gui.favorite")) {
            return saveFavoriteTree(stackover);
        }

        if (NEIClientConfig.isKeyHashDown("gui.bookmark")) {
            return saveRecipeInBookmark(stackover, NEIClientUtils.shiftKey(), NEIClientUtils.controlKey());
        }

        if (NEIClientConfig.isKeyHashDown("gui.bookmark_pull_items")) {
            return pullRecipeItems(stackover, NEIClientUtils.shiftKey());
        }

        if (NEIClientConfig.autocraftingEnabled() && NEIClientConfig.isKeyHashDown("gui.craft_items")
                && NEIClientUtils.shiftKey()) {
            return runAutoCrafting(stackover, !NEIClientUtils.controlKey());
        }

        return false;
    }

    public static boolean handleMouseClick(ItemStack stackover) {

        if (!NEIClientConfig.isLoaded()) {
            return false;
        }

        if (stackover != null) {
            final int button = Mouse.getEventButton();

            if (button == 0) {
                return GuiCraftingRecipe.openRecipeGui("item", stackover.copy());
            } else if (button == 1) {
                return GuiUsageRecipe.openRecipeGui("item", stackover.copy());
            }
        }

        return false;
    }

    private static boolean copyItemStackName(ItemStack stackover) {
        GuiScreen.setClipboardString(SearchField.getEscapedSearchText(stackover));
        return true;
    }

    private static boolean copyItemStackID(ItemStack stackover) {
        if (stackover == null || stackover.getItem() == null) return false;
        GuiScreen.setClipboardString(
                stackover.getItem().delegate.name()
                        + (stackover.getItemDamage() != 0 ? "/" + stackover.getItemDamage() : ""));
        return true;
    }

    private static boolean sendItemStackChatLink(ItemStack stackover) {
        if (stackover == null) return false;

        NEIClientUtils.sendChatItemLink(stackover); // I wish clients could just send formatted messages
        return true;
    }

    private static boolean copyItemStackOreDictionary(ItemStack stackover) {
        StringBuilder builder = new StringBuilder();

        for (int id : OreDictionary.getOreIDs(stackover)) {
            String oreDictionaryName = OreDictionary.getOreName(id);
            if (!"Unknown".equals(oreDictionaryName)) {
                builder.append(oreDictionaryName).append(",");
            }
        }

        if (builder.length() > 0) {
            builder.deleteCharAt(builder.length() - 1);
        }

        GuiScreen.setClipboardString(builder.toString());
        return true;
    }

    private static boolean runAutoCrafting(ItemStack stackover, boolean ignoreInventory) {
        final RecipeId recipeId = GuiCraftingRecipe.getRecipeId(NEIClientUtils.getGuiContainer(), stackover);

        if (recipeId != null) {
            final Point mouseover = GuiDraw.getMousePosition();
            final BookmarksGridSlot slot = ItemPanels.bookmarkPanel.getSlotMouseOver(mouseover.x, mouseover.y);
            RecipeChainMath math = null;

            if (slot == null) {
                final Recipe recipe = Recipe.of(recipeId);
                if (recipe != null) {
                    math = RecipeChainMath.of(recipe, 1);
                }
            } else if (slot.getRecipeId() != null && slot.getType() == BookmarkItemType.RESULT) {

                if (slot.getGroup().crafting == null) {
                    final Recipe recipe = Recipe.of(slot.getRecipeId());
                    if (recipe != null) {
                        math = RecipeChainMath.of(recipe, slot != null ? slot.getMultiplier() : 1);
                    }
                } else {
                    math = ItemPanels.bookmarkPanel.getGrid()
                            .createRecipeChainMath(slot.getGroupId(), slot.getRecipeId());
                }

            }

            if (math != null) {

                if (ignoreInventory) {
                    autocraftingIgnoreInventory(math);
                }

                AutoCraftingManager.runProcessing(math);
                return true;
            }
        }

        return false;
    }

    private static boolean openOverlayRecipe(ItemStack stackover) {
        final GuiContainer gui = NEIClientUtils.getGuiContainer();
        final RecipeId recipeId = GuiCraftingRecipe.getRecipeId(gui, stackover);

        if (recipeId != null) {
            final RecipeHandlerRef handlerRef = RecipeHandlerRef.of(recipeId);

            if (handlerRef != null) {

                if (NEIClientUtils.shiftKey() && handlerRef.canFillCraftingGrid(gui)) {
                    int multiplier = 0;

                    if (NEIClientUtils.controlKey()) {
                        final Point mouseover = GuiDraw.getMousePosition();
                        final BookmarksGridSlot slot = ItemPanels.bookmarkPanel
                                .getSlotMouseOver(mouseover.x, mouseover.y);
                        multiplier = slot != null ? (int) Math.min(64, slot.getMultiplier()) : 1;
                    }

                    handlerRef.fillCraftingGrid(gui, multiplier);
                } else if (!NEIClientUtils.shiftKey() && handlerRef.canUseOverlayRenderer(gui)) {
                    handlerRef.useOverlayRenderer(gui);
                }
            }
        }

        return false;
    }

    private static boolean saveFavoriteTree(ItemStack stackover) {
        final Point mouse = GuiDraw.getMousePosition();

        if (ItemPanels.itemPanel.contains(mouse.x, mouse.y)
                || ItemPanels.itemPanel.historyPanel.contains(mouse.x, mouse.y)) {
            final RecipeHandlerRef handlerRef = RecipeHandlerRef.of(FavoriteRecipes.getFavorite(stackover));

            if (handlerRef != null) {
                final GuiFavoriteButton button = new GuiFavoriteButton(handlerRef, 0, 0);
                button.saveRecipeInBookmark();
                return true;
            }

            return true;
        }

        return false;
    }

    private static boolean saveRecipeInBookmark(ItemStack stackover, boolean saveIngredients, boolean saveStackSize) {
        final Point mousePos = GuiDraw.getMousePosition();

        // If no slot was removed from the bookmark panel on click:
        if (!ItemPanels.bookmarkPanel.removeSlot(mousePos.x, mousePos.y, saveIngredients)) {
            final Recipe recipe = getFocusedRecipe(stackover, mousePos.x, mousePos.y, saveIngredients);

            // --- Case 1: saving a recipe (with ingredients) ---
            if (recipe != null && saveIngredients) {

                // If the recipe is not already bookmarked, add it
                if (!ItemPanels.bookmarkPanel.removeRecipe(recipe.getRecipeId(), BookmarkGrid.DEFAULT_GROUP_ID)) {
                    final Recipe singleRecipe = Recipe.of(
                            Arrays.asList(recipe.getResult(stackover)),
                            recipe.getHandlerName(),
                            recipe.getIngredients());
                    singleRecipe.setCustomRecipeId(recipe.getRecipeId());

                    ItemPanels.bookmarkPanel.addRecipe(
                            singleRecipe,
                            // Determine if stack size should be saved
                            saveStackSize
                                    || NEIClientConfig.getBooleanSetting("inventory.bookmarks.bookmarkRecipeWithCount")
                                            ? 1
                                            : 0,
                            BookmarkGrid.DEFAULT_GROUP_ID);
                }

                // --- Case 2: saving an item only ---
            } else {
                final RecipeId recipeId = recipe != null ? recipe.getRecipeId() : null;

                // Determine if the item is associated with a recipe and should be saved with it
                final boolean existsRecipe = recipeId != null
                        && ItemPanels.bookmarkPanel.existsRecipe(recipeId, BookmarkGrid.DEFAULT_GROUP_ID)
                        || NEIClientConfig.getBooleanSetting("inventory.bookmarks.bookmarkItemsWithRecipe");

                // If the item was not removed from the bookmarks, add it
                if (!ItemPanels.bookmarkPanel
                        .removeItem(stackover, existsRecipe ? recipeId : null, BookmarkGrid.DEFAULT_GROUP_ID)) {

                    // Determine whether to preserve the item count
                    saveStackSize = saveStackSize || saveIngredients
                            || ItemPanels.bookmarkPanel.existsRecipe(recipeId, BookmarkGrid.DEFAULT_GROUP_ID);

                    if (!saveStackSize) {
                        stackover = StackInfo.withAmount(stackover, 0);
                    } else if (ItemPanels.itemPanel.containsWithSubpanels(mousePos.x, mousePos.y)) {
                        stackover = ItemQuantityField.prepareStackWithQuantity(stackover, 0);
                    }

                    ItemPanels.bookmarkPanel
                            .addItem(stackover, existsRecipe ? recipeId : null, BookmarkGrid.DEFAULT_GROUP_ID);
                }

            }
        }

        return true;
    }

    private static Recipe getFocusedRecipe(ItemStack stackover, int mousex, int mousey, boolean useFavorites) {
        final GuiContainer gui = NEIClientUtils.getGuiContainer();
        Recipe recipe = null;

        if (useFavorites) {
            ItemsGridSlot itemSlot = ItemPanels.itemPanel.getSlotMouseOver(mousex, mousey);

            if (itemSlot == null) {
                itemSlot = ItemPanels.itemPanel.historyPanel.getSlotMouseOver(mousex, mousey);
            }

            if (itemSlot == null) {
                itemSlot = ItemPanels.itemPanel.craftablesPanel.getSlotMouseOver(mousex, mousey);
            }

            if (itemSlot != null) {
                final RecipeId recipeId = itemSlot.getRecipeId();

                if (recipeId != null && (recipe = Recipe.of(recipeId)) != null) {

                    recipe.getIngredients().stream().forEach(ingr -> {
                        final List<ItemStack> permutations = ingr.getPermutations();
                        for (int index = 0; index < permutations.size(); index++) {
                            if (FavoriteRecipes.contains(permutations.get(index))) {
                                ingr.setActiveIndex(index);
                                break;
                            }
                        }
                    });

                }

            }
        }

        if (gui instanceof GuiRecipe guiRecipe) {
            recipe = guiRecipe.getFocusedRecipe();
        }

        return recipe;
    }

    private static boolean pullRecipeItems(ItemStack stackover, boolean shift) {
        final Point mousePos = GuiDraw.getMousePosition();

        if (ItemPanels.bookmarkPanel.contains(mousePos.x, mousePos.y)) {
            stackover = ItemPanels.bookmarkPanel.getStackMouseOverWithQuantity(mousePos.x, mousePos.y);
        } else if (ItemPanels.itemPanel.contains(mousePos.x, mousePos.y)) {
            stackover = ItemPanels.itemPanel.getStackMouseOverWithQuantity(mousePos.x, mousePos.y);
        } else if (ItemPanels.itemPanel.historyPanel.contains(mousePos.x, mousePos.y)) {
            stackover = ItemPanels.itemPanel.historyPanel.getStackMouseOverWithQuantity(mousePos.x, mousePos.y);
        } else {
            stackover = ItemQuantityField.prepareStackWithQuantity(stackover, 0);
        }

        final RecipeChainMath math = RecipeChainMath
                .of(Arrays.asList(BookmarkItem.of(-3, stackover)), Collections.emptySet());
        return ItemPanels.bookmarkPanel.pullBookmarkItems(math, shift);
    }

    public static Map<String, String> handleHotkeys(int mousex, int mousey, ItemStack stack) {
        final int groupId = ItemPanels.bookmarkPanel.getHoveredGroupId(true);

        if (hotkeysCache.matches(mousex, mousey, stack, groupId)) {
            return hotkeysCache.hotkeys;
        }

        final GuiContainer gui = NEIClientUtils.getGuiContainer();
        final Map<String, String> hotkeys = new HashMap<>();

        if (groupId != -1) {

            hotkeys.put(
                    NEIClientConfig.getKeyName("gui.remove_recipe"),
                    NEIClientUtils.translate("bookmark.group.remove_recipe"));

            if (BookmarkContainerInfo.getBookmarkContainerHandler(gui) != null) {
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.bookmark_pull_items"),
                        NEIClientUtils.translate("bookmark.group.pull_items"));
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.bookmark_pull_items", NEIClientUtils.SHIFT_HASH),
                        NEIClientUtils.translate("bookmark.group.pull_items_shift"));
            }

            if (NEIClientConfig.autocraftingEnabled() && ItemPanels.bookmarkPanel.getGrid().isCraftingMode(groupId)) {
                hotkeys.put(
                        NEIClientConfig
                                .getKeyName("gui.craft_items", NEIClientUtils.SHIFT_HASH + NEIClientUtils.CTRL_HASH),
                        NEIClientUtils.translate("bookmark.group.craft_missing"));
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.craft_items", NEIClientUtils.SHIFT_HASH),
                        NEIClientUtils.translate("bookmark.group.craft_all"));
            }

            hotkeysCache.update(mousex, mousey, stack, groupId, hotkeys);
            return hotkeys;
        }

        if (stack == null) {
            hotkeysCache.update(mousex, mousey, stack, groupId, hotkeys);
            return hotkeys;
        }

        final BookmarksGridSlot slot = ItemPanels.bookmarkPanel.getSlotMouseOver(mousex, mousey);
        final RecipeId recipeId = getHotkeyRecipeId(gui, mousex, mousey, stack, slot);

        if (slot != null) {
            final BookmarkGroup group = slot.getGroup();

            if (group.crafting != null && group.collapsed) {
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.bookmark", NEIClientUtils.SHIFT_HASH),
                        NEIClientUtils.translate("bookmark.group.remove_recipe"));
            } else if (slot.getRecipeId() == null || slot.getType() == BookmarkItemType.ITEM) {
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.bookmark"),
                        NEIClientUtils.translate("bookmark.remove_item"));
            } else if (group.crafting != null && group.crafting.recipeRelations.entrySet().stream().anyMatch(
                    entry -> entry.getKey().equals(slot.getRecipeId())
                            || entry.getValue().contains(slot.getRecipeId()))) {
                                hotkeys.put(
                                        NEIClientConfig.getKeyName("gui.bookmark", NEIClientUtils.SHIFT_HASH),
                                        NEIClientUtils.translate("bookmark.remove_recipe"));
                            } else {
                                hotkeys.put(
                                        NEIClientConfig.getKeyName("gui.bookmark", NEIClientUtils.SHIFT_HASH),
                                        NEIClientUtils.translate("bookmark.remove_recipe"));

                                if (slot.getType() == BookmarkItemType.INGREDIENT) {
                                    hotkeys.put(
                                            NEIClientConfig.getKeyName("gui.bookmark"),
                                            NEIClientUtils.translate("bookmark.remove_item"));

                                    hotkeys.put(
                                            NEIClientConfig.getKeyName("gui.bookmark", NEIClientUtils.SHIFT_HASH),
                                            NEIClientUtils.translate("bookmark.remove_recipe"));
                                } else {
                                    final BookmarkItem item = slot.getBookmarkItem();

                                    if (ItemPanels.bookmarkPanel.getGrid().createChainItems(groupId).values().stream()
                                            .noneMatch(
                                                    m -> m.type == BookmarkItemType.RESULT && !m.equals(item)
                                                            && item.equalsRecipe(m))) {
                                        hotkeys.put(
                                                NEIClientConfig.getKeyName("gui.bookmark"),
                                                NEIClientUtils.translate("bookmark.remove_recipe"));
                                    } else {
                                        hotkeys.put(
                                                NEIClientConfig.getKeyName("gui.bookmark"),
                                                NEIClientUtils.translate("bookmark.remove_item"));
                                        hotkeys.put(
                                                NEIClientConfig.getKeyName("gui.bookmark", NEIClientUtils.SHIFT_HASH),
                                                NEIClientUtils.translate("bookmark.remove_recipe"));
                                    }

                                }

                            }

            if (BookmarkContainerInfo.getBookmarkContainerHandler(gui) != null) {
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.bookmark_pull_items"),
                        NEIClientUtils.translate("bookmark.group.pull_items"));
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.bookmark_pull_items", NEIClientUtils.SHIFT_HASH),
                        NEIClientUtils.translate("bookmark.group.pull_items_shift"));
            }

        } else {
            hotkeys.put(NEIClientConfig.getKeyName("gui.bookmark"), NEIClientUtils.translate("bookmark.add_item"));

            if (StackInfo.getAmount(stack) > 0) {
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.bookmark", NEIClientUtils.CTRL_HASH),
                        NEIClientUtils.translate("bookmark.add_item_with_count"));
            }

            if (recipeId != null) {
                hotkeys.put(
                        NEIClientConfig
                                .getKeyName("gui.bookmark", NEIClientUtils.SHIFT_HASH + NEIClientUtils.CTRL_HASH),
                        NEIClientUtils.translate("bookmark.add_item_with_recipe_and_count"));
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.bookmark", NEIClientUtils.SHIFT_HASH),
                        NEIClientUtils.translate("bookmark.add_item_with_recipe"));
            }

            if ((ItemPanels.itemPanel.contains(mousex, mousey)
                    || ItemPanels.itemPanel.historyPanel.contains(mousex, mousey)) && FavoriteRecipes.contains(stack)) {
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.favorite"),
                        NEIClientUtils.translate("recipe.favorite.bookmark_recipe"));
            }

        }

        hotkeys.put(NEIClientConfig.getKeyName("gui.recipe"), NEIClientUtils.translate("itempanel.open_crafting"));
        hotkeys.put(NEIClientConfig.getKeyName("gui.usage"), NEIClientUtils.translate("itempanel.open_usage"));

        hotkeys.put(NEIClientConfig.getKeyName("gui.copy_name"), NEIClientUtils.translate("itempanel.copy_name"));
        hotkeys.put(NEIClientConfig.getKeyName("gui.copy_oredict"), NEIClientUtils.translate("itempanel.copy_oredict"));
        hotkeys.put(NEIClientConfig.getKeyName("gui.copy_id"), NEIClientUtils.translate("itempanel.copy_id"));
        hotkeys.put(
                NEIClientConfig.getKeyName("gui.chat_link_item"),
                NEIClientUtils.translate("itempanel.chat_link_item"));

        if (!(gui instanceof GuiRecipe) && NEIClientConfig.canCheatItem(stack)) {
            hotkeys.put(
                    NEIClientUtils.getKeyName(NEIClientUtils.SHIFT_HASH, NEIMouseUtils.MOUSE_BTN_LMB),
                    NEIClientUtils.translate("itempanel.open_crafting"));
            hotkeys.put(
                    NEIClientUtils.getKeyName(NEIClientUtils.SHIFT_HASH, NEIMouseUtils.MOUSE_BTN_RMB),
                    NEIClientUtils.translate("itempanel.open_usage"));
        } else {
            hotkeys.put(
                    NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_LMB),
                    NEIClientUtils.translate("itempanel.open_crafting"));
            hotkeys.put(
                    NEIMouseUtils.getKeyName(NEIMouseUtils.MOUSE_BTN_RMB),
                    NEIClientUtils.translate("itempanel.open_usage"));
        }

        if (recipeId != null) {
            final RecipeHandlerRef handlerRef = RecipeHandlerRef.of(recipeId);

            if (handlerRef != null) {

                if (handlerRef.canUseOverlayRenderer(gui)) {
                    hotkeys.put(
                            NEIClientConfig.getKeyName("gui.overlay"),
                            NEIClientUtils.translate("itempanel.overlay_recipe"));
                }

                if (handlerRef.canFillCraftingGrid(gui)) {
                    hotkeys.put(
                            NEIClientConfig.getKeyName("gui.overlay", NEIClientUtils.SHIFT_HASH),
                            NEIClientUtils.translate("itempanel.fill_crafting_grid"));

                    if (handlerRef.allowedTransferAlghoritm(gui)) {
                        hotkeys.put(
                                NEIClientConfig.getKeyName(
                                        "gui.overlay",
                                        NEIClientUtils.SHIFT_HASH + NEIClientUtils.CTRL_HASH),
                                NEIClientUtils.translate("itempanel.fill_crafting_grid_quantity"));
                    }
                }

                if (NEIClientConfig.autocraftingEnabled()) {
                    hotkeys.put(
                            NEIClientConfig.getKeyName(
                                    "gui.craft_items",
                                    NEIClientUtils.SHIFT_HASH + NEIClientUtils.CTRL_HASH),
                            NEIClientUtils.translate("itempanel.craft_missing"));
                    hotkeys.put(
                            NEIClientConfig.getKeyName("gui.craft_items", NEIClientUtils.SHIFT_HASH),
                            NEIClientUtils.translate("itempanel.craft_all"));
                }
            }

        }

        hotkeysCache.update(mousex, mousey, stack, groupId, hotkeys);

        return hotkeys;
    }

    private static void autocraftingIgnoreInventory(RecipeChainMath math) {
        final GuiContainer guiContainer = NEIClientUtils.getGuiContainer();
        final List<ItemStack> playerInventory = AutoCraftingManager.getInventoryItems(guiContainer).values();
        final RecipeId rootRecipeId = math.createMasterRoot();

        for (BookmarkItem item : math.recipeIngredients) {
            if (item.amount > 0 && rootRecipeId.equals(item.recipeId)) {
                long amount = 0;

                for (ItemStack stack : playerInventory) {
                    if (NEIClientUtils.areStacksSameTypeCraftingWithNBT(stack, item.itemStack)) {
                        amount += StackInfo.getAmount(stack);
                    }
                }

                if (amount >= item.amount) {
                    item.factor = item.amount = amount + item.amount - (amount % item.amount);
                }
            }
        }
    }

    private static RecipeId getHotkeyRecipeId(GuiContainer gui, int mousex, int mousey, ItemStack stack,
            BookmarksGridSlot slot) {

        if (slot != null) {
            return slot.getType() == BookmarkItemType.INGREDIENT ? null : slot.getRecipeId();
        }

        ItemsGridSlot itemSlot = ItemPanels.itemPanel.getSlotMouseOver(mousex, mousey);

        if (itemSlot == null) {
            itemSlot = ItemPanels.itemPanel.historyPanel.getSlotMouseOver(mousex, mousey);
        }

        if (itemSlot == null) {
            itemSlot = ItemPanels.itemPanel.craftablesPanel.getSlotMouseOver(mousex, mousey);
        }

        if (itemSlot != null) {
            return itemSlot.getRecipeId();
        }

        if (gui instanceof GuiRecipe guiRecipe) {
            final Recipe focusedRecipe = guiRecipe.getFocusedRecipe();

            if (focusedRecipe != null) {
                return focusedRecipe.getRecipeId();
            }
        }

        return null;
    }

}
