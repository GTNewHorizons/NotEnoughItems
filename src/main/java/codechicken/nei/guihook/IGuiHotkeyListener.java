package codechicken.nei.guihook;

/**
 * Interface for GuiContainers to receive NEI compatible hotkey events from both keyboard keys and mouse buttons
 */
public interface IGuiHotkeyListener {

    /**
     * Called before the input handlers when a key or a mouse button is pressed, check key bindings with
     * {@link codechicken.nei.KeyManager#isKeyDown}
     *
     * @return true to terminate further processing of this event.
     */
    public boolean onHotkeyPressed();
}
