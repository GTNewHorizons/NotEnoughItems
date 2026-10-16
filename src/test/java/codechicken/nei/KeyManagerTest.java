package codechicken.nei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.util.function.IntConsumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.mockito.MockedStatic;

import codechicken.nei.util.NEIKeyboardUtils;
import cpw.mods.fml.client.registry.ClientRegistry;

class KeyManagerTest {

    @BeforeEach
    void registerBindings() {
        try (MockedStatic<ClientRegistry> registry = mockStatic(ClientRegistry.class)) {
            KeyManager.registerKeyBinding("test.mouse", Keyboard.KEY_NONE);
            KeyManager.registerKeyBinding("test.other_mouse", Keyboard.KEY_NONE);
            KeyManager.registerKeyBinding("test.keyboard", Keyboard.KEY_NONE);
        }
    }

    @AfterEach
    void clearBindings() {
        KeyManager.getKeyBinding("test.mouse").setKeyCode(Keyboard.KEY_NONE);
        KeyManager.getKeyBinding("test.other_mouse").setKeyCode(Keyboard.KEY_NONE);
        KeyManager.getKeyBinding("test.keyboard").setKeyCode(Keyboard.KEY_NONE);
    }

    @ParameterizedTest
    @ValueSource(ints = { 3, 4 })
    void sideButtonPressActivatesOnlyItsBindings(int button) {
        KeyManager.getKeyBinding("test.mouse").setKeyCode(button - 100);
        KeyManager.getKeyBinding("test.other_mouse").setKeyCode((button == 3 ? 4 : 3) - 100);
        KeyManager.getKeyBinding("test.keyboard").setKeyCode(Keyboard.KEY_R);

        try (MockedStatic<Keyboard> keyboard = mockStatic(Keyboard.class);
                MockedStatic<Mouse> mouse = mockStatic(Mouse.class)) {
            keyboard.when(() -> Keyboard.isKeyDown(Keyboard.KEY_R)).thenReturn(true);
            mouse.when(() -> Mouse.isButtonDown(3)).thenReturn(true);
            mouse.when(() -> Mouse.isButtonDown(4)).thenReturn(true);

            assertTrue(KeyManager.handleMouseKeybind(button, true, keyCode -> {
                assertEquals(button - 100, keyCode);
                assertTrue(KeyManager.isKeyDown("test.mouse"));
                assertFalse(KeyManager.isKeyDown("test.other_mouse"));
                assertFalse(KeyManager.isKeyDown("test.keyboard"));
            }));

            assertTrue(KeyManager.isKeyDown("test.keyboard"));
            assertTrue(KeyManager.isKeyDown("test.other_mouse"));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 3, 4 })
    void bufferedPressDoesNotDependOnPolledButtonState(int button) {
        KeyManager.getKeyBinding("test.mouse").setKeyCode(button - 100);
        try (MockedStatic<Mouse> mouse = mockStatic(Mouse.class)) {
            assertTrue(
                    KeyManager.handleMouseKeybind(
                            button,
                            true,
                            keyCode -> { assertTrue(KeyManager.isKeyDown("test.mouse")); }));
            assertFalse(KeyManager.isKeyDown("test.mouse"));
        }
    }

    @Test
    void releasesDoNotRepeatTheAction() {
        KeyManager.getKeyBinding("test.mouse").setKeyCode(-97);
        assertTrue(KeyManager.handleMouseKeybind(3, false, unexpectedInput()));
    }

    @ParameterizedTest
    @ValueSource(ints = { -1, 0, 1, 2, 100 })
    void primaryButtonsAndWheelKeepTheirNormalBehavior(int button) {
        KeyManager.getKeyBinding("test.mouse").setKeyCode(button - 100);
        assertFalse(KeyManager.handleMouseKeybind(button, true, unexpectedInput()));
        assertFalse(KeyManager.handleMouseKeybind(button, false, unexpectedInput()));
    }

    @Test
    void unboundSideButtonsKeepTheirNormalBehavior() {
        assertFalse(KeyManager.handleMouseKeybind(3, true, unexpectedInput()));
        assertFalse(KeyManager.handleMouseKeybind(4, false, unexpectedInput()));
    }

    @Test
    void restoresInputStateWhenHandlerThrows() {
        KeyManager.getKeyBinding("test.mouse").setKeyCode(-97);
        KeyManager.getKeyBinding("test.keyboard").setKeyCode(Keyboard.KEY_R);
        try (MockedStatic<Keyboard> keyboard = mockStatic(Keyboard.class)) {
            keyboard.when(() -> Keyboard.isKeyDown(Keyboard.KEY_R)).thenReturn(true);
            assertThrows(
                    IllegalStateException.class,
                    () -> KeyManager.handleMouseKeybind(
                            3,
                            true,
                            keyCode -> { throw new IllegalStateException("Test handler failure"); }));
            assertTrue(KeyManager.isKeyDown("test.keyboard"));
        }
    }

    @Test
    void sideButtonsRetainModifierChecks() {
        KeyManager.getKeyBinding("test.mouse").setKeyCode(-97);
        try (MockedStatic<NEIClientUtils> utils = mockStatic(NEIClientUtils.class)) {
            utils.when(NEIClientUtils::shiftKey).thenReturn(true);
            assertTrue(KeyManager.handleMouseKeybind(3, true, keyCode -> {
                assertTrue(KeyManager.isHashDown("test.mouse", NEIKeyboardUtils.SHIFT_HASH));
                assertFalse(KeyManager.isHashDown("test.mouse"));
                assertFalse(KeyManager.isHashDown("test.mouse", NEIKeyboardUtils.CTRL_HASH));
            }));
        }
    }

    private static IntConsumer unexpectedInput() {
        return keyCode -> { throw new AssertionError("Unexpected key event: " + keyCode); };
    }

    @Test
    void nestedDispatchRestoresTheOuterBinding() {
        KeyManager.getKeyBinding("test.mouse").setKeyCode(-97);
        KeyManager.getKeyBinding("test.other_mouse").setKeyCode(-96);
        try (MockedStatic<Mouse> mouse = mockStatic(Mouse.class)) {
            assertTrue(KeyManager.handleMouseKeybind(3, true, outer -> {
                assertTrue(KeyManager.isKeyDown("test.mouse"));
                assertTrue(KeyManager.handleMouseKeybind(4, true, inner -> {
                    assertTrue(KeyManager.isKeyDown("test.other_mouse"));
                    assertFalse(KeyManager.isKeyDown("test.mouse"));
                }));
                assertTrue(KeyManager.isKeyDown("test.mouse"));
                assertFalse(KeyManager.isKeyDown("test.other_mouse"));
            }));
            assertFalse(KeyManager.isKeyDown("test.mouse"));
        }
    }
}
