package codechicken.nei;

import static codechicken.lib.gui.GuiDraw.getMousePosition;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ChatAllowedCharacters;

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
    protected Point pendingMouse;
    protected GuiTextField textInputHolder;

    public AutoFocusWidget() {
        GuiContainerManager.addInputHandler(this);
    }

    public void load(GuiContainer gui) {
        this.autofocus = LayoutManager.searchField.isVisible() && NEIClientConfig.searchWidgetAutofocus() != 0
                && isAllowedGuiAutoSearchFocus(gui);
        this.mouse = null;
        this.pendingMouse = null;

        if (this.autofocus) {
            beginTextInput();
        }
    }

    protected void beginTextInput() {
        if (this.textInputHolder == null) {
            this.textInputHolder = new GuiTextField(Minecraft.getMinecraft().fontRenderer, 0, 0, 0, 0);
        }

        this.textInputHolder.setFocused(true);
    }

    protected void endTextInput() {
        if (this.textInputHolder != null) {
            this.textInputHolder.setFocused(false);
        }
    }

    public void guiTick() {
        if (!this.autofocus) {
            return;
        }

        final Point current = getMousePosition();

        if (this.mouse == null) {
            if (current.equals(this.pendingMouse)) {
                this.mouse = current;
            }

            this.pendingMouse = current;
        } else if (!this.mouse.equals(current)) {
            cancel();
        }
    }

    protected void cancel() {
        if (this.autofocus) {
            this.autofocus = false;
            endTextInput();
        }
    }

    @Override
    public boolean keyTyped(GuiContainer gui, char keyChar, int keyCode) {

        if (this.autofocus) {

            if (NEIClientConfig.searchWidgetAutofocus() == 2
                    && GameSettings.isKeyDown(Minecraft.getMinecraft().gameSettings.keyBindInventory)) {
                cancel();
                return false;
            }

            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER
                    || keyCode == Keyboard.KEY_ESCAPE) {
                cancel();
                return false;
            }

            if (!ChatAllowedCharacters.isAllowedCharacter(keyChar)) {
                return false;
            }

            this.autofocus = false;

            LayoutManager.searchField.setFocus(true);
            endTextInput();

            final String oldText = LayoutManager.searchField.text();
            LayoutManager.searchField.handleKeyPress(keyCode, keyChar);

            return !LayoutManager.searchField.text().equals(oldText);
        }

        return false;
    }

    @Override
    public boolean lastKeyTyped(GuiContainer gui, char keyChar, int keyID) {
        return false;
    }

    @Override
    public boolean mouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        cancel();
        return false;
    }

    @Override
    public boolean mouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        cancel();
        return false;
    }

    @Override
    public void onKeyTyped(GuiContainer gui, char keyChar, int keyID) {}

    @Override
    public void onMouseClicked(GuiContainer gui, int mousex, int mousey, int button) {
        cancel();
    }

    @Override
    public void onMouseDragged(GuiContainer gui, int mousex, int mousey, int button, long heldTime) {
        cancel();
    }

    @Override
    public void onMouseScrolled(GuiContainer gui, int mousex, int mousey, int scrolled) {
        cancel();
    }

    @Override
    public void onMouseUp(GuiContainer gui, int mousex, int mousey, int button) {
        cancel();
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
