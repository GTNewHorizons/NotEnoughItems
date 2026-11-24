package codechicken.nei.theme;

import net.minecraft.util.ResourceLocation;

import codechicken.nei.drawable.DrawableResource;

public final class UISprite {

    public static final int[] DEFAULT_SPACE = new int[] { 0, 0, 0, 0 };

    private UISprite() {}

    public static DrawableResource of(String name, int w, int h, int[] margin, int[] slice) {
        return frames(name, w, h, 1, margin, slice)[0];
    }

    public static DrawableResource of(String name, int w, int h) {
        return of(name, w, h, DEFAULT_SPACE, DEFAULT_SPACE);
    }

    public static DrawableResource[] frames(String name, int w, int h, int count) {
        return frames(name, w, h, count, DEFAULT_SPACE, DEFAULT_SPACE);
    }

    // frames are stacked top to bottom and each includes the margin; {left, top, right, bottom}
    public static DrawableResource[] frames(String name, int w, int h, int count, int[] margin, int[] slice) {
        final ResourceLocation texture = new ResourceLocation("nei", "textures/gui/" + name + ".png");
        final int frameWidth = w + margin[0] + margin[2];
        final int frameHeight = h + margin[1] + margin[3];
        final DrawableResource[] frames = new DrawableResource[count];

        for (int i = 0; i < count; i++) {
            frames[i] = new DrawableResource(
                    texture,

                    0,
                    i * frameHeight,
                    frameWidth,
                    frameHeight,

                    -margin[1],
                    -margin[3],
                    -margin[0],
                    -margin[2],

                    frameWidth,
                    frameHeight * count,

                    slice[0] + margin[0],
                    slice[2] + margin[2],
                    slice[1] + margin[1],
                    slice[3] + margin[3]);
        }

        return frames;
    }

}
