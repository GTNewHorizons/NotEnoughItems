package codechicken.nei.api;

import static codechicken.lib.gui.GuiDraw.drawRect;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.Slot;

import org.lwjgl.opengl.GL11;

import codechicken.nei.NEIClientUtils;
import codechicken.nei.PositionedStack;
import codechicken.nei.guihook.GuiContainerManager;

public class DefaultOverlayRenderer implements IRecipeOverlayRenderer {

    public DefaultOverlayRenderer(List<PositionedStack> ai, IStackPositioner positioner) {
        this.positioner = positioner;
        ingreds = new ArrayList<>();
        for (PositionedStack stack : ai) ingreds.add(stack.copy());
        ingreds = positioner.positionStacks(ingreds);
    }

    @Override
    public void renderOverlay(GuiContainerManager gui, Slot slot) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(770, 1);

        for (PositionedStack stack : ingreds) {
            if (stack.relx == slot.xDisplayPosition && stack.rely == slot.yDisplayPosition && !slot.getHasStack()) {
                GuiContainerManager.drawItem(stack.relx, stack.rely, stack.item);
                drawHover(stack.relx, stack.rely);
            }
        }

        GL11.glPopAttrib();
    }

    private static void drawHover(int x, int y) {
        NEIClientUtils.gl2DRenderContext(() -> drawRect(x, y, 16, 16, 0x66555555));
    }

    final IStackPositioner positioner;
    ArrayList<PositionedStack> ingreds;
}
