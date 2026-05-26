package codechicken.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import codechicken.nei.recipe.Badge;

public class GuiNEIButton extends GuiButton {

    protected static final ResourceLocation guiTex = new ResourceLocation("textures/gui/widgets.png");

    public GuiNEIButton(int i, int j, int k, int l, int i1, String s) {
        super(i, j, k, l, i1, s);
    }

    public void drawButton(Minecraft minecraft, int x, int y) {
        if (!visible) return;

        minecraft.renderEngine.bindTexture(guiTex);
        GL11.glColor4f(1, 1, 1, 1);
        boolean mouseOver = x >= xPosition && y >= yPosition && x < xPosition + width && y < yPosition + height;
        int k = getHoverState(mouseOver);
        drawTexturedModalRect(xPosition, yPosition, 0, 46 + k * 20, width / 2, height / 2); // top left
        drawTexturedModalRect(xPosition + width / 2, yPosition, 200 - width / 2, 46 + k * 20, width / 2, height / 2); // top
                                                                                                                      // right
        drawTexturedModalRect(
                xPosition,
                yPosition + height / 2,
                0,
                46 + k * 20 + 20 - height / 2,
                width / 2,
                height / 2); // bottom left
        drawTexturedModalRect(
                xPosition + width / 2,
                yPosition + height / 2,
                200 - width / 2,
                46 + k * 20 + 20 - height / 2,
                width / 2,
                height / 2); // bottom right
        mouseDragged(minecraft, x, y);

        drawContent(minecraft, x, y, mouseOver);
    }

    protected int getTextColour(boolean mouseOver) {
        int color = Badge
                .getHexValue(NEIClientUtils.getTextColorOrDefault("string.button.recipe.color", "E0E0E0"), 0xe0e0e0);

        if (!enabled) {
            color = Badge.getHexValue(
                    NEIClientUtils.getTextColorOrDefault("string.button.recipe.color.disabled", "A0A0A0"),
                    0xffa0a0a0);
        } else if (mouseOver) {
            color = Badge.getHexValue(
                    NEIClientUtils.getTextColorOrDefault("string.button.recipe.color.hover", "FFFFA0"),
                    0xffffa0);
        }

        return color;
    }

    protected void drawContent(Minecraft minecraft, int y, int x, boolean mouseOver) {
        FontRenderer fontrenderer = minecraft.fontRenderer;
        int color = getTextColour(mouseOver);

        drawCenteredString(fontrenderer, displayString, xPosition + width / 2, yPosition + (height - 8) / 2, color);
    }
}
