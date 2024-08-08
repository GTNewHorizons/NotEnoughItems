package codechicken.nei;

import static codechicken.lib.gui.GuiDraw.getMousePosition;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.GameSettings;

import org.lwjgl.input.Keyboard;

import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerInputHandler;

public class AutoFocusWidget implements IContainerInputHandler {

    /**
     * Implement this interface on a GuiContainer to enable the autofocus search in NEI (if the autofocus option is
     * enabled) without needing to add the classname to the config file .
     */
    public interface INEIAutoFocusSearchEnable {
    }

    public static AutoFocusWidget instance = new AutoFocusWidget();

    public static List<String> enableAutoFocusPrefixes = new ArrayList<>();

    protected boolean autofocus = false;
    protected Point mouse;

    public AutoFocusWidget() {
        GuiContainerManager.addInputHandler(this);
    }

    public void load(GuiContainer gui) {
        this.autofocus = LayoutManager.searchField.isVisible() && NEIClientConfig.searchWidgetAutofocus() != 0
                && isAllowedGuiAutoSearchFocus(gui);
        this.mouse = getMousePosition();
    }

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {

        if (this.autofocus) {
            this.autofocus = false;

            if (!this.mouse.equals(getMousePosition())) {
                return false;
            }

            if (NEIClientConfig.searchWidgetAutofocus() == 2
                    && GameSettings.isKeyDown(Minecraft.getMinecraft().gameSettings.keyBindInventory)) {
                return false;
            }

            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER
                    || keyCode == Keyboard.KEY_ESCAPE) {
                return false;
            }

            LayoutManager.searchField.setFocus(true);
            LayoutManager.searchField.handleKeyPress(keyCode, keyChar);
            return true;
        }

        return false;
    }

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyID) {
        return false;
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        this.autofocus = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        this.autofocus = false;
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyID) {}

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        this.autofocus = false;
    }

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {
        this.autofocus = false;
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        this.autofocus = false;
    }

    @Override
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {
        this.autofocus = false;
    }

    protected boolean isAllowedGuiAutoSearchFocus(GuiContainer gui) {
        if (gui instanceof INEIAutoFocusSearchEnable) {
            return true;
        }
        String guiClassName = gui.getClass().getName();
        for (String prefix : enableAutoFocusPrefixes) {
            if (guiClassName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

}
