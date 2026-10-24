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
    default boolean onHotkeyPressed() {
        return false;
    }

    /**
     * Called after the input handlers for screen-wide hotkeys that do not depend on the hovered object, so they never
     * take priority over more specific actions
     *
     * @return true to terminate further processing of this event.
     */
    default boolean onScreenHotkeyPressed() {
        return false;
    }
}
