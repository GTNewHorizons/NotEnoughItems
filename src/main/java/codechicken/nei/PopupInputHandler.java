package codechicken.nei;

import static codechicken.nei.NEIClientConfig.canPerformAction;

import net.minecraft.client.gui.inventory.GuiContainer;

import codechicken.nei.guihook.IContainerInputHandler;

public class PopupInputHandler implements IContainerInputHandler {

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {
        return false;
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyID) {}

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyID) {

        if (KeyManager.isHashDown("gui.enchant") && canPerformAction("enchant")) {
            NEICPH.sendOpenEnchantmentWindow();
            return true;
        }

        if (KeyManager.isHashDown("gui.potion") && canPerformAction("potion")) {
            NEICPH.sendOpenPotionWindow();
            return true;
        }

        return false;
    }

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {}

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        return false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {}

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {}
}
