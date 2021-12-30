package codechicken.nei.drawable;

import net.minecraft.util.ResourceLocation;

public class DrawableBuilder {
    private final ResourceLocation resourceLocation;
    private int u;
    private int v;
    private int width;
    private int height;
    private int textureWidth = 256;
    private int textureHeight = 256;
    private int paddingTop = 0;
    private int paddingBottom = 0;
    private int paddingLeft = 0;
    private int paddingRight = 0;

    public DrawableBuilder(String resourceLocation, int u, int v, int width, int height) {
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
        this.resourceLocation = new ResourceLocation(resourceLocation);
    }

    public DrawableBuilder setTextureSize(int width, int height) {
        this.textureWidth = width;
        this.textureHeight = height;
        return this;
    }

    public DrawableBuilder addPadding(int paddingTop, int paddingBottom, int paddingLeft, int paddingRight) {
        this.paddingTop = paddingTop;
        this.paddingBottom = paddingBottom;
        this.paddingLeft = paddingLeft;
        this.paddingRight = paddingRight;
        return this;
    }

    public DrawableBuilder trim(int trimTop, int trimBottom, int trimLeft, int trimRight) {
        this.u += trimLeft;
        this.v += trimTop;
        this.width -= trimLeft + trimRight;
        this.height -= trimTop + trimBottom;
        return this;
    }

    public DrawableResource build() {
        return new DrawableResource(resourceLocation, u, v, width, height, paddingTop, paddingBottom, paddingLeft, paddingRight, textureWidth, textureHeight);
    }

}
