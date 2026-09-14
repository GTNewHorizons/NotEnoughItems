package codechicken.nei.guihook;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

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
    void sideButtonsReachOnlyOptedInHandlersOncePerPress(int button) {
        List<IContainerInputHandler> previousHandlers = new ArrayList<>(GuiContainerManager.inputHandlers);
        try (MockedStatic<ClientRegistry> registry = mockStatic(ClientRegistry.class);
                MockedStatic<Mouse> mouse = mockStatic(Mouse.class)) {
            KeyManager.registerKeyBinding("test.dispatch", Keyboard.KEY_NONE).setKeyCode(button - 100);
            try {
                GuiContainer gui = mock(GuiContainer.class);
                GuiContainerManager manager = new GuiContainerManager(gui);
                IContainerInputHandler foreign = mock(IContainerInputHandler.class);
                IContainerInputHandler aware = mock(IContainerInputHandler.class);
                when(aware.supportsMouseKeybinds()).thenReturn(true);
                GuiContainerManager.inputHandlers.clear();
                GuiContainerManager.inputHandlers.add(foreign);
                GuiContainerManager.inputHandlers.add(aware);
                mouse.when(Mouse::getEventButton).thenReturn(button);
                mouse.when(Mouse::getEventButtonState).thenReturn(true);

                assertTrue(manager.handleMouseKeybind());
                mouse.when(Mouse::getEventButtonState).thenReturn(false);
                assertTrue(manager.handleMouseKeybind());
                verify(aware).onKeyTyped(gui, '\0', button - 100);
                verify(aware).keyTyped(gui, '\0', button - 100);
                verify(aware).lastKeyTyped(gui, '\0', button - 100);
                verify(foreign, never()).onKeyTyped(gui, '\0', button - 100);
                verify(foreign, never()).keyTyped(gui, '\0', button - 100);
                verify(foreign, never()).lastKeyTyped(gui, '\0', button - 100);

                // Ordinary keyboard events must still reach legacy handlers such as ModularUI.
                manager.firstKeyTyped('r', Keyboard.KEY_R);
                manager.lastKeyTyped(Keyboard.KEY_R, 'r');
                verify(foreign).onKeyTyped(gui, 'r', Keyboard.KEY_R);
                verify(foreign).keyTyped(gui, 'r', Keyboard.KEY_R);
                verify(foreign).lastKeyTyped(gui, 'r', Keyboard.KEY_R);

                mouse.when(Mouse::getEventButton).thenReturn(-1);
                assertFalse(manager.handleMouseKeybind());
            } finally {
                KeyManager.getKeyBinding("test.dispatch").setKeyCode(Keyboard.KEY_NONE);
            }
        } finally {
            GuiContainerManager.inputHandlers.clear();
            GuiContainerManager.inputHandlers.addAll(previousHandlers);
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
