package codechicken.nei.recipe;

import java.util.Map;

import net.minecraft.client.gui.inventory.GuiContainer;

import codechicken.nei.ItemPanels;
import codechicken.nei.api.ShortcutInputHandler;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;

public class RecipeItemInputHandler implements IContainerInputHandler, IContainerTooltipHandler {

    public static void load() {
        RecipeItemInputHandler recipeHandler = new RecipeItemInputHandler();
        GuiContainerManager.addInputHandler(recipeHandler);
        GuiContainerManager.addTooltipHandler(recipeHandler);
    }

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return ShortcutInputHandler.handleKeyEvent(GuiContainerManager.getStackMouseOver(gui));
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        if (!(gui instanceof GuiRecipe) || ItemPanels.itemPanel.contains(mousex, mousey)
                || ItemPanels.bookmarkPanel.contains(mousex, mousey)
                || ItemPanels.itemPanel.historyPanel.contains(mousex, mousey))
            return false;

        return ShortcutInputHandler.handleMouseClick(GuiContainerManager.getStackMouseOver(gui));
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {

        if ((gui instanceof GuiRecipe) || ItemPanels.itemPanel.contains(mousex, mousey)
                || ItemPanels.bookmarkPanel.contains(mousex, mousey)
                || ItemPanels.itemPanel.historyPanel.contains(mousex, mousey)) {
            hotkeys.putAll(
                    ShortcutInputHandler
                            .handleHotkeys(gui, mousex, mousey, GuiContainerManager.getStackMouseOver(gui)));
        }

        return hotkeys;
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
