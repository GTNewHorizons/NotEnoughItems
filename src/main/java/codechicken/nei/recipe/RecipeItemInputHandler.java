package codechicken.nei.recipe;

import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import codechicken.lib.gui.GuiDraw;
import codechicken.nei.BookmarkPanel.BookmarkViewMode;
import codechicken.nei.ItemPanels;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.api.ShortcutInputHandler;
import codechicken.nei.bookmark.BookmarksGridSlot;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.recipe.Recipe.RecipeId;

public class RecipeItemInputHandler implements IContainerInputHandler, IContainerTooltipHandler {

    protected RecipeTooltipLineHandler recipeTooltipLineHandler = null;

    public static void load() {
        RecipeItemInputHandler recipeHandler = new RecipeItemInputHandler();
        GuiContainerManager.addInputHandler(recipeHandler);
        GuiContainerManager.addTooltipHandler(recipeHandler);
    }

    @Override
    public boolean supportsMouseKeybinds() {
        return true;
    }

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return ShortcutInputHandler.handleKeyEvent(GuiContainerManager.getStackMouseOver(gui));
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        return false;
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {

        if (gui instanceof GuiRecipe || ItemPanels.itemPanel.containsWithSubpanels(mousex, mousey)
                || ItemPanels.bookmarkPanel.contains(mousex, mousey)) {
            hotkeys.putAll(
                    ShortcutInputHandler.handleHotkeys(mousex, mousey, GuiContainerManager.getStackMouseOver(gui)));
        }

        return hotkeys;
    }

    @Override
    public List<String> handleItemTooltip(GuiContainer gui, ItemStack itemstack, int mousex, int mousey,
            List<String> currenttip) {

        if (itemstack != null) {
            final HoveredRecipe hovered = HoveredRecipe.of(gui, itemstack);
            final RecipeId recipeId = hovered != null && showRecipeTooltip(hovered, mousex, mousey) ? hovered.recipeId
                    : null;

            if (recipeId == null) {
                recipeTooltipLineHandler = null;
            } else if (recipeTooltipLineHandler == null || !recipeTooltipLineHandler.getRecipeId().equals(recipeId)) {
                recipeTooltipLineHandler = new RecipeTooltipLineHandler(recipeId);
            }

            if (recipeTooltipLineHandler != null) {
                currenttip.add(GuiDraw.TOOLTIP_HANDLER + GuiDraw.getTipLineId(recipeTooltipLineHandler));
            }
        } else {
            recipeTooltipLineHandler = null;
        }

        return currenttip;
    }

    private boolean showRecipeTooltip(HoveredRecipe hovered, int mousex, int mousey) {
        return switch (hovered.source) {
            case BOOKMARK -> !hovered.favorite && showRecipeTooltipInBookmarks(mousex, mousey);
            case ITEM_PANEL -> NEIClientConfig.showRecipeTooltipInPanel();
            case RECIPE_GUI -> hovered.favorite && NEIClientConfig.showRecipeTooltipInGui();
            default -> false;
        };
    }

    private boolean showRecipeTooltipInBookmarks(int mousex, int mousey) {
        final BookmarksGridSlot slot = ItemPanels.bookmarkPanel.getSlotMouseOver(mousex, mousey);
        final int tooltipMode = NEIClientConfig.getRecipeTooltipsMode();
        final BookmarkViewMode viewMode = slot.getGroup().viewMode;
        return tooltipMode == 3 || viewMode == BookmarkViewMode.DEFAULT && tooltipMode == 1
                || viewMode == BookmarkViewMode.TODO_LIST && tooltipMode == 2;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyID) {}

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyID) {
        return false;
    }

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {}
}
