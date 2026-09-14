package codechicken.nei.guihook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.mockito.MockedStatic;

import codechicken.nei.KeyManager;
import cpw.mods.fml.client.registry.ClientRegistry;

public class GuiContainerManagerTest {

    @ParameterizedTest
    @ValueSource(ints = { 3, 4 })
    void sideButtonsReachTheKeyboardDispatchOncePerPress(int button) {
        try (MockedStatic<ClientRegistry> registry = mockStatic(ClientRegistry.class);
                MockedStatic<Mouse> mouse = mockStatic(Mouse.class)) {
            KeyManager.registerKeyBinding("test.dispatch", Keyboard.KEY_NONE).setKeyCode(button - 100);
            try {
                GuiContainerManager manager = spy(new GuiContainerManager(mock(GuiContainer.class)));
                doNothing().when(manager).keyTyped('\0', button - 100);
                mouse.when(Mouse::getEventButton).thenReturn(button);
                mouse.when(Mouse::getEventButtonState).thenReturn(true);

                assertTrue(manager.handleMouseKeybind());
                mouse.when(Mouse::getEventButtonState).thenReturn(false);
                assertTrue(manager.handleMouseKeybind());
                verify(manager, times(1)).keyTyped('\0', button - 100);

                mouse.when(Mouse::getEventButton).thenReturn(-1);
                assertFalse(manager.handleMouseKeybind());
            } finally {
                KeyManager.getKeyBinding("test.dispatch").setKeyCode(Keyboard.KEY_NONE);
            }
        }
    }

    @Test
    void matrixStackLoggingSupportsNestedContexts() {
        try (MockedStatic<GL11> gl = mockStatic(GL11.class)) {
            GuiContainerManager.enableMatrixStackLogging();
            GuiContainerManager.enableMatrixStackLogging();
            GuiContainerManager.disableMatrixStackLogging();

            gl.verify(() -> GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT));
            gl.verify(GL11::glPopAttrib, times(0));

            GuiContainerManager.disableMatrixStackLogging();
            gl.verify(GL11::glPopAttrib);
        }
    }
}
