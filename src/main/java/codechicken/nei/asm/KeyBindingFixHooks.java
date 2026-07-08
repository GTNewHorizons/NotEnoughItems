package codechicken.nei.asm;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.settings.KeyBinding;

import cpw.mods.fml.relauncher.ReflectionHelper;

public final class KeyBindingFixHooks {

    private static volatile boolean initialized;
    private static Field keybindArrayField;
    private static Field pressedField;
    private static Field pressTimeField;

    private KeyBindingFixHooks() {}

    public static void onTickAll(int keyCode) {

        if (keyCode == 0) {
            return;
        }

        for (KeyBinding binding : getBindings()) {
            if (binding.getKeyCode() == keyCode) {
                try {
                    pressTimeField.setInt(binding, pressTimeField.getInt(binding) + 1);
                } catch (Exception ignored) {}
            }
        }

    }

    public static void setKeyBindStateAll(int keyCode, boolean pressed) {

        if (keyCode == 0) {
            return;
        }

        for (KeyBinding binding : getBindings()) {
            if (binding.getKeyCode() == keyCode) {
                try {
                    pressedField.setBoolean(binding, pressed);
                } catch (Exception ignored) {}
            }
        }

    }

    @SuppressWarnings("unchecked")
    private static List<KeyBinding> getBindings() {
        init();

        try {
            return (List<KeyBinding>) keybindArrayField.get(null);
        } catch (Exception ignored) {}

        return Collections.emptyList();
    }

    private static synchronized void init() {

        if (initialized) {
            return;
        }

        keybindArrayField = ReflectionHelper.findField(KeyBinding.class, "keybindArray", "field_74516_a");
        pressedField = ReflectionHelper.findField(KeyBinding.class, "pressed", "field_74513_e");
        pressTimeField = ReflectionHelper.findField(KeyBinding.class, "pressTime", "field_151474_i");

        initialized = true;
    }

}
