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
import codechicken.nei.LayoutManager;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.NEIClientUtils;
import codechicken.nei.SearchField;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkItem;
import codechicken.nei.bookmark.BookmarksGridSlot;
import codechicken.nei.recipe.AutoCraftingManager;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import codechicken.nei.recipe.Recipe;
import codechicken.nei.recipe.Recipe.RecipeId;
import codechicken.nei.recipe.RecipeHandlerRef;
import codechicken.nei.recipe.StackInfo;
import codechicken.nei.recipe.chain.RecipeChainMath;
import codechicken.nei.util.NEIMouseUtils;

public abstract class ShortcutInputHandler {

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

                if (NEIClientConfig.autocraftingEnabled() && ItemPanels.bookmarkPanel.getGrid().isCraftingMode(groupId)
                        && NEIClientConfig.isKeyHashDown("gui.craft")) {
                    AutoCraftingManager
                            .runProcessing(ItemPanels.bookmarkPanel.getGrid().createRecipeChainMath(groupId));
                    return true;
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

        if (NEIClientConfig.isKeyHashDown("gui.copy_oredict")) {
            return copyItemStackOreDictionary(stackover);
        }

        if (NEIClientConfig.isKeyHashDown("gui.recipe")) {
            return GuiCraftingRecipe.openRecipeGui("item", stackover);
        }

        if (NEIClientConfig.isKeyHashDown("gui.usage")) {
            return GuiUsageRecipe.openRecipeGui("item", stackover);
        }

        if (NEIClientConfig.isKeyHashDown("gui.bookmark")) {
            return saveRecipeInBookmark(stackover, NEIClientUtils.shiftKey(), NEIClientUtils.controlKey());
        }

        if (NEIClientConfig.isKeyHashDown("gui.bookmark_pull_items")) {
            return pullRecipeItems(stackover, NEIClientUtils.shiftKey());
        }

        if (NEIClientConfig.autocraftingEnabled() && NEIClientConfig.isKeyHashDown("gui.craft")) {
            return runAutoCrafting(stackover);
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
                return GuiCraftingRecipe.openRecipeGui("item", stackover);
            } else if (button == 1) {
                return GuiUsageRecipe.openRecipeGui("item", stackover);
            }
        }

