package codechicken.nei;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;

import org.lwjgl.input.Keyboard;

import codechicken.nei.util.NEIKeyboardUtils;

/**
 * Good old down/held/up keystate tracker
 */
public class KeyManager {

    public static interface IKeyStateTracker {

        public void tickKeyStates();
    }

    public static class KeyState {

        public boolean down;
        public boolean held;
        public boolean up;
    }

    public static HashMap<String, KeyState> keyStates = new HashMap<>();
    public static LinkedList<IKeyStateTracker> trackers = new LinkedList<>();

    public static void tickKeyStates() {
        for (Entry<String, KeyState> entry : keyStates.entrySet()) {
            final int keyCode = NEIKeyboardUtils.unhash(NEIClientConfig.getKeyBinding(entry.getKey()));
            final boolean down = keyCode > 0 && keyCode < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(keyCode);
            final KeyState state = entry.getValue();

            if (down) {
                state.down = !state.held;
                state.up = false;
            } else {
                state.up = state.held;
                state.down = false;
            }

            state.held = down;
        }

        for (IKeyStateTracker tracker : trackers) tracker.tickKeyStates();
    }
}
