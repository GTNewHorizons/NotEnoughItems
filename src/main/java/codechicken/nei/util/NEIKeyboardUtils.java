package codechicken.nei.util;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Keyboard;

import codechicken.nei.NEIClientUtils;

public class NEIKeyboardUtils {

    public static final int ALT_HASH = 1 << 27;
    public static final int SHIFT_HASH = 1 << 26;
    public static final int CTRL_HASH = 1 << 25;

    private static final Map<Integer, Integer> keyHashMap = new HashMap<>();

    static {
        keyHashMap.put(Keyboard.KEY_LSHIFT, SHIFT_HASH);
        keyHashMap.put(Keyboard.KEY_RSHIFT, SHIFT_HASH);

        keyHashMap.put(Keyboard.KEY_LCONTROL, CTRL_HASH);
        keyHashMap.put(Keyboard.KEY_RCONTROL, CTRL_HASH);

        keyHashMap.put(Keyboard.KEY_LMETA, CTRL_HASH);
        keyHashMap.put(Keyboard.KEY_RMETA, CTRL_HASH);

        keyHashMap.put(Keyboard.KEY_LMENU, ALT_HASH);
        keyHashMap.put(Keyboard.KEY_RMENU, ALT_HASH);
    }

    private NEIKeyboardUtils() {}

    public static boolean isHashKey(int keycode) {
        return keyHashMap.containsKey(keycode);
    }

    public static String getHashName(int keyBind) {
        StringJoiner keyText = new StringJoiner(" + ");

        if ((keyBind & CTRL_HASH) != 0) {
            keyText.add(NEIClientUtils.translate(Minecraft.isRunningOnMac ? "key.ctrl.mac" : "key.ctrl"));
        }

        if ((keyBind & SHIFT_HASH) != 0) {
            keyText.add(NEIClientUtils.translate("key.shift"));
        }

        if ((keyBind & ALT_HASH) != 0) {
            keyText.add(NEIClientUtils.translate("key.alt"));
        }

        return keyText.toString();
    }

    /**
     * Mouse buttons are negative key codes, so the hash can not be packed into them and is passed separately.
     */
    public static String getKeyName(int keyCode, int hash) {
        if (hash == 0 && keyHashMap.containsKey(keyCode)) {
            hash = keyHashMap.get(keyCode);
            keyCode = Keyboard.CHAR_NONE;
        }

        final StringJoiner keyText = new StringJoiner(" + ");
        final String hashText = getHashName(hash);

        if (!hashText.isEmpty()) {
            keyText.add(hashText);
        }

        if (keyCode != Keyboard.CHAR_NONE || hashText.isEmpty()) {
            keyText.add(keyCode < 0 ? NEIMouseUtils.getKeyName(keyCode + 100) : Keyboard.getKeyName(keyCode));
        }

        return keyText.toString();
    }

    public static int unhash(int keyBind) {
        return keyBind & ~(CTRL_HASH | SHIFT_HASH | ALT_HASH);
    }

}