        return false;
    }

    private static boolean copyItemStackName(ItemStack stackover) {
        GuiScreen.setClipboardString(SearchField.getEscapedSearchText(stackover));
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

    private static boolean runAutoCrafting(ItemStack stackover) {
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
            } else if (slot.getRecipeId() != null && !slot.isIngredient()) {

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

    private static boolean saveRecipeInBookmark(ItemStack stackover, boolean saveIngredients, boolean saveStackSize) {
        final Point mousePos = GuiDraw.getMousePosition();

        if (!ItemPanels.bookmarkPanel.removeSlot(mousePos.x, mousePos.y, saveIngredients)) {
            final Recipe recipe = getFocusedRecipe(stackover, mousePos.x, mousePos.y, saveIngredients);

            if (recipe != null && saveIngredients) {

                if (!ItemPanels.bookmarkPanel.removeRecipe(recipe.getRecipeId(), BookmarkGrid.DEFAULT_GROUP_ID)) {
                    final Recipe singleRecipe = Recipe.of(
                            Arrays.asList(recipe.getResult(stackover)),
                            recipe.getHandlerName(),
                            recipe.getIngredients());
                    singleRecipe.setCustomRecipeId(recipe.getRecipeId());

                    ItemPanels.bookmarkPanel.addRecipe(singleRecipe, BookmarkGrid.DEFAULT_GROUP_ID);
                }

            } else {
                final RecipeId recipeId = recipe != null ? recipe.getRecipeId() : null;
                final boolean existsRecipe = recipeId != null
                        && ItemPanels.bookmarkPanel.existsRecipe(recipeId, BookmarkGrid.DEFAULT_GROUP_ID);

                if (!ItemPanels.bookmarkPanel
                        .removeItem(stackover, existsRecipe ? recipeId : null, BookmarkGrid.DEFAULT_GROUP_ID)) {
                    saveStackSize = saveStackSize || saveIngredients
                            || ItemPanels.bookmarkPanel.existsRecipe(recipeId, BookmarkGrid.DEFAULT_GROUP_ID);

                    if (!saveStackSize) {
                        stackover = StackInfo.withAmount(stackover, 0);
                    } else if (ItemPanels.itemPanel.contains(mousePos.x, mousePos.y)
                            || ItemPanels.itemPanel.historyPanel.contains(mousePos.x, mousePos.y)) {
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

        if (useFavorites && (ItemPanels.itemPanel.contains(mousex, mousey)
                || ItemPanels.itemPanel.historyPanel.contains(mousex, mousey))) {
            recipe = Recipe.of(FavoriteRecipes.getFavorite(stackover));

            if (recipe != null) {

                recipe.getIngredients().stream().forEach(ingr -> {
                    final List<ItemStack> permutations = ingr.getPermutations();
                    for (int index = 0; index < permutations.size(); index++) {
                        if (FavoriteRecipes.getFavorite(permutations.get(index)) != null) {
                            ingr.setActiveIndex(index);
                            break;
                        }
                    }
                });

            }

        } else if (gui instanceof GuiRecipe guiRecipe) {
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

    public static Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, ItemStack stack) {
        final int groupId = ItemPanels.bookmarkPanel.getHoveredGroupId(true);
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
                hotkeys.put(NEIClientConfig.getKeyName("gui.craft"), NEIClientUtils.translate("bookmark.group.craft"));
            }
        }

        if (stack == null) {
            return hotkeys;
        }

        final BookmarksGridSlot slot = ItemPanels.bookmarkPanel.getSlotMouseOver(mousex, mousey);
        final RecipeId recipeId = getHotkeyRecipeId(gui, mousex, mousey, stack, slot);

        if (slot != null) {
            hotkeys.put(
                    NEIClientConfig.getKeyName("gui.bookmark"),
                    NEIClientUtils
                            .translate(slot.isIngredient() ? "itempanel.remove_item" : "itempanel.remove_recipe"));

            if (slot.isIngredient()) {
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.bookmark", NEIClientUtils.SHIFT_HASH),
                        NEIClientUtils.translate("itempanel.remove_recipe"));
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
            hotkeys.put(NEIClientConfig.getKeyName("gui.bookmark"), NEIClientUtils.translate("itempanel.add_item"));

            if (StackInfo.getAmount(stack) > 0) {
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.bookmark", NEIClientUtils.CTRL_HASH),
                        NEIClientUtils.translate("itempanel.add_item_with_count"));
            }

            if (recipeId != null) {
                hotkeys.put(
                        NEIClientConfig.getKeyName("gui.bookmark", NEIClientUtils.SHIFT_HASH),
                        NEIClientUtils.translate("itempanel.add_item_with_recipe"));
            }

        }

        hotkeys.put(NEIClientConfig.getKeyName("gui.recipe"), NEIClientUtils.translate("itempanel.open_crafting"));
        hotkeys.put(NEIClientConfig.getKeyName("gui.usage"), NEIClientUtils.translate("itempanel.open_usage"));

        hotkeys.put(NEIClientConfig.getKeyName("gui.copy_name"), NEIClientUtils.translate("itempanel.copy_name"));
        hotkeys.put(NEIClientConfig.getKeyName("gui.copy_oredict"), NEIClientUtils.translate("itempanel.copy_oredict"));

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
                    hotkeys.put(NEIClientConfig.getKeyName("gui.craft"), NEIClientUtils.translate("itempanel.craft"));
                }
            }

        }

        return hotkeys;
    }

    private static RecipeId getHotkeyRecipeId(GuiContainer gui, int mousex, int mousey, ItemStack stack,
            BookmarksGridSlot slot) {

        if (slot != null) {
            return slot.getRecipeId();
        } else if (ItemPanels.itemPanel.contains(mousex, mousey)
                || ItemPanels.itemPanel.historyPanel.contains(mousex, mousey)) {
                    return FavoriteRecipes.getFavorite(stack);
                } else
            if (gui instanceof GuiRecipe guiRecipe) {
                final Recipe focusedRecipe = guiRecipe.getFocusedRecipe();

                if (focusedRecipe != null) {
                    return focusedRecipe.getRecipeId();
                }
            }

        return null;
    }

}
