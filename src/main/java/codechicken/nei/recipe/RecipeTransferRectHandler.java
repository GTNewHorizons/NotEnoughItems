package codechicken.nei.recipe;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.gui.inventory.GuiContainer;

import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.recipe.TemplateRecipeHandler.RecipeTransferRect;

public class RecipeTransferRectHandler implements IContainerInputHandler, IContainerTooltipHandler {

    private static final Map<Class<? extends GuiContainer>, Set<RecipeTransferRect>> guiMap = new ConcurrentHashMap<>();

    static {
        final RecipeTransferRectHandler handler = new RecipeTransferRectHandler();
        GuiContainerManager.addInputHandler(handler);
        GuiContainerManager.addTooltipHandler(handler);
    }

    public static void registerRectsToGuis(List<Class<? extends GuiContainer>> classes,
            List<RecipeTransferRect> rects) {
        if (classes == null) return;

        for (Class<? extends GuiContainer> clazz : classes) {
            guiMap.computeIfAbsent(clazz, k -> new LinkedHashSet<>()).addAll(rects);
        }
    }

    public boolean canHandle(GuiContainer gui) {
        return guiMap.containsKey(gui.getClass());
    }

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    @Override
    public boolean onHotkeyPressed(GuiContainer gui) {
        return false;
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        if (!canHandle(gui)) return false;

        if (button == 0) {
            return transferRect(gui, false);
        } else if (button == 1) {
            return transferRect(gui, true);
        }

        return false;
    }

    private boolean transferRect(GuiContainer gui, boolean usage) {
        final int[] offset = RecipeInfo.getGuiOffset(gui);
        return TemplateRecipeHandler.transferRect(gui, guiMap.get(gui.getClass()), offset[0], offset[1], usage);
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
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {
        if (!canHandle(gui)) return currenttip;

        if (GuiContainerManager.shouldShowTooltip(gui) && currenttip.size() == 0) {
            int[] offset = RecipeInfo.getGuiOffset(gui);
            currenttip = TemplateRecipeHandler
                    .transferRectTooltip(gui, guiMap.get(gui.getClass()), offset[0], offset[1], currenttip);
        }
        return currenttip;
    }

    @Override
    public Map<String, String> handleHotkeys(GuiContainer gui, int mousex, int mousey, Map<String, String> hotkeys) {
        return hotkeys;
    }

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {}
}
