package codechicken.nei;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.StringJoiner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import codechicken.nei.util.NEIKeyboardUtils;
import codechicken.nei.util.NEIMouseUtils;
import cpw.mods.fml.client.registry.ClientRegistry;

/**
 * Registers NEI key bindings as vanilla {@link KeyBinding}s and exposes their state to the rest of NEI.
 */
public class KeyManager {

    public static interface IKeyStateTracker {

        public void tickKeyStates();
    }

    public static LinkedList<IKeyStateTracker> trackers = new LinkedList<>();
    private static final Map<String, KeyBinding> keyBindings = new HashMap<>();
    private static final Map<String, String> keyAliases = new HashMap<>();
    private static volatile boolean pendingOptionsReload = false;

    static {
        // Aliases for legacy keybinds
        keyAliases.put("gui.recipe", "recipe.recipe");
        keyAliases.put("gui.usage", "recipe.usage");
        keyAliases.put("gui.hide_bookmarks", "bookmark.hide");
    }

    public static KeyBinding registerKeyBinding(String ident, int defaultKey) {
        return keyBindings.computeIfAbsent(ident, id -> {
            final KeyBinding binding = new KeyBinding(
                    "nei.options.keys." + id,
                    NEIKeyboardUtils.unhash(defaultKey),
                    categoryFor(id));
            ClientRegistry.registerKeyBinding(binding);
            return binding;
        });
    }

    public static KeyBinding getKeyBinding(String ident) {
        return keyBindings.get(keyAliases.getOrDefault(ident, ident));
    }

    public static int getKeyCode(String ident) {
        final KeyBinding binding = getKeyBinding(ident);
        return binding == null ? Keyboard.KEY_NONE : binding.getKeyCode();
    }

    public static boolean isKeyDown(String ident) {
        final int keyCode = getKeyCode(ident);

        if (keyCode == Keyboard.KEY_NONE) {
            return false;
        }

        if (keyCode < 0) {
            return Mouse.isButtonDown(keyCode + 100);
        }

        return keyCode < Keyboard.KEYBOARD_SIZE && Keyboard.isKeyDown(keyCode);
    }

    public static boolean isHashDown(String ident) {
        return isHashDown(ident, 0);
    }

    public static boolean isHashDown(String ident, int modifierMask) {

        if (!isKeyDown(ident)) {
            return false;
        }

        if (NEIKeyboardUtils.isHashKey(getKeyCode(ident))) {
            return true;
        }

        return ((modifierMask & NEIKeyboardUtils.CTRL_HASH) != 0) == NEIClientUtils.controlKey()
                && ((modifierMask & NEIKeyboardUtils.SHIFT_HASH) != 0) == NEIClientUtils.shiftKey()
                && ((modifierMask & NEIKeyboardUtils.ALT_HASH) != 0) == NEIClientUtils.altKey();
    }

    public static String getKeyName(String ident) {
        return getKeyName(ident, 0);
    }

    public static String getKeyName(String ident, int meta) {
        final int keyCode = getKeyCode(ident);
        return keyCode == Keyboard.KEY_NONE ? null : NEIKeyboardUtils.getKeyName(keyCode + meta);
    }

    public static String getKeyName(String ident, int meta, int mouseBind) {
        final int keyCode = getKeyCode(ident);
        return keyCode == Keyboard.KEY_NONE && mouseBind == NEIMouseUtils.MOUSE_BTN_NONE ? null
                : getKeyName(keyCode + meta, mouseBind);
    }

    public static String getKeyName(int keyBind, int mouseBind) {
        StringJoiner keyText = new StringJoiner(" + ");
        String keyHash = keyBind == Keyboard.KEY_NONE ? "" : NEIKeyboardUtils.getKeyName(keyBind);
        String mouseHash = mouseBind == NEIMouseUtils.MOUSE_BTN_NONE ? "" : NEIMouseUtils.getKeyName(mouseBind);

        if (!keyHash.isEmpty()) {
            keyText.add(keyHash);
        }

        if (!mouseHash.isEmpty()) {
            keyText.add(mouseHash);
        }

        return keyText.toString();
    }

    public static boolean isPressed(String ident) {
        final KeyBinding binding = getKeyBinding(ident);
        return binding != null && binding.isPressed();
    }

    private static String categoryFor(String ident) {
        final int dot = ident.indexOf('.');
        return dot < 0 ? "nei.options.keys" : "nei.options.keys." + ident.substring(0, dot);
    }

    public static void requestOptionsReload() {
        pendingOptionsReload = true;
    }

    public static void tickKeyStates() {

        if (pendingOptionsReload) {
            pendingOptionsReload = false;
            Minecraft.getMinecraft().gameSettings.loadOptions();
        }

        for (IKeyStateTracker tracker : trackers) tracker.tickKeyStates();
    }
}
