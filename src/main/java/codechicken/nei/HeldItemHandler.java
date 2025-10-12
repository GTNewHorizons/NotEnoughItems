package codechicken.nei;

import static codechicken.nei.NEIClientUtils.translate;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.EnumChatFormatting;

import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;
import codechicken.nei.guihook.IContainerTooltipHandler;
import codechicken.nei.recipe.AutoCraftingManager;

public class HeldItemHandler implements IContainerInputHandler, IContainerTooltipHandler {

    protected HeldItemHandler() {

    }

    public static void load() {
        HeldItemHandler instance = new HeldItemHandler();
        GuiContainerManager.addInputHandler(instance);
        GuiContainerManager.addTooltipHandler(instance);
    }

    protected boolean contains(int mousex, int mousey) {
        return NEIClientUtils.getHeldItem() != null && !ItemPanels.bookmarkPanel.inEditingState()
                && !AutoCraftingManager.processing()
                && (ItemPanels.bookmarkPanel.contains(mousex, mousey)
                        || ItemPanels.itemPanel.containsWithSubpanels(mousex, mousey));
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {

        if (contains(mousex, mousey)) {

            if (NEIClientConfig.canPerformAction("delete") && NEIClientConfig.canPerformAction("item")) {
                if (button == 1) {
                    NEIClientUtils.decreaseSlotStack(-999);
                } else {
                    NEIClientUtils.deleteHeldItem();
                }
            } else {
                NEIClientUtils.dropHeldItem();
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {

        return false;
    }

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyID) {

        return false;
    }

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {

        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyID) {

    }

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {

    }

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {

    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {

    }

    @Override
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {

    }

    @Override
    public List<String> handleTooltip(GuiContainer gui, int mousex, int mousey, List<String> currenttip) {

        if (contains(mousex, mousey)) {
            currenttip.clear();

            if (NEIClientConfig.canPerformAction("delete") && NEIClientConfig.canPerformAction("item")) {
                currenttip.add(EnumChatFormatting.RED + translate("itempanel.deleteItem"));
            }
        }

        return currenttip;
    }

}
