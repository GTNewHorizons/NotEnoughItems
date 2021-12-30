package codechicken.nei.drawable;

import codechicken.nei.Image;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

public class DrawableResource extends Image {

    private final ResourceLocation resourceLocation;
    private final int textureWidth;
    private final int textureHeight;

    private final int paddingTop;
    private final int paddingBottom;
    private final int paddingLeft;
    private final int paddingRight;

    public DrawableResource(ResourceLocation resourceLocation, int u, int v, int width, int height, int paddingTop, int paddingBottom, int paddingLeft, int paddingRight, int textureWidth, int textureHeight) {
        super(u, v, width, height);
        this.resourceLocation = resourceLocation;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        
        this.paddingTop = paddingTop;
        this.paddingBottom = paddingBottom;
        this.paddingLeft = paddingLeft;
        this.paddingRight = paddingRight;
    }

    public int getWidth() {
        return width + paddingLeft + paddingRight;
    }

    public int getHeight() {
        return height + paddingTop + paddingBottom;
    }

    public void draw(int xOffset, int yOffset) {
        draw(xOffset, yOffset, 0, 0, 0, 0);
    }

    public void draw(int xOffset, int yOffset, int maskTop, int maskBottom, int maskLeft, int maskRight) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(this.resourceLocation);

        final int x = xOffset + this.paddingLeft + maskLeft;
        final int y = yOffset + this.paddingTop + maskTop;
        final int textureX = this.x + maskLeft;
        final int textureY = this.y + maskTop;
        final int width = this.width - maskRight - maskLeft;
        final int height = this.height - maskBottom - maskTop;
        // drawModalRectWithCustomSizedTexture
        Gui.func_146110_a(x, y, textureX, textureY, width, height, textureWidth, textureHeight);
    }
}

