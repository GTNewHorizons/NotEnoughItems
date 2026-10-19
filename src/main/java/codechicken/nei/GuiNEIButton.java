package codechicken.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import codechicken.nei.drawable.DrawableBuilder;
import codechicken.nei.drawable.DrawableResource;

public class GuiNEIButton extends GuiButton {

    protected static final ResourceLocation guiTex = new ResourceLocation("textures/gui/widgets.png");
    private static final DrawableResource[] BACKGROUNDS = new DrawableResource[3];

    static {
        for (int state = 0; state < BACKGROUNDS.length; state++) {
            BACKGROUNDS[state] = new DrawableBuilder(guiTex, 0, 46 + state * 20, 200, 20).setSlices(2, 2, 2, 2).build();
        }
    }

    public GuiNEIButton(int i, int j, int k, int l, int i1, String s) {
        super(i, j, k, l, i1, s);
    }

    protected DrawableResource getBackground(int mouseX, int mouseY, boolean mouseOver) {
        return BACKGROUNDS[getHoverState(mouseOver)];
    }

    public void drawButton(Minecraft minecraft, int x, int y) {
        if (!visible) return;

        boolean mouseOver = x >= xPosition && y >= yPosition && x < xPosition + width && y < yPosition + height;

        GL11.glColor4f(1, 1, 1, 1);
        getBackground(x, y, mouseOver).draw(xPosition, yPosition, width, height);
        mouseDragged(minecraft, x, y);
        drawContent(minecraft, x, y, mouseOver);
    }

    protected int getTextColour(boolean mouseOver) {

        if (!enabled) {
            return ColorUtils.buttonLabelInactive.getColor();
        } else if (mouseOver) {
            return ColorUtils.buttonLabelHover.getColor();
        }

        return ColorUtils.buttonLabelNormal.getColor();
    }

    protected void drawContent(Minecraft minecraft, int x, int y, boolean mouseOver) {
        FontRenderer fontrenderer = minecraft.fontRenderer;
        int color = getTextColour(mouseOver);

        drawCenteredString(fontrenderer, displayString, xPosition + width / 2, yPosition + (height - 8) / 2, color);
    }
}
