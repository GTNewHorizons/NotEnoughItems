package codechicken.nei.guihook;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.mockito.MockedStatic;

public class GuiContainerManagerTest {

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
