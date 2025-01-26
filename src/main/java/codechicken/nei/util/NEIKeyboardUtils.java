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
        keyHashMap.put(Keyboard.KEY_LCONTROL, CTRL_HASH);

        keyHashMap.put(Keyboard.KEY_LMETA, CTRL_HASH);
        keyHashMap.put(Keyboard.KEY_RMETA, CTRL_HASH);

        keyHashMap.put(Keyboard.KEY_LMENU, ALT_HASH);
        keyHashMap.put(Keyboard.KEY_LMENU, ALT_HASH);
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

    public static String getKeyName(int keyBind) {
        keyBind = keyHashMap.getOrDefault(keyBind, keyBind);
        StringJoiner keyText = new StringJoiner(" + ");
        String hashText = getHashName(keyBind);
        int keyID = unhash(keyBind);

        if (!hashText.isEmpty()) {
            keyText.add(hashText);
        }

        if (keyID != Keyboard.CHAR_NONE || hashText.isEmpty()) {
            keyText.add(Keyboard.getKeyName(keyID));
        }

        return keyText.toString();
    }

    public static int unhash(int keyBind) {
        return keyBind & ~(CTRL_HASH | SHIFT_HASH | ALT_HASH);
    }

}
