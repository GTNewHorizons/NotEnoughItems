package codechicken.nei.drawable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import codechicken.nei.Image;

public class DrawableResource extends Image {

    private final ResourceLocation resourceLocation;
    private final int textureWidth;
    private final int textureHeight;

    private final int paddingTop;
    private final int paddingBottom;
    private final int paddingLeft;
    private final int paddingRight;

    private final int sliceLeft;
    private final int sliceRight;
    private final int sliceTop;
    private final int sliceBottom;

    public DrawableResource(ResourceLocation resourceLocation, int u, int v, int width, int height, int paddingTop,
            int paddingBottom, int paddingLeft, int paddingRight, int textureWidth, int textureHeight) {
        this(
                resourceLocation,
                u,
                v,
                width,
                height,
                paddingTop,
                paddingBottom,
                paddingLeft,
                paddingRight,
                textureWidth,
                textureHeight,
                0,
                0,
                0,
                0);
    }

    // negative padding lets the texture overflow the box it is drawn into
    public DrawableResource(ResourceLocation resourceLocation, int u, int v, int width, int height, int paddingTop,
            int paddingBottom, int paddingLeft, int paddingRight, int textureWidth, int textureHeight, int sliceLeft,
            int sliceRight, int sliceTop, int sliceBottom) {
        super(u, v, width, height);

        this.sliceLeft = sliceLeft;
        this.sliceRight = sliceRight;
        this.sliceTop = sliceTop;
        this.sliceBottom = sliceBottom;

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

    public void bindTexture() {
        Minecraft.getMinecraft().getTextureManager().bindTexture(this.resourceLocation);
    }

    public void draw(int xOffset, int yOffset) {
        draw(xOffset, yOffset, 0, 0, 0, 0);
    }

    public void draw(int xOffset, int yOffset, int maskTop, int maskBottom, int maskLeft, int maskRight) {
        final boolean is2DTexture = GL11.glGetBoolean(GL11.GL_TEXTURE_2D);
        final int width = this.width - maskRight - maskLeft;
        final int height = this.height - maskBottom - maskTop;

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        bindTexture();
        drawModalRectWithCustomSizedTexture(xOffset + maskLeft, yOffset + maskTop, maskLeft, maskTop, width, height);
        if (!is2DTexture) GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    public void draw(int xOffset, int yOffset, int width, int height) {

        if (width == getWidth() && height == getHeight()) {
            draw(xOffset, yOffset);
            return;
        }

        final int sliceLeft = this.sliceLeft;
        final int sliceRight = this.sliceRight;
        final int sliceTop = this.sliceTop;
        final int sliceBottom = this.sliceBottom;
        final int middleWidth = this.width - sliceLeft - sliceRight;
        final int middleHeight = this.height - sliceTop - sliceBottom;
        final int tileWidth = width - this.paddingLeft - this.paddingRight - sliceLeft - sliceRight;
        final int tileHeight = height - this.paddingTop - this.paddingBottom - sliceTop - sliceBottom;

        if (middleWidth > 0 && middleHeight > 0 && tileWidth > 0 && tileHeight > 0) {
            final boolean is2DTexture = GL11.glGetBoolean(GL11.GL_TEXTURE_2D);
            final int tileWidthCount = tileWidth / middleWidth;
            final int remainderWidth = tileWidth - (tileWidthCount * middleWidth);
            final int tileHeightCount = tileHeight / middleHeight;
            final int remainderHeight = tileHeight - (tileHeightCount * middleHeight);

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            bindTexture();

            // middle parts
            for (int tileW = 0; tileW <= tileWidthCount; tileW++) {
                final int tileWidthSize = (tileW == tileWidthCount) ? remainderWidth : middleWidth;
                final int x = xOffset + sliceLeft + tileW * middleWidth;

                for (int tileH = 0; tileH <= tileHeightCount; tileH++) {
                    final int tileHeightSize = (tileH == tileHeightCount) ? remainderHeight : middleHeight;
                    drawModalRectWithCustomSizedTexture(
                            x,
                            yOffset + sliceTop + tileH * middleHeight,
                            sliceLeft,
                            sliceTop,
                            tileWidthSize,
                            tileHeightSize);
                }
            }

            // border top
            if (sliceTop > 0) {
                for (int tileW = 0; tileW <= tileWidthCount; tileW++) {
                    final int tileWidthSize = (tileW == tileWidthCount) ? remainderWidth : middleWidth;
                    drawModalRectWithCustomSizedTexture(
                            xOffset + sliceLeft + tileW * middleWidth,
                            yOffset,
                            sliceLeft,
                            0,
                            tileWidthSize,
                            sliceTop);
                }
            }

            // border bottom
            if (sliceBottom > 0) {
                for (int tileW = 0; tileW <= tileWidthCount; tileW++) {
                    final int tileWidthSize = (tileW == tileWidthCount) ? remainderWidth : middleWidth;
                    drawModalRectWithCustomSizedTexture(
                            xOffset + sliceLeft + tileW * middleWidth,
                            yOffset + sliceTop + tileHeight,
                            sliceLeft,
                            sliceTop + middleHeight,
                            tileWidthSize,
                            sliceBottom);
                }
            }

            // border left
            if (sliceLeft > 0) {
                for (int tileH = 0; tileH <= tileHeightCount; tileH++) {
                    final int tileHeightSize = (tileH == tileHeightCount) ? remainderHeight : middleHeight;
                    drawModalRectWithCustomSizedTexture(
                            xOffset,
                            yOffset + sliceTop + tileH * middleHeight,
                            0,
                            sliceTop,
                            sliceLeft,
                            tileHeightSize);
                }
            }

            // border right
            if (sliceRight > 0) {
                for (int tileH = 0; tileH <= tileHeightCount; tileH++) {
                    final int tileHeightSize = (tileH == tileHeightCount) ? remainderHeight : middleHeight;
                    drawModalRectWithCustomSizedTexture(
                            xOffset + sliceLeft + tileWidth,
                            yOffset + sliceTop + tileH * middleHeight,
                            sliceLeft + middleWidth,
                            sliceTop,
                            sliceRight,
                            tileHeightSize);
                }
            }

            // angle left-top
            if (sliceLeft > 0 && sliceTop > 0) {
                drawModalRectWithCustomSizedTexture(xOffset, yOffset, 0, 0, sliceLeft, sliceTop);
            }

            // angle right-top
            if (sliceRight > 0 && sliceTop > 0) {
                drawModalRectWithCustomSizedTexture(
                        xOffset + sliceLeft + tileWidth,
                        yOffset,
                        sliceLeft + middleWidth,
                        0,
                        sliceRight,
                        sliceTop);
            }

            // angle left-bottom
            if (sliceLeft > 0 && sliceBottom > 0) {
                drawModalRectWithCustomSizedTexture(
                        xOffset,
                        yOffset + sliceTop + tileHeight,
                        0,
                        sliceTop + middleHeight,
                        sliceLeft,
                        sliceBottom);
            }

            // angle right-bottom
            if (sliceRight > 0 && sliceBottom > 0) {
                drawModalRectWithCustomSizedTexture(
                        xOffset + sliceLeft + tileWidth,
                        yOffset + sliceTop + tileHeight,
                        sliceLeft + middleWidth,
                        sliceTop + middleHeight,
                        sliceRight,
                        sliceBottom);
            }

            if (!is2DTexture) GL11.glDisable(GL11.GL_TEXTURE_2D);
        }
    }

    private void drawModalRectWithCustomSizedTexture(int xOffset, int yOffset, int u, int v, int width, int height) {
        Gui.func_146110_a(
                xOffset + this.paddingLeft,
                yOffset + this.paddingTop,
                this.x + u,
                this.y + v,
                width,
                height,
                this.textureWidth,
                this.textureHeight);
    }

}
