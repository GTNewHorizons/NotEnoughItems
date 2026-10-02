package codechicken.nei.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.input.Keyboard;
import org.mockito.MockedStatic;

import codechicken.nei.NEIClientUtils;

class NEIKeyboardUtilsTest {

    @ParameterizedTest
    @ValueSource(ints = { -100, -99, -98, -97, -96, Keyboard.KEY_NONE, Keyboard.KEY_R, Keyboard.KEY_F12 })
    void preservesKeyCodesWithEveryModifierCombination(int keyCode) {
        for (int modifiers = 0; modifiers < 8; modifiers++) {
            assertEquals(keyCode, NEIKeyboardUtils.unhash(keyCode + (modifiers << 25)));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { -97, -96 })
    void mouseBindingsDoNotAcquirePhantomModifiers(int keyCode) {
        assertEquals("", NEIKeyboardUtils.getHashName(keyCode));
        try (MockedStatic<NEIClientUtils> utils = mockStatic(NEIClientUtils.class)) {
            utils.when(() -> NEIClientUtils.translate("key.shift")).thenReturn("Shift");
            String buttonName = "Button " + (keyCode + 101);
            utils.when(() -> NEIClientUtils.translate("mouse.other", keyCode + 101)).thenReturn(buttonName);

            assertEquals(buttonName, NEIKeyboardUtils.getKeyName(keyCode));
            assertEquals("Shift + " + buttonName, NEIKeyboardUtils.getKeyName(keyCode + NEIKeyboardUtils.SHIFT_HASH));
        }
    }
}
