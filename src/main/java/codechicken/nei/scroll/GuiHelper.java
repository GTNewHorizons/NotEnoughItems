package codechicken.nei.scroll;

import java.nio.FloatBuffer;
import java.util.Stack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiHelper {

    private static final Stack<int[]> scissorFrameStack = new Stack<>();

    public static void useScissor(int x, int y, int width, int height, Runnable codeBlock) {
        pushScissorFrame(x, y, width, height);

        try {
            codeBlock.run();
        } finally {
            popScissorFrame();
        }
    }

    private static int[] peekFirstScissorOrFullScreen() {
        final int[] currentTopFrame = scissorFrameStack.isEmpty() ? null : scissorFrameStack.peek();
        if (currentTopFrame == null) {
            final Minecraft minecraft = Minecraft.getMinecraft();
            return new int[] { 0, 0, minecraft.displayWidth, minecraft.displayHeight };
        }
        return currentTopFrame;
    }

    private static void pushScissorFrame(int x, int y, int width, int height) {
        int[] matrix = prepareMatrix(x, y, width, height);
        int[] parentScissor = peekFirstScissorOrFullScreen();
        int parentX = parentScissor[0];
        int parentY = parentScissor[1];
        int parentWidth = parentScissor[2];
        int parentHeight = parentScissor[3];
        boolean pushedFrame = false;

        x = matrix[0];
        y = matrix[1];
        width = matrix[2];
        height = matrix[3];

        if (x <= parentX + parentWidth && y <= parentY + parentHeight) {
            int newX = Math.max(x, parentX);
            int newY = Math.max(y, parentY);
            int newWidth = width - (newX - x);
            int newHeight = height - (newY - y);
            if (newWidth > 0 && newHeight > 0) {
                int maxWidth = parentWidth - (x - parentX);
                int maxHeight = parentHeight - (y - parentY);
                newWidth = Math.min(maxWidth, newWidth);
                newHeight = Math.min(maxHeight, newHeight);
                applyScissor(newX, newY, newWidth, newHeight);
                // finally, push applied scissor on top of scissor stack

                if (scissorFrameStack.isEmpty()) {
                    GL11.glEnable(GL11.GL_SCISSOR_TEST);
                }

                scissorFrameStack.push(new int[] { newX, newY, newWidth, newHeight });
                pushedFrame = true;
            }
        }

        if (!pushedFrame) {

            if (scissorFrameStack.isEmpty()) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }

            scissorFrameStack.push(new int[] { parentX, parentY, parentWidth, parentHeight });
        }

    }

    public static void popScissorFrame() {
        scissorFrameStack.pop();
        int[] parentScissor = peekFirstScissorOrFullScreen();
        int parentX = parentScissor[0];
        int parentY = parentScissor[1];
        int parentWidth = parentScissor[2];
        int parentHeight = parentScissor[3];
        applyScissor(parentX, parentY, parentWidth, parentHeight);

        if (scissorFrameStack.isEmpty()) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    // applies scissor with gui-space coordinates and sizes
    private static void applyScissor(int x, int y, int w, int h) {
        // translate upper-left to bottom-left
        final Minecraft mc = Minecraft.getMinecraft();
        final ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        final int scaleFactor = res.getScaleFactor();
        final int translatedY = mc.displayHeight - (y + h) * scaleFactor;

        GL11.glScissor(x * scaleFactor, translatedY, w * scaleFactor, h * scaleFactor);
    }

    private static int[] prepareMatrix(int x, int y, int w, int h) {
        // Get the current translation component to support the Gui being drawn at any position on the screen
        final FloatBuffer matBuf = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matBuf);
        final float xScale = matBuf.get(0);
        final float yScale = matBuf.get(5);
        final int xTranslate = (int) (matBuf.get(12) / xScale);
        final int yTranslate = (int) (matBuf.get(13) / yScale);

        x += xTranslate;
        y += yTranslate;

        x *= xScale;
        y *= yScale;

        w *= xScale;
        h *= yScale;

        return new int[] { x, y, w, h };
    }
}
